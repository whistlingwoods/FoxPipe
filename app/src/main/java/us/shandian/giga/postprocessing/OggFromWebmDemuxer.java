package us.shandian.giga.postprocessing;

import androidx.annotation.NonNull;

import org.schabi.newpipe.streams.OggFromWebMWriter;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.File; // تم إضافة هذا الاستيراد
import java.io.IOException;
import java.nio.ByteBuffer;

class OggFromWebmDemuxer extends Postprocessing {

    OggFromWebmDemuxer() {
        super(true, true, ALGORITHM_OGG_FROM_WEBM_DEMUXER);
    }

    @Override
    boolean test(SharpStream... sources) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        sources[0].read(buffer.array());

        // youtube uses WebM as container, but the file extension (format suffix) is "*.opus"
        // check if the file is a webm/mkv file before proceed

        switch (buffer.getInt()) {
            case 0x1a45dfa3:
                return true;// webm/mkv
            case 0x4F676753:
                return false;// ogg
        }

        throw new UnsupportedOperationException("file not recognized, failed to demux the audio stream");
    }

    @Override
    int process(SharpStream out, @NonNull SharpStream... sources) throws IOException {
        // 1. تحميل صورة الغلاف (من الدالة التي أضفناها في Postprocessing.java)
        File coverArtFile = downloadCoverArt();

        OggFromWebMWriter demuxer = new OggFromWebMWriter(sources[0], out, streamInfo);
        
        // 2. تمرير الصورة إلى الكاتب (Writer) إذا تم تحميلها
        if (coverArtFile != null && coverArtFile.exists()) {
            // ملاحظة هامة: يجب أن يحتوي OggFromWebMWriter على دالة setCover
            // إذا كان الكلاس لا يحتوي عليها، ستحتاج لإضافتها أو سيتم تجاهل الصورة هنا
            // demuxer.setCover(coverArtFile); 
            
            // بما أنني لا أملك كود OggFromWebMWriter الخاص بك، 
            // سأترك هذا السطر كتعليق لتفعيله إذا عدلت الـ Writer.
        }

        demuxer.parseSource();
        demuxer.selectTrack(0);
        demuxer.build();

        return OK_RESULT;
    }
}