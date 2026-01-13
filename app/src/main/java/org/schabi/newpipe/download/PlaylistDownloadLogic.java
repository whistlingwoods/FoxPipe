package org.schabi.newpipe.download;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.util.FilenameUtils;
import org.schabi.newpipe.util.SecondaryStreamHelper;

import java.util.List;

import us.shandian.giga.get.MissionRecoveryInfo;
import us.shandian.giga.postprocessing.Postprocessing;

public class PlaylistDownloadLogic {

    public static final String QUAL_BEST_VIDEO = "Best Video";
    public static final String QUAL_BEST_AUDIO = "Best Audio";
    public static final String QUAL_1080P = "1080p";
    public static final String QUAL_720P = "720p";
    public static final String QUAL_480P = "480p";
    public static final String QUAL_360P = "360p";

    /**
     * Bundle containing all information needed to start a download mission.
     */
    public static class DownloadBundle {
        public String[] urls;
        public char kind;
        public String psName;
        public String[] psArgs;
        public long nearLength;
        public List<MissionRecoveryInfo> recovery;
        public String filename;
        public String mimeType;
    }

    /**
     * Prepares a complete download bundle including secondary streams and post-processing.
     *
     * @param context       Android context
     * @param info          StreamInfo containing available streams
     * @param targetQuality User's desired quality
     * @return DownloadBundle ready for DownloadManagerService, or null if no suitable stream
     */
    public static DownloadBundle prepareDownload(Context context, StreamInfo info, String targetQuality) {
        DownloadBundle bundle = new DownloadBundle();
        
        if (targetQuality.equals(QUAL_BEST_AUDIO)) {
            Log.d("PlaylistDownloadLogic", "Preparing AUDIO download");
            // Audio-only download
            // Filter to only PROGRESSIVE_HTTP streams (like DownloadDialog does)
            List<AudioStream> allAudioStreams = info.getAudioStreams();
            Log.d("PlaylistDownloadLogic", "Total audio streams: " + allAudioStreams.size());
            
            List<AudioStream> audioStreams = filterProgressiveHttpStreams(allAudioStreams);
            Log.d("PlaylistDownloadLogic", "PROGRESSIVE_HTTP audio streams: " + audioStreams.size());
            
            if (audioStreams.isEmpty()) {
                Log.e("PlaylistDownloadLogic", "No PROGRESSIVE_HTTP audio streams available!");
                return null;
            }
            
            AudioStream audioStream = getBestAudio(audioStreams);
            Log.d("PlaylistDownloadLogic", "Selected audio stream:");
            Log.d("PlaylistDownloadLogic", "  Format: " + audioStream.getFormat());
            Log.d("PlaylistDownloadLogic", "  Bitrate: " + audioStream.getAverageBitrate());
            Log.d("PlaylistDownloadLogic", "  URL: " + audioStream.getContent());
            Log.d("PlaylistDownloadLogic", "  DeliveryMethod: " + audioStream.getDeliveryMethod());
            bundle.kind = 'a';
            bundle.urls = new String[]{audioStream.getContent()};
            bundle.recovery = List.of(new MissionRecoveryInfo(audioStream));
            bundle.nearLength = 0;
            
            // Post-processing for audio formats
            if (audioStream.getFormat() == MediaFormat.M4A) {
                bundle.psName = Postprocessing.ALGORITHM_M4A_NO_DASH;
            } else if (audioStream.getFormat() == MediaFormat.WEBMA_OPUS) {
                bundle.psName = Postprocessing.ALGORITHM_OGG_FROM_WEBM_DEMUXER;
            }
            
            bundle.filename = createSafeFilename(context, info.getName(), audioStream.getFormat().getSuffix());
            bundle.mimeType = audioStream.getFormat().mimeType;
            return bundle;
        }

        // Video download
        // Filter to only PROGRESSIVE_HTTP streams (like DownloadDialog does)
        List<VideoStream> allVideoStreams = info.getVideoStreams();
        List<VideoStream> videoStreams = filterProgressiveHttpStreams(allVideoStreams);
        if (videoStreams.isEmpty()) return null;

        VideoStream videoStream = matchVideoStream(videoStreams, targetQuality);
        if (videoStream == null) return null;

        bundle.kind = 'v';
        
        // Check if video-only stream requires audio muxing
        if (videoStream.isVideoOnly()) {
            // Filter audio streams for muxing
            List<AudioStream> allAudioStreams = info.getAudioStreams();
            List<AudioStream> audioStreams = filterProgressiveHttpStreams(allAudioStreams);
            AudioStream audioStream = SecondaryStreamHelper.getAudioStreamFor(context, audioStreams, videoStream);
            
            if (audioStream != null) {
                // Video-only + Audio muxing
                bundle.urls = new String[]{videoStream.getContent(), audioStream.getContent()};
                bundle.recovery = List.of(
                    new MissionRecoveryInfo(videoStream),
                    new MissionRecoveryInfo(audioStream)
                );
                
                // Select appropriate muxer
                if (videoStream.getFormat() == MediaFormat.MPEG_4) {
                    bundle.psName = Postprocessing.ALGORITHM_MP4_FROM_DASH_MUXER;
                } else {
                    bundle.psName = Postprocessing.ALGORITHM_WEBM_MUXER;
                }
                
                // Calculate approximate size
                long videoSize = videoStream.getContent() != null ? 0 : 0; // Size calculated by downloader
                long audioSize = audioStream.getContent() != null ? 0 : 0;
                bundle.nearLength = 0; // Let downloader calculate
            } else {
                // Video-only but no audio available - still download video
                bundle.urls = new String[]{videoStream.getContent()};
                bundle.recovery = List.of(new MissionRecoveryInfo(videoStream));
                bundle.nearLength = 0;
            }
        } else {
            // Regular video with embedded audio
            bundle.urls = new String[]{videoStream.getContent()};
            bundle.recovery = List.of(new MissionRecoveryInfo(videoStream));
            bundle.nearLength = 0;
        }
        
        bundle.filename = createSafeFilename(context, info.getName(), videoStream.getFormat().getSuffix());
        bundle.mimeType = videoStream.getFormat().mimeType;
        return bundle;
    }
    
