package org.schabi.newpipe.streams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import android.util.Log;
import android.util.Pair;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.streams.WebMReader.WebMTrack;
import org.schabi.newpipe.streams.io.SharpStream;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Unit tests for the Opus metadata header built by {@link OggFromWebMWriter}.
 * <p>
 * {@code makeMetadata()} is private, so it is called through reflection. {@link Pair} and
 * {@link Log} are only stubs in local unit tests and are mocked statically.
 */
public class OggFromWebMWriterTest {
    private MockedStatic<Log> logMock;
    private MockedStatic<Pair> pairMock;

    @Before
    public void setUp() {
        logMock = mockStatic(Log.class);
        pairMock = mockStatic(Pair.class);
        pairMock.when(() -> Pair.create(any(), any()))
                .thenAnswer(invocation -> pair(invocation.getArgument(0),
                        invocation.getArgument(1)));
    }

    @After
    public void tearDown() {
        pairMock.close();
        logMock.close();
    }

    @Test
    public void opusMetadataWithoutUploadDate() throws Exception {
        final StreamInfo info = mock(StreamInfo.class);
        when(info.getUrl()).thenReturn("https://example.com/watch?v=1");
        when(info.getCategory()).thenReturn("Music");
        when(info.getUploaderName()).thenReturn("Uploader");
        when(info.getName()).thenReturn("Title");
        when(info.getUploadDate()).thenReturn(null);

        final byte[] header = makeMetadata(info);

        final String text = new String(header, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("OpusTags"));
        assertEquals(4, tagCount(header));
        assertTrue(text.contains("COMMENT=https://example.com/watch?v=1"));
        assertTrue(text.contains("TITLE=Title"));
        assertFalse(text.contains("DATE="));
    }

    @Test
    public void opusMetadataSkipsNullTextFields() throws Exception {
        final StreamInfo info = mock(StreamInfo.class);
        when(info.getUrl()).thenReturn("https://example.com/watch?v=1");
        when(info.getCategory()).thenReturn(null);
        when(info.getUploaderName()).thenReturn(null);
        when(info.getName()).thenReturn("Title");
        when(info.getUploadDate()).thenReturn(new DateWrapper(
                OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.UTC)));

        final byte[] header = makeMetadata(info);

        final String text = new String(header, StandardCharsets.UTF_8);
        assertEquals(3, tagCount(header));
        assertTrue(text.contains("DATE=2026-10-01"));
        assertFalse(text.contains("GENRE="));
        assertFalse(text.contains("ARTIST="));
    }

    private static byte[] makeMetadata(final StreamInfo info) throws Exception {
        final SharpStream stream = mock(SharpStream.class);
        when(stream.canRead()).thenReturn(true);
        when(stream.canWrite()).thenReturn(true);
        when(stream.canRewind()).thenReturn(true);
        final OggFromWebMWriter writer = new OggFromWebMWriter(stream, stream, info);

        final WebMTrack track = new WebMTrack();
        track.codecId = "A_OPUS";
        final Field trackField = OggFromWebMWriter.class.getDeclaredField("webmTrack");
        trackField.setAccessible(true);
        trackField.set(writer, track);

        final Method method = OggFromWebMWriter.class.getDeclaredMethod("makeMetadata");
        method.setAccessible(true);
        return (byte[]) method.invoke(writer);
    }

    // number of comments, stored after "OpusTags" and the empty vendor string
    private static int tagCount(final byte[] header) {
        return ByteBuffer.wrap(header, 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    // the stub constructor of Pair throws, so its final fields are set directly
    @SuppressWarnings("unchecked")
    private static <F, S> Pair<F, S> pair(final F first, final S second)
            throws ReflectiveOperationException {
        final Pair<F, S> pair = mock(Pair.class);
        final Field firstField = Pair.class.getField("first");
        firstField.setAccessible(true);
        firstField.set(pair, first);
        final Field secondField = Pair.class.getField("second");
        secondField.setAccessible(true);
        secondField.set(pair, second);
        return pair;
    }
}
