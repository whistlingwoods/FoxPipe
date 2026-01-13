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
    private static final String CHANNEL_ID = "playlist_enqueuer_channel";

    private final io.reactivex.rxjava3.disposables.CompositeDisposable disposables = new io.reactivex.rxjava3.disposables.CompositeDisposable();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_ENQUEUE_PLAYLIST.equals(action)) {
                ArrayList<String> urls = intent.getStringArrayListExtra(EXTRA_URLS);
                ArrayList<String> titles = intent.getStringArrayListExtra(EXTRA_TITLES);
                String quality = intent.getStringExtra(EXTRA_QUALITY);
                
                startForeground(NOTIFICATION_ID, createNotification(0, urls.size()));
                
                // Process directly without needing DownloadManagerService binding
                processPlaylist(urls, titles, quality);
            } else if (ACTION_CANCEL_ALL.equals(action)) {
                Log.d(TAG, "Cancelling playlist enqueuing...");
                disposables.clear();
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
                        return false;
                    }

                    // Update notification with "Processed / Total" count
                    int currentCount = progressCounter.incrementAndGet();
                    updateNotification(currentCount, total, "Processing (" + currentCount + "/" + total + "): " + title);

                    processSingleVideo(url, title, quality);
                    return true;
                }).subscribeOn(Schedulers.io()), 3) // <--- Safety Mechanism: Max Concurrency = 3
                .doOnComplete(this::stopSelf)
                .subscribe());
    }

    private void processSingleVideo(String url, String title, String quality) {
        try {
            Log.d(TAG, "=== Processing: " + title + " ===");
            Log.d(TAG, "URL: " + url);
            Log.d(TAG, "Quality: " + quality);

            // 1. Fetch Info
            StreamInfo info = StreamInfo.getInfo(NewPipe.getService(0), url);
            Log.d(TAG, "StreamInfo fetched successfully");
            
            // 2. Prepare complete download bundle
            PlaylistDownloadLogic.DownloadBundle bundle = 
                PlaylistDownloadLogic.prepareDownload(this, info, quality);

            if (bundle == null) {
                Log.e(TAG, "❌ Bundle is NULL for: " + title);
                return;
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
                    Log.e(TAG, "❌ createUniqueFile returned NULL for: " + title);
                    return;
                }

            } catch (IOException e) {
                Log.e(TAG, "❌❌ IOException creating storage", e);
                return;
            }

            // 4. Enqueue
            DownloadManagerService.startMission(this, bundle.urls, finalStorage, 
                bundle.kind, 3, info, bundle.psName, bundle.psArgs, 
                bundle.nearLength, new ArrayList<>(bundle.recovery));
            
            Log.d(TAG, "✅ Mission started successfully for: " + title);

        } catch (Exception e) {
            Log.e(TAG, "❌❌❌ EXCEPTION while processing: " + title, e);
        }
    }

    private Notification createNotification(int progress, int max) {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Playlist Download", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        
        Intent cancelIntent = new Intent(this, PlaylistEnqueuerService.class);
        cancelIntent.setAction(ACTION_CANCEL_ALL);
        android.app.PendingIntent cancelPendingIntent = android.app.PendingIntent.getService(this, 0, cancelIntent, 
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? (android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT) : android.app.PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
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

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
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
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
