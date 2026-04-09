/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.streams.io.StoredDirectoryHelper;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.FilenameUtils;
import org.schabi.newpipe.util.ListHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.get.MissionRecoveryInfo;
import us.shandian.giga.postprocessing.Postprocessing;
import us.shandian.giga.service.DownloadManagerService;

/**
 * Initiates bulk downloads for all items in a playlist.
 * Fetches full StreamInfo for each item and queues downloads progressively.
 */
public final class BulkDownloadInitiator {
    private static final String TAG = "BulkDownloadInitiator";

    private BulkDownloadInitiator() {
        // Utility class
    }

    /**
     * Configuration for bulk downloads.
     */
    public static class BulkDownloadConfig {
        public final boolean downloadAudio;
        public final boolean tagAudioMetadata;
        public final boolean createOfflineMappings;
        public final String downloadFolder;

        /**
         * Creates a bulk download configuration.
         *
         * @param downloadAudio         true to download audio, false for video
         * @param tagAudioMetadata      true to tag audio files with playlist metadata
         * @param createOfflineMappings true to create offline file mappings
         * @param downloadFolder        the download folder path
         */
        public BulkDownloadConfig(final boolean downloadAudio,
                                  final boolean tagAudioMetadata,
                                  final boolean createOfflineMappings,
                                  final String downloadFolder) {
            this.downloadAudio = downloadAudio;
            this.tagAudioMetadata = tagAudioMetadata;
            this.createOfflineMappings = createOfflineMappings;
            this.downloadFolder = downloadFolder;
        }
    }

    /**
     * Starts a bulk download of all items in a playlist.
     * Fetches StreamInfo for each item and queues downloads progressively.
     *
     * <p>This implementation:
     * <ul>
     *   <li>Generates numbered filenames (e.g., "01 - Video Title.mp4")</li>
     *   <li>Creates PlaylistMetadata for each item</li>
     *   <li>Sets up audio tagging post-processing if requested</li>
     *   <li>Queues each download via DownloadManagerService</li>
     *   <li>Creates offline file mappings if requested</li>
     * </ul>
     *
     * @param context      the context
     * @param playlistInfo the playlist information (nullable - basic fields are extracted)
     * @param streamItems  all stream items to download
     * @param config       bulk download configuration
     */
    public static void startBulkDownload(
            @NonNull final Context context,
            @androidx.annotation.Nullable final Object playlistInfo,
            @NonNull final List<StreamInfoItem> streamItems,
            @NonNull final BulkDownloadConfig config) {

        if (streamItems.isEmpty()) {
            Toast.makeText(context, "No items to download", Toast.LENGTH_SHORT).show();
            return;
        }

        // Extract playlist information
        final BulkDownloadDialog.SerializablePlaylistInfo serializedInfo =
            (BulkDownloadDialog.SerializablePlaylistInfo) playlistInfo;

        if (serializedInfo == null) {
            Toast.makeText(context, "Error: Missing playlist information",
                Toast.LENGTH_SHORT).show();
            return;
        }

        final String playlistName = serializedInfo.name;
        final int serviceId = serializedInfo.serviceId;
        final String thumbnailUrl = serializedInfo.thumbnailUrl;

        // Show initial toast
        final String format = config.downloadAudio ? "Audio" : "Video";
        Toast.makeText(context,
            String.format("Starting bulk download of %d %s files...",
                streamItems.size(), format),
            Toast.LENGTH_SHORT).show();

        // Bind to DownloadManagerService and start queueing downloads
        final Intent intent = new Intent(context, DownloadManagerService.class);
        final ServiceConnection[] connection = new ServiceConnection[1];
        final android.os.Handler timeoutHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
        final boolean[] isConnected = {false};

        // Timeout runnable - unbind if service doesn't connect within 5 seconds
        final Runnable timeoutRunnable = () -> {
            if (!isConnected[0] && connection[0] != null) {
                Log.w(TAG, "Service binding timeout - unbinding");
                try {
                    context.unbindService(connection[0]);
                } catch (final IllegalArgumentException e) {
                    // Service was never bound
                }
                connection[0] = null;
                Toast.makeText(context, "Download service connection timeout",
                    Toast.LENGTH_SHORT).show();
            }
        };

        connection[0] = new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName name, final IBinder service) {
                isConnected[0] = true;
                timeoutHandler.removeCallbacks(timeoutRunnable);

                final DownloadManagerService.DownloadManagerBinder binder =
                    (DownloadManagerService.DownloadManagerBinder) service;

                final StoredDirectoryHelper mainStorage = config.downloadAudio
                    ? binder.getMainStorageAudio()
                    : binder.getMainStorageVideo();

                if (mainStorage == null) {
                    Toast.makeText(context, "Error: Download storage not configured",
                        Toast.LENGTH_LONG).show();
                    context.unbindService(this);
                    connection[0] = null;
                    return;
                }

                // Start queueing downloads progressively
                // Don't unbind immediately - let queueDownloads complete first
                queueDownloads(context, streamItems, config, serviceId,
                    playlistName, thumbnailUrl, mainStorage, connection[0]);
            }

