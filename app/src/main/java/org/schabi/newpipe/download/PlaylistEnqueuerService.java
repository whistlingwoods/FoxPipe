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

/**
 * Service to fetch metadata for a list of videos and enqueue them for download.
 * Running as a Foreground Service to ensure longevity.
 */
public class PlaylistEnqueuerService extends Service {
    private static final String TAG = "PlaylistEnqueuer";
    public static final String ACTION_ENQUEUE_PLAYLIST = "org.schabi.newpipe.action.ENQUEUE_PLAYLIST";
    
    public static final String EXTRA_URLS = "org.schabi.newpipe.extra.URLS";
    public static final String EXTRA_TITLES = "org.schabi.newpipe.extra.TITLES";
    public static final String EXTRA_QUALITY = "org.schabi.newpipe.extra.QUALITY";

    private static final int NOTIFICATION_ID = 10134;
    private static final String CHANNEL_ID = "playlist_enqueuer_channel";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_ENQUEUE_PLAYLIST.equals(intent.getAction())) {
            ArrayList<String> urls = intent.getStringArrayListExtra(EXTRA_URLS);
            ArrayList<String> titles = intent.getStringArrayListExtra(EXTRA_TITLES);
            String quality = intent.getStringExtra(EXTRA_QUALITY);
            
            startForeground(NOTIFICATION_ID, createNotification(0, urls.size()));
            
            // Process directly without needing DownloadManagerService binding
            processPlaylist(urls, titles, quality);
        }
        return START_NOT_STICKY;
    }

    private void processPlaylist(List<String> urls, List<String> titles, String quality) {
        Observable.fromIterable(urls)
                .zipWith(Observable.fromIterable(titles), (url, title) -> new String[]{url, title})
                .observeOn(Schedulers.io())
                .subscribeOn(Schedulers.io())
                .doOnNext(pair -> {
                      String url = pair[0];
                      String title = pair[1];
                      updateNotification(titles.indexOf(title) + 1, urls.size(), "Processing: " + title);
                      
                      try {
                          Log.d(TAG, "=== Processing: " + title + " ===");
                          Log.d(TAG, "URL: " + url);
                          Log.d(TAG, "Quality: " + quality);
                          
                          // 1. Fetch Info
                          StreamInfo info = StreamInfo.getInfo(NewPipe.getService(0), url);
                          Log.d(TAG, "StreamInfo fetched successfully");
                          Log.d(TAG, "Available audio streams: " + info.getAudioStreams().size());
                          Log.d(TAG, "Available video streams: " + info.getVideoStreams().size());
                          
                          // 2. Prepare complete download bundle (with muxing support)
                          PlaylistDownloadLogic.DownloadBundle bundle = 
                              PlaylistDownloadLogic.prepareDownload(this, info, quality);
                          
                          if (bundle == null) {
                              Log.e(TAG, "❌ Bundle is NULL for: " + title);
                              return;
                          }
                          
                          Log.d(TAG, "Bundle created successfully:");
                          Log.d(TAG, "  - kind: " + bundle.kind);
                          Log.d(TAG, "  - urls: " + java.util.Arrays.toString(bundle.urls));
                          Log.d(TAG, "  - filename: " + bundle.filename);
                          Log.d(TAG, "  - mimeType: " + bundle.mimeType);
                          Log.d(TAG, "  - psName: " + bundle.psName);
                          Log.d(TAG, "  - recovery size: " + bundle.recovery.size());
                          
                          // 3. Create Storage directly (like DownloadDialog does)
                          boolean isVideo = bundle.kind == 'v';
                          String key = getString(isVideo ? R.string.download_path_video_key : R.string.download_path_audio_key);
                          
                          StoredFileHelper finalStorage;
                          try {
                              // Get download path from preferences
                              String downloadPath = androidx.preference.PreferenceManager
                                  .getDefaultSharedPreferences(this)
                                  .getString(key, null);
                              
                              if (downloadPath == null || downloadPath.isEmpty()) {
                                  // Use default directory
                                  java.io.File defaultDir = NewPipeSettings.getDir(
                                      isVideo ? android.os.Environment.DIRECTORY_MOVIES 
                                             : android.os.Environment.DIRECTORY_MUSIC);
                                  downloadPath = android.net.Uri.fromFile(defaultDir).toString();
                              }
                              
                              android.net.Uri pathUri = android.net.Uri.parse(downloadPath);
                              StoredDirectoryHelper storage = new StoredDirectoryHelper(this, pathUri, null);
                              
                              // Create unique file
                              finalStorage = storage.createUniqueFile(bundle.filename, bundle.mimeType);
                              
                              if (finalStorage == null) {
                                  Log.e(TAG, "❌ createUniqueFile returned NULL");
                                  Log.e(TAG, "  Filename: " + bundle.filename);
                                  Log.e(TAG, "  Filename length: " + bundle.filename.length());
                                  Log.e(TAG, "  MIME: " + bundle.mimeType);
                                  return;
                              }
                              
                              Log.d(TAG, "✅ File created: " + finalStorage.getUri());
                              
                          } catch (IOException e) {
                              Log.e(TAG, "❌❌ IOException creating storage", e);
                              Log.e(TAG, "  Exception message: " + e.getMessage());
                              Log.e(TAG, "  Filename: " + bundle.filename);
                              return;
                          }

                          // 4. Enqueue with complete bundle information
                          Log.d(TAG, "Calling DownloadManagerService.startMission...");
                          DownloadManagerService.startMission(this, bundle.urls, finalStorage, 
                              bundle.kind, 3, info, bundle.psName, bundle.psArgs, 
                              bundle.nearLength, new ArrayList<>(bundle.recovery));
                          Log.d(TAG, "✅ Mission started successfully for: " + title);
                          
                      } catch (Exception e) {
                          Log.e(TAG, "❌❌❌ EXCEPTION while processing: " + title, e);
                          Log.e(TAG, "Exception type: " + e.getClass().getName());
                          Log.e(TAG, "Exception message: " + e.getMessage());
                      }
                })
                .doOnComplete(this::stopSelf)
                .subscribe();
    }

    private Notification createNotification(int progress, int max) {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Playlist Download", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Queueing Playlist Downloads")
                .setContentText(progress + "/" + max)
                .setSmallIcon(R.drawable.ic_playlist_add) // verify icon exists
                .setProgress(max, progress, false)
                .build();
    }
    
    private void updateNotification(int progress, int max, String text) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Queueing Playlist Downloads")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(max, progress, false)
                .build();
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
