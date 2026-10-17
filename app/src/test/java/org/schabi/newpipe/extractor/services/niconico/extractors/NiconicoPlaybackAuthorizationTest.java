package org.schabi.newpipe.extractor.services.niconico.extractors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.grack.nanojson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.CancellableCall;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.zip.GZIPOutputStream;
import java.util.zip.DeflaterOutputStream;

public class NiconicoPlaybackAuthorizationTest {
    private static final String AUDIO = "https://cdn.example/audio-aac-192kbps.m3u8?token=a";
    private static final String VIDEO = "https://cdn.example/video-h264-1080p.m3u8?token=v";
    private static final String MASTER = "#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,URI=\""
            + AUDIO + "\"\n#EXT-X-STREAM-INF:BANDWIDTH=2000000\n" + VIDEO;
    private static final String ACCESS = "{\"data\":{\"contentUrl\":"
            + "\"https://cdn.example/master.m3u8\"}}";
    private final List<Request> requests = new ArrayList<>();
    private final Queue<Response> responses = new ArrayDeque<>();
    private final Downloader downloader = new Downloader() {
        @Override
        public Response execute(final Request request) throws IOException {
            requests.add(request);
            if (responses.isEmpty()) {
                throw new IOException("Unexpected extra request");
            }
            return responses.remove();
        }

        @Override
        public CancellableCall executeAsync(final Request request, final AsyncCallback callback) {
            throw new AssertionError("Only synchronous authorization requests are expected");
        }
    };
    private Downloader previousDownloader;
    private String previousTokens;
    private NiconicoWatchDataCache cache;
    private NiconicoStreamExtractor extractor;

    @Before
    public void setUp() throws Exception {
        previousDownloader = NewPipe.getDownloader();
        previousTokens = ServiceList.NicoNico.getTokens();
        NewPipe.init(downloader, NewPipe.getPreferredLocalization(),
                NewPipe.getPreferredContentCountry());
        ServiceList.NicoNico.setTokens(null);
        cache = spy(new NiconicoWatchDataCache());
        doReturn(JsonParser.object().from("""
                {"video":{"duration":30},"client":{"watchTrackId":"track"},
                 "media":{"domand":{"accessRightKey":"access-key",
                   "audios":[{"id":"audio-aac-192kbps","isAvailable":true}],
                   "videos":[{"id":"video-h264-1080p","isAvailable":true}]}}}
                """)).when(cache).refreshAndGetWatchData(any(), anyString());
        doReturn(NiconicoWatchDataCache.WatchDataType.GUEST).when(cache).getLastWatchDataType();
        extractor = new NiconicoStreamExtractor(ServiceList.NicoNico,
                ServiceList.NicoNico.getStreamLHFactory()
                        .fromUrl("https://www.nicovideo.jp/watch/sm46796166"), cache);
    }

    @After
    public void tearDown() {
        ServiceList.NicoNico.setTokens(previousTokens);
        NewPipe.init(previousDownloader, NewPipe.getPreferredLocalization(),
                NewPipe.getPreferredContentCountry());
    }

    @Test
    public void firstPlaylistAndAllTracksUseFreshCookieFromAnySetCookieHeader() throws Exception {
        cache.setStreamCookie("domand_bid=stale");
        responses.add(response(200, Map.of("sEt-CoOkIe", List.of(
                "unrelated=value; Path=/", "domand_bid=fresh; Secure; HttpOnly")), ACCESS));
        responses.add(response(200, Map.of(), MASTER));

        extractor.onFetchPage(downloader);

        assertEquals(2, requests.size());
        assertEquals("domand_bid=stale", cookie(0));
        assertEquals("domand_bid=fresh", cookie(1));
        assertEquals(DeliveryMethod.HLS, extractor.getAudioStreams().get(0).getDeliveryMethod());
        assertEquals(DeliveryMethod.HLS,
                extractor.getVideoOnlyStreams().get(0).getDeliveryMethod());
        assertTrue(extractor.getAudioStreams().get(0).getContent()
                .endsWith("#cookie=domand_bid%3Dfresh&length=30"));
        assertTrue(extractor.getVideoOnlyStreams().get(0).getContent()
                .endsWith("#cookie=domand_bid%3Dfresh&length=30"));
    }

    @Test
    public void retryUsesTheRotatedCookieAndCompressedResponses() throws Exception {
        responses.add(gzip(Map.of("Set-Cookie", List.of("domand_bid=first; Path=/")), ACCESS));
        responses.add(response(403, Map.of(), "Forbidden"));
        responses.add(gzip(Map.of("Set-Cookie", List.of("domand_bid=second; Path=/")), ACCESS));
        responses.add(gzip(Map.of(), MASTER));

        extractor.onFetchPage(downloader);

        assertEquals(4, requests.size());
        assertEquals("domand_bid=first", cookie(1));
        assertEquals("domand_bid=first", cookie(2));
        assertEquals("domand_bid=second", cookie(3));
        assertTrue(extractor.getVideoOnlyStreams().get(0).getContent()
                .contains("cookie=domand_bid%3Dsecond"));
    }

