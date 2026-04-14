package us.shandian.giga.postprocessing;

import org.schabi.newpipe.streams.Mp4DashReader;
import org.schabi.newpipe.streams.Mp4FromDashWriter;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.File;
import java.io.IOException;

class M4aNoDash extends Postprocessing {

    M4aNoDash() {
        super(false, true, ALGORITHM_M4A_NO_DASH);
    }

    @Override
    boolean test(SharpStream... sources) throws IOException {
        // check if the mp4 file is DASH (youtube)

        Mp4DashReader reader = new Mp4DashReader(sources[0]);
        reader.parse();

        switch (reader.getBrands()[0]) {
            case 0x64617368:// DASH
            case 0x69736F35:// ISO5
                return true;
            default:
                return false;
        }
    }

    @Override
    int process(SharpStream out, SharpStream... sources) throws IOException {
        // 1. download cover art
        File cover = downloadCoverArt();

        Mp4FromDashWriter muxer = new Mp4FromDashWriter(sources[0]);
        
        // 2. pass cover art to muxer (make sure Mp4FromDashWriter has a setCover method)
        if (cover != null && cover.exists()) {
             // if this fails, you need to add a setCover(File) method to Mp4FromDashWriter
             muxer.setCover(cover);
        }

        muxer.setMainBrand(0x4D344120);// binary string "M4A "
        muxer.parseSources();
        muxer.selectTracks(0);
        muxer.build(out);

        return OK_RESULT;
    }
}