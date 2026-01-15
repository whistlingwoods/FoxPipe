package org.schabi.newpipe.download;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.streams.io.StoredDirectoryHelper;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.settings.NewPipeSettings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import us.shandian.giga.get.MissionRecoveryInfo;
import us.shandian.giga.get.QueuedMission;
import us.shandian.giga.service.DownloadManager;
import us.shandian.giga.service.DownloadManagerService;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Service to fetch metadata for a list of videos and enqueue them for download.
 * Running as a Foreground Service to ensure longevity.
 */
public class PlaylistEnqueuerService extends Service {
    private static final String TAG = "PlaylistEnqueuer";
    public static final String ACTION_ENQUEUE_PLAYLIST = "org.schabi.newpipe.action.ENQUEUE_PLAYLIST";
    
    public static final String ACTION_CANCEL_ALL = "org.schabi.newpipe.action.CANCEL_PLAYLIST";
    
    public static final String EXTRA_URLS = "org.schabi.newpipe.extra.URLS";
    public static final String EXTRA_TITLES = "org.schabi.newpipe.extra.TITLES";
    public static final String EXTRA_QUALITY = "org.schabi.newpipe.extra.QUALITY";

    private static final int NOTIFICATION_ID = 10134;
    private static final String NOTIFICATION_CHANNEL_ID = "playlist_enqueuer_channel";

