package org.schabi.newpipe.info_list.holder;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.ktx.ViewUtils;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.DependentPreferenceHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.StreamTypeUtil;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.views.AnimatedProgressBar;

import java.util.concurrent.TimeUnit;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.schabi.newpipe.util.dearrow.DeArrowHelper;
import org.schabi.newpipe.util.ExtractorHelper;

public class StreamMiniInfoItemHolder extends InfoItemHolder {
    public final ImageView itemThumbnailView;
    public final TextView itemVideoTitleView;
    public final TextView itemUploaderView;
    public final TextView itemDurationView;
    private final AnimatedProgressBar itemProgressView;
    private Disposable deArrowDisposable;
    private String boundUrl;

    StreamMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                             final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemVideoTitleView = itemView.findViewById(R.id.itemVideoTitleView);
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView);
        itemDurationView = itemView.findViewById(R.id.itemDurationView);
        itemProgressView = itemView.findViewById(R.id.itemProgressView);
    }

    public StreamMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_stream_mini_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        if (!(infoItem instanceof StreamInfoItem)) {
            return;
        }
        final StreamInfoItem item = (StreamInfoItem) infoItem;

        itemVideoTitleView.setText(item.getName());
        itemUploaderView.setText(item.getUploaderName());

        if (item.getDuration() > 0) {
            itemDurationView.setText(Localization.getDurationString(item.getDuration()));
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(),
                    R.color.duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);

            StreamStateEntity state2 = null;
            if (DependentPreferenceHelper
                    .getPositionsInListsEnabled(itemProgressView.getContext())) {
                state2 = historyRecordManager.loadStreamState(infoItem).blockingGet();
            }
            if (state2 != null) {
                itemProgressView.setVisibility(View.VISIBLE);
                itemProgressView.setMax((int) item.getDuration());
                itemProgressView.setProgress((int) TimeUnit.MILLISECONDS
                        .toSeconds(state2.getProgressMillis()));
            } else {
                itemProgressView.setVisibility(View.GONE);
            }
        } else if (StreamTypeUtil.isLiveStream(item.getStreamType())) {
            itemDurationView.setText(R.string.duration_live);
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(),
                    R.color.live_duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);
            itemProgressView.setVisibility(View.GONE);
        } else {
            itemDurationView.setVisibility(View.GONE);
            itemProgressView.setVisibility(View.GONE);
        }

        // For YouTube livestreams, show the channel avatar as the thumbnail
        // ONLY if DeArrow thumbnail replacement is enabled
        final boolean replaceThumbnails = android.preference.PreferenceManager
                .getDefaultSharedPreferences(itemBuilder.getContext())
                .getBoolean(itemBuilder.getContext().getString(
                        R.string.dearrow_replace_thumbnails_key), true);

        if (replaceThumbnails
                && item.getServiceId() == org.schabi.newpipe.extractor
                        .ServiceList.YouTube.getServiceId()
                && StreamTypeUtil.isLiveStream(item.getStreamType())
                && item.getUploaderAvatars() != null
                && !item.getUploaderAvatars().isEmpty()) {
            CoilHelper.INSTANCE.loadThumbnail(itemThumbnailView, item.getUploaderAvatars());
        } else {
            // Default thumbnail is shown on error, while loading and if the url is empty
            CoilHelper.INSTANCE.loadThumbnail(itemThumbnailView, item.getThumbnails());
        }

        if (deArrowDisposable != null) {
            deArrowDisposable.dispose();
            deArrowDisposable = null;
        }
        boundUrl = item.getUrl();

        if (ExtractorHelper.getDeArrowApiSettings(itemBuilder.getContext()) != null) {
            String videoId = null;
            try {
                videoId = org.schabi.newpipe.extractor.NewPipe.getService(item.getServiceId())
                        .getStreamLHFactory().getId(item.getUrl());
            } catch (final Exception e) {
                // Ignore
            }
            if (videoId != null && !videoId.isEmpty()) {
                final String finalVideoId = videoId;
                deArrowDisposable = DeArrowHelper
                        .fetchDeArrowInfoAsync(itemBuilder.getContext(), finalVideoId)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(deArrowInfo -> {
                            // Check if we are still bound to the same item
                            if (!item.getUrl().equals(boundUrl)) {
                                return;
                            }

                            final String formattedTitle = DeArrowHelper.getFormattedTitle(
                                    itemBuilder.getContext(), finalVideoId, deArrowInfo);
                            if (formattedTitle != null) {
                                itemVideoTitleView.setText(formattedTitle);
                            }

                            // Do not override the thumbnail for YouTube livestreams (use avatar)
                            final boolean isYouTubeLive = item.getServiceId()
                                    == org.schabi.newpipe.extractor.ServiceList
                                            .YouTube.getServiceId()
                                    && StreamTypeUtil.isLiveStream(item.getStreamType());

                            if (!isYouTubeLive) {
                                final String thumbnailUrl = DeArrowHelper.getThumbnailUrl(
                                        itemBuilder.getContext(), finalVideoId, deArrowInfo);
                                if (thumbnailUrl != null) {
                                    CoilHelper.INSTANCE.loadThumbnail(
                                            itemThumbnailView, thumbnailUrl);
                                }
                            }
                        }, throwable -> {
                            // Ignore errors (e.g. no data found)
                        });
            }
        }

        itemView.setOnClickListener(view -> {
            if (itemBuilder.getOnStreamSelectedListener() != null) {
                itemBuilder.getOnStreamSelectedListener().selected(item);
            }
        });

        switch (item.getStreamType()) {
            case AUDIO_STREAM:
            case VIDEO_STREAM:
            case LIVE_STREAM:
            case AUDIO_LIVE_STREAM:
            case POST_LIVE_STREAM:
            case POST_LIVE_AUDIO_STREAM:
                enableLongClick(item);
                break;
            case NONE:
            default:
                disableLongClick();
                break;
        }
    }

    @Override
    public void updateState(final InfoItem infoItem,
                            final HistoryRecordManager historyRecordManager) {
        final StreamInfoItem item = (StreamInfoItem) infoItem;

        StreamStateEntity state = null;
        if (DependentPreferenceHelper.getPositionsInListsEnabled(itemProgressView.getContext())) {
            state = historyRecordManager
                    .loadStreamState(infoItem)
                    .blockingGet();
        }
        if (state != null && item.getDuration() > 0
                && !StreamTypeUtil.isLiveStream(item.getStreamType())) {
            itemProgressView.setMax((int) item.getDuration());
            if (itemProgressView.getVisibility() == View.VISIBLE) {
                itemProgressView.setProgressAnimated((int) TimeUnit.MILLISECONDS
                        .toSeconds(state.getProgressMillis()));
            } else {
                itemProgressView.setProgress((int) TimeUnit.MILLISECONDS
                        .toSeconds(state.getProgressMillis()));
                ViewUtils.animate(itemProgressView, true, 500);
            }
        } else if (itemProgressView.getVisibility() == View.VISIBLE) {
            ViewUtils.animate(itemProgressView, false, 500);
        }
    }

    private void enableLongClick(final StreamInfoItem item) {
        itemView.setLongClickable(true);
        itemView.setOnLongClickListener(view -> {
            if (itemBuilder.getOnStreamSelectedListener() != null) {
                itemBuilder.getOnStreamSelectedListener().held(item);
            }
            return true;
        });
    }

    private void disableLongClick() {
        itemView.setLongClickable(false);
        itemView.setOnLongClickListener(null);
    }
}
