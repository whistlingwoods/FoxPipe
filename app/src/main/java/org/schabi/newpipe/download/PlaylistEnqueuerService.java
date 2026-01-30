package org.schabi.newpipe.download;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.settings.NewPipeSettings;
import org.schabi.newpipe.streams.io.StoredDirectoryHelper;
import org.schabi.newpipe.streams.io.StoredFileHelper;

import us.shandian.giga.get.QueuedMission;
import us.shandian.giga.service.DownloadManager;
import us.shandian.giga.service.DownloadManagerService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service to fetch metadata for a list of videos and enqueue them for download.
 * Running as a Foreground Service to ensure longevity.
 */
public class PlaylistEnqueuerService extends Service {

    public static final String ACTION_ENQUEUE_PLAYLIST =
            "org.schabi.newpipe.action.ENQUEUE_PLAYLIST";
    public static final String ACTION_RETRY_SINGLE =
            "org.schabi.newpipe.action.RETRY_SINGLE";
    public static final String ACTION_CANCEL_ALL =
            "org.schabi.newpipe.action.CANCEL_PLAYLIST";

    public static final String EXTRA_URLS = "org.schabi.newpipe.extra.URLS";
    public static final String EXTRA_TITLES = "org.schabi.newpipe.extra.TITLES";
    public static final String EXTRA_QUALITY = "org.schabi.newpipe.extra.QUALITY";
    public static final String EXTRA_CUSTOM_DIRECTORY = "org.schabi.newpipe.extra.CUSTOM_DIRECTORY";
    public static final String EXTRA_VIDEO_URL = "org.schabi.newpipe.extra.VIDEO_URL";
    public static final String EXTRA_VIDEO_TITLE = "org.schabi.newpipe.extra.VIDEO_TITLE";

    private static final String TAG = "PlaylistEnqueuer";
    private static final int NOTIFICATION_ID = 10134;
    private static final String NOTIFICATION_CHANNEL_ID = "playlist_enqueuer_channel";
    private static final int DELAY_MS = 500;
    private static final int JITTER_BASE = 500;
    private static final int JITTER_RANGE = 1000;
    private static final int THREAD_COUNT = 3;
    private static final int CONCURRENCY = 2;