    @Test
    public void brotliMasterPlaylistKeepsItsFinalVideoVariant() throws Exception {
        responses.add(response(200, Map.of("Set-Cookie", List.of("domand_bid=fresh")), ACCESS));
        // The MASTER fixture compressed with Brotli, including real line separators.
        final byte[] compressed = Base64.getDecoder().decode(
                "G60AAGRgnn0lblVyhC66wYFDVuBZIpuO9rbZHmPHtkUokNPXCdUAvCE9kjh2YIj1"
                + "kDuK0dhBBjdloIDH9/v8Tebljq5AWOQFMo4zQgjkZXp+0Bv147VqrIyg6KyqS8dI"
                + "kCB1RdNI7S6wa185bovjOKANWx/kIFkaIXAef4J/");
        responses.add(new Response(200, "", Map.of("Content-Encoding", List.of("br")),
                "", compressed, ""));
        extractor.onFetchPage(downloader);
        assertEquals(1, extractor.getVideoOnlyStreams().size());
        assertEquals(1, extractor.getAudioStreams().size());
    }

    @Test
    public void deflatePlaylistAndLoggedInCookieAreSupported() throws Exception {
        ServiceList.NicoNico.setTokens("user_session=login;");
        responses.add(response(200, Map.of("Set-Cookie", List.of("domand_bid=fresh")), ACCESS));
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflate = new DeflaterOutputStream(bytes)) {
            deflate.write(MASTER.getBytes(StandardCharsets.UTF_8));
        }
        responses.add(new Response(200, "", Map.of("Content-Encoding", List.of("deflate")),
                "", bytes.toByteArray(), ""));
        extractor.onFetchPage(downloader);
        assertEquals("user_session=login;domand_bid=fresh", cookie(1));
        assertEquals(1, extractor.getVideoOnlyStreams().size());
    }

    @Test
    public void validCachedCookieCanBeReusedWhenServerDoesNotRotateIt() throws Exception {
        cache.setStreamCookie("domand_bid=existing");
        responses.add(response(200, Map.of(), ACCESS));
        responses.add(response(200, Map.of(), MASTER));
        extractor.onFetchPage(downloader);
        assertEquals("domand_bid=existing", cookie(1));
    }

    @Test
    public void absentCookieReportsParsingErrorBeforeRequestingPlaylist() {
        responses.add(response(200, Map.of("Set-Cookie", List.of("unrelated=value")), ACCESS));
        final ParsingException error = assertThrows(ParsingException.class,
                () -> extractor.onFetchPage(downloader));
        assertEquals("Missing NicoNico playback cookie", error.getMessage());
        assertEquals(1, requests.size());
    }

    @Test
    public void clearedCookieDoesNotReuseTheOldSession() {
        cache.setStreamCookie("domand_bid=old");
        responses.add(response(200, Map.of("Set-Cookie", List.of("domand_bid=; Max-Age=0")),
                ACCESS));
        assertThrows(ParsingException.class, () -> extractor.onFetchPage(downloader));
        assertEquals(1, requests.size());
    }

    @Test
    public void failedRetryStopsWithoutParsingErrorPagesAsJson() {
        responses.add(response(200, Map.of("Set-Cookie", List.of("domand_bid=first")), ACCESS));
        responses.add(response(403, Map.of(), "Forbidden"));
        responses.add(response(403, Map.of(), "<html>Forbidden</html>"));
        final ExtractionException error = assertThrows(ExtractionException.class,
                () -> extractor.onFetchPage(downloader));
        assertTrue(error.getMessage().contains("HTTP 403"));
        assertEquals(3, requests.size());
    }

    private String cookie(final int index) {
        return requests.get(index).headers().get("Cookie").get(0);
    }

    private static Response response(final int status, final Map<String, List<String>> headers,
                                     final String body) {
        return new Response(status, "", headers, body, body.getBytes(StandardCharsets.UTF_8), "");
    }

    private static Response gzip(final Map<String, List<String>> headers, final String body)
            throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(body.getBytes(StandardCharsets.UTF_8));
        }
        final Map<String, List<String>> encodedHeaders = new java.util.HashMap<>(headers);
        encodedHeaders.put("Content-Encoding", List.of("gzip"));
        return new Response(200, "", encodedHeaders, "", bytes.toByteArray(), "");
    }
}
