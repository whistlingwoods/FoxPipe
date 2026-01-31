package org.schabi.newpipe.fragments.list.playlist;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.player.playqueue.PlayQueue;

import java.util.List;

/**
 * Interface for {@code R.layout.playlist_control} view holders
 * to give access to the play queue.
 */
public interface PlaylistControlViewHolder {
    PlayQueue getPlayQueue();

    /**
     * Get the list of stream items for download.
     * @return list of StreamInfoItem or empty list if not available
     */
    default List<StreamInfoItem> getStreamItems() {
        return List.of();
    }
}
