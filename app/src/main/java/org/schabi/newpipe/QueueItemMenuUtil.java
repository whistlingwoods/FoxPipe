package org.schabi.newpipe;

import static org.schabi.newpipe.util.SparseItemUtil.fetchStreamInfoAndSaveToDatabase;
import static org.schabi.newpipe.util.external_communication.ShareUtils.shareText;

import android.content.Context;
import android.view.View;

import androidx.fragment.app.FragmentManager;

import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.download.DownloadDialog;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.ui.MaterialActionSheetDialog;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.SparseItemUtil;

import java.util.ArrayList;
import java.util.List;

public final class QueueItemMenuUtil {
    private QueueItemMenuUtil() {
    }

    public static void openActionSheet(final PlayQueue playQueue,
                                       final PlayQueueItem item,
                                       final View view,
                                       final boolean hideDetails,
                                       final FragmentManager fragmentManager,
                                       final Context context) {
        final List<MaterialActionSheetDialog.ActionItem> actionItems = new ArrayList<>();

        actionItems.add(MaterialActionSheetDialog.ActionItem.create(
                R.id.menu_item_remove,
                context.getString(R.string.play_queue_remove),
                R.drawable.ic_delete,
                () -> {
                    final int index = playQueue.indexOf(item);
                    playQueue.remove(index);
                }));
        if (!hideDetails) {
            actionItems.add(MaterialActionSheetDialog.ActionItem.create(
                    R.id.menu_item_details,
                    context.getString(R.string.play_queue_stream_detail),
                    R.drawable.ic_info_outline,
                    () -> NavigationHelper.openVideoDetail(
                            context, item.getServiceId(), item.getUrl(), item.getTitle(), null,
                            false)));
        }
        actionItems.add(MaterialActionSheetDialog.ActionItem.create(
                R.id.menu_item_append_playlist,
                context.getString(R.string.add_to_playlist),
                R.drawable.ic_playlist_add,
                () -> PlaylistDialog.createCorrespondingDialog(
                        context,
                        List.of(new StreamEntity(item)),
                        dialog -> dialog.show(
                                fragmentManager,
                                "QueueItemMenuUtil@append_playlist"
                        ))));
        actionItems.add(MaterialActionSheetDialog.ActionItem.create(
                R.id.menu_item_channel_details,
                context.getString(R.string.show_channel_details),
                R.drawable.ic_person,
                () -> SparseItemUtil.fetchUploaderUrlIfSparse(
                        context,
                        item.getServiceId(),
                        item.getUrl(),
                        item.getUploaderUrl(),
                        uploaderUrl -> NavigationHelper.openChannelFragmentUsingIntent(
                                context, item.getServiceId(), uploaderUrl, item.getUploader()
                        ))));
        actionItems.add(MaterialActionSheetDialog.ActionItem.create(
                R.id.menu_item_share,
                context.getString(R.string.share),
                R.drawable.ic_share,
                () -> shareText(context, item.getTitle(), item.getUrl(), item.getThumbnails())));
        actionItems.add(MaterialActionSheetDialog.ActionItem.create(
                R.id.menu_item_download,
                context.getString(R.string.download),
                R.drawable.ic_file_download,
                () -> fetchStreamInfoAndSaveToDatabase(context, item.getServiceId(), item.getUrl(),
                        info -> {
                            final DownloadDialog downloadDialog = new DownloadDialog(context,
                                    info);
                            downloadDialog.show(fragmentManager, "downloadDialog");
                        })));

        MaterialActionSheetDialog.show(context, item.getTitle(), actionItems);
    }
}
