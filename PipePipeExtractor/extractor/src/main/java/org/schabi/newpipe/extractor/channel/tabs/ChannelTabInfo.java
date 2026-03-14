package org.schabi.newpipe.extractor.channel.tabs;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import java.io.IOException;

public class ChannelTabInfo extends org.schabi.newpipe.extractor.channel.ChannelTabInfo {
    public ChannelTabInfo(final int serviceId, final ListLinkHandler linkHandler) {
        super(serviceId, linkHandler);
    }

    public static ChannelTabInfo getInfo(final StreamingService service,
                                         final ListLinkHandler linkHandler)
            throws ExtractionException, IOException {
        return wrap(org.schabi.newpipe.extractor.channel.ChannelTabInfo.getInfo(service,
                linkHandler));
    }

    public static ChannelTabInfo getInfo(final ChannelTabExtractor extractor) {
        return wrap(org.schabi.newpipe.extractor.channel.ChannelTabInfo.getInfo(extractor));
    }

    public static ListExtractor.InfoItemsPage<InfoItem> getMoreItems(
            final StreamingService service, final ListLinkHandler linkHandler, final Page page)
            throws ExtractionException, IOException {
        return org.schabi.newpipe.extractor.channel.ChannelTabInfo.getMoreItems(service,
                linkHandler, page);
    }

    private static ChannelTabInfo wrap(
            final org.schabi.newpipe.extractor.channel.ChannelTabInfo source) {
        final ListLinkHandler linkHandler = new ListLinkHandler(source.getOriginalUrl(),
                source.getUrl(), source.getId(), source.getContentFilters(), source.getSortFilter());
        final ChannelTabInfo wrapped = new ChannelTabInfo(source.getServiceId(), linkHandler);
        wrapped.setOriginalUrl(source.getOriginalUrl());
        wrapped.setRelatedItems(source.getRelatedItems());
        wrapped.setNextPage(source.getNextPage());
        wrapped.addAllErrors(source.getErrors());
        wrapped.setName(source.getName());
        return wrapped;
    }
}
