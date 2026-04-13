package org.schabi.newpipe.fragments.list.videos;

import android.content.Context;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.blockedchannel.BlockedChannelManager;

import java.util.ArrayList;
import java.util.Collections;

public final class RelatedItemsInfo extends ListInfo<InfoItem> {
    /**
     * This class is used to wrap the related items of a StreamInfo into a ListInfo object.
     *
     * @param info the stream info from which to get related items
     * @param context the context for database access
     */
    public RelatedItemsInfo(final StreamInfo info, final Context context) {
        super(info.getServiceId(), new ListLinkHandler(info.getOriginalUrl(), info.getUrl(),
                info.getId(), Collections.emptyList(), null), info.getName());

        // Always start with original items - filtering will be done lazily if needed
        setRelatedItems(new ArrayList<>(info.getRelatedItems()));
    }

    /**
     * Filter out videos from blocked channels. This should be called on a background thread.
     * @param context the context for database access
     */
    public void filterBlockedChannels(final Context context) {
        if (context == null) return;

        final ArrayList<InfoItem> filteredItems = new ArrayList<>();
        final BlockedChannelManager blockedChannelManager = new BlockedChannelManager(context);

        for (final InfoItem item : getRelatedItems()) {
            if (item instanceof StreamInfoItem) {
                final StreamInfoItem streamItem = (StreamInfoItem) item;
                // Filter out videos from blocked channels
                if (!blockedChannelManager.isChannelBlocked(streamItem.getServiceId(), streamItem.getUploaderUrl())) {
                    filteredItems.add(item);
                }
            } else {
                // Keep non-stream items (like playlists, channels, etc.)
                filteredItems.add(item);
            }
        }

        setRelatedItems(filteredItems);
    }
}
