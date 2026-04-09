package org.schabi.newpipe.util;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.database.stream.dao.StreamDAO;
import org.schabi.newpipe.database.stream.model.StreamEntity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Utility class for extracting metadata from offline audio files
 * and updating the database before playlist display.
 */
public final class OfflineMetadataExtractor {
    private static final String TAG = "OfflineMetadataExtractor";

    private OfflineMetadataExtractor() {
    }

    /**
     * Scans offline files for a list of streams and updates database with extracted metadata.
     *
     * @param context application context
     * @param streams list of stream entities to check
     * @return Completable that completes when all metadata is extracted
     */
    public static Completable extractAndUpdateMetadata(@NonNull final Context context,
                                                        @NonNull final List<StreamEntity> streams) {
        return Completable.fromAction(() -> {
            final StreamDAO streamDAO = NewPipeDatabase.getInstance(context).streamDAO();

            for (final StreamEntity stream : streams) {
                // Skip if stream already has valid metadata
                if (stream.getTitle() != null && !stream.getTitle().isEmpty()
                        && stream.getThumbnailUrl() != null
                        && !stream.getThumbnailUrl().isEmpty()
                        && !stream.getThumbnailUrl().startsWith("data:")) {
                    continue;
                }

                // Check if offline file exists
                final String offlineUri;
                try {
                    offlineUri = OfflinePlaybackHelper.getOfflineFileUriBlocking(
                            context, stream.getServiceId(), stream.getUrl());
                    if (offlineUri == null) {
                        continue;
                    }
                } catch (final Exception e) {
                    continue;
                }

                // Extract metadata from file
                MediaMetadataRetriever retriever = null;
                try {
                    retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(context, Uri.parse(offlineUri));

                    final String embeddedTitle = retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_TITLE);
                    final String embeddedArtist = retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_ARTIST);
                    final byte[] artBytes = retriever.getEmbeddedPicture();

                    boolean needsUpdate = false;

                    // Update title if empty
                    if ((stream.getTitle() == null || stream.getTitle().isEmpty())
                            && embeddedTitle != null && !embeddedTitle.isEmpty()) {
                        stream.setTitle(embeddedTitle);
                        needsUpdate = true;
                        Log.i(TAG, "Extracted title: " + embeddedTitle);
                    }

                    // Update artist if empty
                    if ((stream.getUploader() == null || stream.getUploader().isEmpty())
                            && embeddedArtist != null && !embeddedArtist.isEmpty()) {
                        stream.setUploader(embeddedArtist);
                        needsUpdate = true;
                        Log.i(TAG, "Extracted artist: " + embeddedArtist);
                    }

                    // Update thumbnail if missing or is data URI
                    if ((stream.getThumbnailUrl() == null
                            || stream.getThumbnailUrl().isEmpty()
                            || stream.getThumbnailUrl().startsWith("data:"))
                            && artBytes != null && artBytes.length > 0) {
                        final String thumbnailPath = saveAlbumArtToFile(
                                context, stream.getUrl(), artBytes);
                        if (thumbnailPath != null) {
                            stream.setThumbnailUrl(thumbnailPath);
                            needsUpdate = true;
                            Log.i(TAG, "Extracted album art, size: " + artBytes.length);
                        }
                    }

                    // Update database if any changes were made
                    if (needsUpdate) {
                        streamDAO.upsert(stream);
                        Log.i(TAG, "Updated metadata for: " + stream.getUrl());
                    }

                } catch (final Exception e) {
                    Log.w(TAG, "Failed to extract metadata for "
                            + stream.getUrl() + ": " + e.getMessage());
                } finally {
                    if (retriever != null) {
                        try {
                            retriever.release();
                        } catch (final Exception e) {
                            // Log but don't fail resource cleanup errors shouldn't block execution
                            Log.w(TAG, "Error releasing MediaMetadataRetriever", e);
                        }
                    }
                }
            }
        }).subscribeOn(Schedulers.io());
    }

    private static String saveAlbumArtToFile(@NonNull final Context context,
                                             @NonNull final String streamUrl,
                                             @NonNull final byte[] artBytes) {
        try {
            final File thumbnailDir = new File(context.getCacheDir(), "offline_thumbnails");
            if (!thumbnailDir.exists() && !thumbnailDir.mkdirs()) {
                return null;
            }

            final String filename = String.valueOf(streamUrl.hashCode()) + ".jpg";
            final File thumbnailFile = new File(thumbnailDir, filename);

            try (FileOutputStream fos = new FileOutputStream(thumbnailFile)) {
                fos.write(artBytes);
            }

            return "file://" + thumbnailFile.getAbsolutePath();
        } catch (final IOException e) {
            Log.w(TAG, "Failed to save album art: " + e.getMessage());
            return null;
        }
    }
}
