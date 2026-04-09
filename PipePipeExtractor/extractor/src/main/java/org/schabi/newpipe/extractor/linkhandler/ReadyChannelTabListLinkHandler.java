package org.schabi.newpipe.extractor.linkhandler;

import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

public class ReadyChannelTabListLinkHandler extends ListLinkHandler {
    public ReadyChannelTabListLinkHandler(final String originalUrl,
                                          final String url,
                                          final String id,
                                          final List<FilterItem> selectedContentFilters,
                                          final List<FilterItem> selectedSortFilter) {
        super(originalUrl, url, id, selectedContentFilters, selectedSortFilter);
    }

    public ReadyChannelTabListLinkHandler(final ListLinkHandler handler) {
        super(handler);
    }
}
