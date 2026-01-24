package us.shandian.giga.service;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.get.FinishedMission;
import us.shandian.giga.get.Mission;
import us.shandian.giga.get.QueuedMission;
import us.shandian.giga.get.sqlite.FinishedMissionStore;
import org.schabi.newpipe.streams.io.StoredDirectoryHelper;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import us.shandian.giga.util.Utility;

import static org.schabi.newpipe.BuildConfig.DEBUG;
import static us.shandian.giga.get.DownloadMission.ERROR_NOTHING;
import static us.shandian.giga.get.DownloadMission.ERROR_PROGRESS_LOST;

public class DownloadManager {
    private static final String TAG = DownloadManager.class.getSimpleName();

    enum NetworkState {Unavailable, Operating, MeteredOperating}

    public static final int SPECIAL_NOTHING = 0;
    public static final int SPECIAL_PENDING = 1;
    public static final int SPECIAL_FINISHED = 2;
    public static final int SPECIAL_QUEUED = 3;  // For queued downloads (waiting to be processed)

    public static final String TAG_AUDIO = "audio";
    public static final String TAG_VIDEO = "video";
    private static final String DOWNLOADS_METADATA_FOLDER = "pending_downloads";

    private final FinishedMissionStore mFinishedMissionStore;

    private final ArrayList<DownloadMission> mMissionsPending = new ArrayList<>();
    private final ArrayList<FinishedMission> mMissionsFinished;
    private final ArrayList<QueuedMission> mMissionsQueued = new ArrayList<>();  // Queued downloads

    private final Handler mHandler;
    private final File mPendingMissionsDir;

    private NetworkState mLastNetworkStatus = NetworkState.Unavailable;

    int mPrefMaxRetry;
    boolean mPrefMeteredDownloads;
    boolean mPrefQueueLimit;
    private boolean mSelfMissionsControl;

    StoredDirectoryHelper mMainStorageAudio;
    StoredDirectoryHelper mMainStorageVideo;

    /**
     * Create a new instance
     *
     * @param context Context for the data source for finished downloads
     * @param handler Thread required for Messaging
     */
    DownloadManager(@NonNull Context context, Handler handler, StoredDirectoryHelper storageVideo, StoredDirectoryHelper storageAudio) {
        if (DEBUG) {
            Log.d(TAG, "new DownloadManager instance. 0x" + Integer.toHexString(this.hashCode()));
        }

        mFinishedMissionStore = new FinishedMissionStore(context);
        mHandler = handler;
        mMainStorageAudio = storageAudio;
        mMainStorageVideo = storageVideo;
        mMissionsFinished = loadFinishedMissions();
        mPendingMissionsDir = getPendingDir(context);

        loadPendingMissions(context);
    }

    private static File getPendingDir(@NonNull Context context) {
        File dir = context.getExternalFilesDir(DOWNLOADS_METADATA_FOLDER);
        if (testDir(dir)) return dir;

        dir = new File(context.getFilesDir(), DOWNLOADS_METADATA_FOLDER);
        if (testDir(dir)) return dir;

        throw new RuntimeException("path to pending downloads are not accessible");
    }

