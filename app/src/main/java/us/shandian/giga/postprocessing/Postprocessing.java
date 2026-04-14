package us.shandian.giga.postprocessing;

import android.util.Log;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.io.ChunkFileInputStream;
import us.shandian.giga.io.CircularFileWriter;
import us.shandian.giga.io.CircularFileWriter.OffsetChecker;
import us.shandian.giga.io.ProgressReport;

import static us.shandian.giga.get.DownloadMission.ERROR_NOTHING;
import static us.shandian.giga.get.DownloadMission.ERROR_POSTPROCESSING;
import static us.shandian.giga.get.DownloadMission.ERROR_POSTPROCESSING_HOLD;

public abstract class Postprocessing implements Serializable {

    static transient final byte OK_RESULT = ERROR_NOTHING;

    public transient static final String ALGORITHM_TTML_CONVERTER = "ttml";
    public transient static final String ALGORITHM_WEBM_MUXER = "webm";
    public transient static final String ALGORITHM_MP4_FROM_DASH_MUXER = "mp4D-mp4";
    public transient static final String ALGORITHM_M4A_NO_DASH = "mp4D-m4a";
    public transient static final String ALGORITHM_OGG_FROM_WEBM_DEMUXER = "webm-ogg-d";

    public static Postprocessing getAlgorithm(@NonNull String algorithmName, String[] args,
                                              StreamInfo streamInfo) {
        Postprocessing instance;

        switch (algorithmName) {
            case ALGORITHM_TTML_CONVERTER:
                instance = new TtmlConverter();
                break;
            case ALGORITHM_WEBM_MUXER:
                instance = new WebMMuxer();
                break;
            case ALGORITHM_MP4_FROM_DASH_MUXER:
                instance = new Mp4FromDashMuxer();
                break;
            case ALGORITHM_M4A_NO_DASH:
                instance = new M4aNoDash();
                break;
            case ALGORITHM_OGG_FROM_WEBM_DEMUXER:
                instance = new OggFromWebmDemuxer();
                break;
            /*case "example-algorithm":
                instance = new ExampleAlgorithm();*/
            default:
                throw new UnsupportedOperationException("Unimplemented post-processing algorithm: " + algorithmName);
        }

        instance.args = args;
        instance.streamInfo = streamInfo;
        return instance;
    }

    /**
     * Get a boolean value that indicate if the given algorithm work on the same
     * file
     */
    public boolean worksOnSameFile;

    /**
     * Indicates whether the selected algorithm needs space reserved at the beginning of the file
     */
    public boolean reserveSpace;

    /**
     * Gets the given algorithm short name
     */
    private final String name;

    private String[] args;
    protected StreamInfo streamInfo;

    private transient DownloadMission mission;

    private transient File tempFile;
    protected transient File tempCover; 

    Postprocessing(boolean reserveSpace, boolean worksOnSameFile, String algorithmName) {
        this.reserveSpace = reserveSpace;
        this.worksOnSameFile = worksOnSameFile;
        this.name = algorithmName;// for debugging only
    }

    public void setTemporalDir(@NonNull File directory) {
        long rnd = (int) (Math.random() * 100000.0f);
        tempFile = new File(directory, rnd + "_" + System.nanoTime() + ".tmp");
    }

    public void cleanupTemporalDir() {
        if (tempFile != null && tempFile.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            } catch (Exception e) {
                // nothing to do
            }
        }
        