            @Override
            public void onServiceDisconnected(final ComponentName name) {
                // Service disconnected unexpectedly
                isConnected[0] = false;
                timeoutHandler.removeCallbacks(timeoutRunnable);
                connection[0] = null;
                Log.w(TAG, "Download service disconnected unexpectedly");
            }
        };

        // Attempt to bind service with timeout
        if (context.bindService(intent, connection[0], Context.BIND_AUTO_CREATE)) {
            // Schedule timeout
            timeoutHandler.postDelayed(timeoutRunnable, 5000);  // 5 second timeout
        } else {
            // Binding failed immediately
            Toast.makeText(context, "Failed to bind to download service",
                Toast.LENGTH_SHORT).show();
            connection[0] = null;
        }
    }

    /**
     * Queues downloads for all stream items progressively.
     * Fetches StreamInfo for each item and queues the download.
     *
     * @param context       the context
     * @param streamItems   list of stream items to download
     * @param config        bulk download configuration
     * @param serviceId     the service ID
     * @param playlistName  the playlist name
     * @param thumbnailUrl  the playlist thumbnail URL
     * @param mainStorage   the storage directory helper
     * @param connection    the service connection to unbind when complete
     */
    private static void queueDownloads(
            @NonNull final Context context,
            @NonNull final List<StreamInfoItem> streamItems,
            @NonNull final BulkDownloadConfig config,
            final int serviceId,
            final String playlistName,
            final String thumbnailUrl,
            @NonNull final StoredDirectoryHelper mainStorage,
            @NonNull final android.content.ServiceConnection connection) {

        final CompositeDisposable disposables = new CompositeDisposable();
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failureCount = new AtomicInteger(0);
        final int totalCount = streamItems.size();

        // Create subdirectory for this playlist
        // For SAF storage, we get a DocumentFile; for file:// storage, we get null
        // and use a subdirectory StoredDirectoryHelper instead
        final StoredDirectoryHelper subdirStorage;
        final DocumentFile safSubdir;
        try {
            final SubdirectoryResult result = createPlaylistSubdirectory(
                context, mainStorage, playlistName);
            subdirStorage = result.directoryHelper;
            safSubdir = result.safSubdirectory;
        } catch (final IOException e) {
            Log.e(TAG, "Failed to create playlist subdirectory", e);
            Toast.makeText(context,
                "Failed to create playlist directory: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
            return;
        }

        // Process each stream item sequentially to avoid overwhelming the server
        disposables.add(
            Observable.range(0, streamItems.size())
                .concatMapCompletable(index -> {
                    final StreamInfoItem item = streamItems.get(index);
                    final int position = index + 1;

                    Log.i(TAG, String.format("Processing item %d/%d: %s (URL: %s)",
                        position, totalCount, item.getName(), item.getUrl()));

                    // Validate item URL before attempting to fetch StreamInfo
                    if (item.getUrl() != null && item.getUrl().contains("/channel/")) {
                        Log.w(TAG, String.format("Skipping invalid item %d: channel URL in "
                            + "playlist: %s (corrupt playlist data)", position, item.getUrl()));
                        failureCount.incrementAndGet();
                        return io.reactivex.rxjava3.core.Completable.complete();
                    }

                    // Add delay between requests to avoid rate limiting (skip delay for first item)
                    final io.reactivex.rxjava3.core.Completable delayCompletable =
                        index == 0
                        ? io.reactivex.rxjava3.core.Completable.complete()
                        : io.reactivex.rxjava3.core.Completable.timer(
                            1, java.util.concurrent.TimeUnit.SECONDS, Schedulers.io());

                    return delayCompletable.andThen(ExtractorHelper.getStreamInfo(
                        serviceId,
                        item.getUrl(),
                        false
                    )
                    .subscribeOn(Schedulers.io())
                    .doOnSuccess(fetchedStreamInfo -> {
                        try {
                            Log.d(TAG, "=== Item " + position + " Debug ===");
                            Log.d(TAG, "StreamInfoItem from playlist - Name: '"
                                + item.getName() + "', Uploader: '" + item.getUploaderName()
                                + "', UploaderUrl: '" + item.getUploaderUrl() + "'");

                            // Validate stream has downloadable content
                            if (fetchedStreamInfo == null) {
                                Log.w(TAG, "Skipping null StreamInfo for item " + position);
                                failureCount.incrementAndGet();
                                return;
                            }

                            Log.d(TAG, "Fetched StreamInfo - Name: '"
                                + fetchedStreamInfo.getName() + "', Uploader: '"
                                + fetchedStreamInfo.getUploaderName() + "', UploaderUrl: '"
                                + fetchedStreamInfo.getUploaderUrl() + "'");

                            // Fix corrupted StreamInfo - if fetched data has channel URL as name,
                            // create corrected StreamInfo using StreamInfoItem data
                            StreamInfo streamInfo = fetchedStreamInfo;
                            if (fetchedStreamInfo.getName() != null
                                    && fetchedStreamInfo.getName().contains("/channel/")) {
                                Log.w(TAG, "Detected corrupted StreamInfo name (channel URL): "
                                    + fetchedStreamInfo.getName()
                                    + ", fixing with StreamInfoItem data: " + item.getName());

                                // Create new StreamInfo with correct constructor params
                                // (serviceId, url, originalUrl, streamType, id, name, ageLimit)
                                streamInfo = new StreamInfo(
                                    fetchedStreamInfo.getServiceId(),
                                    fetchedStreamInfo.getUrl(),
                                    fetchedStreamInfo.getUrl(),      // originalUrl
                                    fetchedStreamInfo.getStreamType(),
                                    fetchedStreamInfo.getUrl(),      // id
                                    item.getName(),                  // name (from StreamInfoItem)
                                    0                                // ageLimit
                                );

                                // Set additional fields using setters
                                streamInfo.setUploaderName(
                                    item.getUploaderName() != null ? item.getUploaderName() : "");
                                streamInfo.setUploaderUrl(
                                    item.getUploaderUrl() != null ? item.getUploaderUrl() : "");
                                streamInfo.setDuration(fetchedStreamInfo.getDuration());

                                // Copy stream lists and other data
                                streamInfo.setAudioStreams(
                                    fetchedStreamInfo.getAudioStreams());
                                streamInfo.setVideoStreams(
                                    fetchedStreamInfo.getVideoStreams());
                                streamInfo.setVideoOnlyStreams(
                                    fetchedStreamInfo.getVideoOnlyStreams());
                                streamInfo.setThumbnails(item.getThumbnails());
                            }

                            // Check if URL is a channel URL (invalid for downloading)
                            if (streamInfo.getUrl() != null
                                    && streamInfo.getUrl().contains("/channel/")) {
                                Log.w(TAG, "Skipping invalid item " + position
                                    + ": channel URL in playlist: " + streamInfo.getUrl());
                                failureCount.incrementAndGet();
                                return;
                            }

                            // Check if stream has any downloadable content
                            final boolean hasAudio = streamInfo.getAudioStreams() != null
                                && !streamInfo.getAudioStreams().isEmpty();
                            final boolean hasVideo = streamInfo.getVideoStreams() != null
                                && !streamInfo.getVideoStreams().isEmpty();
                            final boolean hasVideoOnly = streamInfo.getVideoOnlyStreams() != null
                                && !streamInfo.getVideoOnlyStreams().isEmpty();

                            if (!hasAudio && !hasVideo && !hasVideoOnly) {
                                Log.w(TAG, "Skipping item " + position
                                    + ": no downloadable streams for: " + streamInfo.getName());
                                failureCount.incrementAndGet();
                                return;
                            }

                            queueSingleDownload(context, streamInfo, item, config, serviceId,
                                playlistName, thumbnailUrl, position, totalCount,
                                mainStorage, subdirStorage, safSubdir);
                            successCount.incrementAndGet();
                        } catch (final Exception e) {
                            Log.e(TAG, "Failed to queue download for item " + position
                                + ": " + e.getMessage(), e);
                            failureCount.incrementAndGet();
                        }
                    })
                    .doOnError(e -> {
                        Log.e(TAG, "Failed to fetch stream info for: " + item.getName(), e);
                        failureCount.incrementAndGet();
                    })
                    .ignoreElement()
                    .onErrorComplete());
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        // All items processed successfully
                        Toast.makeText(context,
                            String.format("Bulk download queued: %d downloads started, "
                                + "%d failed", successCount.get(), failureCount.get()),
                            Toast.LENGTH_LONG).show();
                        disposables.dispose();
                        // Unbind service after all downloads are queued
                        if (connection != null) {
                            try {
                                context.unbindService(connection);
                            } catch (final IllegalArgumentException e) {
                                // Service was already unbound
                                Log.w(TAG, "Service was already unbound");
                            }
                        }
                    },
                    error -> {
                        // Unexpected error (individual errors are handled in doOnError)
                        Log.e(TAG, "Unexpected error during bulk download", error);
                        Toast.makeText(context,
                            String.format("Bulk download completed with errors: "
                                + "%d succeeded, %d failed",
                                successCount.get(), failureCount.get()),
                            Toast.LENGTH_LONG).show();
                        disposables.dispose();
                        // Unbind service even on error
                        if (connection != null) {
                            try {
                                context.unbindService(connection);
                            } catch (final IllegalArgumentException e) {
                                // Service was already unbound
                                Log.w(TAG, "Service was already unbound");
                            }
                        }
                    }
                )
        );
    }

    /**
     * Queues a single download.
     *
     * @param context       the context
     * @param streamInfo    the stream info (for stream URLs only)
     * @param streamItem    the stream info item (for metadata: title, artist, thumbnail)
     * @param config        bulk download configuration
     * @param serviceId     the service ID
     * @param playlistName  the playlist name
     * @param thumbnailUrl  the playlist thumbnail URL
     * @param position      the position in playlist (1-based)
     * @param totalCount    total number of items
     * @param mainStorage   the main storage directory helper
     * @param subdirStorage the subdirectory helper (for file:// paths, null for SAF)
     * @param safSubdir     the SAF subdirectory DocumentFile (for SAF, null for file://)
     * @throws IOException if file creation or download queueing fails
     */
    private static void queueSingleDownload(
            @NonNull final Context context,
            @NonNull final StreamInfo streamInfo,
            @NonNull final StreamInfoItem streamItem,
            @NonNull final BulkDownloadConfig config,
            final int serviceId,
            final String playlistName,
            final String thumbnailUrl,
            final int position,
            final int totalCount,
            @NonNull final StoredDirectoryHelper mainStorage,
            @androidx.annotation.Nullable final StoredDirectoryHelper subdirStorage,
            @androidx.annotation.Nullable final DocumentFile safSubdir) throws IOException {

        // Select best quality stream
        final List<?> streams;
        final Object selectedStream;
        final char kind;
        String psName = null;
        final String[] psArgs;

        if (config.downloadAudio) {
            kind = 'a';
            streams = ListHelper.getStreamsOfSpecifiedDelivery(
                streamInfo.getAudioStreams(),
                org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP
            );

            if (streams == null || streams.isEmpty()) {
                throw new IOException("No audio streams available");
            }

            final int streamIndex = ListHelper.getDefaultAudioFormat(context,
                (List<AudioStream>) streams);
            selectedStream = ((List<AudioStream>) streams).get(streamIndex);

            // Determine post-processing for audio
            final MediaFormat format = ((AudioStream) selectedStream).getFormat();
            if (format == MediaFormat.M4A) {
                psName = Postprocessing.ALGORITHM_M4A_NO_DASH;
            } else if (format == MediaFormat.WEBMA_OPUS) {
                psName = Postprocessing.ALGORITHM_OGG_FROM_WEBM_DEMUXER;
            }

            // Add audio metadata tagging if requested
            if (config.tagAudioMetadata && psName != null) {
                // Store the original algorithm name before changing psName
                final String formatConversionAlgorithm = psName;

                // Use StreamInfoItem data for metadata (correct data from playlist)
                // NOT StreamInfo which may be corrupted from YouTube
                // Select highest quality thumbnail for embedded album art in downloads
                final String streamThumbnailUrl = selectHighestQualityThumbnail(
                        streamItem.getThumbnails());

                final String title = streamItem.getName();
                final String artist = cleanArtistName(streamItem.getUploaderName());

                Log.d(TAG, "Audio metadata tagging - Title: " + title
                    + ", Artist: " + artist
                    + ", Thumbnail: " + (streamThumbnailUrl != null ? streamThumbnailUrl : "null"));

                // Create composite post-processing: format conversion + metadata tagging
                psName = Postprocessing.ALGORITHM_COMPOSITE;
                psArgs = new String[] {
                    formatConversionAlgorithm,
                    Postprocessing.ALGORITHM_AUDIO_METADATA_TAGGING,
                    String.valueOf(position),
                    playlistName,
                    artist,
                    title,
                    streamThumbnailUrl != null ? streamThumbnailUrl : ""
                };
            } else {
                psArgs = null;
            }
        } else {
            kind = 'v';
            streams = ListHelper.getStreamsOfSpecifiedDelivery(
                streamInfo.getVideoOnlyStreams(),
                org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP
            );

            if (streams == null || streams.isEmpty()) {
                throw new IOException("No video streams available");
            }

            final int streamIndex = ListHelper.getDefaultResolutionIndex(context,
                (List<VideoStream>) streams);
            selectedStream = ((List<VideoStream>) streams).get(streamIndex);
            psArgs = null;
        }

        // Generate numbered filename: "01 - Video Title.ext"
        // Use streamItem.getName() for correct title, not streamInfo which may be corrupted
        final String cleanTitle = FilenameUtils.createFilename(context, streamItem.getName());
        final String ext = ((org.schabi.newpipe.extractor.stream.Stream) selectedStream)
            .getFormat().getSuffix();
        final String paddedPosition = String.format("%0" + String.valueOf(totalCount).length()
            + "d", position);
        final String filename = paddedPosition + " - " + cleanTitle + "." + ext;
        final String mimeType = ((org.schabi.newpipe.extractor.stream.Stream) selectedStream)
            .getFormat().getMimeType();

        // Create storage for the file
        final StoredFileHelper storage;
        if (safSubdir != null) {
            // SAF storage: create file in subdirectory manually
            final DocumentFile file = safSubdir.createFile(mimeType, filename);
            if (file == null) {
                throw new IOException("Failed to create file in SAF subdirectory: " + filename);
            }
            storage = new StoredFileHelper(context, file.getUri(), mimeType);
        } else if (subdirStorage != null) {
            // file:// storage with subdirectory
            storage = subdirStorage.createFile(filename, mimeType);
            if (storage == null) {
                throw new IOException("Failed to create file: " + filename);
            }
        } else {
            // Fallback: use main storage directly (shouldn't happen)
            storage = mainStorage.createFile(filename, mimeType);
            if (storage == null) {
                throw new IOException("Failed to create file: " + filename);
            }
        }

        // Save StreamEntity to database for offline playback
        // Use streamItem data (correct metadata), not streamInfo (may be corrupted)
        try {
            final org.schabi.newpipe.database.stream.model.StreamEntity streamEntity =
                new org.schabi.newpipe.database.stream.model.StreamEntity(streamItem);
            final org.schabi.newpipe.database.stream.dao.StreamDAO streamDAO =
                org.schabi.newpipe.NewPipeDatabase.getInstance(context).streamDAO();
            streamDAO.upsert(streamEntity);
            Log.d(TAG, "Saved StreamEntity to database: " + streamItem.getName());
        } catch (final Exception e) {
            Log.w(TAG, "Failed to save StreamEntity to database: " + e.getMessage(), e);
            // Continue with download even if database save fails
        }

        // Create playlist metadata - use streamItem for correct metadata
        final DownloadMission.PlaylistMetadata playlistMetadata = createPlaylistMetadata(
            serviceId, playlistName, streamItem, position, thumbnailUrl
        );

        // Prepare download parameters
        final String[] urls = new String[] {
            ((org.schabi.newpipe.extractor.stream.Stream) selectedStream).getContent()
        };
        final ArrayList<MissionRecoveryInfo> recoveryInfo = new ArrayList<>();
        recoveryInfo.add(new MissionRecoveryInfo(
            (org.schabi.newpipe.extractor.stream.Stream) selectedStream
        ));

        // Start the download
        DownloadManagerService.startMission(
            context,
            urls,
            storage,
            kind,
            3, // default thread count
            streamInfo,
            psName,
            psArgs,
            0, // nearLength - not known
            recoveryInfo,
            playlistMetadata,
            config.createOfflineMappings
        );

        Log.i(TAG, "Queued download: " + filename);
    }

    /**
     * Creates playlist metadata for an item.
     *
     * @param serviceId     the service ID (e.g., YouTube = 0)
     * @param playlistName  the playlist name
     * @param streamItem    the stream info item (for correct metadata)
     * @param position      the position in the playlist (1-based)
     * @param thumbnailUrl  the playlist thumbnail URL
     * @return playlist metadata for audio tagging
     */
    private static DownloadMission.PlaylistMetadata createPlaylistMetadata(
            final int serviceId,
            final String playlistName,
            final StreamInfoItem streamItem,
            final int position,
            final String thumbnailUrl) {

        final DownloadMission.PlaylistMetadata metadata =
            new DownloadMission.PlaylistMetadata();

        metadata.serviceId = serviceId;
        metadata.playlistName = playlistName;
        metadata.trackPosition = position;
        metadata.playlistThumbnailUrl = thumbnailUrl;
        metadata.uploaderName = cleanArtistName(streamItem.getUploaderName());
        metadata.videoTitle = streamItem.getName();
        metadata.streamUrl = streamItem.getUrl();

        return metadata;
    }

    /**
     * Result of creating a playlist subdirectory.
     */
    private static class SubdirectoryResult {
        final StoredDirectoryHelper directoryHelper; // For file:// paths
        final DocumentFile safSubdirectory; // For SAF paths

        SubdirectoryResult(final StoredDirectoryHelper directoryHelper,
                          final DocumentFile safSubdirectory) {
            this.directoryHelper = directoryHelper;
            this.safSubdirectory = safSubdirectory;
        }
    }

    /**
     * Creates a subdirectory for the playlist within the main storage directory.
     * For file:// storage, returns a new StoredDirectoryHelper for the subdirectory.
     * For SAF storage, returns a DocumentFile for the subdirectory.
     *
     * @param context      the context
     * @param mainStorage  the main storage directory
     * @param playlistName the playlist name
     * @return SubdirectoryResult with either directoryHelper or safSubdirectory set
     * @throws IOException if subdirectory creation fails
     */
    private static SubdirectoryResult createPlaylistSubdirectory(
            @NonNull final Context context,
            @NonNull final StoredDirectoryHelper mainStorage,
            @NonNull final String playlistName) throws IOException {

        // Sanitize playlist name for use as directory name
        final String sanitizedName = sanitizeDirectoryName(playlistName);
        if (sanitizedName.isEmpty()) {
            throw new IOException("Invalid playlist name for directory creation");
        }

        // Get the URI of the main storage
        final Uri mainUri = Uri.parse(mainStorage.toString());

        if (android.content.ContentResolver.SCHEME_FILE.equalsIgnoreCase(mainUri.getScheme())) {
            // Non-SAF path: create directory using file system
            final java.nio.file.Path mainPath = java.nio.file.Paths.get(
                java.net.URI.create(mainUri.toString())
            );
            final java.nio.file.Path subdirPath = mainPath.resolve(sanitizedName);

            // Create directory if it doesn't exist
            if (!java.nio.file.Files.exists(subdirPath)) {
                java.nio.file.Files.createDirectories(subdirPath);
            }

            final Uri subdirUri = Uri.fromFile(subdirPath.toFile());

            // For file:// URIs, we can create a new StoredDirectoryHelper directly
            final StoredDirectoryHelper subdirHelper =
                new StoredDirectoryHelper(context, subdirUri, mainStorage.getTag());
            return new SubdirectoryResult(subdirHelper, null);
        } else {
            // SAF path: create subdirectory using DocumentFile
            // We cannot create a new StoredDirectoryHelper for SAF subdirectories
            // because the constructor tries to take persistable permissions,
            // which only works for root directories selected by the user.
            // Instead, we return the DocumentFile directly and handle file creation manually.

            final DocumentFile mainDoc = DocumentFile.fromTreeUri(context, mainUri);

            if (mainDoc == null) {
                throw new IOException("Failed to get DocumentFile for main storage");
            }

            // Check if subdirectory already exists
            DocumentFile subdirDoc = mainDoc.findFile(sanitizedName);

            if (subdirDoc == null) {
                // Create new subdirectory
                subdirDoc = mainDoc.createDirectory(sanitizedName);
                if (subdirDoc == null) {
                    throw new IOException("Failed to create subdirectory: " + sanitizedName);
                }
            }

            return new SubdirectoryResult(null, subdirDoc);
        }
    }

    /**
     * Sanitizes a playlist name for use as a directory name.
     * Removes characters that are invalid in filesystem paths.
     *
     * @param playlistName the original playlist name
     * @return sanitized directory name
     */
    private static String sanitizeDirectoryName(@NonNull final String playlistName) {
        // Remove invalid filesystem characters: / \ : * ? " < > |
        // Also trim whitespace and replace multiple spaces with single space
        return playlistName
            .replaceAll("[/\\\\:*?\"<>|]", "")
            .trim()
            .replaceAll("\\s+", " ");
    }

    /**
     * Cleans artist name by removing common YouTube channel suffixes.
     * Removes: " - Topic", " VEVO", " - Official", etc.
     *
     * @param artistName the original artist/uploader name
     * @return cleaned artist name, or empty string if input is null
     */
    private static String cleanArtistName(final String artistName) {
        if (artistName == null) {
            return "";
        }

        String cleaned = artistName;

        // Remove common YouTube music channel suffixes (case-insensitive)
        cleaned = cleaned.replaceAll("(?i)\\s*-\\s*Topic$", "");
        cleaned = cleaned.replaceAll("(?i)\\s*VEVO$", "");
        cleaned = cleaned.replaceAll("(?i)\\s*-\\s*Official$", "");
        cleaned = cleaned.replaceAll("(?i)\\s*Official$", "");

        return cleaned.trim();
    }

    /**
     * Selects the highest quality thumbnail from a list of images.
     * Prefers images with known dimensions, then chooses by estimated pixel count.
     *
     * @param thumbnails list of thumbnail images
     * @return URL of the highest quality thumbnail, or null if list is empty
     */
    private static String selectHighestQualityThumbnail(@NonNull final List<Image> thumbnails) {
        if (thumbnails.isEmpty()) {
            return null;
        }

        Image bestImage = null;
        double bestPixelCount = 0;

        for (final Image image : thumbnails) {
            final int width = image.getWidth();
            final int height = image.getHeight();

            // Calculate estimated pixel count
            final double pixelCount;
            if (width > 0 && height > 0) {
                // Both dimensions known - exact pixel count
                pixelCount = width * height;
            } else if (width > 0) {
                // Only width known - estimate assuming 16:9 aspect ratio
                pixelCount = width * width / 1.777;
            } else if (height > 0) {
                // Only height known - estimate assuming 16:9 aspect ratio
                pixelCount = height * height * 1.777;
            } else {
                // No dimensions known - skip
                continue;
            }

            // Update best image if this has more pixels
            if (bestImage == null || pixelCount > bestPixelCount) {
                bestImage = image;
                bestPixelCount = pixelCount;
            }
        }

        // If no image with known dimensions found, just use the first one
        if (bestImage == null) {
            bestImage = thumbnails.get(0);
        }

        return bestImage.getUrl();
    }
}
