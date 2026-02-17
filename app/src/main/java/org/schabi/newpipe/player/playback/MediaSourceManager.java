package org.schabi.newpipe.player.playback;

import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.mediasource.FailedMediaSource;
import org.schabi.newpipe.player.mediasource.LoadedMediaSource;
import org.schabi.newpipe.player.mediasource.ManagedMediaSource;
import org.schabi.newpipe.player.mediasource.ManagedMediaSourcePlaylist;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.playqueue.events.MoveEvent;
import org.schabi.newpipe.player.playqueue.events.PlayQueueEvent;
import org.schabi.newpipe.player.playqueue.events.RemoveEvent;
import org.schabi.newpipe.player.playqueue.events.ReorderEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.internal.subscriptions.EmptySubscription;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

import static org.schabi.newpipe.player.mediasource.FailedMediaSource.MediaSourceResolutionException;
import static org.schabi.newpipe.player.mediasource.FailedMediaSource.StreamInfoLoadException;
import static org.schabi.newpipe.player.playqueue.PlayQueue.DEBUG;
import static org.schabi.newpipe.util.ServiceHelper.getCacheExpirationMillis;

import android.content.Context;
import android.net.Uri;

import com.google.android.exoplayer2.MediaItem;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.mediaitem.StreamInfoTag;
import org.schabi.newpipe.util.OfflinePlaybackHelper;

public class MediaSourceManager {
    @NonNull
    private final String TAG = "MediaSourceManager@" + hashCode();

    /**
     * Determines how many streams before and after the current stream should be loaded.
     * The default value (1) ensures seamless playback under typical network settings.
     * <p>
     * The streams after the current will be loaded into the playlist timeline while the
     * streams before will only be cached for future usage.
     * </p>
     *
     * @see #onMediaSourceReceived(PlayQueueItem, ManagedMediaSource)
     */
    private static final int WINDOW_SIZE = 1;

    /**
     * Determines the maximum number of disposables allowed in the {@link #loaderReactor}.
     * Once exceeded, new calls to {@link #loadImmediate()} will evict all disposables in the
     * {@link #loaderReactor} in order to load a new set of items.
     *
     * @see #loadImmediate()
     * @see #maybeLoadItem(PlayQueueItem)
     */
    private static final int MAXIMUM_LOADER_SIZE = WINDOW_SIZE * 2 + 1;

    @NonNull
    private final PlaybackListener playbackListener;
    @NonNull
    private final PlayQueue playQueue;
    @NonNull
    private final Context context;

    /**
     * Determines the gap time between the playback position and the playback duration which
     * the {@link #getEdgeIntervalSignal()} begins to request loading.
     *
     * @see #progressUpdateIntervalMillis
     */
    private final long playbackNearEndGapMillis;

    /**
     * Determines the interval which the {@link #getEdgeIntervalSignal()} waits for between
     * each request for loading, once {@link #playbackNearEndGapMillis} has reached.
     */
    private final long progressUpdateIntervalMillis;

    @NonNull
    private final Observable<Long> nearEndIntervalSignal;

    /**
     * Process only the last load order when receiving a stream of load orders (lessens I/O).
     * <p>
     * The higher it is, the less loading occurs during rapid noncritical timeline changes.
     * </p>
     * <p>
     * Not recommended to go below 100ms.
     * </p>
     *
     * @see #loadDebounced()
     */
    private final long loadDebounceMillis;

    @NonNull
    private final Disposable debouncedLoader;
    @NonNull
    private final PublishSubject<Long> debouncedSignal;

    @NonNull
    private Subscription playQueueReactor;

    @NonNull
    private final CompositeDisposable loaderReactor;
    @NonNull
    private final Set<PlayQueueItem> loadingItems;

    @NonNull
    private final AtomicBoolean isBlocked;

    @NonNull
    private ManagedMediaSourcePlaylist playlist;

    private final Handler removeMediaSourceHandler = new Handler();