    /**
     * Create a safe filename with length limits to prevent filesystem errors.
     * Max filename is typically 255 bytes, but UTF-8 characters can be multiple bytes.
     * We truncate to 100 characters to be safe.
     */
    private static String createSafeFilename(Context context, String title, String extension) {
        String baseName = FilenameUtils.createFilename(context, title);
        
        // Limit to 100 characters (safe for most filesystems even with UTF-8)
        final int MAX_LENGTH = 100;
        if (baseName.length() > MAX_LENGTH) {
            baseName = baseName.substring(0, MAX_LENGTH);
            Log.d("PlaylistDownloadLogic", "Truncated long filename: " + baseName);
        }
        
        return baseName + "." + extension;
    }

    /**
     * Filter streams to only include PROGRESSIVE_HTTP delivery method.
     * This matches the behavior of DownloadDialog which filters using ListHelper.getStreamsOfSpecifiedDelivery()
     */
    private static <T extends Stream> List<T> filterProgressiveHttpStreams(List<T> streams) {
        List<T> filtered = new java.util.ArrayList<>();
        for (T stream : streams) {
            if (stream.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP) {
                filtered.add(stream);
            }
        }
        return filtered;
    }
    
    private static VideoStream matchVideoStream(List<VideoStream> videoStreams, String targetQuality) {
        if (targetQuality.equals(QUAL_BEST_VIDEO)) {
            return getBestVideo(videoStreams);
        }

        // Target Resolution Matching
        String targetRes = targetQuality.replace("p", "");
        
        // Try exact match first
        for (VideoStream stream : videoStreams) {
            if (stream.getResolution().startsWith(targetRes)) {
                return stream;
            }
        }
        
        // Fallback: Return best video if specific resolution not found
        return getBestVideo(videoStreams);
    }
    
    /**
     * Select audio stream closest to 48kbps target bitrate.
     * This is a low-quality option suitable for large playlist downloads.
     */
    private static AudioStream getBestAudio(List<AudioStream> streams) {
        if (streams == null || streams.isEmpty()) return null;
        
        final int TARGET_BITRATE = 48000; // 48 kbps
        AudioStream best = streams.get(0);
        int bestDiff = Math.abs(best.getAverageBitrate() - TARGET_BITRATE);
        
        for (AudioStream s : streams) {
            int currentDiff = Math.abs(s.getAverageBitrate() - TARGET_BITRATE);
            if (currentDiff < bestDiff) {
                best = s;
                bestDiff = currentDiff;
            }
        }
        return best;
    }

    private static VideoStream getBestVideo(List<VideoStream> streams) {
        if (streams == null || streams.isEmpty()) return null;
        VideoStream best = streams.get(0);
        for (VideoStream s : streams) {
            int resBest = parseHeight(best.getResolution());
            int resCurr = parseHeight(s.getResolution());
            if (resCurr > resBest) {
                best = s;
            }
        }
        return best;
    }
    
    private static int parseHeight(String res) {
        try {
            // Extract only digits before 'p'
            String digits = res.replaceAll("p.*", "").replaceAll("[^0-9]", "");
            return Integer.parseInt(digits);
        } catch (Exception e) {
            return 0;
        }
    }
    
    public static char getDownloadKind(String targetQuality) {
        if (QUAL_BEST_AUDIO.equals(targetQuality)) {
            return 'a';
        }
        return 'v';
    }
}
