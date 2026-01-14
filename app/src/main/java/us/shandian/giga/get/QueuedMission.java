package us.shandian.giga.get;

import androidx.annotation.NonNull;

import java.io.Serializable;

/**
 * Represents a video/audio download that is waiting to be processed.
 * Used by PlaylistEnqueuerService to show items in the queue before actual download starts.
 */
public class QueuedMission extends Mission implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Status of the queued mission
     */
    public enum Status {
        WAITING,        // Waiting to be processed
        EXTRACTING,     // Currently extracting stream info
        PREPARING,      // Preparing download bundle
        FAILED          // Failed during extraction
    }
    
    // === Basic Information ===
    
    /** Original video URL */
    public String videoUrl;
    
    /** Video title from playlist */
    public String title;
    
    /** Thumbnail URL */
    public String thumbnailUrl;
    
    /** Target quality (e.g., "720p", "Audio High") */
    public String targetQuality;
    
    /** Current status */
    public Status status;
    
    /** Error message if status is FAILED */
    public String errorMessage;
    
    /** Timestamp when added to queue */
    public long timestamp;
    
    /** Position in original playlist (0-indexed) */
    public int positionInPlaylist;
    
    // === Constructors ===
    
    public QueuedMission() {
        this.timestamp = System.currentTimeMillis();
        this.status = Status.WAITING;
    }
    
    public QueuedMission(String videoUrl, String title, String targetQuality) {
        this();
        this.videoUrl = videoUrl;
        this.title = title;
        this.targetQuality = targetQuality;
    }
    
    // === Mission Abstract Methods Implementation ===
    
    public long getLength() {
        return -1;  // Unknown until extraction completes
    }
    
    public boolean isFinished() {
        return false;  // Never finished (moves to DownloadMission when ready)
    }
    
    // === Helper Methods ===
    
    /**
     * Check if this mission is currently being processed
     */
    public boolean isProcessing() {
        return status == Status.EXTRACTING || status == Status.PREPARING;
    }
    
    /**
     * Check if this mission failed
     */
    public boolean isFailed() {
        return status == Status.FAILED;
    }
    
    /**
     * Get a user-friendly status string
     */
    @NonNull
    public String getStatusString() {
        switch (status) {
            case WAITING:
                return "Waiting...";
            case EXTRACTING:
                return "Extracting info...";
            case PREPARING:
                return "Preparing download...";
            case FAILED:
                return "Failed" + (errorMessage != null ? ": " + errorMessage : "");
            default:
                return "Unknown";
        }
    }
    
    @NonNull
    @Override
    public String toString() {
        return "QueuedMission{" +
                "title='" + title + '\'' +
                ", status=" + status +
                ", position=" + positionInPlaylist +
                '}';
    }
}
