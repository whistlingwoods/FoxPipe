package org.schabi.newpipe.info_list.dialog;

import static org.schabi.newpipe.util.NavigationHelper.openChannelFragment;
import static org.schabi.newpipe.util.SparseItemUtil.fetchItemInfoIfSparse;
import static org.schabi.newpipe.util.SparseItemUtil.fetchStreamInfoAndSaveToDatabase;
import static org.schabi.newpipe.util.SparseItemUtil.fetchUploaderUrlIfSparse;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.download.DownloadDialog;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.KoreUtils;
import org.schabi.newpipe.util.external_communication.ShareUtils;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

/**
 * <p>
 *     This enum provides entries that are accepted
 *     by the {@link InfoItemDialog.Builder}.
 * </p>
 * <p>
 *     These entries contain a String {@link #resource} which is displayed in the dialog and
 *     a default {@link #action} that is executed
 *     when the entry is selected (via <code>onClick()</code>).
 *     <br/>
 *     They action can be overridden by using the Builder's
 *     {@link InfoItemDialog.Builder#setAction(
 *     StreamDialogDefaultEntry, StreamDialogEntry.StreamDialogEntryAction)}
 *     method.
 * </p>
 */
public enum StreamDialogDefaultEntry {
    SHOW_CHANNEL_DETAILS(R.string.show_channel_details, R.drawable.ic_person, (fragment, item) ->
            fetchUploaderUrlIfSparse(fragment.requireContext(), item.getServiceId(), item.getUrl(),
                    item.getUploaderUrl(), url -> openChannelFragment(fragment, item, url))
    ),

    /**
     * Enqueues the stream automatically to the current PlayerType.
     */
    ENQUEUE(R.string.enqueue_stream, R.drawable.ic_playlist_play, (fragment, item) -> {
            final Context ctx = fragment.requireContext().getApplicationContext();
            fetchItemInfoIfSparse(ctx, item, singlePlayQueue ->
                    NavigationHelper.enqueueOnPlayer(ctx, singlePlayQueue));
    }),

    /**
     * Enqueues the stream automatically to the current PlayerType
     * after the currently playing stream.
     */
    ENQUEUE_NEXT(R.string.enqueue_next_stream, R.drawable.ic_next, (fragment, item) -> {
            final Context ctx = fragment.requireContext().getApplicationContext();
            fetchItemInfoIfSparse(ctx, item, singlePlayQueue ->
                    NavigationHelper.enqueueNextOnPlayer(ctx, singlePlayQueue));
    }),

    START_HERE_ON_BACKGROUND(R.string.start_here_on_background, R.drawable.ic_headset,
            (fragment, item) -> {
                final Context ctx = fragment.requireContext().getApplicationContext();
                fetchItemInfoIfSparse(ctx, item, singlePlayQueue ->
                        NavigationHelper.playOnBackgroundPlayer(ctx, singlePlayQueue, true));
            }),

    START_HERE_ON_POPUP(R.string.start_here_on_popup, R.drawable.ic_picture_in_picture,
            (fragment, item) -> {
                final Context ctx = fragment.requireContext().getApplicationContext();
                fetchItemInfoIfSparse(ctx, item, singlePlayQueue ->
                        NavigationHelper.playOnPopupPlayer(ctx, singlePlayQueue, true));
            }),

    SET_AS_PLAYLIST_THUMBNAIL(R.string.set_as_playlist_thumbnail, R.drawable.ic_playlist_add_check,
            (fragment, item) -> {
        throw new UnsupportedOperationException("This needs to be implemented manually "
                + "by using InfoItemDialog.Builder.setAction()");
    }),

    DELETE(R.string.delete, R.drawable.ic_delete, (fragment, item) -> {
        throw new UnsupportedOperationException("This needs to be implemented manually "
                + "by using InfoItemDialog.Builder.setAction()");
    }),

    /**
     * Opens a {@link PlaylistDialog} to either append the stream to a playlist
     * or create a new playlist if there are no local playlists.
     */
    APPEND_PLAYLIST(R.string.add_to_playlist, R.drawable.ic_playlist_add, (fragment, item) ->
        PlaylistDialog.createCorrespondingDialog(
                fragment.getContext(),
                List.of(new StreamEntity(item)),
                dialog -> dialog.show(
                        fragment.getParentFragmentManager(),
                        "StreamDialogEntry@"
                                + (dialog instanceof PlaylistAppendDialog ? "append" : "create")
                                + "_playlist"
                )
        )
    ),

    PLAY_WITH_KODI(R.string.play_with_kodi_title, R.drawable.ic_cast, (fragment, item) ->
            KoreUtils.playWithKore(fragment.requireContext(), Uri.parse(item.getUrl()))),

    SHARE(R.string.share, R.drawable.ic_share, (fragment, item) ->
            ShareUtils.shareText(fragment.requireContext(), item.getName(), item.getUrl(),
                    item.getThumbnails())),

    /**
     * Opens a {@link DownloadDialog} after fetching some stream info.
     * If the user quits the current fragment, it will not open a DownloadDialog.
     */
    DOWNLOAD(R.string.download, R.drawable.ic_file_download, (fragment, item) ->
            fetchStreamInfoAndSaveToDatabase(fragment.requireContext(), item.getServiceId(),
                    item.getUrl(), info -> {
                        // Ensure the fragment is attached and its state hasn't been saved to avoid
                        // showing dialog during lifecycle changes or when the activity is paused,
                        // e.g. by selecting the download option and opening a different fragment.
                        if (fragment.isAdded() && !fragment.isStateSaved()) {
                            final DownloadDialog downloadDialog =
                                    new DownloadDialog(fragment.requireContext(), info);
                            downloadDialog.show(fragment.getChildFragmentManager(),
                                    "downloadDialog");
                        }
                    })
    ),

    OPEN_IN_BROWSER(R.string.open_in_browser, R.drawable.ic_public, (fragment, item) ->
            ShareUtils.openUrlInBrowser(fragment.requireContext(), item.getUrl())),


    MARK_AS_WATCHED(R.string.mark_as_watched, R.drawable.ic_visibility_on, (fragment, item) ->
        new HistoryRecordManager(fragment.getContext())
                .markAsWatched(item)
                .doOnError(error -> {
                    ErrorUtil.showSnackbar(
                            fragment.requireContext(),
                            new ErrorInfo(
                                    error,
                                    UserAction.OPEN_INFO_ITEM_DIALOG,
                                    "Got an error when trying to mark as watched"
                            )
                    );
                })
                .onErrorComplete()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe()
    );


    @StringRes
    public final int resource;
    @DrawableRes
    public final int iconResource;
    @NonNull
    public final StreamDialogEntry.StreamDialogEntryAction action;

    StreamDialogDefaultEntry(@StringRes final int resource,
                             @DrawableRes final int iconResource,
                             @NonNull final StreamDialogEntry.StreamDialogEntryAction action) {
        this.resource = resource;
        this.iconResource = iconResource;
        this.action = action;
    }

    @NonNull
    public StreamDialogEntry toStreamDialogEntry() {
        return new StreamDialogEntry(resource, iconResource, action);
    }

}
