package org.schabi.newpipe.download;

import android.content.Context;
import android.util.Log;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.util.FilenameUtils;
import org.schabi.newpipe.util.SecondaryStreamHelper;

import java.util.ArrayList;
import java.util.List;

import us.shandian.giga.get.MissionRecoveryInfo;
import us.shandian.giga.postprocessing.Postprocessing;

/**
 * Logic for handling playlist downloads and
 * determining the best streams based on quality preferences.
 */
public final class PlaylistDownloadLogic {

    private static final String TAG = "PlaylistDownloadLogic";
    private static final int MAX_FILENAME_LENGTH = 100;
    private static final int TARGET_BITRATE_MEDIUM = 128;
    private static final int KILO_MULTIPLIER = 1000;

    // --- Video Constants ---
    public static final String QUAL_BEST_VIDEO = "Best Video";
    public static final String QUAL_1080P = "1080p";
    public static final String QUAL_720P = "720p";
    public static final String QUAL_480P = "480p";
    public static final String QUAL_360P = "360p";
    public static final String QUAL_240P = "240p";
    public static final String QUAL_144P = "144p";

    // --- Audio Constants ---
    // Replaced "Best Audio" with these three options for greater flexibility
    /** High quality (often Opus). */
    public static final String QUAL_AUDIO_HIGH = "Audio High (160kbps)";
    /** Medium quality (often M4A). */
    public static final String QUAL_AUDIO_MEDIUM = "Audio Medium (128kbps)";
    /** Data saving (Opus/M4A). */
    public static final String QUAL_AUDIO_LOW = "Audio Low (48kbps)";
    /** Kept for compatibility, treated as Audio High. */
    public static final String QUAL_BEST_AUDIO = "Best Audio";

