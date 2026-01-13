package us.shandian.giga.postprocessing;

import androidx.annotation.NonNull;

import org.schabi.newpipe.streams.OggFromWebMWriter;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.File;
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

        // youtube uses WebM as container, but the file extension is "*.opus"
        switch (buffer.getInt()) {
            case 0x1a45dfa3:
                return true; // webm/mkv
            case 0x4F676753:
                return false; // ogg
        }

        throw new UnsupportedOperationException("file not recognized");
    }

    @Override
    int process(SharpStream out, @NonNull SharpStream... sources) throws IOException {
        // 1. تحميل الصورة (نفس الدالة الموجودة في Postprocessing)
        File cover = downloadCoverArt();

        OggFromWebMWriter demuxer = new OggFromWebMWriter(sources[0], out, streamInfo);
        
        // 2. تمرير الصورة (تأكد من تطبيق الخطوة التالية في الملف الآخر)
        if (cover != null) {
            demuxer.setCover(cover);
        }

        demuxer.parseSource();
        demuxer.selectTrack(0);
        demuxer.build();

        return OK_RESULT;
    }
}