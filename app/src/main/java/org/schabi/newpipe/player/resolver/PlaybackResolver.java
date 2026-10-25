package org.schabi.newpipe.player.resolver;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.util.Util;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.helper.PlayerDataSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;

public interface PlaybackResolver extends Resolver<StreamInfo, MediaSource> {

    String TAG = "PlaybackResolver";

    /**
     * Build the media source of a single stream, whether it is addressed by URL or described by a
     * DASH manifest.
     *
     * <p>
     * YouTube serves its adaptive formats in bounded byte ranges only -- a plain GET is answered
     * HTTP 403 -- so the extractor hands those over as generated DASH manifests instead of URLs.
     * Those have to go through {@link com.google.android.exoplayer2.source.dash.DashMediaSource},
     * which requests exactly the ranges the manifest describes.
     * </p>
     *
     * @return the media source, or null if the stream cannot be played
     */
    @Nullable
    default MediaSource buildStreamMediaSource(@NonNull final PlayerDataSource dataSource,
                                               @NonNull final Stream stream,
                                               @NonNull final String cacheKey,
                                               @NonNull final MediaSourceTag metadata) {
        final String content = stream.getContent();
        if (TextUtils.isEmpty(content)) {
            Log.e(TAG, "Stream " + stream.getFormatId() + " carries no content");
            return null;
        }

        if (stream.isUrl()) {
            return buildMediaSource(dataSource, content, cacheKey,
                    MediaFormat.getSuffixById(stream.getFormatId()), metadata);
        }

        try {
            // The generated manifest carries an absolute <BaseURL>, so the URI passed here is only
            // a base for resolving relative ones and is never used
            return dataSource.getDashMediaSourceFactory().setTag(metadata).createMediaSource(
                    new DashManifestParser().parse(Uri.parse("https://www.youtube.com"),
                            new ByteArrayInputStream(content.getBytes(Charset.forName("UTF-8")))));
        } catch (final IOException e) {
            // Returning null here means the player waits forever with nothing to report, so say
            // which stream was dropped and why
            Log.e(TAG, "Could not parse the generated DASH manifest of stream "
                    + stream.getFormatId(), e);
            return null;
        }
    }

    @Nullable
    default MediaSource maybeBuildLiveMediaSource(@NonNull final PlayerDataSource dataSource,
                                                  @NonNull final StreamInfo info) {
        final StreamType streamType = info.getStreamType();
        if (!(streamType == StreamType.AUDIO_LIVE_STREAM || streamType == StreamType.LIVE_STREAM)) {
            return null;
        }

        final MediaSourceTag tag = new MediaSourceTag(info);
        if (!info.getHlsUrl().isEmpty()) {
            return buildLiveMediaSource(dataSource, info.getHlsUrl(), C.TYPE_HLS, tag);
        } else if (!info.getDashMpdUrl().isEmpty()) {
            return buildLiveMediaSource(dataSource, info.getDashMpdUrl(), C.TYPE_DASH, tag);
        }

        return null;
    }

    @NonNull
    default MediaSource buildLiveMediaSource(@NonNull final PlayerDataSource dataSource,
                                             @NonNull final String sourceUrl,
                                             @C.ContentType final int type,
                                             @NonNull final MediaSourceTag metadata) {
        final Uri uri = Uri.parse(sourceUrl);
        switch (type) {
            case C.TYPE_SS:
                return dataSource.getLiveSsMediaSourceFactory().setTag(metadata)
                        .createMediaSource(uri);
            case C.TYPE_DASH:
                return dataSource.getLiveDashMediaSourceFactory().setTag(metadata)
                        .createMediaSource(uri);
            case C.TYPE_HLS:
                return dataSource.getLiveHlsMediaSourceFactory().setTag(metadata)
                        .createMediaSource(uri);
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }
    }

    @NonNull
    default MediaSource buildMediaSource(@NonNull final PlayerDataSource dataSource,
                                         @NonNull final String sourceUrl,
                                         @NonNull final String cacheKey,
                                         @NonNull final String overrideExtension,
                                         @NonNull final MediaSourceTag metadata) {
        final Uri uri = Uri.parse(sourceUrl);
        @C.ContentType final int type = TextUtils.isEmpty(overrideExtension)
                ? Util.inferContentType(uri) : Util.inferContentType("." + overrideExtension);

        switch (type) {
            case C.TYPE_SS:
                return dataSource.getLiveSsMediaSourceFactory().setTag(metadata)
                        .createMediaSource(uri);
            case C.TYPE_DASH:
                return dataSource.getDashMediaSourceFactory().setTag(metadata)
                        .createMediaSource(uri);
            case C.TYPE_HLS:
                return dataSource.getHlsMediaSourceFactory().setTag(metadata)
                        .createMediaSource(uri);
            case C.TYPE_OTHER:
                return dataSource.getExtractorMediaSourceFactory(cacheKey).setTag(metadata)
                        .createMediaSource(uri);
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }
    }
}