    private final io.reactivex.rxjava3.disposables.CompositeDisposable disposables = new io.reactivex.rxjava3.disposables.CompositeDisposable();
    private final android.os.Handler mHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    
    // Track cancelled video URLs to stop processing them
    private static final java.util.Set<String> cancelledUrls = 
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    
    /**
     * Cancel a queued item - called from UI
     * @param videoUrl The video URL to cancel
     */
    public static void cancelQueuedItem(String videoUrl) {
        if (videoUrl != null && !videoUrl.isEmpty()) {
            android.util.Log.d(TAG, "🚫 Cancelling queued item: " + videoUrl);
            cancelledUrls.add(videoUrl);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_ENQUEUE_PLAYLIST.equals(action)) {
                ArrayList<String> urls = intent.getStringArrayListExtra(EXTRA_URLS);
                ArrayList<String> titles = intent.getStringArrayListExtra(EXTRA_TITLES);
                String quality = intent.getStringExtra(EXTRA_QUALITY);
                
                startForeground(NOTIFICATION_ID, createNotification(0, urls.size()));
                
                // Start DownloadManagerService first
                Intent dmIntent = new Intent(this, DownloadManagerService.class);
                startService(dmIntent);
                
                // Wait 500ms for service to initialize, THEN process playlist
                // This allows onCreate() to complete on Main Thread
                mHandler.postDelayed(() -> {
                    android.util.Log.d(TAG, "⏰ Starting playlist processing after delay...");
                    processPlaylist(urls, titles, quality);
                }, 500);  // 500ms delay
                
            } else if (ACTION_CANCEL_ALL.equals(action)) {
                Log.d(TAG, "Cancelling playlist enqueuing...");
                disposables.clear();
                mHandler.removeCallbacksAndMessages(null);  // Cancel any pending tasks
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        return START_NOT_STICKY;
    }

    private void processPlaylist(List<String> urls, List<String> titles, String quality) {
        final AtomicInteger progressCounter = new AtomicInteger(0);
        final Random random = new Random(); // For jitter
        final int total = urls.size();

        // ===== 🆕 Add all videos to queue first =====
        android.util.Log.e(TAG, "========================================");
        android.util.Log.e(TAG, "🔍 DEBUG: processPlaylist() called");
        android.util.Log.e(TAG, "   Total videos: " + total);
        android.util.Log.e(TAG, "   Quality: " + quality);
        android.util.Log.e(TAG, "========================================");
        
        DownloadManager downloadManager = getDownloadManager();
        
        if (downloadManager != null) {
            android.util.Log.e(TAG, "✅ SUCCESS: DownloadManager obtained!");
            android.util.Log.e(TAG, "   Adding " + total + " videos to queue...");
            
            for (int i = 0; i < total; i++) {
                QueuedMission queued = new QueuedMission();
                queued.videoUrl = urls.get(i);
                queued.title = titles.get(i);
                queued.targetQuality = quality;
                queued.status = QueuedMission.Status.WAITING;
                queued.timestamp = System.currentTimeMillis();
                queued.positionInPlaylist = i;
                
                // Try to get thumbnail from URL (basic heuristic)
                queued.thumbnailUrl = extractThumbnailFromUrl(urls.get(i));
                
                downloadManager.addQueuedMission(queued);
                android.util.Log.d(TAG, "   ✅ #" + i + ": " + queued.title);
            }
            
            android.util.Log.e(TAG, "========================================");
            android.util.Log.e(TAG, "✅ All " + total + " missions added to queue");
            android.util.Log.e(TAG, "========================================");
        } else {
            android.util.Log.e(TAG, "========================================");
            android.util.Log.e(TAG, "❌ CRITICAL ERROR: DownloadManager is NULL!");
            android.util.Log.e(TAG, "   This means QueuedMissions will NOT be added!");
            android.util.Log.e(TAG, "   Queue section will NOT appear in UI!");
            android.util.Log.e(TAG, "========================================");
        }

        // Use 'range' to handle indices, mapped to parallel execution via flatMap
        disposables.add(Observable.range(0, total)
                .flatMap(index -> Observable.fromCallable(() -> {
                    String url = urls.get(index);
                    String title = titles.get(index);

                    // --- Safety Mechanism: Random Jitter ---
                    // Sleep for 500ms to 1500ms to avoid pattern detection/rate limiting
                    try {
                        long jitter = 500 + random.nextInt(1000);
                        Thread.sleep(jitter);
                    } catch (InterruptedException e) {
                        // If interrupted, it means the service is likely shutting down or the task was cancelled.
                        // We can just return false to indicate failure for this item.
                        android.util.Log.d(TAG, "Thread interrupted during jitter sleep for: " + title);
                        return false;
                    }

                    // Update notification with "Processed / Total" count
                    int current = progressCounter.incrementAndGet();
                    updateNotification(current, total, title);

                    // Check if cancelled before extracting
                    if (cancelledUrls.contains(url)) {
                        android.util.Log.d(TAG, "   🚫 Item cancelled before extraction: " + title);
                        cancelledUrls.remove(url);
                        if (downloadManager != null) {
                            downloadManager.removeQueuedMissionByUrl(url);
                        }
                        return false;
                    }

                    // Update status to EXTRACTING
                    if (downloadManager != null) {
                        downloadManager.updateQueuedMissionStatus(index, QueuedMission.Status.EXTRACTING);
                    }

                    // 1. Extract StreamInfo
                    org.schabi.newpipe.extractor.stream.StreamInfo info;
                    try {
                        int serviceId = org.schabi.newpipe.extractor.NewPipe.getServiceByUrl(url).getServiceId();
                        info = org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(
                            org.schabi.newpipe.extractor.NewPipe.getService(serviceId), url);
                    } catch (Exception e) {
                        android.util.Log.e(TAG, "❌ Failed to extract info for: " + title, e);
                        
                        // 🆕 Mark as FAILED
                        if (downloadManager != null) {
                            QueuedMission mission = downloadManager.getQueuedMission(index);
                            if (mission != null) {
                                mission.status = QueuedMission.Status.FAILED;
                                mission.errorMessage = e.getMessage();
                                downloadManager.updateQueuedMissionStatus(index, QueuedMission.Status.FAILED);
                            }
                        }
                        return false;
                    }
                    
                    // 🆕 Update thumbnail URL if available
                    if (downloadManager != null && info.getThumbnails() != null && !info.getThumbnails().isEmpty()) {
                        QueuedMission mission = downloadManager.getQueuedMission(index);
                        if (mission != null) {
                            mission.thumbnailUrl = info.getThumbnails().get(0).getUrl();
                        }
                    }

                    // 🆕 Update status to PREPARING
                    if (downloadManager != null) {
                        downloadManager.updateQueuedMissionStatus(index, QueuedMission.Status.PREPARING);
                    }

                    // 2. Prepare complete download bundle
                    PlaylistDownloadLogic.DownloadBundle bundle = 
                        PlaylistDownloadLogic.prepareDownload(this, info, quality);

                    if (bundle == null) {
                        android.util.Log.e(TAG, "❌ Bundle is NULL for: " + title);
                        
                        // 🆕 Mark as FAILED
                        if (downloadManager != null) {
                            QueuedMission mission = downloadManager.getQueuedMission(index);
                            if (mission != null) {
                                mission.status = QueuedMission.Status.FAILED;
                                mission.errorMessage = "Failed to prepare download";
                                downloadManager.updateQueuedMissionStatus(index, QueuedMission.Status.FAILED);
                            }
                        }
                        return false;
                    }
                                // 3. Create Storage directly
                    boolean isVideo = bundle.kind == 'v';
                    String key = getString(isVideo ? R.string.download_path_video_key : R.string.download_path_audio_key);

                    StoredFileHelper finalStorage;
                    try {
                        String downloadPath = androidx.preference.PreferenceManager
                            .getDefaultSharedPreferences(this)
                            .getString(key, null);

                        if (downloadPath == null || downloadPath.isEmpty()) {
                            java.io.File defaultDir = NewPipeSettings.getDir(
                                isVideo ? android.os.Environment.DIRECTORY_MOVIES 
                                       : android.os.Environment.DIRECTORY_MUSIC);
                            downloadPath = android.net.Uri.fromFile(defaultDir).toString();
                        }

                        android.net.Uri pathUri = android.net.Uri.parse(downloadPath);
                        StoredDirectoryHelper storage = new StoredDirectoryHelper(this, pathUri, null);

                        finalStorage = storage.createUniqueFile(bundle.filename, bundle.mimeType);
                
                        if (finalStorage == null) {
                            android.util.Log.e(TAG, "❌ createUniqueFile returned NULL for: " + title);
                            return false;
                        }

                    } catch (IOException e) {
                        android.util.Log.e(TAG, "❌❌ IOException creating storage", e);
                        return false;
                    }

                    // 4. Start the download
                    android.util.Log.d(TAG, "📥 Starting download mission for: " + title);
                    DownloadManagerService.startMission(
                        getApplicationContext(),
                        bundle.urls,
                        finalStorage,
                        bundle.kind,
                        3, // threads
                        info,
                        bundle.psName,
                        bundle.psArgs,
                        bundle.nearLength,
                        new ArrayList<>(bundle.recovery)
                    );
                    android.util.Log.d(TAG, "   ✅ startMission() completed");

                    // 🆕 Remove from queue on success - use URL instead of index
                    if (downloadManager != null) {
                        android.util.Log.d(TAG, "   🗑️ Removing from queue: " + url);
                        boolean removed = downloadManager.removeQueuedMissionByUrl(url);
                        if (removed) {
                            android.util.Log.d(TAG, "   ✅ Removed from queue - UI should update now");
                        } else {
                            android.util.Log.w(TAG, "   ⚠️ Could not remove from queue (already removed?)");
                        }
                    }

                    android.util.Log.d(TAG, "✅ Started download for: " + title);
                    return true;

                }), false, 2) // concurrency = 2 (reduced from 3)
                .subscribeOn(Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(
                    success -> {
                        // Handle individual success if needed
                    },
                    error -> {
                        android.util.Log.e(TAG, "Error processing playlist item", error);
                        stopForeground(true);
                        stopSelf();
                    },
                    () -> {
                        // All items processed
                        android.util.Log.d(TAG, "Playlist processing complete.");
                        stopForeground(true);
                        stopSelf();
                    }
                ));
    }

    /**
     * Get DownloadManager instance from DownloadManagerService
     * Should be called after service has had time to initialize
     */
    private DownloadManager getDownloadManager() {
        android.util.Log.d(TAG, "🔍 Getting DownloadManager...");
        
        try {
            DownloadManager dm = DownloadManagerService.getDownloadManager();
            
            if (dm != null) {
                android.util.Log.d(TAG, "   ✅ DownloadManager retrieved successfully!");
                return dm;
            } else {
                android.util.Log.e(TAG, "   ❌ DownloadManager is still NULL");
                android.util.Log.e(TAG, "   This should not happen after 500ms delay!");
                return null;
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "   ❌ Exception getting DownloadManager", e);
            return null;
        }
    }

    /**
     * Extract thumbnail URL from video URL (basic heuristic for YouTube)
     */
    private String extractThumbnailFromUrl(String videoUrl) {
        try {
            if (videoUrl.contains("youtube.com") || videoUrl.contains("youtu.be")) {
                // Extract video ID
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
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
    private Notification createNotification(int progress, int max) {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "Playlist Download", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        
        Intent cancelIntent = new Intent(this, PlaylistEnqueuerService.class);
        cancelIntent.setAction(ACTION_CANCEL_ALL);
        android.app.PendingIntent cancelPendingIntent = android.app.PendingIntent.getService(this, 0, cancelIntent, 
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? (android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT) : android.app.PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Queueing Playlist Downloads")
                .setContentText(progress + "/" + max)
                .setSmallIcon(R.drawable.ic_playlist_add) // verify icon exists
                .setProgress(max, progress, false)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.cancel), cancelPendingIntent)
                .setOngoing(true)
                .build();
    }
    
    private void updateNotification(int progress, int max, String text) {
        Intent cancelIntent = new Intent(this, PlaylistEnqueuerService.class);
        cancelIntent.setAction(ACTION_CANCEL_ALL);
        android.app.PendingIntent cancelPendingIntent = android.app.PendingIntent.getService(this, 0, cancelIntent, 
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? (android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT) : android.app.PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Queueing Playlist Downloads")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(max, progress, false)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.cancel), cancelPendingIntent)
                .setOngoing(true)
                .build();
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
    }
    
    @Override
    public void onDestroy() {
        disposables.clear();
        mHandler.removeCallbacksAndMessages(null);  // Clean up any pending tasks
        cancelledUrls.clear();  // Clear cancelled URLs
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