    public MediaSourceManager(@NonNull final Context context,
                              @NonNull final PlaybackListener listener,
                              @NonNull final PlayQueue playQueue) {
        this(context, listener, playQueue, 400L,
                /*playbackNearEndGapMillis=*/TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS),
                /*progressUpdateIntervalMillis*/TimeUnit.MILLISECONDS.convert(2, TimeUnit.SECONDS));
    }

    private MediaSourceManager(@NonNull final Context context,
                               @NonNull final PlaybackListener listener,
                               @NonNull final PlayQueue playQueue,
                               final long loadDebounceMillis,
                               final long playbackNearEndGapMillis,
                               final long progressUpdateIntervalMillis) {
        if (playQueue.getBroadcastReceiver() == null) {
            throw new IllegalArgumentException("Play Queue has not been initialized.");
        }
        if (playbackNearEndGapMillis < progressUpdateIntervalMillis) {
            throw new IllegalArgumentException("Playback end gap=[" + playbackNearEndGapMillis
                    + " ms] must be longer than update interval=[ " + progressUpdateIntervalMillis
                    + " ms] for them to be useful.");
        }

        this.context = context;
        this.playbackListener = listener;
        this.playQueue = playQueue;

        this.playbackNearEndGapMillis = playbackNearEndGapMillis;
        this.progressUpdateIntervalMillis = progressUpdateIntervalMillis;
        this.nearEndIntervalSignal = getEdgeIntervalSignal();

        this.loadDebounceMillis = loadDebounceMillis;
        this.debouncedSignal = PublishSubject.create();
        this.debouncedLoader = getDebouncedLoader();

        this.playQueueReactor = EmptySubscription.INSTANCE;
        this.loaderReactor = new CompositeDisposable();

        this.isBlocked = new AtomicBoolean(false);

        this.playlist = new ManagedMediaSourcePlaylist();

        this.loadingItems = Collections.synchronizedSet(new ArraySet<>());

        playQueue.getBroadcastReceiver()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getReactor());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Exposed Methods
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Dispose the manager and releases all message buses and loaders.
     */
    public void dispose() {
        if (DEBUG) {
            Log.d(TAG, "close() called.");
        }

        debouncedSignal.onComplete();
        debouncedLoader.dispose();

        playQueueReactor.cancel();
        loaderReactor.dispose();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Event Reactor
    //////////////////////////////////////////////////////////////////////////*/

    private Subscriber<PlayQueueEvent> getReactor() {
        return new Subscriber<>() {
            @Override
            public void onSubscribe(@NonNull final Subscription d) {
                playQueueReactor.cancel();
                playQueueReactor = d;
                playQueueReactor.request(1);
            }

            @Override
            public void onNext(@NonNull final PlayQueueEvent playQueueMessage) {
                onPlayQueueChanged(playQueueMessage);
            }

            @Override
            public void onError(@NonNull final Throwable e) {
            }

            @Override
            public void onComplete() {
            }
        };
    }

    private void onPlayQueueChanged(final PlayQueueEvent event) {
        if (playQueue.isEmpty() && playQueue.isComplete()) {
            playbackListener.onPlaybackShutdown();
            return;
        }

        // Event specific action
        switch (event.type()) {
            case INIT:
            case ERROR:
                maybeBlock();
            case APPEND:
                populateSources();
                break;
            case SELECT:
                maybeRenewCurrentIndex();
                break;
            case REMOVE:
                final RemoveEvent removeEvent = (RemoveEvent) event;
                playlist.remove(removeEvent.getRemoveIndex());
                break;
            case MOVE:
                final MoveEvent moveEvent = (MoveEvent) event;
                playlist.move(moveEvent.getFromIndex(), moveEvent.getToIndex());
                break;
            case REORDER:
                // Need to move to ensure the playing index from play queue matches that of
                // the source timeline, and then window correction can take care of the rest
                final ReorderEvent reorderEvent = (ReorderEvent) event;
                playlist.move(reorderEvent.getFromSelectedIndex(),
                        reorderEvent.getToSelectedIndex());
                break;
            case RECOVERY:
            default:
                break;
        }

        // Loading and Syncing
        switch (event.type()) {
            case INIT: case REORDER: case ERROR: case SELECT:
                loadImmediate(); // low frequency, critical events
                break;
            case APPEND: case REMOVE: case MOVE: case RECOVERY:
            default:
                loadDebounced(); // high frequency or noncritical events
                break;
        }

        // update ui and notification
        switch (event.type()) {
            case APPEND: case REMOVE: case MOVE: case REORDER:
                playbackListener.onPlayQueueEdited();
        }

        if (!isPlayQueueReady()) {
            maybeBlock();
            playQueue.fetch();
        }
        playQueueReactor.request(1);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Playback Locking
    //////////////////////////////////////////////////////////////////////////*/

    private boolean isPlayQueueReady() {
        final boolean isWindowLoaded = playQueue.size() - playQueue.getIndex() > WINDOW_SIZE;
        return playQueue.isComplete() || isWindowLoaded;
    }

    private boolean isPlaybackReady() {
        if (playlist.size() != playQueue.size()) {
            return false;
        }

        final ManagedMediaSource mediaSource = playlist.get(playQueue.getIndex());
        final PlayQueueItem playQueueItem = playQueue.getItem();
        if (mediaSource == null || playQueueItem == null) {
            return false;
        }

        return mediaSource.isStreamEqual(playQueueItem);
    }

    private void maybeBlock() {
        if (DEBUG) {
            Log.d(TAG, "maybeBlock() called.");
        }

        if (isBlocked.get()) {
            return;
        }

        playbackListener.onPlaybackBlock();
        resetSources();

        isBlocked.set(true);
    }

    private boolean maybeUnblock() {
        if (DEBUG) {
            Log.d(TAG, "maybeUnblock() called.");
        }

        if (isBlocked.get()) {
            isBlocked.set(false);
            playbackListener.onPlaybackUnblock(playlist.getParentMediaSource());
            return true;
        }

        return false;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Metadata Synchronization
    //////////////////////////////////////////////////////////////////////////*/

    private void maybeSync(final boolean wasBlocked) {
        if (DEBUG) {
            Log.d(TAG, "maybeSync() called.");
        }

        final PlayQueueItem currentItem = playQueue.getItem();
        if (isBlocked.get() || currentItem == null) {
            return;
        }

        playbackListener.onPlaybackSynchronize(currentItem, wasBlocked);
    }

    private synchronized void maybeSynchronizePlayer() {
        if (isPlayQueueReady() && isPlaybackReady()) {
            final boolean isBlockReleased = maybeUnblock();
            maybeSync(isBlockReleased);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // MediaSource Loading
    //////////////////////////////////////////////////////////////////////////*/

    private Observable<Long> getEdgeIntervalSignal() {
        return Observable.interval(progressUpdateIntervalMillis,
                                   TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .filter(ignored ->
                        playbackListener.isApproachingPlaybackEdge(playbackNearEndGapMillis));
    }

    private Disposable getDebouncedLoader() {
        return debouncedSignal.mergeWith(nearEndIntervalSignal)
                .debounce(loadDebounceMillis, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(timestamp -> loadImmediate());
    }

    private void loadDebounced() {
        debouncedSignal.onNext(System.currentTimeMillis());
    }

    private void loadImmediate() {
        if (DEBUG) {
            Log.d(TAG, "MediaSource - loadImmediate() called");
        }
        final ItemsToLoad itemsToLoad = getItemsToLoad(playQueue);
        if (itemsToLoad == null) {
            return;
        }

        // Evict the previous items being loaded to free up memory, before start loading new ones
        maybeClearLoaders();

        maybeLoadItem(itemsToLoad.center);
        for (final PlayQueueItem item : itemsToLoad.neighbors) {
            maybeLoadItem(item);
        }
    }

    private void maybeLoadItem(@NonNull final PlayQueueItem item) {
        if (DEBUG) {
            Log.d(TAG, "maybeLoadItem() called.");
        }
        if (playQueue.indexOf(item) >= playlist.size()) {
            return;
        }

        if (!loadingItems.contains(item) && isCorrectionNeeded(item)) {
            if (DEBUG) {
                Log.d(TAG, "MediaSource - Loading=[" + item.getTitle() + "] "
                        + "with url=[" + item.getUrl() + "]");
            }

            loadingItems.add(item);
            final Disposable loader = getLoadedMediaSource(item)
                    .observeOn(AndroidSchedulers.mainThread())
                    /* No exception handling since getLoadedMediaSource guarantees nonnull return */
                    .subscribe(mediaSource -> onMediaSourceReceived(item, mediaSource));
            loaderReactor.add(loader);
        }
    }

    private Single<ManagedMediaSource> getLoadedMediaSource(@NonNull final PlayQueueItem stream) {
        // Check for offline file first - if available, create media source directly
        // without fetching StreamInfo (which requires internet)
        return Single.fromCallable(() -> {
                    try {
                        final String offlineUri = OfflinePlaybackHelper.getOfflineFileUriSync(
                                context, stream.getServiceId(), stream.getUrl());
                        return Optional.ofNullable(offlineUri);
                    } catch (final Exception e) {
                        Log.w(TAG, "Error checking for offline file: " + e.getMessage());
                        return Optional.<String>empty();
                    }
                })
                .subscribeOn(Schedulers.io())
                .flatMap(offlineUriOpt -> {
                    if (offlineUriOpt.isPresent()) {
                        // Offline file found - create media source from local file
                        Log.i(TAG, "Loading offline file for: " + stream.getTitle());
                        return createOfflineMediaSource(stream, offlineUriOpt.get())
                                .onErrorResumeNext(error -> {
                                    Log.e(TAG, "Failed to create offline source, "
                                            + "trying to stream instead", error);
                                    return fetchStreamFromInternet(stream);
                                });
                    } else {
                        // No offline file - fetch StreamInfo from internet
                        Log.d(TAG, "No offline file, fetching from internet: "
                                + stream.getTitle());
                        return fetchStreamFromInternet(stream);
                    }
                })
                .onErrorReturn(throwable -> {
                    Log.e(TAG, "Error loading media source for: " + stream.getTitle(),
                            throwable);
                    if (throwable instanceof ExtractionException) {
                        return FailedMediaSource.of(stream, new StreamInfoLoadException(throwable));
                    }
                    // Non-source related error expected here (e.g. network),
                    // should allow retry shortly after the error.
                    final long allowRetryIn = TimeUnit.MILLISECONDS.convert(3,
                            TimeUnit.SECONDS);
                    return FailedMediaSource.of(stream, new Exception(throwable), allowRetryIn);
                });
    }

    private Single<ManagedMediaSource> fetchStreamFromInternet(
            @NonNull final PlayQueueItem stream) {
        return stream.getStream()
                .map(streamInfo -> Optional
                        .ofNullable(playbackListener.sourceOf(stream, streamInfo))
                        .<ManagedMediaSource>flatMap(source ->
                                MediaItemTag.from(source.getMediaItem())
                                        .map(tag -> {
                                            final int serviceId = streamInfo.getServiceId();
                                            final long expiration = System.currentTimeMillis()
                                                    + getCacheExpirationMillis(serviceId);
                                            return new LoadedMediaSource(
                                                    source, tag, stream, expiration);
                                        })
                        )
                        .orElseGet(() -> {
                            final String message = "Unable to resolve source from "
                                    + "stream info. URL: " + stream.getUrl();
                            return FailedMediaSource.of(stream,
                                    new MediaSourceResolutionException(message));
                        })
                );
    }

    private Single<ManagedMediaSource> createOfflineMediaSource(
            @NonNull final PlayQueueItem stream,
            @NonNull final String offlineUri) {
        return Single.fromCallable(() -> {
            Log.i(TAG, "Creating offline media source from URI: " + offlineUri);

            // Query database for StreamEntity to get correct metadata
            // (not from PlayQueueItem which may have corrupted playlist data)
            StreamInfo offlineStreamInfo = null;
            // Store reference to existing entity for checking later
            org.schabi.newpipe.database.stream.model.StreamEntity existingEntity = null;

            try {
                final org.schabi.newpipe.database.stream.dao.StreamDAO streamDAO =
                        org.schabi.newpipe.NewPipeDatabase.getInstance(context).streamDAO();
                final java.util.List<org.schabi.newpipe.database.stream.model.StreamEntity>
                        entities = streamDAO.getStream(
                                stream.getServiceId(), stream.getUrl()).blockingFirst();

                if (!entities.isEmpty()) {
                    existingEntity = entities.get(0);
                    Log.d(TAG, "=== Database StreamEntity ===");
                    Log.d(TAG, "Title: '" + existingEntity.getTitle() + "'");
                    Log.d(TAG, "Uploader: '" + existingEntity.getUploader() + "'");
                    Log.d(TAG, "UploaderUrl: '" + existingEntity.getUploaderUrl() + "'");
                    Log.d(TAG, "URL: '" + existingEntity.getUrl() + "'");
                    Log.d(TAG, "Thumbnail: '" + existingEntity.getThumbnailUrl() + "'");

                    // Only use database data if it has valid title
                    // (empty title means corrupted data, use PlayQueueItem fallback)
                    if (existingEntity.getTitle() != null
                            && !existingEntity.getTitle().isEmpty()) {
                        // Create StreamInfo from database entity
                        // Constructor: serviceId, url, originalUrl, streamType, id, name
                        offlineStreamInfo = new StreamInfo(
                                existingEntity.getServiceId(),
                                existingEntity.getUrl(),
                                existingEntity.getUrl(),        // originalUrl
                                existingEntity.getStreamType(),
                                existingEntity.getUrl(),        // id
                                existingEntity.getTitle(),      // name
                                0                               // ageLimit
                        );

                        // Set additional fields using setters
                        offlineStreamInfo.setUploaderName(existingEntity.getUploader());
                        offlineStreamInfo.setUploaderUrl(
                                existingEntity.getUploaderUrl() != null
                                        ? existingEntity.getUploaderUrl() : "");
                        offlineStreamInfo.setDuration(existingEntity.getDuration());

                        // Set thumbnails from database
                        offlineStreamInfo.setThumbnails(
                                org.schabi.newpipe.util.image.ImageStrategy.dbUrlToImageList(
                                        existingEntity.getThumbnailUrl()));

                        // If database has no thumbnail or has a base64 data URI,
                        // try to extract from embedded file metadata
                        final boolean needsExtraction = existingEntity.getThumbnailUrl() == null
                                || existingEntity.getThumbnailUrl().isEmpty()
                                || existingEntity.getThumbnailUrl().startsWith("data:");
                        if (needsExtraction) {
                            Log.i(TAG, "Database entry missing thumbnail, extracting from file");
                            try {
                                android.media.MediaMetadataRetriever retriever = null;
                                try {
                                    retriever = new android.media.MediaMetadataRetriever();
                                    retriever.setDataSource(context,
                                            android.net.Uri.parse(offlineUri));

                                    final byte[] artBytes = retriever.getEmbeddedPicture();
                                    if (artBytes != null && artBytes.length > 0) {
                                        // Save album art as file
                                        final String thumbnailPath = saveAlbumArtToFile(
                                                context, stream.getUrl(), artBytes);

                                        if (thumbnailPath != null) {
                                            // Create Image list with file:// URL
                                            final java.util.List<org.schabi.newpipe.extractor.Image>
                                                    thumbnails = new java.util.ArrayList<>();
                                            thumbnails.add(new org.schabi.newpipe.extractor.Image(
                                                    thumbnailPath, -1, -1,
                                                    org.schabi.newpipe.extractor.Image
                                                            .ResolutionLevel.UNKNOWN));
                                            offlineStreamInfo.setThumbnails(thumbnails);

                                            // Update database with the file path
                                            final org.schabi.newpipe.database.stream.model
                                                    .StreamEntity updatedEntity =
                                                    new org.schabi.newpipe.database.stream.model
                                                            .StreamEntity(offlineStreamInfo);
                                            org.schabi.newpipe.NewPipeDatabase.getInstance(context)
                                                    .streamDAO().upsert(updatedEntity);

                                            Log.i(TAG, "Extracted and saved album art to file: "
                                                    + thumbnailPath);
                                        }
                                    }
                                } finally {
                                    if (retriever != null) {
                                        try {
                                            retriever.release();
                                        } catch (final Exception ignored) {
                                            // Ignore
                                        }
                                    }
                                }
                            } catch (final Exception e) {
                                Log.w(TAG, "Failed to extract album art: " + e.getMessage());
                            }
                        }

                        Log.d(TAG, "=== Created StreamInfo from database ===");
                        Log.d(TAG, "getName(): '" + offlineStreamInfo.getName() + "'");
                        Log.d(TAG, "getUploaderName(): '"
                                + offlineStreamInfo.getUploaderName() + "'");
                        Log.d(TAG, "getUploaderUrl(): '"
                                + offlineStreamInfo.getUploaderUrl() + "'");
                    } else {
                        Log.w(TAG, "Database entity has empty title, will use PlayQueueItem"
                                + " fallback");
                    }
                }
            } catch (final Exception e) {
                Log.w(TAG, "Failed to load StreamEntity from database: " + e.getMessage());
            }

            // Fallback to PlayQueueItem data if database query failed or had empty title
            if (offlineStreamInfo == null) {
                String title = stream.getTitle();
                String uploader = stream.getUploader();
                byte[] embeddedArtBytes = null;

                // If PlayQueueItem also has empty data, try to extract from file metadata
                if ((title == null || title.isEmpty()) && offlineUri != null) {
                    try {
                        Log.w(TAG, "PlayQueueItem has empty title, extracting from file");

                        // First try to read embedded metadata tags (ID3, etc.)
                        android.media.MediaMetadataRetriever retriever = null;
                        try {
                            retriever = new android.media.MediaMetadataRetriever();
                            retriever.setDataSource(context, android.net.Uri.parse(offlineUri));

                            // Extract metadata from audio file
                            final String embeddedTitle = retriever.extractMetadata(
                                    android.media.MediaMetadataRetriever.METADATA_KEY_TITLE);
                            final String embeddedArtist = retriever.extractMetadata(
                                    android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST);
                            final String embeddedAlbum = retriever.extractMetadata(
                                    android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM);

                            if (embeddedTitle != null && !embeddedTitle.isEmpty()) {
                                title = embeddedTitle;
                                Log.i(TAG, "Extracted title from file metadata: " + title);
                            }
                            if (embeddedArtist != null && !embeddedArtist.isEmpty()) {
                                uploader = embeddedArtist;
                                Log.i(TAG, "Extracted artist from file metadata: " + uploader);
                            }

                            // Extract embedded album art
                            embeddedArtBytes = retriever.getEmbeddedPicture();
                            if (embeddedArtBytes != null) {
                                Log.i(TAG, "Found embedded album art, size: "
                                        + embeddedArtBytes.length);
                            }
                        } catch (final Exception e) {
                            Log.w(TAG, "Failed to read embedded metadata: " + e.getMessage());
                        } finally {
                            if (retriever != null) {
                                try {
                                    retriever.release();
                                } catch (final Exception ignored) {
                                    // Ignore
                                }
                            }
                        }

                        // Fallback to filename parsing if metadata extraction failed
                        if (title == null || title.isEmpty()) {
                            Log.w(TAG, "No embedded metadata, parsing filename");

                            // For content:// URIs, decode and extract the actual file path
                            final String decodedPath =
                                    java.net.URLDecoder.decode(offlineUri, "UTF-8");
                            Log.d(TAG, "Decoded URI: " + decodedPath);

                            // Extract path after "raw:" or "raw%3A"
                            final String filePath;
                            if (decodedPath.contains("raw:")) {
                                filePath = decodedPath.substring(
                                        decodedPath.lastIndexOf("raw:") + 4);
                            } else {
                                filePath = decodedPath;
                            }
                            Log.d(TAG, "Extracted file path: " + filePath);

                            // Get filename from path
                            final String filename =
                                    filePath.substring(filePath.lastIndexOf('/') + 1);
                            // Remove file extension
                            final String nameWithoutExt = filename.replaceFirst("\\.[^.]+$", "");
                            // Remove track number prefix like "05 - "
                            final String extractedTitle =
                                    nameWithoutExt.replaceFirst("^\\d+\\s*-\\s*", "");
                            title = extractedTitle;
                            Log.i(TAG, "Extracted title from filename: " + title);

                            // Extract folder name (artist/album) if still no artist
                            if (uploader == null || uploader.isEmpty()) {
                                final int lastSlash = filePath.lastIndexOf('/');
                                if (lastSlash > 0) {
                                    final int secondLastSlash =
                                            filePath.lastIndexOf('/', lastSlash - 1);
                                    if (secondLastSlash > 0) {
                                        uploader = filePath.substring(
                                                secondLastSlash + 1, lastSlash);
                                        Log.i(TAG, "Extracted artist from folder: " + uploader);
                                    }
                                }
                            }
                        }
                    } catch (final Exception e) {
                        Log.w(TAG, "Failed to extract metadata: " + e.getMessage());
                    }
                }

                Log.w(TAG, "Using PlayQueueItem/filename fallback - Title: " + title);
                // Constructor: (serviceId, url, originalUrl, streamType, id, name, ageLimit)
                offlineStreamInfo = new StreamInfo(
                        stream.getServiceId(),
                        stream.getUrl(),
                        stream.getUrl(),                                       // originalUrl
                        stream.getStreamType(),
                        stream.getUrl(),                                       // id
                        title != null && !title.isEmpty() ? title : "Unknown", // name
                        0                                                      // ageLimit
                );

                // Set additional fields using setters
                offlineStreamInfo.setUploaderName(
                        uploader != null && !uploader.isEmpty() ? uploader : "Unknown");
                offlineStreamInfo.setUploaderUrl(
                        stream.getUploaderUrl() != null ? stream.getUploaderUrl() : "");
                offlineStreamInfo.setDuration(stream.getDuration());

                // Add embedded album art as thumbnail if available
                if (embeddedArtBytes != null && embeddedArtBytes.length > 0) {
                    try {
                        // Save album art as file
                        final String thumbnailPath = saveAlbumArtToFile(
                                context, stream.getUrl(), embeddedArtBytes);

                        if (thumbnailPath != null) {
                            // Create Image list and set as thumbnails
                            final java.util.List<org.schabi.newpipe.extractor.Image> thumbnails =
                                    new java.util.ArrayList<>();
                            thumbnails.add(new org.schabi.newpipe.extractor.Image(
                                    thumbnailPath, -1, -1,
                                    org.schabi.newpipe.extractor.Image.ResolutionLevel.UNKNOWN));
                            offlineStreamInfo.setThumbnails(thumbnails);

                            Log.i(TAG, "Added embedded album art from file: " + thumbnailPath);
                        }
                    } catch (final Exception e) {
                        Log.w(TAG, "Failed to save album art: " + e.getMessage());
                    }
                }

                // Update the corrupted database entry with correct metadata
                // ONLY if:
                // 1. We successfully extracted non-empty metadata
                // 2. The existing database entry was corrupted (empty title)
                if (title != null && !title.isEmpty()
                        && (existingEntity == null
                        || existingEntity.getTitle() == null
                        || existingEntity.getTitle().isEmpty())) {
                    try {
                        final org.schabi.newpipe.database.stream.dao.StreamDAO streamDAO =
                                org.schabi.newpipe.NewPipeDatabase.getInstance(context)
                                        .streamDAO();
                        final org.schabi.newpipe.database.stream.model.StreamEntity newEntity =
                                new org.schabi.newpipe.database.stream.model.StreamEntity(
                                        offlineStreamInfo);
                        streamDAO.upsert(newEntity);
                        Log.i(TAG, "Updated corrupted database entry with recovered metadata"
                                + " - Title: " + title + ", Artist: " + uploader);
                    } catch (final Exception e) {
                        Log.w(TAG, "Failed to update database with recovered metadata: "
                                + e.getMessage());
                    }
                } else if (existingEntity != null && existingEntity.getTitle() != null
                        && !existingEntity.getTitle().isEmpty()) {
                    Log.d(TAG, "NOT updating database - existing entry has valid data");
                } else {
                    Log.w(TAG, "NOT updating database - extracted metadata is still empty");
                }
            }

            Log.d(TAG, "Final StreamInfo - getName(): " + offlineStreamInfo.getName()
                    + ", getUploaderName(): " + offlineStreamInfo.getUploaderName());

            // Create tag for the media item
            final MediaItemTag tag = StreamInfoTag.of(offlineStreamInfo);

            // Create media item with offline file URI and proper metadata
            // Use tag.asMediaItem() to get MediaMetadata, then override URI for offline playback
            final MediaItem mediaItem = tag.asMediaItem()
                    .buildUpon()
                    .setUri(Uri.parse(offlineUri))
                    .build();

            // Create progressive media source directly for offline file
            // Don't go through resolvers - just play the local file
            final com.google.android.exoplayer2.source.ProgressiveMediaSource.Factory factory =
                    new com.google.android.exoplayer2.source.ProgressiveMediaSource.Factory(
                            new com.google.android.exoplayer2.upstream
                                    .DefaultDataSource.Factory(context));
            final com.google.android.exoplayer2.source.ProgressiveMediaSource
                    progressiveSource = factory.createMediaSource(mediaItem);

            final long expiration = System.currentTimeMillis()
                    + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);

            Log.i(TAG, "Offline media source created successfully - Title: "
                    + offlineStreamInfo.getName() + ", Artist: "
                    + offlineStreamInfo.getUploaderName());
            return new LoadedMediaSource(progressiveSource, tag, stream, expiration);
        });
    }

    private void onMediaSourceReceived(@NonNull final PlayQueueItem item,
                                       @NonNull final ManagedMediaSource mediaSource) {
        if (DEBUG) {
            Log.d(TAG, "MediaSource - Loaded=[" + item.getTitle()
                    + "] with url=[" + item.getUrl() + "]");
        }

        loadingItems.remove(item);

        final int itemIndex = playQueue.indexOf(item);
        // Only update the playlist timeline for items at the current index or after.
        if (isCorrectionNeeded(item)) {
            if (DEBUG) {
                Log.d(TAG, "MediaSource - Updating index=[" + itemIndex + "] with "
                        + "title=[" + item.getTitle() + "] at url=[" + item.getUrl() + "]");
            }
            playlist.update(itemIndex, mediaSource, removeMediaSourceHandler,
                    this::maybeSynchronizePlayer);
        }
    }

    /**
     * Checks if the corresponding MediaSource in
     * {@link com.google.android.exoplayer2.source.ConcatenatingMediaSource}
     * for a given {@link PlayQueueItem} needs replacement, either due to gapless playback
     * readiness or playlist desynchronization.
     * <p>
     * If the given {@link PlayQueueItem} is currently being played and is already loaded,
     * then correction is not only needed if the playlist is desynchronized. Otherwise, the
     * check depends on the status (e.g. expiration or placeholder) of the
     * {@link ManagedMediaSource}.
     * </p>
     *
     * @param item {@link PlayQueueItem} to check
     * @return whether a correction is needed
     */
    private boolean isCorrectionNeeded(@NonNull final PlayQueueItem item) {
        final int index = playQueue.indexOf(item);
        final ManagedMediaSource mediaSource = playlist.get(index);
        return mediaSource != null && mediaSource.shouldBeReplacedWith(item,
                index != playQueue.getIndex());
    }

    /**
     * Checks if the current playing index contains an expired {@link ManagedMediaSource}.
     * If so, the expired source is replaced by a dummy {@link ManagedMediaSource} and
     * {@link #loadImmediate()} is called to reload the current item.
     * <br><br>
     * If not, then the media source at the current index is ready for playback, and
     * {@link #maybeSynchronizePlayer()} is called.
     * <br><br>
     * Under both cases, {@link #maybeSync(boolean)} will be called to ensure the listener
     * is up-to-date.
     */
    private void maybeRenewCurrentIndex() {
        final int currentIndex = playQueue.getIndex();
        final PlayQueueItem currentItem = playQueue.getItem();
        final ManagedMediaSource currentSource = playlist.get(currentIndex);
        if (currentItem == null || currentSource == null) {
            return;
        }

        if (!currentSource.shouldBeReplacedWith(currentItem, true)) {
            maybeSynchronizePlayer();
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "MediaSource - Reloading currently playing, "
                    + "index=[" + currentIndex + "], item=[" + currentItem.getTitle() + "]");
        }
        playlist.invalidate(currentIndex, removeMediaSourceHandler, this::loadImmediate);
    }

    private void maybeClearLoaders() {
        if (DEBUG) {
            Log.d(TAG, "MediaSource - maybeClearLoaders() called.");
        }
        if (!loadingItems.contains(playQueue.getItem())
                && loaderReactor.size() > MAXIMUM_LOADER_SIZE) {
            loaderReactor.clear();
            loadingItems.clear();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // MediaSource Playlist Helpers
    //////////////////////////////////////////////////////////////////////////*/

    private void resetSources() {
        if (DEBUG) {
            Log.d(TAG, "resetSources() called.");
        }
        playlist = new ManagedMediaSourcePlaylist();
    }

    private void populateSources() {
        if (DEBUG) {
            Log.d(TAG, "populateSources() called.");
        }
        while (playlist.size() < playQueue.size()) {
            playlist.expand();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Manager Helpers
    //////////////////////////////////////////////////////////////////////////*/

    @Nullable
    private static ItemsToLoad getItemsToLoad(@NonNull final PlayQueue playQueue) {
        // The current item has higher priority
        final int currentIndex = playQueue.getIndex();
        final PlayQueueItem currentItem = playQueue.getItem(currentIndex);
        if (currentItem == null) {
            return null;
        }

        // The rest are just for seamless playback
        // Although timeline is not updated prior to the current index, these sources are still
        // loaded into the cache for faster retrieval at a potentially later time.
        final int leftBound = Math.max(0, currentIndex - MediaSourceManager.WINDOW_SIZE);
        final int rightLimit = currentIndex + MediaSourceManager.WINDOW_SIZE + 1;
        final int rightBound = Math.min(playQueue.size(), rightLimit);
        final Set<PlayQueueItem> neighbors = new ArraySet<>(
                playQueue.getStreams().subList(leftBound, rightBound));

        // Do a round robin
        final int excess = rightLimit - playQueue.size();
        if (excess >= 0) {
            neighbors.addAll(playQueue.getStreams()
                    .subList(0, Math.min(playQueue.size(), excess)));
        }
        neighbors.remove(currentItem);

        return new ItemsToLoad(currentItem, neighbors);
    }

    /**
     * Saves album art bytes to a file in the app's cache directory.
     *
     * @param context the application context
     * @param streamUrl the URL of the stream (used to generate filename)
     * @param artBytes the album art image bytes
     * @return the file:// URL of the saved file, or null if save failed
     */
    @Nullable
    private static String saveAlbumArtToFile(@NonNull final android.content.Context context,
                                             @NonNull final String streamUrl,
                                             @NonNull final byte[] artBytes) {
        try {
            // Create thumbnails directory in cache
            final java.io.File thumbnailDir = new java.io.File(
                    context.getCacheDir(), "offline_thumbnails");
            if (!thumbnailDir.exists() && !thumbnailDir.mkdirs()) {
                Log.w("MediaSourceManager", "Failed to create thumbnail directory");
                return null;
            }

            // Generate filename from stream URL hash to avoid collisions
            final String filename = String.valueOf(streamUrl.hashCode()) + ".jpg";
            final java.io.File thumbnailFile = new java.io.File(thumbnailDir, filename);

            // Write bytes to file
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(thumbnailFile)) {
                fos.write(artBytes);
            }

            // Return file:// URL
            return "file://" + thumbnailFile.getAbsolutePath();
        } catch (final java.io.IOException e) {
            Log.w("MediaSourceManager", "Failed to save album art to file: " + e.getMessage());
            return null;
        }
    }

    private static class ItemsToLoad {
        @NonNull
        private final PlayQueueItem center;
        @NonNull
        private final Collection<PlayQueueItem> neighbors;

        ItemsToLoad(@NonNull final PlayQueueItem center,
                    @NonNull final Collection<PlayQueueItem> neighbors) {
            this.center = center;
            this.neighbors = neighbors;
        }
    }
}