    private static boolean testDir(@Nullable File dir) {
        if (dir == null) return false;

        try {
            if (!Utility.mkdir(dir, false)) {
                Log.e(TAG, "testDir() cannot create the directory in path: " + dir.getAbsolutePath());
                return false;
            }

            File tmp = new File(dir, ".tmp");
            if (!tmp.createNewFile()) return false;
            return tmp.delete();// if the file was created, SHOULD BE deleted too
        } catch (Exception e) {
            Log.e(TAG, "testDir() failed: " + dir.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Loads finished missions from the data source and forgets finished missions whose file does
     * not exist anymore.
     */
    private ArrayList<FinishedMission> loadFinishedMissions() {
        ArrayList<FinishedMission> finishedMissions = mFinishedMissionStore.loadFinishedMissions();

        // check if the files exists, otherwise, forget the download
        for (int i = finishedMissions.size() - 1; i >= 0; i--) {
            FinishedMission mission = finishedMissions.get(i);

            if (!mission.storage.existsAsFile()) {
                if (DEBUG) Log.d(TAG, "downloaded file removed: " + mission.storage.getName());

                mFinishedMissionStore.deleteMission(mission);
                finishedMissions.remove(i);
            }
        }

        return finishedMissions;
    }

    private void loadPendingMissions(Context ctx) {
        File[] subs = mPendingMissionsDir.listFiles();

        if (subs == null) {
            Log.e(TAG, "listFiles() returned null");
            return;
        }
        if (subs.length < 1) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "Loading pending downloads from directory: " + mPendingMissionsDir.getAbsolutePath());
        }

        File tempDir = pickAvailableTemporalDir(ctx);
        Log.i(TAG, "using '" + tempDir + "' as temporal directory");

        for (File sub : subs) {
            if (!sub.isFile()) continue;
            if (sub.getName().equals(".tmp")) continue;

            DownloadMission mis = Utility.readFromFile(sub);
            if (mis == null) {
                //noinspection ResultOfMethodCallIgnored
                sub.delete();
                continue;
            }

            // DON'T delete missions that are truly finished - let them be moved to finished list
            if (mis.isFinished()) {
                // Move to finished missions instead of deleting
                setFinished(mis);
                //noinspection ResultOfMethodCallIgnored
                sub.delete();
                continue;
            }

            // DON'T delete missions with storage issues - try to recover them
            if (mis.hasInvalidStorage() && mis.errCode != ERROR_PROGRESS_LOST) {
                // Only delete if it's truly unrecoverable (not just progress lost)
                if (mis.storage == null) {
                    //noinspection ResultOfMethodCallIgnored
                    sub.delete();
                    continue;
                }
            }

            mis.threads = new Thread[0];

            boolean exists;
            try {
                mis.storage = StoredFileHelper.deserialize(mis.storage, ctx);
                exists = !mis.storage.isInvalid() && mis.storage.existsAsFile();
            } catch (Exception ex) {
                Log.e(TAG, "Failed to load the file source of " + mis.storage.toString(), ex);
                // Don't invalidate storage immediately - try to recover first
                exists = false;
            }

            if (mis.isPsRunning()) {
                if (mis.psAlgorithm.worksOnSameFile) {
                    // Incomplete post-processing results in a corrupted download file
                    if (exists && mis.storage.isDirect() && !mis.storage.delete())
                        Log.w(TAG, "Unable to delete incomplete download file: " + sub.getPath());
                }

                mis.psState = 0;
                mis.errCode = DownloadMission.ERROR_POSTPROCESSING_STOPPED;
            } else if (!exists) {
                tryRecover(mis);
                // Keep the mission even if recovery fails - don't reset to ERROR_PROGRESS_LOST
                // This allows user to see the failed download and potentially retry
                if (mis.isInitialized() && mis.errCode == ERROR_NOTHING) {
                    mis.resetState(true, true, ERROR_PROGRESS_LOST);
                }
            }

            if (mis.psAlgorithm != null) {
                mis.psAlgorithm.cleanupTemporalDir();
                mis.psAlgorithm.setTemporalDir(tempDir);
            }

            mis.metadata = sub;
            mis.maxRetry = mPrefMaxRetry;
            mis.mHandler = mHandler;

            mMissionsPending.add(mis);
        }

        if (mMissionsPending.size() > 1)
            Collections.sort(mMissionsPending, Comparator.comparingLong(Mission::getTimestamp));
    }

    /**
     * Start a new download mission
     *
     * @param mission the new download mission to add and run (if possible)
     */
    void startMission(DownloadMission mission) {
        synchronized (this) {
            mission.timestamp = System.currentTimeMillis();
            mission.mHandler = mHandler;
            mission.maxRetry = mPrefMaxRetry;

            // create metadata file
            while (true) {
                mission.metadata = new File(mPendingMissionsDir, String.valueOf(mission.timestamp));
                if (!mission.metadata.isFile() && !mission.metadata.exists()) {
                    try {
                        if (!mission.metadata.createNewFile())
                            throw new RuntimeException("Cant create download metadata file");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    break;
                }
                mission.timestamp = System.currentTimeMillis();
            }

            mSelfMissionsControl = true;
            mMissionsPending.add(mission);

            // Before continue, save the metadata in case the internet connection is not available
            Utility.writeToFile(mission.metadata, mission);

            if (mission.storage == null) {
                // noting to do here
                mission.errCode = DownloadMission.ERROR_FILE_CREATION;
                if (mission.errObject != null)
                    mission.errObject = new IOException("DownloadMission.storage == NULL");
                return;
            }

            boolean start = !mPrefQueueLimit || getRunningMissionsCount() < 1;

            if (canDownloadInCurrentNetwork() && start) {
                mission.start();
            }
        }
    }


    public void resumeMission(DownloadMission mission) {
        if (!mission.running) {
            mission.start();
        }
    }

    public void pauseMission(DownloadMission mission) {
        if (mission.running) {
            mission.setEnqueued(false);
            mission.pause();
        }
    }

    public void deleteMission(Mission mission, boolean alsoDeleteFile) {
        synchronized (this) {
            if (mission instanceof DownloadMission) {
                mMissionsPending.remove(mission);
            } else if (mission instanceof FinishedMission) {
                mMissionsFinished.remove(mission);
                mFinishedMissionStore.deleteMission(mission);
            }

            if (alsoDeleteFile) {
                mission.delete();
            }
        }
    }

    public void forgetMission(StoredFileHelper storage) {
        synchronized (this) {
            Mission mission = getAnyMission(storage);
            if (mission == null) return;

            if (mission instanceof DownloadMission) {
                mMissionsPending.remove(mission);
            } else if (mission instanceof FinishedMission) {
                mMissionsFinished.remove(mission);
                mFinishedMissionStore.deleteMission(mission);
            }

            mission.storage = null;
            mission.delete();
        }
    }

    public void tryRecover(DownloadMission mission) {
        StoredDirectoryHelper mainStorage = getMainStorage(mission.storage.getTag());

        if (!mission.storage.isInvalid() && mission.storage.create()) return;

        // using javaIO cannot recreate the file
        // using SAF in older devices (no tree available)
        //
        // force the user to pick again the save path
        mission.storage.invalidate();

        if (mainStorage == null) return;

        // if the user has changed the save path before this download, the original save path will be lost
        StoredFileHelper newStorage = mainStorage.createFile(mission.storage.getName(), mission.storage.getType());

        if (newStorage != null) mission.storage = newStorage;
    }


    /**
     * Get a pending mission by its path
     *
     * @param storage where the file possible is stored
     * @return the mission or null if no such mission exists
     */
    @Nullable
    private DownloadMission getPendingMission(StoredFileHelper storage) {
        for (DownloadMission mission : mMissionsPending) {
            if (mission.storage.equals(storage)) {
                return mission;
            }
        }
        return null;
    }

    /**
     * Get the index into {@link #mMissionsFinished} of a finished mission by its path, return
     * {@code -1} if there is no such mission. This function also checks if the matched mission's
     * file exists, and, if it does not, the related mission is forgotten about (like in {@link
     * #loadFinishedMissions()}) and {@code -1} is returned.
     *
     * @param storage where the file would be stored
     * @return the mission index or -1 if no such mission exists
     */
    private int getFinishedMissionIndex(StoredFileHelper storage) {
        for (int i = 0; i < mMissionsFinished.size(); i++) {
            if (mMissionsFinished.get(i).storage.equals(storage)) {
                // If the file does not exist the mission is not valid anymore. Also checking if
                // length == 0 since the file picker may create an empty file before yielding it,
                // but that does not mean the file really belonged to a previous mission.
                if (!storage.existsAsFile() || storage.length() == 0) {
                    if (DEBUG) {
                        Log.d(TAG, "matched downloaded file removed: " + storage.getName());
                    }

                    mFinishedMissionStore.deleteMission(mMissionsFinished.get(i));
                    mMissionsFinished.remove(i);
                    return -1; // finished mission whose associated file was removed
                }
                return i;
            }
        }

        return -1;
    }

    private Mission getAnyMission(StoredFileHelper storage) {
        synchronized (this) {
            Mission mission = getPendingMission(storage);
            if (mission != null) return mission;

            int idx = getFinishedMissionIndex(storage);
            if (idx >= 0) return mMissionsFinished.get(idx);
        }

        return null;
    }

    int getRunningMissionsCount() {
        int count = 0;
        synchronized (this) {
            for (DownloadMission mission : mMissionsPending) {
                if (mission.running && !mission.isPsFailed() && !mission.isFinished())
                    count++;
            }
        }

        return count;
    }

    public void pauseAllMissions(boolean force) {
        synchronized (this) {
            for (DownloadMission mission : mMissionsPending) {
                if (!mission.running || mission.isPsRunning() || mission.isFinished()) continue;

                if (force) {
                    // avoid waiting for threads
                    mission.init = null;
                    mission.threads = new Thread[0];
                }

                mission.pause();
            }
        }
    }

    public void startAllMissions() {
        synchronized (this) {
            for (DownloadMission mission : mMissionsPending) {
                if (mission.running || mission.isCorrupt()) continue;

                mission.start();
            }
        }
    }

    /**
     * Check if a mission is finished
     *
     * @return {@code true} if exists
     */
    public boolean existsFinishedMission(StoredFileHelper storage) {
        synchronized (this) {
            for (FinishedMission mission : mMissionsFinished) {
                if (mission.storage != null && mission.storage.equals(storage)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ========== Queued Missions Management ==========

    /**
     * Add a new queued mission
     * @param mission The queued mission to add
     */
    public void addQueuedMission(QueuedMission mission) {
        synchronized (this) {
            mMissionsQueued.add(mission);
            android.util.Log.d(TAG, "✅ QueuedMission added: \"" + mission.title + "\"");
            android.util.Log.d(TAG, "   Total queued: " + mMissionsQueued.size());
            mHandler.sendEmptyMessage(DownloadManagerService.MESSAGE_RUNNING);
            android.util.Log.d(TAG, "   MESSAGE_RUNNING sent to UI");
        }
    }

    /**
     * Update the status of a queued mission
     * @param index Index of the queued mission
     * @param newStatus New status to set
     */
    public void updateQueuedMissionStatus(int index, QueuedMission.Status newStatus) {
        synchronized (this) {
            if (index >= 0 && index < mMissionsQueued.size()) {
                mMissionsQueued.get(index).status = newStatus;
                mHandler.sendEmptyMessage(DownloadManagerService.MESSAGE_RUNNING);
            }
        }
    }

    /**
     * Get a queued mission by index
     * @param index Index of the queued mission
     * @return The queued mission, or null if index is invalid
     */
    public QueuedMission getQueuedMission(int index) {
        synchronized (this) {
            if (index >= 0 && index < mMissionsQueued.size()) {
                return mMissionsQueued.get(index);
            }
            return null;
        }
    }

    /**
     * Remove a queued mission (when it starts downloading or fails permanently)
     * @param index Index of the queued mission to remove
     */
    public void removeQueuedMission(int index) {
        synchronized (this) {
            if (index >= 0 && index < mMissionsQueued.size()) {
                QueuedMission removed = mMissionsQueued.remove(index);
                android.util.Log.d(TAG, "🗑️ QueuedMission removed: \"" + removed.title + "\"");
                android.util.Log.d(TAG, "   Remaining in queue: " + mMissionsQueued.size());
                mHandler.sendEmptyMessage(DownloadManagerService.MESSAGE_RUNNING);
                android.util.Log.d(TAG, "   MESSAGE_RUNNING sent to update UI");
            }
        }
    }
    
    /**
     * Remove a queued mission by URL (thread-safe, avoids index issues)
     * @param videoUrl The video URL to remove
     * @return true if removed, false if not found
     */
    public boolean removeQueuedMissionByUrl(String videoUrl) {
        synchronized (this) {
            if (videoUrl == null || videoUrl.isEmpty()) {
                return false;
            }
            
            for (int i = 0; i < mMissionsQueued.size(); i++) {
                QueuedMission mission = mMissionsQueued.get(i);
                if (videoUrl.equals(mission.videoUrl)) {
                    mMissionsQueued.remove(i);
                    android.util.Log.d(TAG, "🗑️ QueuedMission removed by URL: \"" + mission.title + "\"");
                    android.util.Log.d(TAG, "   Remaining in queue: " + mMissionsQueued.size());
                    mHandler.sendEmptyMessage(DownloadManagerService.MESSAGE_RUNNING);
                    android.util.Log.d(TAG, "   MESSAGE_RUNNING sent to update UI");
                    return true;
                }
            }
            android.util.Log.w(TAG, "⚠️ Could not find queued mission with URL: " + videoUrl);
            return false;
        }
    }
    
    /**
     * Update the status of a queued mission by URL (thread-safe, avoids index issues)
     * @param videoUrl The video URL to find
     * @param newStatus New status to set
     * @param errorMessage Optional error message (for FAILED status)
     * @return true if updated, false if not found
     */
    public boolean updateQueuedMissionStatusByUrl(String videoUrl, QueuedMission.Status newStatus, String errorMessage) {
        synchronized (this) {
            if (videoUrl == null || videoUrl.isEmpty()) {
                return false;
            }
            
            for (QueuedMission mission : mMissionsQueued) {
                if (videoUrl.equals(mission.videoUrl)) {
                    mission.status = newStatus;
                    if (errorMessage != null) {
                        mission.errorMessage = errorMessage;
                    }
                    android.util.Log.d(TAG, "📝 QueuedMission status updated: \"" + mission.title + "\" -> " + newStatus);
                    mHandler.sendEmptyMessage(DownloadManagerService.MESSAGE_RUNNING);
                    return true;
                }
            }
            android.util.Log.w(TAG, "⚠️ Could not find queued mission with URL for status update: " + videoUrl);
            return false;
        }
    }
    
    /**
     * Get a queued mission by URL (thread-safe)
     * @param videoUrl The video URL to find
     * @return The queued mission, or null if not found
     */
    public QueuedMission getQueuedMissionByUrl(String videoUrl) {
        synchronized (this) {
            if (videoUrl == null || videoUrl.isEmpty()) {
                return null;
            }
            
            for (QueuedMission mission : mMissionsQueued) {
                if (videoUrl.equals(mission.videoUrl)) {
                    return mission;
                }
            }
            return null;
        }
    }

    /**
     * Get the count of queued missions
     * @return Number of queued missions
     */
    public int getQueuedCount() {
        synchronized (this) {
            return mMissionsQueued.size();
        }
    }

    /**
     * Set a pending download as finished
     *
     * @param mission the desired mission
     */
    void setFinished(DownloadMission mission) {
        synchronized (this) {
            mMissionsPending.remove(mission);
            mMissionsFinished.add(0, new FinishedMission(mission));
            mFinishedMissionStore.addFinishedMission(mission);
        }
    }

    /**
     * runs one or multiple missions in from queue if possible
     *
     * @return true if one or multiple missions are running, otherwise, false
     */
    boolean runMissions() {
        synchronized (this) {
            if (mMissionsPending.size() < 1) return false;
            if (!canDownloadInCurrentNetwork()) return false;

            if (mPrefQueueLimit) {
                for (DownloadMission mission : mMissionsPending)
                    if (!mission.isFinished() && mission.running) return true;
            }

            boolean flag = false;
            for (DownloadMission mission : mMissionsPending) {
                if (mission.running || !mission.enqueued || mission.isFinished())
                    continue;

                resumeMission(mission);
                if (mission.errCode != ERROR_NOTHING) continue;

                if (mPrefQueueLimit) return true;
                flag = true;
            }

            return flag;
        }
    }

    public MissionIterator getIterator() {
        mSelfMissionsControl = true;
        return new MissionIterator();
    }

    /**
     * Forget all finished downloads, but, doesn't delete any file
     */
    public void forgetFinishedDownloads() {
        synchronized (this) {
            for (FinishedMission mission : mMissionsFinished) {
                mFinishedMissionStore.deleteMission(mission);
            }
            mMissionsFinished.clear();
        }
    }

    private boolean canDownloadInCurrentNetwork() {
        if (mLastNetworkStatus == NetworkState.Unavailable) return false;
        return !(mPrefMeteredDownloads && mLastNetworkStatus == NetworkState.MeteredOperating);
    }

    void handleConnectivityState(NetworkState currentStatus, boolean updateOnly) {
        if (currentStatus == mLastNetworkStatus) return;

        mLastNetworkStatus = currentStatus;
        if (currentStatus == NetworkState.Unavailable) return;

        if (!mSelfMissionsControl || updateOnly) {
            return;// don't touch anything without the user interaction
        }

        boolean isMetered = mPrefMeteredDownloads && mLastNetworkStatus == NetworkState.MeteredOperating;

        synchronized (this) {
            for (DownloadMission mission : mMissionsPending) {
                if (mission.isCorrupt() || mission.isPsRunning()) continue;

                if (mission.running && isMetered) {
                    mission.pause();
                } else if (!mission.running && !isMetered && mission.enqueued) {
                    mission.start();
                    if (mPrefQueueLimit) break;
                }
            }
        }
    }

    void updateMaximumAttempts() {
        synchronized (this) {
            for (DownloadMission mission : mMissionsPending) mission.maxRetry = mPrefMaxRetry;
        }
    }

    public boolean canRecoverMission(DownloadMission mission) {
        if (mission == null) return false;

        // Can recover missions with progress lost or storage issues
        return mission.errCode == ERROR_PROGRESS_LOST ||
                mission.storage == null ||
                !mission.storage.existsAsFile();
    }

    public MissionState checkForExistingMission(StoredFileHelper storage) {
        synchronized (this) {
            DownloadMission pending = getPendingMission(storage);

            if (pending == null) {
                if (getFinishedMissionIndex(storage) >= 0) return MissionState.Finished;
            } else {
                if (pending.isFinished()) {
                    return MissionState.Finished;// this never should happen (race-condition)
                } else {
                    return pending.running ? MissionState.PendingRunning : MissionState.Pending;
                }
            }
        }

        return MissionState.None;
    }

    private static boolean isDirectoryAvailable(File directory) {
        return directory != null && directory.canWrite() && directory.exists();
    }

    static File pickAvailableTemporalDir(@NonNull Context ctx) {
        File dir = ctx.getExternalFilesDir(null);
        if (isDirectoryAvailable(dir)) return dir;

        dir = ctx.getFilesDir();
        if (isDirectoryAvailable(dir)) return dir;

        // this never should happen
        dir = ctx.getDir("muxing_tmp", Context.MODE_PRIVATE);
        if (isDirectoryAvailable(dir)) return dir;

        // fallback to cache dir
        dir = ctx.getCacheDir();
        if (isDirectoryAvailable(dir)) return dir;

        throw new RuntimeException("Not temporal directories are available");
    }

    @Nullable
    private StoredDirectoryHelper getMainStorage(@NonNull String tag) {
        if (tag.equals(TAG_AUDIO)) return mMainStorageAudio;
        if (tag.equals(TAG_VIDEO)) return mMainStorageVideo;

        Log.w(TAG, "Unknown download category, not [audio video]: " + tag);

        return null;// this never should happen
    }

    public class MissionIterator extends DiffUtil.Callback {
        final Object FINISHED = new Object();
        final Object PENDING = new Object();
        final Object QUEUED = new Object();  // For queued downloads

        ArrayList<Object> snapshot;
        ArrayList<Object> current;
        ArrayList<Mission> hidden;

        boolean hasFinished = false;

        private MissionIterator() {
            hidden = new ArrayList<>(2);
            current = null;
            snapshot = getSpecialItems();
        }

        private ArrayList<Object> getSpecialItems() {
            synchronized (DownloadManager.this) {
                ArrayList<Mission> pending = new ArrayList<>(mMissionsPending);
                ArrayList<Mission> finished = new ArrayList<>(mMissionsFinished);
                ArrayList<QueuedMission> queued = new ArrayList<>(mMissionsQueued);
                List<Mission> remove = new ArrayList<>(hidden);

                // Don't hide recoverable missions
                remove.removeIf(mission -> {
                    if (mission instanceof DownloadMission dm && canRecoverMission(dm)) {
                        return false; // Don't remove recoverable missions
                    }
                    return pending.remove(mission) || finished.remove(mission);
                });

                int fakeTotal = pending.size();
                if (fakeTotal > 0) fakeTotal++;

                fakeTotal += finished.size();
                if (finished.size() > 0) fakeTotal++;
                
                fakeTotal += queued.size();
                if (queued.size() > 0) fakeTotal++;

                ArrayList<Object> list = new ArrayList<>(fakeTotal);
                
                // Add queued first
                if (queued.size() > 0) {
                    list.add(QUEUED);
                    list.addAll(queued);
                }
                
                if (pending.size() > 0) {
                    list.add(PENDING);
                    list.addAll(pending);
                }
                if (finished.size() > 0) {
                    list.add(FINISHED);
                    list.addAll(finished);
                }

                hasFinished = finished.size() > 0;

                return list;
            }
        }

        public MissionItem getItem(int position) {
            Object object = snapshot.get(position);

            if (object == PENDING) return new MissionItem(SPECIAL_PENDING);
            if (object == FINISHED) return new MissionItem(SPECIAL_FINISHED);
            if (object == QUEUED) return new MissionItem(SPECIAL_QUEUED);

            return new MissionItem(SPECIAL_NOTHING, (Mission) object);
        }

        public int getSpecialAtItem(int position) {
            Object object = snapshot.get(position);

            if (object == PENDING) return SPECIAL_PENDING;
            if (object == FINISHED) return SPECIAL_FINISHED;
            if (object == QUEUED) return SPECIAL_QUEUED;

            return SPECIAL_NOTHING;
        }
        
        /**
         * Get the index of a queued mission in mMissionsQueued list
         * @param mission The queued mission to find
         * @return Index in mMissionsQueued, or -1 if not found
         */
        public int getQueuedMissionIndex(QueuedMission mission) {
            synchronized (DownloadManager.this) {
                return mMissionsQueued.indexOf(mission);
            }
        }


        public void start() {
            current = getSpecialItems();
        }

        public void end() {
            snapshot = current;
            current = null;
        }

        public void hide(Mission mission) {
            hidden.add(mission);
        }

        public void unHide(Mission mission) {
            hidden.remove(mission);
        }

        public boolean hasFinishedMissions() {
            return hasFinished;
        }

        /**
         * Check if exists missions running and paused. Corrupted and hidden missions are not counted
         *
         * @return two-dimensional array contains the current missions state.
         * 1° entry: true if has at least one mission running
         * 2° entry: true if has at least one mission paused
         */
        public boolean[] hasValidPendingMissions() {
            boolean running = false;
            boolean paused = false;

            synchronized (DownloadManager.this) {
                for (DownloadMission mission : mMissionsPending) {
                    if (hidden.contains(mission) || mission.isCorrupt())
                        continue;

                    if (mission.running)
                        running = true;
                    else
                        paused = true;
                }
            }

            return new boolean[]{running, paused};
        }


        @Override
        public int getOldListSize() {
            return snapshot.size();
        }

        @Override
        public int getNewListSize() {
            return current.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return snapshot.get(oldItemPosition) == current.get(newItemPosition);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Object oldItem = snapshot.get(oldItemPosition);
            Object newItem = current.get(newItemPosition);

            if (oldItem == PENDING || oldItem == FINISHED || oldItem == QUEUED) return oldItem == newItem;

            // Handle QueuedMission (no storage yet)
            if (oldItem instanceof QueuedMission && newItem instanceof QueuedMission) {
                QueuedMission oldQueued = (QueuedMission) oldItem;
                QueuedMission newQueued = (QueuedMission) newItem;
                
                // Compare by URL and title since no storage exists yet
                // Using Objects.equals() for null-safe comparison
                return java.util.Objects.equals(oldQueued.videoUrl, newQueued.videoUrl) 
                    && java.util.Objects.equals(oldQueued.title, newQueued.title)
                    && oldQueued.status == newQueued.status;
            }

            // Handle DownloadMission and FinishedMission (have storage)
            Mission oldMission = (Mission) oldItem;
            Mission newMission = (Mission) newItem;
            
            // Null check for storage (QueuedMission doesn't have storage)
            if (oldMission.storage == null || newMission.storage == null) {
                return false; // Different if either has null storage
            }

            return oldMission.storage.equals(newMission.storage) && oldMission.timestamp == newMission.timestamp;
        }
    }

    public static class MissionItem {
        public int special;
        public Mission mission;

        MissionItem(int s, Mission m) {
            special = s;
            mission = m;
        }

        MissionItem(int s) {
            this(s, null);
        }
    }

}
