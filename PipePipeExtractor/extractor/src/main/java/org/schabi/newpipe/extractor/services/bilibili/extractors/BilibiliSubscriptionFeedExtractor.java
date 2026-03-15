package org.schabi.newpipe.extractor.services.bilibili.extractors;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.SPACE_REFERER;
import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService
        .USER_VIDEO_API_MODE_CLIENT;
import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService
        .USER_VIDEO_API_MODE_SEARCH;
import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService
        .USER_VIDEO_API_MODE_WEB;
import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.getCurrentVideoApiMode;
import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.getHeaders;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.feed.FeedExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.bilibili.BilibiliService;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

public final class BilibiliSubscriptionFeedExtractor extends FeedExtractor {
    private JsonArray videos = new JsonArray();
    private int activeMode = USER_VIDEO_API_MODE_WEB;
    private String channelName = "";

    public BilibiliSubscriptionFeedExtractor(final StreamingService service,
                                             final ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        final String channelId = getId();
        Throwable lastError = null;

        for (final int mode : buildModeOrder()) {
            try {
                final JsonObject response = fetchResponse(downloader, channelId, mode);
                final JsonArray candidateVideos = extractVideos(response, mode);
                if (candidateVideos == null) {
                    continue;
                }

                videos = candidateVideos;
                activeMode = mode;
                channelName = resolveChannelName(candidateVideos);
                return;
            } catch (final IOException | ExtractionException e) {
                lastError = e;
            }
        }

        if (lastError instanceof IOException) {
            throw (IOException) lastError;
        }
        if (lastError instanceof ExtractionException) {
            throw (ExtractionException) lastError;
        }
        throw new ParsingException("Failed to fetch BiliBili subscription feed for " + channelId);
    }

    @Nonnull
    @Override
    public ListExtractor.InfoItemsPage<StreamInfoItem> getInitialPage() throws ParsingException {
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());

        for (int index = 0; index < videos.size(); index++) {
            final JsonObject video = videos.getObject(index);
            if (activeMode == USER_VIDEO_API_MODE_CLIENT) {
                collector.commit(new BilibiliChannelInfoItemClientAPIExtractor(
                        video, channelName, null));
            } else {
                collector.commit(new BilibiliChannelInfoItemWebAPIExtractor(
                        video, channelName, null));
            }
        }

        if (ServiceList.BiliBili.getFilterTypes().contains("channels")) {
            collector.applyBlocking(ServiceList.BiliBili.getFilterConfig());
        }
        return new InfoItemsPage<>(collector, null);
    }

    @Override
    public ListExtractor.InfoItemsPage<StreamInfoItem> getPage(final Page page) {
        return InfoItemsPage.emptyPage();
    }

    @Nonnull
    @Override
    public String getId() {
        return getLinkHandler().getId();
    }

    @Nonnull
    @Override
    public String getUrl() {
        return getLinkHandler().getUrl();
    }

    @Nonnull
    @Override
    public String getName() {
        return channelName;
    }

    private int[] buildModeOrder() {
        final int currentMode = getCurrentVideoApiMode();
        final List<Integer> orderedModes =
                new ArrayList<>(BilibiliService.SIZE_USER_VIDEO_API_MODE);
        orderedModes.add(currentMode);

        for (int mode = USER_VIDEO_API_MODE_WEB;
             mode < BilibiliService.SIZE_USER_VIDEO_API_MODE;
             mode++) {
            if (mode != currentMode) {
                orderedModes.add(mode);
            }
        }

        final int[] result = new int[orderedModes.size()];
        for (int index = 0; index < orderedModes.size(); index++) {
            result[index] = orderedModes.get(index);
        }
        return result;
    }

    private JsonObject fetchResponse(@Nonnull final Downloader downloader,
                                     @Nonnull final String channelId,
                                     final int mode)
            throws IOException, ExtractionException {
        final String currentUrl = getUrl();
        final Map<String, List<String>> headers;
        final String requestUrl;

        switch (mode) {
            case USER_VIDEO_API_MODE_CLIENT:
                headers = getHeaders(SPACE_REFERER);
                requestUrl = BilibiliChannelExtractor.ClientUserVideoImpl
                        .buildUserVideosUrlClientAPI(channelId, 0);
                break;
            case USER_VIDEO_API_MODE_SEARCH:
                headers = getHeaders(currentUrl);
                requestUrl = BilibiliChannelExtractor.SearchUserVideoImpl
                        .buildUserVideosUrlSearchAPI(currentUrl, channelId);
                break;
            case USER_VIDEO_API_MODE_WEB:
            default:
                headers = getHeaders(currentUrl);
                requestUrl = BilibiliChannelExtractor.WebUserVideoImpl
                        .buildUserVideosUrlWebAPI(currentUrl, channelId);
                break;
        }

        return BilibiliChannelExtractor.requestUserSpaceResponse(downloader, requestUrl, headers);
    }

    private JsonArray extractVideos(@Nonnull final JsonObject response, final int mode) {
        switch (mode) {
            case USER_VIDEO_API_MODE_CLIENT:
                return response.getObject("data").getArray("item");
            case USER_VIDEO_API_MODE_SEARCH:
                return response.getObject("data").getArray("archives");
            case USER_VIDEO_API_MODE_WEB:
            default:
                return response.getObject("data").getObject("list").getArray("vlist");
        }
    }

    @Nonnull
    private String resolveChannelName(@Nonnull final JsonArray results) {
        for (int index = 0; index < results.size(); index++) {
            final JsonObject item = results.getObject(index);
            final String author = item.getString("author");
            if (author != null && !author.isBlank()) {
                return author;
            }
        }
        return "";
    }
}
