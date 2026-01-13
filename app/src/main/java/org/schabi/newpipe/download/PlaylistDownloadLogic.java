package org.schabi.newpipe.download;

import android.content.Context;
import android.util.Log;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.util.FilenameUtils;
import org.schabi.newpipe.util.SecondaryStreamHelper;

import java.util.ArrayList;
import java.util.List;

import us.shandian.giga.get.MissionRecoveryInfo;
import us.shandian.giga.postprocessing.Postprocessing;

public class PlaylistDownloadLogic {

    // --- ثوابت الفيديو ---
    public static final String QUAL_BEST_VIDEO = "Best Video";
    public static final String QUAL_1080P = "1080p";
    public static final String QUAL_720P = "720p";
    public static final String QUAL_480P = "480p";
    public static final String QUAL_360P = "360p";
    public static final String QUAL_240P = "240p";
    public static final String QUAL_144P = "144p";

    // --- ثوابت الصوت ---
    // تم استبدال "Best Audio" بهذه الخيارات الثلاثة لمرونة أكبر
    public static final String QUAL_AUDIO_HIGH   = "Audio High (160kbps)";   // جودة عالية (Opus غالباً)
    public static final String QUAL_AUDIO_MEDIUM = "Audio Medium (128kbps)"; // جودة متوسطة (M4A غالباً)
    public static final String QUAL_AUDIO_LOW    = "Audio Low (48kbps)";     // توفير البيانات (Opus/M4A)
    // نبقي هذا الثابت للتوافق، وسيتم معاملته كـ Audio High
    public static final String QUAL_BEST_AUDIO = "Best Audio";

    public static class DownloadBundle {
        public String[] urls;
        public char kind;
        public String psName;
        public String[] psArgs;
        public long nearLength;
        public List<MissionRecoveryInfo> recovery;
        public String filename;
        public String mimeType;
        public String thumbnailUrl; // متغير لحفظ رابط الصورة
    }

    public static DownloadBundle prepareDownload(Context context, StreamInfo info, String targetQuality) {
        DownloadBundle bundle = new DownloadBundle();

         if (info.getThumbnails() != null && !info.getThumbnails().isEmpty()) {
            bundle.thumbnailUrl = info.getThumbnails().get(0).getUrl();
        } else {
            bundle.thumbnailUrl = null;
        }
        // ---------------------------------------------------------
        // 1. معالجة طلبات الصوت
        // ---------------------------------------------------------
        if (isAudioRequest(targetQuality)) {
            Log.d("PlaylistDownloadLogic", "Preparing AUDIO download: " + targetQuality);

            List<AudioStream> allAudioStreams = info.getAudioStreams();
            // لا نفلتر شيئاً (نسمح بـ DASH)، نحتاج كل الصيغ للحصول على أفضل جودة
            List<AudioStream> audioStreams = filterAudioStreams(allAudioStreams);

            if (audioStreams.isEmpty()) {
                Log.e("PlaylistDownloadLogic", "No audio streams found");
                return null;
            }

            // اختيار مسار الصوت المناسب بناءً على الجودة المطلوبة (Bitrate)
            AudioStream audioStream = matchAudioStream(audioStreams, targetQuality);

            if (audioStream == null) return null;

            bundle.kind = 'a';
            bundle.urls = new String[]{audioStream.getContent()};
            bundle.recovery = List.of(new MissionRecoveryInfo(audioStream));
            bundle.nearLength = 0;

            String extension = audioStream.getFormat().getSuffix();
            String mimeType = audioStream.getFormat().mimeType;

            // إعدادات المعالجة اللاحقة (Post-processing) وإصلاح الامتداد
            if (audioStream.getFormat() == MediaFormat.M4A) {
                bundle.psName = Postprocessing.ALGORITHM_M4A_NO_DASH;
            } else if (audioStream.getFormat() == MediaFormat.WEBMA_OPUS) {
                bundle.psName = Postprocessing.ALGORITHM_OGG_FROM_WEBM_DEMUXER;
                // تغيير الامتداد والنوع لأن النتيجة ستكون OGG/Opus
                extension = "opus";
                mimeType = "audio/ogg";
            }

            bundle.filename = createSafeFilename(context, info.getName(), extension);
            bundle.mimeType = mimeType;
            return bundle;
        }

        // ---------------------------------------------------------
        // 2. معالجة طلبات الفيديو
        // ---------------------------------------------------------
        
        // دمج القائمتين (فيديو بصوت + فيديو بدون صوت) للعثور على جودات 144p و 1080p
        List<VideoStream> searchPool = new ArrayList<>();

        // نضيف الفيديو الجاهز (غالباً 360p و 720p)
        if (info.getVideoStreams() != null) {
            searchPool.addAll(info.getVideoStreams());
        }

        // نضيف الفيديو الخام (144p, 240p, 1080p, 2k, 4k...)
        if (info.getVideoOnlyStreams() != null) {
            searchPool.addAll(info.getVideoOnlyStreams());
        }

        if (searchPool.isEmpty()) return null;

        // البحث داخل القائمة المدمجة
        VideoStream videoStream = matchVideoStream(searchPool, targetQuality);

        // إذا لم نجد الفيديو المطلوب (مثلاً طلبنا 144p ولم نجده)، نحاول إيجاد بديل مناسب
        if (videoStream == null) {
            if (targetQuality.equals(QUAL_144P) || targetQuality.equals(QUAL_240P)) {
                // إذا كان الهدف توفير البيانات، ابحث عن أقل جودة متوفرة بدلاً من الاستسلام
                videoStream = getLowestVideo(searchPool);
            } else {
                return null; // للجودات العالية، نترك الأمر فارغاً إذا لم يتوفر
            }
        }

        bundle.kind = 'v';

        // التعامل مع الفيديو (سواء كان يحتاج دمج صوت أو لا)
        if (videoStream.isVideoOnly()) {
            List<AudioStream> allAudioStreams = info.getAudioStreams();
            // نحتاج أي صوت متاح للدمج مع الفيديو
            AudioStream audioStream = SecondaryStreamHelper.getAudioStreamFor(context, allAudioStreams, videoStream);

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
                // فيديو بدون صوت (حالة نادرة)
                bundle.urls = new String[]{videoStream.getContent()};
                bundle.recovery = List.of(new MissionRecoveryInfo(videoStream));
                bundle.nearLength = 0;
            }
        } else {
            // فيديو جاهز (progressive)
            bundle.urls = new String[]{videoStream.getContent()};
            bundle.recovery = List.of(new MissionRecoveryInfo(videoStream));
            bundle.nearLength = 0;
        }

