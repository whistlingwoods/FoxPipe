package org.schabi.newpipe.util;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.download.dao.OfflineFileMappingDAO;
import org.schabi.newpipe.database.download.model.OfflineFileMappingEntity;
import org.schabi.newpipe.database.stream.dao.StreamDAO;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.Image;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Utility to repair corrupted StreamEntity metadata by extracting it from downloaded files.
 * This is useful when database entries have empty/corrupted metadata but the downloaded
 * files contain correct embedded metadata (ID3 tags, etc.).
 */
public final class StreamMetadataRepair {
    private static final String TAG = "StreamMetadataRepair";

    private StreamMetadataRepair() {
        // Utility class
    }

    /**
     * Repairs all corrupted StreamEntity entries by extracting metadata from their
     * corresponding offline files.
     *
     * @param context the context
     * @return Single that emits the number of repaired entries
     */
    public static Single<Integer> repairAllCorruptedEntries(@NonNull final Context context) {
        return Single.fromCallable(() -> {
            final AppDatabase database = NewPipeDatabase.getInstance(context);
            final StreamDAO streamDAO = database.streamDAO();
            final OfflineFileMappingDAO mappingDAO = database.offlineFileMappingDAO();

            // Get all offline file mappings
            final List<OfflineFileMappingEntity> mappings =
                    mappingDAO.getAllAvailableMappings().blockingFirst();

            int repairedCount = 0;

            for (final OfflineFileMappingEntity mapping : mappings) {
                try {
                    // Get the StreamEntity for this mapping
                    final List<StreamEntity> entities = streamDAO.getStream(
                            mapping.getServiceId(), mapping.getStreamUrl()).blockingFirst();

                    if (entities.isEmpty()) {
                        Log.w(TAG, "No StreamEntity found for: " + mapping.getStreamUrl());
                        continue;
                    }

                    final StreamEntity entity = entities.get(0);

                    // Check if entity needs repair (has empty/corrupted metadata)
                    if (entity.getTitle() != null && !entity.getTitle().isEmpty()
                            && entity.getUploader() != null && !entity.getUploader().isEmpty()
                            && entity.getThumbnailUrl() != null
                            && !entity.getThumbnailUrl().isEmpty()) {
                        // Metadata looks good, skip
                        continue;
                    }

                    Log.i(TAG, "Repairing metadata for: " + entity.getUrl());
                    Log.d(TAG, "Current - Title: '" + entity.getTitle()
                            + "', Uploader: '" + entity.getUploader() + "'");

                    // Extract metadata from the offline file
                    final String fileUri = mapping.getLocalFileUri();
                    final MetadataExtractionResult extracted =
                            extractMetadataFromFile(context, fileUri);

                    if (extracted == null) {
                        Log.w(TAG, "Failed to extract metadata from file: " + fileUri);
                        continue;
                    }

                    // Update the entity with extracted metadata
                    boolean updated = false;

                    if ((entity.getTitle() == null || entity.getTitle().isEmpty())
                            && extracted.title != null && !extracted.title.isEmpty()) {
                        entity.setTitle(extracted.title);
                        updated = true;
                        Log.i(TAG, "Updated title to: " + extracted.title);
                    }

                    if ((entity.getUploader() == null || entity.getUploader().isEmpty())
                            && extracted.artist != null && !extracted.artist.isEmpty()) {
                        entity.setUploader(extracted.artist);
                        updated = true;
                        Log.i(TAG, "Updated uploader to: " + extracted.artist);
                    }

                    // Save album art if available and entity has no thumbnail
                    if ((entity.getThumbnailUrl() == null || entity.getThumbnailUrl().isEmpty()
                            || entity.getThumbnailUrl().startsWith("data:"))
                            && extracted.albumArtBytes != null
                            && extracted.albumArtBytes.length > 0) {
                        final String thumbnailPath = saveAlbumArtToFile(
                                context, entity.getUrl(), extracted.albumArtBytes);
                        if (thumbnailPath != null) {
                            entity.setThumbnailUrl(thumbnailPath);
                            updated = true;
                            Log.i(TAG, "Saved album art to: " + thumbnailPath);
                        }
                    }

                    if (updated) {
                        // Update the database
                        streamDAO.upsert(entity);
                        repairedCount++;
                        Log.i(TAG, "Successfully repaired metadata for: " + entity.getTitle());
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "Error repairing metadata for mapping: "
                            + mapping.getStreamUrl(), e);
                }
            }

            Log.i(TAG, "Metadata repair complete. Repaired " + repairedCount + " entries.");
            return repairedCount;
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Extracts metadata from a media file.
     *
     * @param context the context
     * @param fileUri the file URI
     * @return extracted metadata, or null if extraction failed
     */
    private static MetadataExtractionResult extractMetadataFromFile(
            @NonNull final Context context,
            @NonNull final String fileUri) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(context, Uri.parse(fileUri));

            final String title = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_TITLE);
            final String artist = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_ARTIST);
            final String album = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_ALBUM);
            final byte[] albumArt = retriever.getEmbeddedPicture();

            if (title == null && artist == null && albumArt == null) {
                return null;
            }

            final MetadataExtractionResult result = new MetadataExtractionResult();
            result.title = title;
            result.artist = artist;
            result.album = album;
            result.albumArtBytes = albumArt;

            return result;
        } catch (final Exception e) {
            Log.e(TAG, "Failed to extract metadata from file: " + fileUri, e);
            return null;
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (final Exception e) {
                    // Log but don't fail - resource cleanup errors shouldn't block execution
                    Log.w(TAG, "Error releasing MediaMetadataRetriever", e);
                }
            }
        }
    }

    /**
     * Saves album art bytes to a file and returns the file path.
     *
     * @param context   the context
     * @param streamUrl the stream URL (used to generate unique filename)
     * @param artBytes  the album art bytes
     * @return the file path, or null if save failed
     */
    private static String saveAlbumArtToFile(
            @NonNull final Context context,
            @NonNull final String streamUrl,
            @NonNull final byte[] artBytes) {
        try {
            // Create thumbnails directory
            final File thumbnailsDir = new File(context.getFilesDir(), "thumbnails");
            if (!thumbnailsDir.exists() && !thumbnailsDir.mkdirs()) {
                Log.e(TAG, "Failed to create thumbnails directory");
                return null;
            }

            // Generate filename from stream URL hash
            final String filename = "thumb_" + Math.abs(streamUrl.hashCode()) + ".jpg";
            final File thumbnailFile = new File(thumbnailsDir, filename);

            // Write album art to file
            try (FileOutputStream fos = new FileOutputStream(thumbnailFile)) {
                fos.write(artBytes);
            }

            return "file://" + thumbnailFile.getAbsolutePath();
        } catch (final Exception e) {
            Log.e(TAG, "Failed to save album art", e);
            return null;
        }
    }

    /**
     * Result of metadata extraction from a file.
     */
    private static class MetadataExtractionResult {
        String title;
        String artist;
        String album;
        byte[] albumArtBytes;
    }
}
