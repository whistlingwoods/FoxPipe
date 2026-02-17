package us.shandian.giga.postprocessing;

import android.util.Log;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Post-processor that adds ID3 metadata tags to audio files.
 * Supports MP3, M4A, and OGG formats.
 *
 * Arguments:
 * [0] = track number (playlist position)
 * [1] = album name (playlist name)
 * [2] = artist name (uploader name)
 * [3] = title (video title)
 * [4] = thumbnail URL (for album art)
 */
class AudioMetadataTagging extends Postprocessing {
    private static final String TAG = "AudioMetadataTagging";
    private static final int BUFFER_SIZE = 8192;

    AudioMetadataTagging() {
        super(false, true, "audio-metadata");
    }

    @Override
    int process(SharpStream out, SharpStream... sources) throws IOException {
        Log.d(TAG, "AudioMetadataTagging.process() called");

        // Get arguments
        String trackNumber = getArgumentAt(0, null);
        String albumName = getArgumentAt(1, null);
        String artistName = getArgumentAt(2, null);
        String title = getArgumentAt(3, null);
        String thumbnailUrl = getArgumentAt(4, null);

        Log.d(TAG, "Received arguments - track: " + trackNumber + ", album: " + albumName
            + ", artist: " + artistName + ", title: " + title + ", thumbnail: " + thumbnailUrl);

        if (trackNumber == null || albumName == null) {
            Log.w(TAG, "Missing required metadata (track number or album name), skipping tagging");
            // Copy source to output without tagging
            copyStream(sources[0], out);
            return OK_RESULT;
        }

        File tempAudioFile = null;
        File tempThumbnailFile = null;

        try {
            // Create temporary file with .m4a extension so JAudioTagger can read it
            // JAudioTagger uses file extension to determine the file type
            tempAudioFile = File.createTempFile("newpipe_audio_", ".m4a");

            // Copy source stream to temporary file
            try (FileOutputStream fos = new FileOutputStream(tempAudioFile)) {
                copyStream(sources[0], fos);
            }

            // Read and tag the audio file using JAudioTagger
            AudioFile audioFile = AudioFileIO.read(tempAudioFile);
            Tag tag = audioFile.getTagOrCreateAndSetDefault();

            // Set metadata fields
            if (trackNumber != null) {
                tag.setField(FieldKey.TRACK, trackNumber);
            }
            if (albumName != null) {
                tag.setField(FieldKey.ALBUM, albumName);
            }
            if (artistName != null) {
                tag.setField(FieldKey.ARTIST, artistName);
            }
            if (title != null) {
                tag.setField(FieldKey.TITLE, title);
            }

            // Download and embed album art if thumbnail URL is provided
            if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                try {
                    tempThumbnailFile = downloadThumbnail(thumbnailUrl);
                    if (tempThumbnailFile != null && tempThumbnailFile.exists()) {
                        Artwork artwork = ArtworkFactory.createArtworkFromFile(tempThumbnailFile);
                        tag.setField(artwork);
                        Log.d(TAG, "Successfully embedded album art from: " + thumbnailUrl);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to download or embed album art: " + e.getMessage());
                    // Continue without album art - don't fail the entire operation
                }
            }

            // Commit changes to the file
            audioFile.commit();

            Log.d(TAG, String.format(
                "Successfully tagged audio file: track=%s, album=%s, artist=%s, title=%s",
                trackNumber, albumName, artistName, title
            ));

            // Copy tagged file back to output stream
            try (FileInputStream fis = new FileInputStream(tempAudioFile)) {
                copyStream(fis, out);
            }

            return OK_RESULT;

        } catch (Exception e) {
            Log.e(TAG, "Failed to tag audio file", e);
            // On error, try to copy original stream to output
            try {
                sources[0].rewind();
                copyStream(sources[0], out);
            } catch (IOException rewindError) {
                Log.e(TAG, "Failed to rewind and copy original stream", rewindError);
                throw new IOException("Audio tagging failed and could not recover", e);
            }
            // Return OK to allow download to complete even if tagging failed
            return OK_RESULT;

        } finally {
            // Clean up temporary files
            if (tempAudioFile != null && tempAudioFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempAudioFile.delete();
            }
            if (tempThumbnailFile != null && tempThumbnailFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempThumbnailFile.delete();
            }
        }
    }

    /**
     * Downloads a thumbnail from the given URL to a temporary file.
     *
     * @param urlString the URL of the thumbnail
     * @return temporary file containing the thumbnail, or null if download failed
     */
    private File downloadThumbnail(String urlString) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        File tempFile = null;

        try {
            // Determine image extension from URL
            String extension = ".jpg"; // default
            if (urlString.toLowerCase().endsWith(".png")) {
                extension = ".png";
            } else if (urlString.toLowerCase().endsWith(".webp")) {
                extension = ".webp";
            }

            tempFile = File.createTempFile("newpipe_thumb_", extension);

            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000); // 10 seconds
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Thumbnail download failed with HTTP " + responseCode);
                return null;
            }

            inputStream = connection.getInputStream();
            outputStream = new FileOutputStream(tempFile);

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            return tempFile;

        } catch (Exception e) {
            Log.w(TAG, "Failed to download thumbnail: " + e.getMessage());
            if (tempFile != null && tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
            return null;

        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Copies data from an input stream to an output stream.
     *
     * @param in  input stream
     * @param out output stream
     * @throws IOException if an I/O error occurs
     */
    private void copyStream(InputStream in, java.io.OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
    }

    /**
     * Copies data from a SharpStream to an output stream.
     *
     * @param in  input SharpStream
     * @param out output stream
     * @throws IOException if an I/O error occurs
     */
    private void copyStream(SharpStream in, java.io.OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
    }

    /**
     * Copies data from an input stream to a SharpStream.
     *
     * @param in  input stream
     * @param out output SharpStream
     * @throws IOException if an I/O error occurs
     */
    private void copyStream(InputStream in, SharpStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
    }

    /**
     * Copies data from a SharpStream to a SharpStream.
     *
     * @param in  input SharpStream
     * @param out output SharpStream
     * @throws IOException if an I/O error occurs
     */
    private void copyStream(SharpStream in, SharpStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
    }
}
