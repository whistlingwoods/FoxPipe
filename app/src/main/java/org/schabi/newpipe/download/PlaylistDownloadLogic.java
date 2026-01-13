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

    public static final String QUAL_BEST_VIDEO = "Best Video";
    public static final String QUAL_BEST_AUDIO = "Best Audio";
    public static final String QUAL_1080P = "1080p";
    public static final String QUAL_720P = "720p";
    public static final String QUAL_480P = "480p";
    public static final String QUAL_360P = "360p";
    public static final String QUAL_240P = "240p";
    public static final String QUAL_144P = "144p";

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

    public static DownloadBundle prepareDownload(Context context, StreamInfo info, String targetQuality) {
        DownloadBundle bundle = new DownloadBundle();
        
        if (targetQuality.equals(QUAL_BEST_AUDIO)) {
            Log.d("PlaylistDownloadLogic", "Preparing AUDIO download");

            List<AudioStream> allAudioStreams = info.getAudioStreams();
            // استخدام دالة الفلترة التي تسمح بـ DASH للحصول على أفضل جودة
            List<AudioStream> audioStreams = filterAudioStreams(allAudioStreams);
            
            if (audioStreams.isEmpty()) {
                Log.e("PlaylistDownloadLogic", "No audio streams found");
                return null;
            }
                    
            AudioStream audioStream = getBestAudio(audioStreams);
            
            bundle.kind = 'a';
            bundle.urls = new String[]{audioStream.getContent()};
            bundle.recovery = List.of(new MissionRecoveryInfo(audioStream));
            bundle.nearLength = 0;
            
            // تحديد الامتداد ونوع الملف الافتراضي
            String extension = audioStream.getFormat().getSuffix();
            String mimeType = audioStream.getFormat().mimeType;

            // --- إصلاح المشكلة هنا ---
            // Post-processing setup & File Extension Fix
            if (audioStream.getFormat() == MediaFormat.M4A) {
                bundle.psName = Postprocessing.ALGORITHM_M4A_NO_DASH;
                // M4A يبقى كما هو
            } else if (audioStream.getFormat() == MediaFormat.WEBMA_OPUS) {
                bundle.psName = Postprocessing.ALGORITHM_OGG_FROM_WEBM_DEMUXER;
                
                // [هام جداً]
                // المصدر هو WebM لكن النتيجة بعد المعالجة ستكون Opus/Ogg
                // يجب تغيير الامتداد والنوع يدوياً وإلا سيفشل الملف بعد المعالجة
                extension = "opus";
                mimeType = "audio/ogg";
            }
            // ------------------------
            
            bundle.filename = createSafeFilename(context, info.getName(), extension);
            bundle.mimeType = mimeType;
            return bundle;
        }

          // التعديل 1: دمج القائمتين (فيديو بصوت + فيديو بدون صوت)
        // لكي نعثر على جودات 144p و 240p و 1080p
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

        // التعديل 2: البحث داخل القائمة المدمجة
        VideoStream videoStream = matchVideoStream(searchPool, targetQuality);
        
        // إذا لم نجد الفيديو المطلوب، لا نريد تحميل 8K ونحن نطلب 144p
        // سنضيف منطقاً بسيطاً: إذا فشل البحث، حاول إيجاد "أصغر" فيديو بدلاً من "أفضل" فيديو
        // إذا كان المستخدم يطلب توفير البيانات
        if (videoStream == null) {
             if (targetQuality.equals(QUAL_144P) || targetQuality.equals(QUAL_240P)) {
                 // ابحث عن أقل جودة متوفرة
                 videoStream = getLowestVideo(searchPool); 
             } else {
                 return null; // أو دع matchVideoStream يتصرف
             }
        }
        
        // (ملاحظة: matchVideoStream المعدل في الأسفل سيتعامل مع الـ null)

        bundle.kind = 'v';
        
        // باقي الكود كما هو تماماً، سيتعامل بذكاء مع الدمج
        if (videoStream.isVideoOnly()) {
            List<AudioStream> allAudioStreams = info.getAudioStreams();
            // لا نفلتر الصوت هنا أيضاً، نحتاج أي صوت متاح للدمج
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
                // فيديو بدون صوت (حالة نادرة جداً)
                bundle.urls = new String[]{videoStream.getContent()};
                bundle.recovery = List.of(new MissionRecoveryInfo(videoStream));
                bundle.nearLength = 0;
            }
        } else {
            // فيديو جاهز (360p / 720p)
            bundle.urls = new String[]{videoStream.getContent()};
            bundle.recovery = List.of(new MissionRecoveryInfo(videoStream));
            bundle.nearLength = 0;
        }
        
        bundle.filename = createSafeFilename(context, info.getName(), videoStream.getFormat().getSuffix());
        bundle.mimeType = videoStream.getFormat().mimeType;
        return bundle;
    }
    
    private static String createSafeFilename(Context context, String title, String extension) {
        String baseName = FilenameUtils.createFilename(context, title);
        final int MAX_LENGTH = 100;
        if (baseName.length() > MAX_LENGTH) {
            baseName = baseName.substring(0, MAX_LENGTH);
        }
        return baseName + "." + extension;
    }

    private static <T extends Stream> List<T> filterProgressiveHttpStreams(List<T> streams) {
        List<T> filtered = new ArrayList<>();
        if (streams == null) return filtered;
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

        // استخراج الرقم فقط (مثلاً 144)
        String targetRes = targetQuality.replace("p", "");
        
        // محاولة إيجاد تطابق دقيق (يبدأ بـ 144)
        for (VideoStream stream : videoStreams) {
            // التحقق من أن stream ليس null وتجنب الأخطاء
            if (stream != null && stream.getResolution() != null) {
                if (stream.getResolution().startsWith(targetRes)) {
                    return stream;
                }
            }
        }
        
        // إذا طلب المستخدم جودة منخفضة ولم يجدها، نعطيه أقل جودة متاحة بدلاً من "أفضل جودة"
        if (targetQuality.equals(QUAL_144P) || targetQuality.equals(QUAL_240P)) {
            return getLowestVideo(videoStreams);
        }
        
        // في الحالات الأخرى (طلب 720 ولم يجد، نعطيه الأفضل)
        return getBestVideo(videoStreams);
    }

    // دالة جديدة لجلب أقل جودة (لتوفير البيانات)
    private static VideoStream getLowestVideo(List<VideoStream> streams) {
        if (streams == null || streams.isEmpty()) return null;
        VideoStream lowest = streams.get(0);
        int resLowest = parseHeight(lowest.getResolution());
        
        for (VideoStream s : streams) {
            int resCurr = parseHeight(s.getResolution());
            // نبحث عن الأصغر (بشرط أن يكون أكبر من 0)
            if (resCurr > 0 && resCurr < resLowest) {
                lowest = s;
                resLowest = resCurr;
            } else if (resLowest == 0 && resCurr > 0) {
                // تصحيح إذا كان الأول غير صالح
                lowest = s;
                resLowest = resCurr;
            }
        }
        return lowest;
    }
    
    // السماح بكل أنواع التدفقات للصوت لضمان جودة Opus العالية
    private static List<AudioStream> filterAudioStreams(List<AudioStream> streams) {
        return streams != null ? streams : new ArrayList<>();
    }

    // جلب أعلى Bitrate متاح
    private static AudioStream getBestAudio(List<AudioStream> streams) {
        if (streams == null || streams.isEmpty()) return null;
        
        AudioStream best = streams.get(0);
        for (AudioStream s : streams) {
            if (s.getAverageBitrate() > best.getAverageBitrate()) {
                best = s;
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