    // Track cancelled video URLs to stop processing them
    private static final Set<String> CANCELLED_URLS =
            Collections.synchronizedSet(new HashSet<>());

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * Cancel a queued item - called from UI.
     *
     * @param videoUrl The video URL to cancel.
     */
    public static void cancelQueuedItem(final String videoUrl) {
        if (videoUrl != null && !videoUrl.isEmpty()) {
            Log.d(TAG, "Cancelling queued item: " + videoUrl);
            CANCELLED_URLS.add(videoUrl);
        }
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_ENQUEUE_PLAYLIST.equals(action)) {
                final ArrayList<String> urls = intent.getStringArrayListExtra(EXTRA_URLS);
                final ArrayList<String> titles = intent.getStringArrayListExtra(EXTRA_TITLES);
                final String quality = intent.getStringExtra(EXTRA_QUALITY);
                final String customDirectory = intent.getStringExtra(EXTRA_CUSTOM_DIRECTORY);

                startForeground(NOTIFICATION_ID, createNotification(0, urls.size()));

                // Start DownloadManagerService first
                final Intent dmIntent = new Intent(this, DownloadManagerService.class);
                startService(dmIntent);

                // Wait 500ms for service to initialize, THEN process playlist
                handler.postDelayed(() -> {
                    Log.d(TAG, "Starting playlist processing after delay...");
                    processPlaylist(urls, titles, quality, customDirectory);
                }, DELAY_MS);

            } else if (ACTION_RETRY_SINGLE.equals(action)) {
                final String videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL);
                final String videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE);
                final String quality = intent.getStringExtra(EXTRA_QUALITY);

                if (videoUrl != null && !videoUrl.isEmpty()) {
                    Log.d(TAG, "Retrying single item: " + videoTitle);

                    startForeground(NOTIFICATION_ID, createNotification(0, 1));

                    final Intent dmIntent = new Intent(this, DownloadManagerService.class);
                    startService(dmIntent);

                    handler.postDelayed(() -> {
                        processSingleRetry(videoUrl, videoTitle, quality);
                    }, DELAY_MS);
                }

            } else if (ACTION_CANCEL_ALL.equals(action)) {
                Log.d(TAG, "Cancelling playlist enqueuing...");
                disposables.clear();
                handler.removeCallbacksAndMessages(null);
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        return START_NOT_STICKY;
    }

    private void processPlaylist(final List<String> urls,
                                 final List<String> titles,
                                 final String quality,
                                 @Nullable final String customDirectoryUri) {
        final AtomicInteger progressCounter = new AtomicInteger(0);
        final Random random = new Random();
        final int total = urls.size();

        Log.e(TAG, "DEBUG: processPlaylist() called");
        Log.e(TAG, "Total videos: " + total);
        Log.e(TAG, "Quality: " + quality);

        final DownloadManager downloadManager = getDownloadManager();

        // Helper method 1: Queue initial missions
        queueInitialMissions(downloadManager, urls, titles, quality);

        disposables.add(Observable.range(0, total)
                .flatMap(index -> Observable.fromCallable(() -> {
                    final String url = urls.get(index);
                    final String title = titles.get(index);

                    // Helper method 2: Jitter sleep
                    if (!sleepWithJitter(title, random)) {
                        return false;
                    }
                    final int current = progressCounter.incrementAndGet();
                    updateNotification(current, total, title);

                    if (CANCELLED_URLS.contains(url)) {
                        Log.d(TAG, "Item cancelled before extraction: " + title);
                        CANCELLED_URLS.remove(url);
                        if (downloadManager != null) {
                            downloadManager.removeQueuedMissionByUrl(url);
                        }
                        return false;
                    }
                    if (downloadManager != null) {
                        downloadManager.updateQueuedMissionStatusByUrl(url,
                                QueuedMission.Status.EXTRACTING, null);
                    }

                    // 1. Extract StreamInfo
                    final StreamInfo info;
                    try {
                        final int serviceId = NewPipe.getServiceByUrl(url).getServiceId();
                        info = StreamInfo.getInfo(NewPipe.getService(serviceId), url);
                    } catch (final Exception e) {
                        Log.e(TAG, "Failed to extract info for: " + title, e);
                        if (downloadManager != null) {
                            downloadManager.updateQueuedMissionStatusByUrl(url,
                                    QueuedMission.Status.FAILED, e.getMessage());
                        }
                        return false;
                    }
                    if (downloadManager != null
                            && info.getThumbnails() != null
                            && !info.getThumbnails().isEmpty()) {
                        final QueuedMission mission = downloadManager.getQueuedMissionByUrl(url);
                        if (mission != null) {
                            mission.thumbnailUrl = info.getThumbnails().get(0).getUrl();
                        }
                    }
                    if (downloadManager != null) {
                        downloadManager.updateQueuedMissionStatusByUrl(url,
                                QueuedMission.Status.PREPARING, null);
                    }

                    // 2. Prepare complete download bundle
                    final PlaylistDownloadLogic.DownloadBundle bundle =
                            PlaylistDownloadLogic.prepareDownload(this, info, quality);
                    if (bundle == null) {
                        Log.e(TAG, "Bundle is NULL for: " + title);
                        if (downloadManager != null) {
                            downloadManager.updateQueuedMissionStatusByUrl(url,
                                    QueuedMission.Status.FAILED, "Failed to prepare download");
                        }
                        return false;
                    }
                    // 3. Create Storage (Helper method 3)
                    final StoredFileHelper finalStorage;
                    try {
                        finalStorage = createStorage(customDirectoryUri, bundle);
                    } catch (final IOException e) {
                        Log.e(TAG, "IOException creating storage", e);
                        if (downloadManager != null) {
                            final String errorMsg = e.getMessage() != null
                                    ? e.getMessage() : "Storage error";
                            downloadManager.updateQueuedMissionStatusByUrl(url,
                                    QueuedMission.Status.FAILED, errorMsg);
                        }
                        return false;
                    }
                    if (finalStorage == null) {
                        Log.e(TAG, "createUniqueFile returned NULL for: " + title);
                        if (downloadManager != null) {
                            downloadManager.updateQueuedMissionStatusByUrl(url,
                                    QueuedMission.Status.FAILED, "Failed to create file");
                        }
                        return false;
                    }
                    // 4. Start the download
                    Log.d(TAG, "Starting download mission for: " + title);
                    DownloadManagerService.startMission(
                            getApplicationContext(), bundle.urls, finalStorage,
                            bundle.kind, THREAD_COUNT, info,
                            bundle.psName, bundle.psArgs,
                            bundle.nearLength, new ArrayList<>(bundle.recovery)
                    );
                    if (downloadManager != null) {
                        final boolean removed = downloadManager.removeQueuedMissionByUrl(url);
                        if (removed) {
                            Log.d(TAG, "Removed from queue - UI should update now");
                        } else {
                            Log.w(TAG, "Could not remove from queue (already removed?)");
                        }
                    }
                    Log.d(TAG, "Started download for: " + title);
                    return true;

                }), false, CONCURRENCY)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        success -> {
                            // Handle individual success if needed
                        },
                        error -> {
                            Log.e(TAG, "Error processing playlist item", error);
                            CANCELLED_URLS.clear();
                            stopForeground(true);
                            stopSelf();
                        },
                        () -> {
                            Log.d(TAG, "Playlist processing complete.");
                            final int clearedCount = CANCELLED_URLS.size();
                            CANCELLED_URLS.clear();
                            if (clearedCount > 0) {
                                Log.d(TAG,
                                 "Cleared " + clearedCount + " cancelled URLs from memory");
                            }
                            stopForeground(true);
                            stopSelf();
                        }
                ));
    }

    private void queueInitialMissions(final DownloadManager downloadManager,
                                      final List<String> urls,
                                      final List<String> titles,
                                      final String quality) {
        if (downloadManager == null) {
            Log.e(TAG, "CRITICAL ERROR: DownloadManager is NULL!");
            return;
        }

        final int total = urls.size();
        Log.e(TAG, "SUCCESS: DownloadManager obtained!");
        Log.e(TAG, "Adding " + total + " videos to queue...");

        for (int i = 0; i < total; i++) {
            final QueuedMission queued = new QueuedMission();
            queued.videoUrl = urls.get(i);
            queued.title = titles.get(i);
            queued.targetQuality = quality;
            queued.status = QueuedMission.Status.WAITING;
            queued.timestamp = System.currentTimeMillis();
            queued.positionInPlaylist = i;

            queued.thumbnailUrl = extractThumbnailFromUrl(urls.get(i));

            downloadManager.addQueuedMission(queued);
            Log.d(TAG, "#" + i + ": " + queued.title);
        }
    }

    private boolean sleepWithJitter(final String title, final Random random) {
        try {
            final long jitter = JITTER_BASE + random.nextInt(JITTER_RANGE);
            Thread.sleep(jitter);
            return true;
        } catch (final InterruptedException e) {
            Log.d(TAG, "Thread interrupted during jitter sleep for: " + title);
            return false;
        }
    }

    private StoredFileHelper createStorage(@Nullable final String customDirectoryUri,
                                           final PlaylistDownloadLogic.DownloadBundle bundle)
            throws IOException {
        final boolean isVideo = bundle.kind == 'v';
        final StoredDirectoryHelper storage;

        if (customDirectoryUri != null && !customDirectoryUri.isEmpty()) {
            Log.d(TAG, "Using custom directory: " + customDirectoryUri);
            final Uri pathUri = Uri.parse(customDirectoryUri);
            storage = new StoredDirectoryHelper(this, pathUri, null);
        } else {
            final String key = getString(isVideo
                    ? R.string.download_path_video_key
                    : R.string.download_path_audio_key);
            String downloadPath = PreferenceManager
                    .getDefaultSharedPreferences(this)
                    .getString(key, null);

            if (downloadPath == null || downloadPath.isEmpty()) {
                final File defaultDir = NewPipeSettings.getDir(
                        isVideo
                                ? Environment.DIRECTORY_MOVIES
                                : Environment.DIRECTORY_MUSIC);
                downloadPath = Uri.fromFile(defaultDir).toString();
            }

            final Uri pathUri = Uri.parse(downloadPath);
            storage = new StoredDirectoryHelper(this, pathUri, null);
        }

        return storage.createUniqueFile(bundle.filename, bundle.mimeType);
    }

    /**
     * Process a single retry item (already exists in queue).
     * This method processes the item without adding to queue (prevents duplicates).
     *
     * @param videoUrl   The video URL.
     * @param videoTitle The video title.
     * @param quality    The target quality.
     */
    private void processSingleRetry(final String videoUrl,
                                    final String videoTitle,
                                    final String quality) {
        Log.d(TAG, "processSingleRetry: " + videoTitle);

        final DownloadManager downloadManager = getDownloadManager();
        if (downloadManager == null) {
            Log.e(TAG, "DownloadManager is null, cannot retry");
            stopForeground(true);
            stopSelf();
            return;
        }

        downloadManager.updateQueuedMissionStatusByUrl(videoUrl,
                QueuedMission.Status.EXTRACTING, null);
        updateNotification(0, 1, "Extracting: " + videoTitle);

        disposables.add(Observable.fromCallable(() -> {
            try {
                // 1. Extract StreamInfo
                final StreamInfo info;
                try {
                    final int serviceId = NewPipe.getServiceByUrl(videoUrl).getServiceId();
                    info = StreamInfo.getInfo(NewPipe.getService(serviceId), videoUrl);
                } catch (final Exception e) {
                    Log.e(TAG, "Failed to extract info for retry: " + videoTitle, e);
                    downloadManager.updateQueuedMissionStatusByUrl(videoUrl,
                            QueuedMission.Status.FAILED, e.getMessage());
                    return false;
                }

                if (info.getThumbnails() != null && !info.getThumbnails().isEmpty()) {
                    final QueuedMission mission = downloadManager.getQueuedMissionByUrl(videoUrl);
                    if (mission != null) {
                        mission.thumbnailUrl = info.getThumbnails().get(0).getUrl();
                    }
                }

                downloadManager.updateQueuedMissionStatusByUrl(videoUrl,
                        QueuedMission.Status.PREPARING, null);
                handler.post(() -> updateNotification(0, 1, "Preparing: " + videoTitle));

                // 2. Prepare download bundle
                final PlaylistDownloadLogic.DownloadBundle bundle =
                        PlaylistDownloadLogic.prepareDownload(this, info, quality);

                if (bundle == null) {
                    Log.e(TAG, "Bundle is NULL for retry: " + videoTitle);
                    downloadManager.updateQueuedMissionStatusByUrl(videoUrl,
                            QueuedMission.Status.FAILED, "Failed to prepare download");
                    return false;
                }

                // 3. Create Storage
                final boolean isVideo = bundle.kind == 'v';
                final StoredDirectoryHelper storage;
                final StoredFileHelper finalStorage;

                try {
                    final String key = getString(isVideo
                            ? R.string.download_path_video_key
                            : R.string.download_path_audio_key);
                    String downloadPath = PreferenceManager
                            .getDefaultSharedPreferences(this)
                            .getString(key, null);

                    if (downloadPath == null || downloadPath.isEmpty()) {
                        final File defaultDir = NewPipeSettings.getDir(
                                isVideo
                                ? Environment.DIRECTORY_MOVIES
                                : Environment.DIRECTORY_MUSIC);
                        downloadPath = Uri.fromFile(defaultDir).toString();
                    }

                    final Uri pathUri = Uri.parse(downloadPath);
                    storage = new StoredDirectoryHelper(this, pathUri, null);

                    finalStorage = storage.createUniqueFile(bundle.filename, bundle.mimeType);

                    if (finalStorage == null) {
                        Log.e(TAG, "createUniqueFile returned NULL for retry: " + videoTitle);
                        downloadManager.updateQueuedMissionStatusByUrl(videoUrl,
                                QueuedMission.Status.FAILED, "Failed to create file");
                        return false;
                    }
                } catch (final IOException e) {
                    Log.e(TAG, "IOException creating storage for retry", e);
                    final String errorMsg = e.getMessage()
                            != null ? e.getMessage() : "Storage error";
                    downloadManager.updateQueuedMissionStatusByUrl(videoUrl,
                            QueuedMission.Status.FAILED, errorMsg);
                    return false;
                }

                // 4. Start the download
                Log.d(TAG, "Starting download mission for retry: " + videoTitle);
                DownloadManagerService.startMission(
                        getApplicationContext(),
                        bundle.urls,
                        finalStorage,
                        bundle.kind,
                        THREAD_COUNT,
                        info,
                        bundle.psName,
                        bundle.psArgs,
                        bundle.nearLength,
                        new ArrayList<>(bundle.recovery)
                );

                downloadManager.removeQueuedMissionByUrl(videoUrl);

                Log.d(TAG, "Retry successful for: " + videoTitle);
                return true;

            } catch (final Exception e) {
                Log.e(TAG, "Unexpected error during retry", e);
                downloadManager.updateQueuedMissionStatusByUrl(videoUrl,
                        QueuedMission.Status.FAILED, e.getMessage());
                return false;
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            success -> {
                Log.d(TAG, "Retry processing complete");
                stopForeground(true);
                stopSelf();
            },
            error -> {
                Log.e(TAG, "Error during retry", error);
                stopForeground(true);
                stopSelf();
            }
        ));
    }

    private DownloadManager getDownloadManager() {
        Log.d(TAG, "Getting DownloadManager...");

        try {
            final DownloadManager dm = DownloadManagerService.getDownloadManager();

            if (dm != null) {
                Log.d(TAG, "DownloadManager retrieved successfully!");
                return dm;
            } else {
                Log.e(TAG, "DownloadManager is still NULL");
                Log.e(TAG, "This should not happen after 500ms delay!");
                return null;
            }
        } catch (final Exception e) {
            Log.e(TAG, "Exception getting DownloadManager", e);
            return null;
        }
    }

    private String extractThumbnailFromUrl(final String videoUrl) {
        try {
            if (videoUrl.contains("youtube.com") || videoUrl.contains("youtu.be")) {
                String videoId = null;
                if (videoUrl.contains("v=")) {
                    videoId = videoUrl.split("v=")[1].split("&")[0];
                } else if (videoUrl.contains("youtu.be/")) {
                    videoId = videoUrl.split("youtu.be/")[1].split("\\?")[0];
                }

                if (videoId != null) {
                    return "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
                }
            }
        } catch (final Exception e) {
            // Ignore
        }
        return null;
    }

    private Notification createNotification(final int progress, final int max) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Playlist Download",
                    NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        final Intent cancelIntent = new Intent(this, PlaylistEnqueuerService.class);
        cancelIntent.setAction(ACTION_CANCEL_ALL);
        final PendingIntent cancelPendingIntent = PendingIntent.getService(
                this,
                0,
                cancelIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? (PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Queueing Playlist Downloads")
                .setContentText(progress + "/" + max)
                .setSmallIcon(R.drawable.ic_playlist_add)
                .setProgress(max, progress, false)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.cancel), cancelPendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(final int progress, final int max, final String text) {
        final Intent cancelIntent = new Intent(this, PlaylistEnqueuerService.class);
        cancelIntent.setAction(ACTION_CANCEL_ALL);
        final PendingIntent cancelPendingIntent = PendingIntent.getService(
                this,
                0,
                cancelIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? (PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        final Notification notification = new NotificationCompat.Builder(
                this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Queueing Playlist Downloads")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(max, progress, false)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.cancel), cancelPendingIntent)
                .setOngoing(true)
                .build();
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        disposables.clear();
        handler.removeCallbacksAndMessages(null);
        CANCELLED_URLS.clear();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }
}