    /**
     * Data holder for download information.
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
        /** Variable to store thumbnail URL. */
        public String thumbnailUrl;
    }

    private PlaylistDownloadLogic() {
        // Prevent instantiation
    }

    /**
     * Prepares a download bundle based on the selected quality.
     *
     * @param context       Android context.
     * @param info          Stream information.
     * @param targetQuality The target quality string.
     * @return A DownloadBundle containing download details, or null if no suitable stream is found.
     */
    public static DownloadBundle prepareDownload(final Context context,
                                                 final StreamInfo info,
                                                 final String targetQuality) {
        final DownloadBundle bundle = new DownloadBundle();

        if (info.getThumbnails() != null && !info.getThumbnails().isEmpty()) {
            bundle.thumbnailUrl = info.getThumbnails().get(0).getUrl();
        } else {
            bundle.thumbnailUrl = null;
        }

        // ---------------------------------------------------------
        // 1. Handle audio requests
        // ---------------------------------------------------------
        if (isAudioRequest(targetQuality)) {
            Log.d(TAG, "Preparing AUDIO download: " + targetQuality);

            final List<AudioStream> allAudioStreams = info.getAudioStreams();
            // Do not filter anything (allow DASH), need all formats for best quality
            final List<AudioStream> audioStreams = filterAudioStreams(allAudioStreams);

            if (audioStreams.isEmpty()) {
                Log.e(TAG, "No audio streams found");
                return null;
            }

            // Select appropriate audio stream based on target quality (Bitrate)
            final AudioStream audioStream = matchAudioStream(audioStreams, targetQuality);

            if (audioStream == null) {
                return null;
            }

            bundle.kind = 'a';
            bundle.urls = new String[]{audioStream.getContent()};
            bundle.recovery = List.of(new MissionRecoveryInfo(audioStream));
            bundle.nearLength = 0;

            String extension = audioStream.getFormat().getSuffix();
            String mimeType = audioStream.getFormat().mimeType;

            // Post-processing settings and extension fix
            if (audioStream.getFormat() == MediaFormat.M4A) {
                bundle.psName = Postprocessing.ALGORITHM_M4A_NO_DASH;
            } else if (audioStream.getFormat() == MediaFormat.WEBMA_OPUS) {
                bundle.psName = Postprocessing.ALGORITHM_OGG_FROM_WEBM_DEMUXER;
                // Change extension and type because result will be OGG/Opus
                extension = "opus";
                mimeType = "audio/ogg";
            }

            bundle.filename = createSafeFilename(context, info.getName(), extension);
            bundle.mimeType = mimeType;
            return bundle;
        }

        // ---------------------------------------------------------
        // 2. Handle video requests
        // ---------------------------------------------------------

        // Merge lists (video with audio + video only) to find 144p and 1080p qualities
        final List<VideoStream> searchPool = new ArrayList<>();

        // Add ready video (often 360p and 720p)
        if (info.getVideoStreams() != null) {
            searchPool.addAll(info.getVideoStreams());
        }

        // Add raw video (144p, 240p, 1080p, 2k, 4k...)
        if (info.getVideoOnlyStreams() != null) {
            searchPool.addAll(info.getVideoOnlyStreams());
        }

        if (searchPool.isEmpty()) {
            return null;
        }

        // Search within merged list
        VideoStream videoStream = matchVideoStream(searchPool, targetQuality);

        // If target video not found (e.g. 144p), try to find suitable alternative
        if (videoStream == null) {
            if (targetQuality.equals(QUAL_144P) || targetQuality.equals(QUAL_240P)) {
                // If goal is data saving, find lowest available quality instead of giving up
                videoStream = getLowestVideo(searchPool);
            } else {
                // For high qualities, leave empty if not available
                return null;
            }
        }

        bundle.kind = 'v';

        // Handle video (whether it needs audio muxing or not)
        if (videoStream.isVideoOnly()) {
            final List<AudioStream> allAudioStreams = info.getAudioStreams();
            // Need any available audio to mux with video
            final AudioStream audioStream = SecondaryStreamHelper.getAudioStreamFor(
                    context, allAudioStreams, videoStream);

            if (audioStream != null) {
                bundle.urls = new String[]{videoStream.getContent(), audioStream.getContent()};
                bundle.recovery = List.of(
                        new MissionRecoveryInfo(videoStream),
                        new MissionRecoveryInfo(audioStream)
                );

                if (videoStream.getFormat() == MediaFormat.MPEG_4) {
                    bundle.psName = Postprocessing.ALGORITHM_MP4_FROM_DASH_MUXER;
                } else {
                    bundle.psName = Postprocessing.ALGORITHM_WEBM_MUXER;
                }

                bundle.nearLength = 0;
            } else {
                // Video without audio (rare case)
                bundle.urls = new String[]{videoStream.getContent()};
                bundle.recovery = List.of(new MissionRecoveryInfo(videoStream));
                bundle.nearLength = 0;
            }
        } else {
            // Ready video (progressive)
            bundle.urls = new String[]{videoStream.getContent()};
            bundle.recovery = List.of(new MissionRecoveryInfo(videoStream));
            bundle.nearLength = 0;
        }

        bundle.filename = createSafeFilename(context, info.getName(),
                videoStream.getFormat().getSuffix());
        bundle.mimeType = videoStream.getFormat().mimeType;
        return bundle;
    }

    private static boolean isAudioRequest(final String quality) {
        return quality.equals(QUAL_AUDIO_HIGH)
                || quality.equals(QUAL_AUDIO_MEDIUM)
                || quality.equals(QUAL_AUDIO_LOW)
                || quality.equals(QUAL_BEST_AUDIO);
    }

    private static AudioStream matchAudioStream(final List<AudioStream> streams,
                                                final String targetQuality) {
        if (streams == null || streams.isEmpty()) {
            return null;
        }

        // Sort by bitrate ascending
        streams.sort((a, b) -> Integer.compare(a.getAverageBitrate(), b.getAverageBitrate()));

        switch (targetQuality) {
            case QUAL_AUDIO_LOW:
                // Want lowest (first item)
                return streams.get(0);

            case QUAL_AUDIO_HIGH:
            case QUAL_BEST_AUDIO:
                // Want highest (last item)
                return streams.get(streams.size() - 1);

            case QUAL_AUDIO_MEDIUM:
            default:
                // Want something close to 128kbps
                return getClosestBitrate(streams, TARGET_BITRATE_MEDIUM);
        }
    }

    // Method to find closest bitrate
    private static AudioStream getClosestBitrate(final List<AudioStream> streams,
                                                 final int targetKbps) {
        AudioStream closest = null;
        int minDiff = Integer.MAX_VALUE;
        final int targetBitrate = targetKbps * KILO_MULTIPLIER;

        for (final AudioStream stream : streams) {
            final int diff = Math.abs(stream.getAverageBitrate() - targetBitrate);
            if (diff < minDiff) {
                minDiff = diff;
                closest = stream;
            }
        }
        return closest != null ? closest : streams.get(streams.size() / 2);
    }

    private static List<AudioStream> filterAudioStreams(final List<AudioStream> streams) {
        // Return list as is to allow DASH streams (better quality for Opus)
        return streams != null ? streams : new ArrayList<>();
    }

    private static String createSafeFilename(final Context context,
                                             final String title,
                                             final String extension) {
        String baseName = FilenameUtils.createFilename(context, title);
        if (baseName.length() > MAX_FILENAME_LENGTH) {
            baseName = baseName.substring(0, MAX_FILENAME_LENGTH);
        }
        return baseName + "." + extension;
    }

    private static VideoStream matchVideoStream(final List<VideoStream> videoStreams,
                                                final String targetQuality) {
        if (targetQuality.equals(QUAL_BEST_VIDEO)) {
            return getBestVideo(videoStreams);
        }

        // Extract number only
        final String targetRes = targetQuality.replace("p", "");

        // Try to find exact match
        for (final VideoStream stream : videoStreams) {
            if (stream != null && stream.getResolution() != null) {
                if (stream.getResolution().startsWith(targetRes)) {
                    return stream;
                }
            }
        }

        // If low quality requested and not found, give lowest available
        if (targetQuality.equals(QUAL_144P) || targetQuality.equals(QUAL_240P)) {
            return getLowestVideo(videoStreams);
        }

        // In other cases give the best
        return getBestVideo(videoStreams);
    }

    private static VideoStream getLowestVideo(final List<VideoStream> streams) {
        if (streams == null || streams.isEmpty()) {
            return null;
        }
        VideoStream lowest = streams.get(0);
        int resLowest = parseHeight(lowest.getResolution());

        for (final VideoStream s : streams) {
            final int resCurr = parseHeight(s.getResolution());
            if (resCurr > 0 && resCurr < resLowest) {
                lowest = s;
                resLowest = resCurr;
            } else if (resLowest == 0 && resCurr > 0) {
                lowest = s;
                resLowest = resCurr;
            }
        }
        return lowest;
    }

    private static VideoStream getBestVideo(final List<VideoStream> streams) {
        if (streams == null || streams.isEmpty()) {
            return null;
        }
        VideoStream best = streams.get(0);
        for (final VideoStream s : streams) {
            final int resBest = parseHeight(best.getResolution());
            final int resCurr = parseHeight(s.getResolution());
            if (resCurr > resBest) {
                best = s;
            }
        }
        return best;
    }

    private static int parseHeight(final String res) {
        try {
            final String digits = res.replaceAll("p.*", "").replaceAll("[^0-9]", "");
            return Integer.parseInt(digits);
        } catch (final Exception e) {
            return 0;
        }
    }

    /**
     * Determines the download kind (audio or video) based on the target quality.
     *
     * @param targetQuality The quality string.
     * @return 'a' for audio, 'v' for video.
     */
    public static char getDownloadKind(final String targetQuality) {
        if (isAudioRequest(targetQuality)) {
            return 'a';
        }
        return 'v';
    }
}