     if (tempCover != null && tempCover.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                tempCover.delete();
            } catch (final Exception e) {
                // Ignore exceptions during cleanup
            }
            tempCover = null;
        }
    }

    /**
     * Downloads the cover art to a temporary file for later use (e.g., in FFmpeg).
     *
     * @return The File object of the downloaded cover, or null if failed.
     */
    protected File downloadCoverArt() {
        final int timeout = 10000;
        final int bufferSize = 1024;
        String thumbnailUrl = null;

        // Safely retrieve the URL from the list
        if (streamInfo != null
                && streamInfo.getThumbnails() != null
                && !streamInfo.getThumbnails().isEmpty()) {
            // Take the first image
            thumbnailUrl = streamInfo.getThumbnails().get(0).getUrl();
        }

        if (thumbnailUrl == null || thumbnailUrl.isEmpty()) {
            return null;
        }

        // If no temporary folder is defined, we cannot save
        if (tempFile == null || tempFile.getParentFile() == null) {
            return null;
        }

        try {
            final java.net.URL url = new java.net.URL(thumbnailUrl);
            // Create a file for the image in the same temporary folder
            tempCover = new java.io.File(tempFile.getParentFile(),
                    "cover_" + System.nanoTime() + ".jpg");

            final java.net.URLConnection connection =
                    org.schabi.newpipe.util.DnsHelper.openConnectionWithDoH(url);
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);

            try (java.io.InputStream input = new java.io.BufferedInputStream(
                    connection.getInputStream());
                 java.io.FileOutputStream output = new java.io.FileOutputStream(tempCover)) {

                final byte[] buffer = new byte[bufferSize];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
            }

            return tempCover;
        } catch (final Exception e) {
            android.util.Log.e(getClass().getSimpleName(), "Failed to download cover art", e);
            return null;
        }
    }

    public void run(DownloadMission target) throws IOException {
        this.mission = target;

        int result;
        long finalLength = -1;

        mission.done = 0;

        long length = mission.storage.length() - mission.offsets[0];
        mission.length = Math.max(length, mission.nearLength);

        final ProgressReport readProgress = (long position) -> {
            position -= mission.offsets[0];
            if (position > mission.done) mission.done = position;
        };

        if (worksOnSameFile) {
            ChunkFileInputStream[] sources = new ChunkFileInputStream[mission.urls.length];
            try {
                for (int i = 0, j = 1; i < sources.length; i++, j++) {
                    SharpStream source = mission.storage.getStream();
                    long end = j < sources.length ? mission.offsets[j] : source.length();

                    sources[i] = new ChunkFileInputStream(source, mission.offsets[i], end, readProgress);
                }

                if (test(sources)) {
                    for (SharpStream source : sources) source.rewind();

                    OffsetChecker checker = () -> {
                        for (ChunkFileInputStream source : sources) {
                            /*
                             * WARNING: never use rewind() in any chunk after any writing (especially on first chunks)
                             *          or the CircularFileWriter can lead to unexpected results
                             */
                            if (source.isClosed() || source.available() < 1) {
                                continue;// the selected source is not used anymore
                            }

                            return source.getFilePointer() - 1;
                        }

                        return -1;
                    };

                    try (CircularFileWriter out = new CircularFileWriter(
                            mission.storage.getStream(), tempFile, checker)) {
                        out.onProgress = (long position) -> mission.done = position;

                        out.onWriteError = err -> {
                            mission.psState = 3;
                            mission.notifyError(ERROR_POSTPROCESSING_HOLD, err);

                            try {
                                synchronized (this) {
                                    while (mission.psState == 3)
                                        wait();
                                }
                            } catch (InterruptedException e) {
                                // nothing to do
                                Log.e(getClass().getSimpleName(), "got InterruptedException");
                            }

                            return mission.errCode == ERROR_NOTHING;
                        };

                        result = process(out, sources);

                        if (result == OK_RESULT)
                            finalLength = out.finalizeFile();
                    }
                } else {
                    result = OK_RESULT;
                }
            } finally {
                for (SharpStream source : sources) {
                    if (source != null && !source.isClosed()) {
                        source.close();
                    }
                }
                if (tempFile != null) {
                    //noinspection ResultOfMethodCallIgnored
                    tempFile.delete();
                    tempFile = null;
                }
            }
        } else {
            result = test() ? process(null) : OK_RESULT;
        }

        if (result == OK_RESULT) {
            if (finalLength != -1) {
                mission.length = finalLength;
            }
        } else {
            mission.errCode = ERROR_POSTPROCESSING;
            mission.errObject = new RuntimeException("post-processing algorithm returned " + result);
        }

        if (result != OK_RESULT && worksOnSameFile) mission.storage.delete();

        this.mission = null;
    }

    /**
     * Test if the post-processing algorithm can be skipped
     *
     * @param sources files to be processed
     * @return {@code true} if the post-processing is required, otherwise, {@code false}
     * @throws IOException if an I/O error occurs.
     */
    boolean test(SharpStream... sources) throws IOException {
        return true;
    }

    /**
     * Abstract method to execute the post-processing algorithm
     *
     * @param out     output stream
     * @param sources files to be processed
     * @return an error code, {@code OK_RESULT} means the operation was successful
     * @throws IOException if an I/O error occurs.
     */
    abstract int process(SharpStream out, SharpStream... sources) throws IOException;

    String getArgumentAt(int index, String defaultValue) {
        if (args == null || index >= args.length) {
            return defaultValue;
        }

        return args[index];
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("{ name=").append(name).append('[');

        if (args != null) {
            for (String arg : args) {
                str.append(", ");
                str.append(arg);
            }
            str.delete(0, 1);
        }

        return str.append("] }").toString();
    }
}