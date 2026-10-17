package org.schabi.newpipe.player.resolver

import android.net.Uri
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.mediaitem.StreamInfoTag

@RunWith(AndroidJUnit4::class)
class NiconicoPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun audioAndVideoResolveToHlsWithoutCookieMetadataInTheirUrl() {
        val dataSource = PlayerDataSource(context, null)
        val url = "https://cdn.example/media.m3u8?token=a%2Bb&other=1"
        for (audio in listOf(true, false)) {
            val source = resolve(dataSource, url, "domand_bid=a+b&length=inside", audio)
            assertTrue(source is HlsMediaSource)
            assertEquals(Uri.parse(url), source.mediaItem.localConfiguration!!.uri)
        }
    }

    @Test
    fun missingOrUnsafeCookieProducesAResolverError() {
        val dataSource = PlayerDataSource(context, null)
        for (cookie in listOf("", "domand_bid=x\r\nOther: value")) {
            assertThrows(PlaybackResolver.ResolverException::class.java) {
                resolve(dataSource, "https://cdn.example/media.m3u8", cookie, true)
            }
        }
    }

    // This loopback fixture uses HTTP. Android 28+ blocks cleartext for this app's target SDK;
    // keep production network policy intact and run actual HTTP playback on the API 23 CI job.
    @Test
    @SdkSuppress(maxSdkVersion = 27)
    fun playerAuthenticatesPlaylistKeysAndSegmentsWithoutSharingCookiesBetweenSources() {
        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 15000
            val executor = Executors.newSingleThreadExecutor()
            try {
                val root = "http://127.0.0.1:${server.localPort}/${UUID.randomUUID()}"
                val dataSource = PlayerDataSource(context, null)
                val cookies = listOf("domand_bid=first+value&part=1", "domand_bid=second")
                // Build both sources before starting either one to catch mutable shared headers.
                val sources = cookies.mapIndexed { index, cookie ->
                    resolve(dataSource, "$root/$index/playlist.m3u8", cookie, index == 0)
                } + resolve(dataSource, "$root/2/playlist.m3u8", null, true)
                for ((index, source) in sources.withIndex()) {
                    val received = executor.submit<List<Pair<String, Map<String, String>>>> {
                        (0..2).map {
                            server.accept().use { socket ->
                                socket.soTimeout = 10000
                                val reader = socket.getInputStream().bufferedReader()
                                val path = reader.readLine().split(' ')[1]
                                val headers = mutableMapOf<String, String>()
                                while (true) {
                                    val line = reader.readLine() ?: error("Incomplete request")
                                    if (line.isEmpty()) break
                                    val separator = line.indexOf(':')
                                    headers[line.substring(0, separator).lowercase()] = line.substring(separator + 1).trim()
                                }
                                val body = when {
                                    path.endsWith("playlist.m3u8") -> (
                                        "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:1\n" +
                                            "#EXT-X-MEDIA-SEQUENCE:0\n" +
                                            "#EXT-X-KEY:METHOD=AES-128,URI=\"key\",IV=0x00000000000000000000000000000000\n" +
                                            "#EXTINF:1,\nsegment.ts\n#EXT-X-ENDLIST\n"
                                        ).toByteArray()

                                    path.endsWith("key") -> ByteArray(16)

                                    else -> ByteArray(256)
                                }
                                socket.getOutputStream().apply {
                                    write(("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray())
                                    write(body)
                                    flush()
                                }
                                path to headers
                            }
                        }
                    }
                    var player: ExoPlayer? = null
                    try {
                        instrumentation.runOnMainSync {
                            player = ExoPlayer.Builder(context).build().apply {
                                setMediaSource(source)
                                prepare()
                            }
                        }
                        val requests = received.get(20, TimeUnit.SECONDS)
                        assertEquals(listOf("playlist.m3u8", "key", "segment.ts"), requests.map { it.first.substringAfterLast('/') })
                        for ((_, headers) in requests) {
                            assertEquals(cookies.getOrNull(index), headers["cookie"])
                            if (index < cookies.size) {
                                assertEquals("https://www.nicovideo.jp/", headers["referer"])
                                assertEquals("https://www.nicovideo.jp", headers["origin"])
                            }
                        }
                    } finally {
                        instrumentation.runOnMainSync { player?.release() }
                    }
                }
            } finally {
                executor.shutdownNow()
            }
        }
    }

    private fun resolve(dataSource: PlayerDataSource, url: String, cookie: String?, audio: Boolean): MediaSource {
        val service = if (cookie == null) ServiceList.SoundCloud else ServiceList.NicoNico
        val info = StreamInfo(service.serviceId, "https://www.nicovideo.jp/watch/sm46796166", "", StreamType.VIDEO_STREAM, "sm46796166", "Fixture", 0)
        val content = if (cookie == null) url else "$url#cookie=${URLEncoder.encode(cookie, "UTF-8")}&length=1"
        val stream: Stream = if (audio) {
            AudioStream.Builder().setId("audio").setContent(content, true)
                .setDeliveryMethod(DeliveryMethod.HLS).setMediaFormat(MediaFormat.M4A).build()
        } else {
            VideoStream.Builder().setId("video").setContent(content, true).setIsVideoOnly(true)
                .setResolution("1080p").setDeliveryMethod(DeliveryMethod.HLS).setMediaFormat(MediaFormat.MPEG_4).build()
        }
        return PlaybackResolver.buildMediaSource(dataSource, stream, info, url, StreamInfoTag.of(info))
    }
}