        bundle.filename = createSafeFilename(context, info.getName(), videoStream.getFormat().getSuffix());
        bundle.mimeType = videoStream.getFormat().mimeType;
        return bundle;
    }

    // --- دوال مساعدة للصوت (من الكود الأول) ---

    private static boolean isAudioRequest(String quality) {
        return quality.equals(QUAL_AUDIO_HIGH) ||
               quality.equals(QUAL_AUDIO_MEDIUM) ||
               quality.equals(QUAL_AUDIO_LOW) ||
               quality.equals(QUAL_BEST_AUDIO);
    }

    private static AudioStream matchAudioStream(List<AudioStream> streams, String targetQuality) {
        if (streams == null || streams.isEmpty()) return null;

        // الترتيب حسب معدل البت (Bitrate) من الأصغر للأكبر
        streams.sort((a, b) -> Integer.compare(a.getAverageBitrate(), b.getAverageBitrate()));

        switch (targetQuality) {
            case QUAL_AUDIO_LOW:
                // نريد أقل شيء (أول عنصر)
                return streams.get(0);

            case QUAL_AUDIO_HIGH:
            case QUAL_BEST_AUDIO:
                // نريد أعلى شيء (آخر عنصر)
                return streams.get(streams.size() - 1);

            case QUAL_AUDIO_MEDIUM:
            default:
                // نريد شيئاً قريباً من 128kbps
                return getClosestBitrate(streams, 128);
        }
    }

    // دالة للبحث عن أقرب Bitrate لرقم معين
    private static AudioStream getClosestBitrate(List<AudioStream> streams, int targetKbps) {
        AudioStream closest = null;
        int minDiff = Integer.MAX_VALUE;
        int targetBitrate = targetKbps * 1000;

        for (AudioStream stream : streams) {
            int diff = Math.abs(stream.getAverageBitrate() - targetBitrate);
            if (diff < minDiff) {
                minDiff = diff;
                closest = stream;
            }
        }
        return closest != null ? closest : streams.get(streams.size() / 2);
    }

    private static List<AudioStream> filterAudioStreams(List<AudioStream> streams) {
        // إرجاع القائمة كما هي للسماح بـ DASH streams (جودة أفضل لـ Opus)
        return streams != null ? streams : new ArrayList<>();
    }

    // --- دوال مساعدة للفيديو والملفات (من الكود الثاني) ---

    private static String createSafeFilename(Context context, String title, String extension) {
        String baseName = FilenameUtils.createFilename(context, title);
        final int MAX_LENGTH = 100;
        if (baseName.length() > MAX_LENGTH) {
            baseName = baseName.substring(0, MAX_LENGTH);
        }
        return baseName + "." + extension;
    }

    private static VideoStream matchVideoStream(List<VideoStream> videoStreams, String targetQuality) {
        if (targetQuality.equals(QUAL_BEST_VIDEO)) {
            return getBestVideo(videoStreams);
        }

        // استخراج الرقم فقط
        String targetRes = targetQuality.replace("p", "");

        // محاولة إيجاد تطابق دقيق
        for (VideoStream stream : videoStreams) {
            if (stream != null && stream.getResolution() != null) {
                if (stream.getResolution().startsWith(targetRes)) {
                    return stream;
                }
            }
        }

        // إذا طلب جودة منخفضة ولم يجدها، نعطيه أقل جودة متاحة
        if (targetQuality.equals(QUAL_144P) || targetQuality.equals(QUAL_240P)) {
            return getLowestVideo(videoStreams);
        }

        // في الحالات الأخرى نعطيه الأفضل
        return getBestVideo(videoStreams);
    }

    private static VideoStream getLowestVideo(List<VideoStream> streams) {
        if (streams == null || streams.isEmpty()) return null;
        VideoStream lowest = streams.get(0);
        int resLowest = parseHeight(lowest.getResolution());

        for (VideoStream s : streams) {
            int resCurr = parseHeight(s.getResolution());
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
            String digits = res.replaceAll("p.*", "").replaceAll("[^0-9]", "");
            return Integer.parseInt(digits);
        } catch (Exception e) {
            return 0;
        }
    }

    // دالة تحديد النوع (معدلة لتدعم كل أنواع الصوت)
    public static char getDownloadKind(String targetQuality) {
        if (isAudioRequest(targetQuality)) {
            return 'a';
        }
        return 'v';
    }
}