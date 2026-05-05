package org.schabi.newpipe.player.ui;

import static com.google.android.exoplayer2.Player.REPEAT_MODE_ALL;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_ONE;
import static org.schabi.newpipe.MainActivity.DEBUG;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.ktx.ViewUtils.animateRotation;
import static org.schabi.newpipe.player.Player.RENDERER_UNAVAILABLE;
import static org.schabi.newpipe.player.Player.STATE_BUFFERING;
import static org.schabi.newpipe.player.Player.STATE_COMPLETED;
import static org.schabi.newpipe.player.Player.STATE_PAUSED;
import static org.schabi.newpipe.player.Player.STATE_PAUSED_SEEK;
import static org.schabi.newpipe.player.Player.STATE_PLAYING;
import static org.schabi.newpipe.player.helper.PlayerHelper.formatSpeed;
import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;
import static org.schabi.newpipe.player.helper.PlayerHelper.nextResizeModeAndSaveToPrefs;
import static org.schabi.newpipe.player.helper.PlayerHelper.retrieveSeekDurationFromPreferences;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.BitmapCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.math.MathUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player.RepeatMode;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.CaptionStyleCompat;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.schabi.newpipe.App;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfo;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.ktx.AnimationType;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.gesture.BasePlayerGestureListener;
import org.schabi.newpipe.player.gesture.DisplayPortion;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.playback.SurfaceHolderCallback;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHelper;
import org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHolder;
import org.schabi.newpipe.ui.MaterialActionSheetDialog;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.SponsorBlockHelper;
import org.schabi.newpipe.util.external_communication.KoreUtils;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.views.MarkableSeekBar;
import org.schabi.newpipe.views.PilotIconButton;
import org.schabi.newpipe.views.player.PlayerFastSeekOverlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public abstract class VideoPlayerUi extends PlayerUi implements SeekBar.OnSeekBarChangeListener {
    private static final String TAG = VideoPlayerUi.class.getSimpleName();

    // time constants
    public static final long DEFAULT_CONTROLS_DURATION = 300; // 300 millis
    public static final long DEFAULT_CONTROLS_HIDE_TIME = 2000;  // 2 Seconds
    public static final long DPAD_CONTROLS_HIDE_TIME = 7000;  // 7 Seconds
    public static final int SEEK_OVERLAY_DURATION = 450; // 450 millis

    // other constants (TODO remove playback speeds and use normal menu for popup, too)
    private static final float[] PLAYBACK_SPEEDS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};

    private enum PlayButtonAction {
        PLAY, PAUSE, REPLAY
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    protected PlayerBinding binding;
    private final Handler controlsVisibilityHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private SurfaceHolderCallback surfaceHolderCallback;
    boolean surfaceIsSetup = false;


    /*//////////////////////////////////////////////////////////////////////////
    // Action sheets used by player controls
    //////////////////////////////////////////////////////////////////////////*/

    private static final int POPUP_MENU_ID_QUALITY = 69;
    private static final int POPUP_MENU_ID_AUDIO_TRACK = 70;
    private static final int POPUP_MENU_ID_PLAYBACK_SPEED = 79;
    private static final int POPUP_MENU_ID_CAPTION = 89;

    protected boolean isSomeActionSheetVisible = false;
    @Nullable
    private BottomSheetDialog actionSheetDialog;


    /*//////////////////////////////////////////////////////////////////////////
    // Gestures
    //////////////////////////////////////////////////////////////////////////*/

    private GestureDetector gestureDetector;
    private BasePlayerGestureListener playerGestureListener;
    @Nullable
    private View.OnLayoutChangeListener onLayoutChangeListener = null;

    @NonNull
    private final SeekbarPreviewThumbnailHolder seekbarPreviewThumbnailHolder =
            new SeekbarPreviewThumbnailHolder();
    @NonNull
    private final CompositeDisposable bulletCommentsDisposable = new CompositeDisposable();
    @NonNull
    private List<BulletCommentsInfoItem> bulletComments = Collections.emptyList();
    private int nextBulletCommentIndex = 0;
    private long lastBulletCommentPosition = -1L;


    /*//////////////////////////////////////////////////////////////////////////
    // Constructor, setup, destroy
    //////////////////////////////////////////////////////////////////////////*/
    //region Constructor, setup, destroy

    protected VideoPlayerUi(@NonNull final Player player,
                            @NonNull final PlayerBinding playerBinding) {
        super(player);
        binding = playerBinding;
        setupFromView();
    }

    public void setupFromView() {
        initViews();
        initListeners();
        setupPlayerSeekOverlay();
    }

    private void initViews() {
        setupSubtitleView();

        binding.resizeTextView
                .setText(PlayerHelper.resizeTypeOf(context, binding.surfaceView.getResizeMode()));

        tintPlaybackSeekBar();

        tintDrawable(binding.progressBarLoadingPanel.getIndeterminateDrawable(),
                Color.WHITE, PorterDuff.Mode.MULTIPLY);

        binding.titleTextView.setSelected(true);
        binding.channelTextView.setSelected(true);

        // Prevent hiding of bottom sheet via swipe inside queue
        binding.itemsList.setNestedScrollingEnabled(false);
    }

    abstract BasePlayerGestureListener buildGestureListener();

    protected void initListeners() {
        binding.qualityTextView.setOnClickListener(makeOnClickListener(this::onQualityClicked));
        binding.audioTrackTextView.setOnClickListener(
                makeOnClickListener(this::onAudioTracksClicked));
        binding.playbackSpeed.setOnClickListener(makeOnClickListener(this::onPlaybackSpeedClicked));

        binding.playbackSeekBar.setOnSeekBarChangeListener(this);
        binding.captionTextView.setOnClickListener(makeOnClickListener(this::onCaptionClicked));
        binding.resizeTextView.setOnClickListener(makeOnClickListener(this::onResizeClicked));
        binding.playbackLiveSync.setOnClickListener(makeOnClickListener(player::seekToDefault));

        playerGestureListener = buildGestureListener();
        gestureDetector = new GestureDetector(context, playerGestureListener);
        binding.getRoot().setOnTouchListener(playerGestureListener);

        binding.repeatButton.setOnClickListener(v -> onRepeatClicked());
        binding.shuffleButton.setOnClickListener(v -> onShuffleClicked());

        binding.playPauseButton.setOnClickListener(makeOnClickListener(player::playPause));
        binding.playPreviousButton.setOnClickListener(makeOnClickListener(player::playPrevious));
        binding.playNextButton.setOnClickListener(makeOnClickListener(player::playNext));

        binding.moreOptionsButton.setOnClickListener(
                makeOnClickListener(this::onMoreOptionsClicked));
        binding.share.setOnClickListener(makeOnClickListener(() -> {
            final PlayQueueItem currentItem = player.getCurrentItem();
            if (currentItem != null) {
                ShareUtils.shareText(context, currentItem.getTitle(),
                        player.getVideoUrlAtCurrentTime(), currentItem.getThumbnails());
            }
        }));
        binding.share.setOnLongClickListener(v -> {
            ShareUtils.copyToClipboard(context, player.getVideoUrlAtCurrentTime());
            return true;
        });
        binding.fullScreenButton.setOnClickListener(makeOnClickListener(() -> {
            player.setRecovery();
            NavigationHelper.playOnMainPlayer(context,
                    Objects.requireNonNull(player.getPlayQueue()), true);
        }));
        binding.playWithKodi.setOnClickListener(makeOnClickListener(this::onPlayWithKodiClicked));
        binding.openInBrowser.setOnClickListener(makeOnClickListener(this::onOpenInBrowserClicked));
        binding.playerCloseButton.setOnClickListener(makeOnClickListener(() ->
                // set package to this app's package to prevent the intent from being seen outside
                context.sendBroadcast(new Intent(VideoDetailFragment.ACTION_HIDE_MAIN_PLAYER)
                        .setPackage(App.PACKAGE_NAME))
        ));
        binding.switchMute.setOnClickListener(makeOnClickListener(player::toggleMute));

        ViewCompat.setOnApplyWindowInsetsListener(binding.itemsListPanel, (view, windowInsets) -> {
            final Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            if (!cutout.equals(Insets.NONE)) {
                view.setPadding(cutout.left, cutout.top, cutout.right, cutout.bottom);
            }
            return windowInsets;
        });

        // PlaybackControlRoot already consumed window insets but we should pass them to
        // player_overlays and fast_seek_overlay too. Without it they will be off-centered.
        onLayoutChangeListener =
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    binding.playerOverlays.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                            v.getPaddingRight(), v.getPaddingBottom());

                    // If we added padding to the fast seek overlay, too, it would not go under the
                    // system ui. Instead we apply negative margins equal to the window insets of
                    // the opposite side, so that the view covers all of the player (overflowing on
                    // some sides) and its center coincides with the center of other controls.
                    final RelativeLayout.LayoutParams fastSeekParams = (RelativeLayout.LayoutParams)
                            binding.fastSeekOverlay.getLayoutParams();
                    fastSeekParams.leftMargin = -v.getPaddingRight();
                    fastSeekParams.topMargin = -v.getPaddingBottom();
                    fastSeekParams.rightMargin = -v.getPaddingLeft();
                    fastSeekParams.bottomMargin = -v.getPaddingTop();
                };
        binding.playbackControlRoot.addOnLayoutChangeListener(onLayoutChangeListener);
    }

    protected void deinitListeners() {
        binding.qualityTextView.setOnClickListener(null);
        binding.audioTrackTextView.setOnClickListener(null);
        binding.playbackSpeed.setOnClickListener(null);
        binding.playbackSeekBar.setOnSeekBarChangeListener(null);
        binding.captionTextView.setOnClickListener(null);
        binding.resizeTextView.setOnClickListener(null);
        binding.playbackLiveSync.setOnClickListener(null);

        binding.getRoot().setOnTouchListener(null);
        playerGestureListener = null;
        gestureDetector = null;

        binding.repeatButton.setOnClickListener(null);
        binding.shuffleButton.setOnClickListener(null);

        binding.playPauseButton.setOnClickListener(null);
        binding.playPreviousButton.setOnClickListener(null);
        binding.playNextButton.setOnClickListener(null);

        binding.moreOptionsButton.setOnClickListener(null);
        binding.moreOptionsButton.setOnLongClickListener(null);
        binding.share.setOnClickListener(null);
        binding.share.setOnLongClickListener(null);
        binding.fullScreenButton.setOnClickListener(null);
        binding.screenRotationButton.setOnClickListener(null);
        binding.playWithKodi.setOnClickListener(null);
        binding.openInBrowser.setOnClickListener(null);
        binding.playerCloseButton.setOnClickListener(null);
        binding.switchMute.setOnClickListener(null);

        ViewCompat.setOnApplyWindowInsetsListener(binding.itemsListPanel, null);

        binding.playbackControlRoot.removeOnLayoutChangeListener(onLayoutChangeListener);
    }

    /**
     * Initializes the Fast-For/Backward overlay.
     */
    private void setupPlayerSeekOverlay() {
        binding.fastSeekOverlay
                .seekSecondsSupplier(() -> retrieveSeekDurationFromPreferences(player) / 1000)
                .performListener(new PlayerFastSeekOverlay.PerformListener() {

                    @Override
                    public void onDoubleTap() {
                        animate(binding.fastSeekOverlay, true, SEEK_OVERLAY_DURATION);
                    }

                    @Override
                    public void onDoubleTapEnd() {
                        animate(binding.fastSeekOverlay, false, SEEK_OVERLAY_DURATION);
                    }

                    @NonNull
                    @Override
                    public FastSeekDirection getFastSeekDirection(
                            @NonNull final DisplayPortion portion
                    ) {
                        if (player.exoPlayerIsNull()) {
                            // Abort seeking
                            playerGestureListener.endMultiDoubleTap();
                            return FastSeekDirection.NONE;
                        }
                        if (portion == DisplayPortion.LEFT) {
                            // Check if it's possible to rewind
                            // Small puffer to eliminate infinite rewind seeking
                            if (player.getExoPlayer().getCurrentPosition() < 500L) {
                                return FastSeekDirection.NONE;
                            }
                            return FastSeekDirection.BACKWARD;
                        } else if (portion == DisplayPortion.RIGHT) {
                            // Check if it's possible to fast-forward
                            if (player.getCurrentState() == STATE_COMPLETED
                                    || player.getExoPlayer().getCurrentPosition()
                                    >= player.getExoPlayer().getDuration()) {
                                return FastSeekDirection.NONE;
                            }
                            return FastSeekDirection.FORWARD;
                        }
                        /* portion == DisplayPortion.MIDDLE */
                        return FastSeekDirection.NONE;
                    }

                    @Override
                    public void seek(final boolean forward) {
                        playerGestureListener.keepInDoubleTapMode();
                        if (forward) {
                            player.fastForward();
                        } else {
                            player.fastRewind();
                        }
                    }
                });
        playerGestureListener.doubleTapControls(binding.fastSeekOverlay);
    }

    public void deinitPlayerSeekOverlay() {
        binding.fastSeekOverlay
                .seekSecondsSupplier(null)
                .performListener(null);
    }

    @Override
    public void setupAfterIntent() {
        super.setupAfterIntent();
        setupElementsVisibility();
        setupElementsSize(context.getResources());
        binding.getRoot().setVisibility(View.VISIBLE);
        binding.playPauseButton.requestFocus();
    }

    @Override
    public void initPlayer() {
        super.initPlayer();
        setupVideoSurfaceIfNeeded();
    }

    @Override
    public void initPlayback() {
        super.initPlayback();

        // #6825 - Ensure that the shuffle-button is in the correct state on the UI
        setShuffleButton(player.getExoPlayer().getShuffleModeEnabled());
    }

    public abstract void removeViewFromParent();

    @Override
    public void destroyPlayer() {
        super.destroyPlayer();
        clearVideoSurface();
    }

    @Override
    public void destroy() {
        super.destroy();
        clearBulletComments();
        bulletCommentsDisposable.clear();
        binding.endScreen.setImageDrawable(null);
        deinitPlayerSeekOverlay();
        deinitListeners();
    }

    protected void setupElementsVisibility() {
        setMuteButton(player.isMuted());
        animateRotation(binding.moreOptionsButton, DEFAULT_CONTROLS_DURATION, 0);
    }

    protected abstract void setupElementsSize(Resources resources);

    protected void setupElementsSize(final int buttonsMinWidth,
                                     final int playerTopPad,
                                     final int controlsPad,
                                     final int buttonsPad) {
        binding.topControls.setPaddingRelative(controlsPad, playerTopPad, controlsPad, 0);
        binding.bottomControls.setPaddingRelative(controlsPad, 0, controlsPad, 0);
        binding.qualityTextView.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad);
        binding.audioTrackTextView.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad);
        binding.playbackSpeed.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad);
        binding.playbackSpeed.setMinimumWidth(buttonsMinWidth);
        binding.captionTextView.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad);
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Broadcast receiver
    //////////////////////////////////////////////////////////////////////////*/
    //region Broadcast receiver

    @Override
    public void onBroadcastReceived(final Intent intent) {
        super.onBroadcastReceived(intent);
        if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
            // When the orientation changes, the screen height might be smaller. If the end screen
            // thumbnail is not re-scaled, it can be larger than the current screen height and thus
            // enlarging the whole player. This causes the seekbar to be out of the visible area.
            updateEndScreenThumbnail(player.getThumbnail());
        }
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Thumbnail
    //////////////////////////////////////////////////////////////////////////*/
    //region Thumbnail

    /**
     * Scale the player audio / end screen thumbnail down if necessary.
     * <p>
     * This is necessary when the thumbnail's height is larger than the device's height
     * and thus is enlarging the player's height
     * causing the bottom playback controls to be out of the visible screen.
     * </p>
     */
    @Override
    public void onThumbnailLoaded(@Nullable final Bitmap bitmap) {
        super.onThumbnailLoaded(bitmap);
        updateEndScreenThumbnail(bitmap);
    }

    private void updateEndScreenThumbnail(@Nullable final Bitmap thumbnail) {
        if (thumbnail == null) {
            // remove end screen thumbnail
            binding.endScreen.setImageDrawable(null);
            return;
        }

        final float endScreenHeight = calculateMaxEndScreenThumbnailHeight(thumbnail);
        final Bitmap endScreenBitmap = BitmapCompat.createScaledBitmap(
                thumbnail,
                (int) (thumbnail.getWidth() / (thumbnail.getHeight() / endScreenHeight)),
                (int) endScreenHeight,
                null,
                true);

        if (DEBUG) {
            Log.d(TAG, "Thumbnail - onThumbnailLoaded() called with: "
                    + "currentThumbnail = [" + thumbnail + "], "
                    + thumbnail.getWidth() + "x" + thumbnail.getHeight()
                    + ", scaled end screen height = " + endScreenHeight
                    + ", scaled end screen width = " + endScreenBitmap.getWidth());
        }

        binding.endScreen.setImageBitmap(endScreenBitmap);
    }

    protected abstract float calculateMaxEndScreenThumbnailHeight(@NonNull Bitmap bitmap);
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Progress loop and updates
    //////////////////////////////////////////////////////////////////////////*/
    //region Progress loop and updates

    @Override
    public void onUpdateProgress(final int currentProgress,
                                 final int duration,
                                 final int bufferPercent) {

        if (duration != binding.playbackSeekBar.getMax()) {
            setVideoDurationToControls(duration);
        }
        if (player.getCurrentState() != STATE_PAUSED) {
            updatePlayBackElementsCurrentDuration(currentProgress);
        }
        if (player.isLoading() || bufferPercent > 90) {
            binding.playbackSeekBar.setSecondaryProgress(
                    (int) (binding.playbackSeekBar.getMax() * ((float) bufferPercent / 100)));
        }
        if (DEBUG && bufferPercent % 20 == 0) { //Limit log
            Log.d(TAG, "notifyProgressUpdateToListeners() called with: "
                    + "isVisible = " + isControlsVisible() + ", "
                    + "currentProgress = [" + currentProgress + "], "
                    + "duration = [" + duration + "], bufferPercent = [" + bufferPercent + "]");
        }
        binding.playbackLiveSync.setClickable(!player.isLiveEdge());
        maybeShowBulletComments(currentProgress);
    }

    /**
     * Sets the current duration into the corresponding elements.
     *
     * @param currentProgress the current progress, in milliseconds
     */
    private void updatePlayBackElementsCurrentDuration(final int currentProgress) {
        // Don't set seekbar progress while user is seeking
        if (player.getCurrentState() != STATE_PAUSED_SEEK) {
            binding.playbackSeekBar.setProgress(currentProgress);
        }
        binding.playbackCurrentTime.setText(getTimeString(currentProgress));
    }

    /**
     * Sets the video duration time into all control components (e.g. seekbar).
     *
     * @param duration the video duration, in milliseconds
     */
    private void setVideoDurationToControls(final int duration) {
        binding.playbackEndTime.setText(getTimeString(duration));

        binding.playbackSeekBar.setMax(duration);
        // This is important for Android TVs otherwise it would apply the default from
        // setMax/Min methods which is (max - min) / 20
        binding.playbackSeekBar.setKeyProgressIncrement(
                PlayerHelper.retrieveSeekDurationFromPreferences(player));
    }

    @Override // seekbar listener
    public void onProgressChanged(final SeekBar seekBar, final int progress,
                                  final boolean fromUser) {
        // Currently we don't need method execution when fromUser is false
        if (!fromUser) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "onProgressChanged() called with: "
                    + "seekBar = [" + seekBar + "], progress = [" + progress + "]");
        }

        binding.currentDisplaySeek.setText(getTimeString(progress));

        // Seekbar Preview Thumbnail
        SeekbarPreviewThumbnailHelper
                .tryResizeAndSetSeekbarPreviewThumbnail(
                        player.getContext(),
                        seekbarPreviewThumbnailHolder.getBitmapAt(progress).orElse(null),
                        binding.currentSeekbarPreviewThumbnail,
                        binding.subtitleView::getWidth);

        adjustSeekbarPreviewContainer();
    }


    private void adjustSeekbarPreviewContainer() {
        try {
            // Should only be required when an error occurred before
            // and the layout was positioned in the center
            binding.bottomSeekbarPreviewLayout.setGravity(Gravity.NO_GRAVITY);

            // Calculate the current left position of seekbar progress in px
            // More info: https://stackoverflow.com/q/20493577
            final int currentSeekbarLeft =
                    binding.playbackSeekBar.getLeft()
                            + binding.playbackSeekBar.getPaddingLeft()
                            + binding.playbackSeekBar.getThumb().getBounds().left;

            // Calculate the (unchecked) left position of the container
            final int uncheckedContainerLeft =
                    currentSeekbarLeft - (binding.seekbarPreviewContainer.getWidth() / 2);

            // Fix the position so it's within the boundaries
            final int checkedContainerLeft = MathUtils.clamp(uncheckedContainerLeft,
                    0, binding.playbackWindowRoot.getWidth()
                            - binding.seekbarPreviewContainer.getWidth());

            // See also: https://stackoverflow.com/a/23249734
            final LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            binding.seekbarPreviewContainer.getLayoutParams());
            params.setMarginStart(checkedContainerLeft);
            binding.seekbarPreviewContainer.setLayoutParams(params);
        } catch (final Exception ex) {
            Log.e(TAG, "Failed to adjust seekbarPreviewContainer", ex);
            // Fallback - position in the middle
            binding.bottomSeekbarPreviewLayout.setGravity(Gravity.CENTER);
        }
    }

    @Override // seekbar listener
    public void onStartTrackingTouch(final SeekBar seekBar) {
        if (DEBUG) {
            Log.d(TAG, "onStartTrackingTouch() called with: seekBar = [" + seekBar + "]");
        }
        if (player.getCurrentState() != STATE_PAUSED_SEEK) {
            player.changeState(STATE_PAUSED_SEEK);
        }

        showControls(0);
        animate(binding.currentDisplaySeek, true, DEFAULT_CONTROLS_DURATION,
                AnimationType.SCALE_AND_ALPHA);
        animate(binding.currentSeekbarPreviewThumbnail, true, DEFAULT_CONTROLS_DURATION,
                AnimationType.SCALE_AND_ALPHA);
    }

    @Override // seekbar listener
    public void onStopTrackingTouch(final SeekBar seekBar) {
        if (DEBUG) {
            Log.d(TAG, "onStopTrackingTouch() called with: seekBar = [" + seekBar + "]");
        }

        player.seekTo(seekBar.getProgress());
        if (player.getExoPlayer().getDuration() == seekBar.getProgress()) {
            player.getExoPlayer().play();
        }

        binding.playbackCurrentTime.setText(getTimeString(seekBar.getProgress()));
        animate(binding.currentDisplaySeek, false, 200, AnimationType.SCALE_AND_ALPHA);
        animate(binding.currentSeekbarPreviewThumbnail, false, 200, AnimationType.SCALE_AND_ALPHA);

        if (player.getCurrentState() == STATE_PAUSED_SEEK) {
            player.changeState(STATE_BUFFERING);
        }
        if (!player.isProgressLoopRunning()) {
            player.startProgressLoop();
        }

        showControlsThenHide();
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Controls showing / hiding
    //////////////////////////////////////////////////////////////////////////*/
    //region Controls showing / hiding

    public boolean isControlsVisible() {
        return binding != null && binding.playbackControlRoot.getVisibility() == View.VISIBLE;
    }

    public void showControlsThenHide() {
        if (DEBUG) {
            Log.d(TAG, "showControlsThenHide() called");
        }

        showOrHideButtons();
        showSystemUIPartially();

        final long hideTime = binding.playbackControlRoot.isInTouchMode()
                ? DEFAULT_CONTROLS_HIDE_TIME
                : DPAD_CONTROLS_HIDE_TIME;

        showHideShadow(true, DEFAULT_CONTROLS_DURATION);
        animate(binding.playbackControlRoot, true, DEFAULT_CONTROLS_DURATION,
                AnimationType.ALPHA, 0, () -> hideControls(DEFAULT_CONTROLS_DURATION, hideTime));
    }

    public void showControls(final long duration) {
        if (DEBUG) {
            Log.d(TAG, "showControls() called");
        }
        showOrHideButtons();
        showSystemUIPartially();
        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        showHideShadow(true, duration);
        animate(binding.playbackControlRoot, true, duration);
    }

    public void hideControls(final long duration, final long delay) {
        if (DEBUG) {
            Log.d(TAG, "hideControls() called with: duration = [" + duration
                    + "], delay = [" + delay + "]");
        }

        showOrHideButtons();

        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        controlsVisibilityHandler.postDelayed(() -> {
            showHideShadow(false, duration);
            animate(binding.playbackControlRoot, false, duration, AnimationType.ALPHA,
                    0, this::hideSystemUIIfNeeded);
        }, delay);
    }

    public void showHideShadow(final boolean show, final long duration) {
        animate(binding.playbackControlsShadow, show, duration, AnimationType.ALPHA, 0, null);
        animate(binding.playerTopShadow, show, duration, AnimationType.ALPHA, 0, null);
        animate(binding.playerBottomShadow, show, duration, AnimationType.ALPHA, 0, null);
    }

    protected void showOrHideButtons() {
        @Nullable final PlayQueue playQueue = player.getPlayQueue();
        if (playQueue == null) {
            return;
        }

        final boolean showPrev = playQueue.getIndex() != 0;
        final boolean showNext = playQueue.getIndex() + 1 != playQueue.getStreams().size();

        binding.playPreviousButton.setVisibility(showPrev ? View.VISIBLE : View.INVISIBLE);
        binding.playPreviousButton.setAlpha(showPrev ? 1.0f : 0.0f);
        binding.playNextButton.setVisibility(showNext ? View.VISIBLE : View.INVISIBLE);
        binding.playNextButton.setAlpha(showNext ? 1.0f : 0.0f);
    }

    protected void showSystemUIPartially() {
        // system UI is really changed only by MainPlayerUi, so overridden there
    }

    protected void hideSystemUIIfNeeded() {
        // system UI is really changed only by MainPlayerUi, so overridden there
    }

    protected boolean isAnyListViewOpen() {
        // only MainPlayerUi has list views for the queue and for segments, so overridden there
        return false;
    }

    public boolean isFullscreen() {
        // only MainPlayerUi can be in fullscreen, so overridden there
        return false;
    }

    /**
     * Update the play/pause button ({@link R.id.playPauseButton}) to reflect the action
     * that will be performed when the button is clicked..
     * @param action the action that is performed when the play/pause button is clicked
     */
    private void updatePlayPauseButton(final PlayButtonAction action) {
        final PilotIconButton button = binding.playPauseButton;
        switch (action) {
            case PLAY:
                button.setContentDescription(context.getString(R.string.play));
                button.setImageResource(R.drawable.ic_play_arrow);
                break;
            case PAUSE:
                button.setContentDescription(context.getString(R.string.pause));
                button.setImageResource(R.drawable.ic_pause);
                break;
            case REPLAY:
                button.setContentDescription(context.getString(R.string.replay));
                button.setImageResource(R.drawable.ic_replay);
                break;
        }
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Playback states
    //////////////////////////////////////////////////////////////////////////*/
    //region Playback states

    @Override
    public void onPrepared() {
        super.onPrepared();
        setVideoDurationToControls((int) player.getExoPlayer().getDuration());
        binding.playbackSpeed.setText(formatSpeed(player.getPlaybackSpeed()));
    }

    @Override
    public void onBlocked() {
        super.onBlocked();
        binding.bulletCommentsOverlay.reset();

        // if we are e.g. switching players, hide controls
        hideControls(DEFAULT_CONTROLS_DURATION, 0);

        binding.playbackSeekBar.setEnabled(false);
        tintPlaybackSeekBar();

        binding.loadingPanel.setBackgroundColor(Color.BLACK);
        animate(binding.loadingPanel, true, 0);
        animate(binding.surfaceForeground, true, 100);

        updatePlayPauseButton(PlayButtonAction.PLAY);
        animatePlayButtons(false, 100);
        binding.getRoot().setKeepScreenOn(false);
    }

    @Override
    public void onPlaying() {
        super.onPlaying();

        updateStreamRelatedViews();

        binding.playbackSeekBar.setEnabled(true);
        tintPlaybackSeekBar();

        binding.loadingPanel.setVisibility(View.GONE);

        animate(binding.currentDisplaySeek, false, 200, AnimationType.SCALE_AND_ALPHA);

        animate(binding.playPauseButton, false, 80, AnimationType.SCALE_AND_ALPHA, 0,
                () -> {
                    updatePlayPauseButton(PlayButtonAction.PAUSE);
                    animatePlayButtons(true, 200);
                    if (!isAnyListViewOpen()) {
                        binding.playPauseButton.requestFocus();
                    }
                });

        binding.getRoot().setKeepScreenOn(true);
    }

    private void tintPlaybackSeekBar() {
        final ColorStateList thumbTint = ColorStateList.valueOf(Color.RED);
        binding.playbackSeekBar.setThumbTintList(thumbTint);
        binding.playbackSeekBar.setHaloTintList(ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(Color.RED, 72)));
        binding.playbackSeekBar.setTrackActiveTintList(thumbTint);
        binding.playbackSeekBar.setTrackInactiveTintList(ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(Color.RED, 72)));
        binding.playbackSeekBar.setSecondaryProgressTintList(ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(Color.RED, 144)));
    }

    private void tintDrawable(@Nullable final Drawable drawable,
                              final int color,
                              @NonNull final PorterDuff.Mode mode) {
        if (drawable == null) {
            return;
        }

        drawable.setColorFilter(new PorterDuffColorFilter(color, mode));
    }

    @Override
    public void onBuffering() {
        super.onBuffering();
        binding.loadingPanel.setBackgroundColor(Color.TRANSPARENT);
        binding.loadingPanel.setVisibility(View.VISIBLE);
        binding.getRoot().setKeepScreenOn(true);
    }

    @Override
    public void onPaused() {
        super.onPaused();
        binding.bulletCommentsOverlay.reset();

        // Don't let UI elements popup during double tap seeking. This state is entered sometimes
        // during seeking/loading. This if-else check ensures that the controls aren't popping up.
        if (!playerGestureListener.isDoubleTapping()) {
            showControls(400);
            binding.loadingPanel.setVisibility(View.GONE);

            animate(binding.playPauseButton, false, 80, AnimationType.SCALE_AND_ALPHA, 0,
                    () -> {
                        updatePlayPauseButton(PlayButtonAction.PLAY);
                        animatePlayButtons(true, 200);
                        if (!isAnyListViewOpen()) {
                            binding.playPauseButton.requestFocus();
                        }
                    });
        }

        binding.getRoot().setKeepScreenOn(false);
    }

    @Override
    public void onPausedSeek() {
        super.onPausedSeek();
        animatePlayButtons(false, 100);
        binding.bulletCommentsOverlay.reset();
        binding.getRoot().setKeepScreenOn(true);
    }

    @Override
    public void onCompleted() {
        super.onCompleted();
        binding.bulletCommentsOverlay.reset();

        animate(binding.playPauseButton, false, 0, AnimationType.SCALE_AND_ALPHA, 0,
                () -> {
                    updatePlayPauseButton(PlayButtonAction.REPLAY);
                    animatePlayButtons(true, DEFAULT_CONTROLS_DURATION);
                });

        binding.getRoot().setKeepScreenOn(false);

        // When a (short) video ends the elements have to display the correct values - see #6180
        updatePlayBackElementsCurrentDuration(binding.playbackSeekBar.getMax());

        showControls(500);
        animate(binding.currentDisplaySeek, false, 200, AnimationType.SCALE_AND_ALPHA);
        binding.loadingPanel.setVisibility(View.GONE);
        animate(binding.surfaceForeground, true, 100);
    }

    private void animatePlayButtons(final boolean show, final long duration) {
        animate(binding.playPauseButton, show, duration, AnimationType.SCALE_AND_ALPHA);

        @Nullable final PlayQueue playQueue = player.getPlayQueue();
        if (playQueue == null) {
            return;
        }

        if (!show || playQueue.getIndex() > 0) {
            animate(
                    binding.playPreviousButton,
                    show,
                    duration,
                    AnimationType.SCALE_AND_ALPHA);
        }
        if (!show || playQueue.getIndex() + 1 < playQueue.getStreams().size()) {
            animate(
                    binding.playNextButton,
                    show,
                    duration,
                    AnimationType.SCALE_AND_ALPHA);
        }
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Repeat, shuffle, mute
    //////////////////////////////////////////////////////////////////////////*/
    //region Repeat, shuffle, mute

    public void onRepeatClicked() {
        if (DEBUG) {
            Log.d(TAG, "onRepeatClicked() called");
        }
        player.cycleNextRepeatMode();
    }

    public void onShuffleClicked() {
        if (DEBUG) {
            Log.d(TAG, "onShuffleClicked() called");
        }
        player.toggleShuffleModeEnabled();
    }

    @Override
    public void onRepeatModeChanged(@RepeatMode final int repeatMode) {
        super.onRepeatModeChanged(repeatMode);

        if (repeatMode == REPEAT_MODE_ALL) {
            binding.repeatButton.setImageResource(
                    com.google.android.exoplayer2.ui.R.drawable.exo_controls_repeat_all);
        } else if (repeatMode == REPEAT_MODE_ONE) {
            binding.repeatButton.setImageResource(
                    com.google.android.exoplayer2.ui.R.drawable.exo_controls_repeat_one);
        } else /* repeatMode == REPEAT_MODE_OFF */ {
            binding.repeatButton.setImageResource(
                    com.google.android.exoplayer2.ui.R.drawable.exo_controls_repeat_off);
        }
    }

    @Override
    public void onShuffleModeEnabledChanged(final boolean shuffleModeEnabled) {
        super.onShuffleModeEnabledChanged(shuffleModeEnabled);
        setShuffleButton(shuffleModeEnabled);
    }

    @Override
    public void onMuteUnmuteChanged(final boolean isMuted) {
        super.onMuteUnmuteChanged(isMuted);
        setMuteButton(isMuted);
    }

    private void setMuteButton(final boolean isMuted) {
        binding.switchMute.setImageDrawable(AppCompatResources.getDrawable(context, isMuted
                ? R.drawable.ic_volume_off : R.drawable.ic_volume_up));
    }

    private void setShuffleButton(final boolean shuffled) {
        binding.shuffleButton.setImageAlpha(shuffled ? 255 : 77);
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Other player listeners
    //////////////////////////////////////////////////////////////////////////*/
    //region Other player listeners

    @Override
    public void onPlaybackParametersChanged(@NonNull final PlaybackParameters playbackParameters) {
        super.onPlaybackParametersChanged(playbackParameters);
        binding.playbackSpeed.setText(formatSpeed(playbackParameters.speed));
    }

    @Override
    public void onRenderedFirstFrame() {
        super.onRenderedFirstFrame();
        //TODO check if this causes black screen when switching to fullscreen
        animate(binding.surfaceForeground, false, DEFAULT_CONTROLS_DURATION);
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Metadata & stream related views
    //////////////////////////////////////////////////////////////////////////*/
    //region Metadata & stream related views

    @Override
    public void onMetadataChanged(@NonNull final StreamInfo info) {
        super.onMetadataChanged(info);

        updateStreamRelatedViews();

        binding.titleTextView.setText(info.getName());
        binding.channelTextView.setText(info.getUploaderName());

        this.seekbarPreviewThumbnailHolder.resetFrom(player.getContext(), info.getPreviewFrames());
        SponsorBlockHelper.markSegments(
                player.getContext(), (MarkableSeekBar) binding.playbackSeekBar, info);
        loadBulletComments(info);
    }

    private void loadBulletComments(@NonNull final StreamInfo info) {
        clearBulletComments();
        bulletCommentsDisposable.clear();

        bulletCommentsDisposable.add(
                ExtractorHelper.getBulletCommentsInfo(info.getServiceId(), info.getUrl(), true)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                bulletCommentsInfo -> onBulletCommentsLoaded(info,
                                        bulletCommentsInfo),
                                throwable -> binding.bulletCommentsOverlay.setVisibility(
                                        View.GONE)));
    }

    private void onBulletCommentsLoaded(@NonNull final StreamInfo info,
                                        @Nullable final BulletCommentsInfo bulletCommentsInfo) {
        if (bulletCommentsInfo == null || bulletCommentsInfo.getRelatedItems() == null
                || bulletCommentsInfo.getRelatedItems().isEmpty() || info.getDuration() <= 0) {
            binding.bulletCommentsOverlay.setVisibility(View.GONE);
            return;
        }

        bulletComments = bulletCommentsInfo.getRelatedItems()
                .stream()
                .filter(item -> item.getDuration() != null)
                .sorted()
                .collect(Collectors.toList());
        nextBulletCommentIndex = 0;
        lastBulletCommentPosition = -1L;
        binding.bulletCommentsOverlay.setVisibility(View.VISIBLE);
    }

    private void maybeShowBulletComments(final int currentProgress) {
        if (bulletComments.isEmpty()) {
            return;
        }

        if (lastBulletCommentPosition > currentProgress + Player.PROGRESS_LOOP_INTERVAL_MILLIS) {
            binding.bulletCommentsOverlay.reset();
            nextBulletCommentIndex = findFirstBulletCommentIndexAtOrAfter(currentProgress);
        }

        while (nextBulletCommentIndex < bulletComments.size()) {
            final BulletCommentsInfoItem item = bulletComments.get(nextBulletCommentIndex);
            final long scheduledTimeMillis = item.getDuration().toMillis();
            if (scheduledTimeMillis > currentProgress) {
                break;
            }
            if (scheduledTimeMillis > lastBulletCommentPosition) {
                binding.bulletCommentsOverlay.showBulletComment(item);
            }
            nextBulletCommentIndex++;
        }

        lastBulletCommentPosition = currentProgress;
    }

    private int findFirstBulletCommentIndexAtOrAfter(final int currentProgress) {
        int left = 0;
        int right = bulletComments.size();
        while (left < right) {
            final int middle = (left + right) / 2;
            final long scheduledTimeMillis = bulletComments.get(middle).getDuration().toMillis();
            if (scheduledTimeMillis < currentProgress) {
                left = middle + 1;
            } else {
                right = middle;
            }
        }
        return left;
    }

    private void clearBulletComments() {
        bulletComments = Collections.emptyList();
        nextBulletCommentIndex = 0;
        lastBulletCommentPosition = -1L;
        binding.bulletCommentsOverlay.reset();
        binding.bulletCommentsOverlay.setVisibility(View.GONE);
    }

    private void updateStreamRelatedViews() {
        player.getCurrentStreamInfo().ifPresent(info -> {
            binding.qualityTextView.setVisibility(View.GONE);
            binding.audioTrackTextView.setVisibility(View.GONE);
            binding.playbackSpeed.setVisibility(View.GONE);

            binding.playbackEndTime.setVisibility(View.GONE);
            binding.playbackLiveSync.setVisibility(View.GONE);

            switch (info.getStreamType()) {
                case AUDIO_STREAM:
                case POST_LIVE_AUDIO_STREAM:
                    binding.surfaceView.setVisibility(View.GONE);
                    binding.endScreen.setVisibility(View.VISIBLE);
                    binding.playbackEndTime.setVisibility(View.VISIBLE);
                    break;

                case AUDIO_LIVE_STREAM:
                    binding.surfaceView.setVisibility(View.GONE);
                    binding.endScreen.setVisibility(View.VISIBLE);
                    binding.playbackLiveSync.setVisibility(View.VISIBLE);
                    break;

                case LIVE_STREAM:
                    binding.surfaceView.setVisibility(View.VISIBLE);
                    binding.endScreen.setVisibility(View.GONE);
                    binding.playbackLiveSync.setVisibility(View.VISIBLE);
                    break;

                case VIDEO_STREAM:
                case POST_LIVE_STREAM:
                    if (player.getCurrentMetadata() != null
                            && player.getCurrentMetadata().getMaybeQuality().isEmpty()
                            || (info.getVideoStreams().isEmpty()
                            && info.getVideoOnlyStreams().isEmpty())) {
                        break;
                    }

                    buildQualityMenu();
                    buildAudioTrackMenu();

                    binding.qualityTextView.setVisibility(View.VISIBLE);
                    binding.surfaceView.setVisibility(View.VISIBLE);
                    // fallthrough
                default:
                    binding.endScreen.setVisibility(View.GONE);
                    binding.playbackEndTime.setVisibility(View.VISIBLE);
                    break;
            }

            buildPlaybackSpeedMenu();
            binding.playbackSpeed.setVisibility(View.VISIBLE);
        });
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Action sheets
    //////////////////////////////////////////////////////////////////////////*/
    //region Action sheets

    private void buildQualityMenu() {
        final List<VideoStream> availableStreams = getAvailableVideoStreams();
        final int selectedStreamIndex = getSelectedVideoStreamIndex(availableStreams);
        if (selectedStreamIndex >= 0 && selectedStreamIndex < availableStreams.size()) {
            binding.qualityTextView.setText(availableStreams.get(selectedStreamIndex)
                    .getResolution());
        } else if (!availableStreams.isEmpty()) {
            binding.qualityTextView.setText(availableStreams.get(0).getResolution());
        }
    }

    private void buildAudioTrackMenu() {
        final List<AudioStream> availableStreams = Optional.ofNullable(player.getCurrentMetadata())
                .flatMap(MediaItemTag::getMaybeAudioTrack)
                .map(MediaItemTag.AudioTrack::getAudioStreams)
                .orElse(null);
        if (availableStreams == null || availableStreams.size() < 2) {
            binding.audioTrackTextView.setVisibility(View.GONE);
            return;
        }

        player.getSelectedAudioStream()
                .ifPresent(s -> binding.audioTrackTextView.setText(
                        Localization.audioTrackName(context, s)));
        binding.audioTrackTextView.setVisibility(View.VISIBLE);
    }

    private void buildPlaybackSpeedMenu() {
        binding.playbackSpeed.setText(formatSpeed(player.getPlaybackSpeed()));
    }

    private void buildCaptionMenu(@NonNull final List<String> availableLanguages) {
        // apply caption language from previous user preference
        final int textRendererIndex = player.getCaptionRendererIndex();
        if (textRendererIndex == RENDERER_UNAVAILABLE) {
            return;
        }

        // If user prefers to show no caption, then disable the renderer.
        // Otherwise, DefaultTrackSelector may automatically find an available caption
        // and display that.
        final String userPreferredLanguage =
                player.getPrefs().getString(context.getString(R.string.caption_user_set_key), null);
        if (userPreferredLanguage == null) {
            player.getTrackSelector().setParameters(player.getTrackSelector().buildUponParameters()
                    .setRendererDisabled(textRendererIndex, true));
            return;
        }

        // Only set preferred language if it does not match the user preference,
        // otherwise there might be an infinite cycle at onTextTracksChanged.
        final List<String> selectedPreferredLanguages =
                player.getTrackSelector().getParameters().preferredTextLanguages;
        if (!selectedPreferredLanguages.contains(userPreferredLanguage)) {
            player.getTrackSelector().setParameters(player.getTrackSelector().buildUponParameters()
                    .setPreferredTextLanguages(userPreferredLanguage,
                            PlayerHelper.captionLanguageStemOf(userPreferredLanguage))
                    .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION)
                    .setRendererDisabled(textRendererIndex, false));
        }
    }

    protected final void showPlaybackSpeedActionSheet() {
        final List<MaterialActionSheetDialog.ActionItem> actionItems = new ArrayList<>();
        final float currentSpeed = player.getPlaybackSpeed();
        for (int i = 0; i < PLAYBACK_SPEEDS.length; i++) {
            final float speed = PLAYBACK_SPEEDS[i];
            actionItems.add(MaterialActionSheetDialog.ActionItem.checked(
                    POPUP_MENU_ID_PLAYBACK_SPEED + i,
                    formatSpeed(speed),
                    0,
                    Math.abs(currentSpeed - speed) < 0.001f,
                    () -> {
                        player.setPlaybackSpeed(speed);
                        binding.playbackSpeed.setText(formatSpeed(speed));
                    }));
        }
        showActionSheet(binding.playbackSpeed.getText(), actionItems);
    }

    protected abstract void onPlaybackSpeedClicked();

    private void onQualityClicked() {
        final List<VideoStream> availableStreams = getAvailableVideoStreams();
        if (availableStreams.isEmpty()) {
            return;
        }
        final int selectedStreamIndex = getSelectedVideoStreamIndex(availableStreams);
        final List<MaterialActionSheetDialog.ActionItem> actionItems = new ArrayList<>();
        for (int i = 0; i < availableStreams.size(); i++) {
            final VideoStream videoStream = availableStreams.get(i);
            final int streamIndex = i;
            actionItems.add(MaterialActionSheetDialog.ActionItem.checked(
                    POPUP_MENU_ID_QUALITY + i,
                    buildQualityActionTitle(videoStream),
                    0,
                    selectedStreamIndex == i,
                    () -> onQualityItemClick(streamIndex)));
        }
        showActionSheet(binding.qualityTextView.getText(), actionItems);
    }

    private void onAudioTracksClicked() {
        @Nullable final MediaItemTag currentMetadata = player.getCurrentMetadata();
        if (currentMetadata == null || currentMetadata.getMaybeAudioTrack().isEmpty()) {
            return;
        }
        final MediaItemTag.AudioTrack audioTrack = currentMetadata.getMaybeAudioTrack().get();
        final List<AudioStream> availableStreams = audioTrack.getAudioStreams();
        if (availableStreams.size() < 2) {
            return;
        }
        final int selectedStreamIndex = audioTrack.getSelectedAudioStreamIndex();
        final List<MaterialActionSheetDialog.ActionItem> actionItems = new ArrayList<>();
        for (int i = 0; i < availableStreams.size(); i++) {
            final String title = Localization.audioTrackName(context, availableStreams.get(i));
            final int streamIndex = i;
            actionItems.add(MaterialActionSheetDialog.ActionItem.checked(
                    POPUP_MENU_ID_AUDIO_TRACK + i,
                    title,
                    0,
                    selectedStreamIndex == i,
                    () -> onAudioTrackItemClick(streamIndex, title)));
        }
        showActionSheet(binding.audioTrackTextView.getText(), actionItems);
    }

    private void onQualityItemClick(final int menuItemIndex) {
        final List<VideoStream> availableStreams = getAvailableVideoStreams();
        final int selectedStreamIndex = getSelectedVideoStreamIndex(availableStreams);
        if (selectedStreamIndex == menuItemIndex || availableStreams.size() <= menuItemIndex) {
            return;
        }

        final VideoStream selectedStream = availableStreams.get(menuItemIndex);
        player.setPlaybackQuality(selectedStream);
        binding.qualityTextView.setText(selectedStream.getResolution());
    }

    @NonNull
    private String buildQualityActionTitle(@NonNull final VideoStream videoStream) {
        final String formatName = MediaFormat.getNameById(videoStream.getFormatId());
        return formatName.isEmpty()
                ? videoStream.getResolution()
                : formatName + " " + videoStream.getResolution();
    }

    private void onAudioTrackItemClick(final int menuItemIndex,
                                       @NonNull final CharSequence title) {
        @Nullable final MediaItemTag currentMetadata = player.getCurrentMetadata();
        if (currentMetadata == null || currentMetadata.getMaybeAudioTrack().isEmpty()) {
            return;
        }

        final MediaItemTag.AudioTrack audioTrack =
                currentMetadata.getMaybeAudioTrack().get();
        final List<AudioStream> availableStreams = audioTrack.getAudioStreams();
        final int selectedStreamIndex = audioTrack.getSelectedAudioStreamIndex();
        if (selectedStreamIndex == menuItemIndex || availableStreams.size() <= menuItemIndex) {
            return;
        }

        final String newAudioTrack = availableStreams.get(menuItemIndex).getAudioTrackId();
        player.setAudioTrack(newAudioTrack);

        binding.audioTrackTextView.setText(title);
    }

    private void onActionSheetDismissed() {
        if (DEBUG) {
            Log.d(TAG, "onActionSheetDismissed() called");
        }
        isSomeActionSheetVisible = false;
        actionSheetDialog = null;

        if (player.isPlaying()) {
            hideControls(DEFAULT_CONTROLS_DURATION, 0);
            hideSystemUIIfNeeded();
        }
    }

    @NonNull
    private List<VideoStream> getAvailableVideoStreams() {
        @Nullable final MediaItemTag currentMetadata = player.getCurrentMetadata();
        if (currentMetadata != null && currentMetadata.getMaybeQuality().isPresent()) {
            return currentMetadata.getMaybeQuality().get().getSortedVideoStreams();
        }

        return player.getCurrentStreamInfo()
                .map(info -> ListHelper.getSortedStreamVideosList(
                        context,
                        ListHelper.getPlayableStreams(info.getVideoStreams(), info.getServiceId()),
                        ListHelper.getPlayableStreams(
                                info.getVideoOnlyStreams(),
                                info.getServiceId()),
                        false,
                        true))
                .orElse(Collections.emptyList());
    }

    private int getSelectedVideoStreamIndex(@NonNull final List<VideoStream> availableStreams) {
        @Nullable final MediaItemTag currentMetadata = player.getCurrentMetadata();
        if (currentMetadata == null || currentMetadata.getMaybeQuality().isEmpty()) {
            return -1;
        }

        final int selectedStreamIndex =
                currentMetadata.getMaybeQuality().get().getSelectedVideoStreamIndex();
        return selectedStreamIndex >= 0 && selectedStreamIndex < availableStreams.size()
                ? selectedStreamIndex
                : -1;
    }

    private void onCaptionClicked() {
        if (DEBUG) {
            Log.d(TAG, "onCaptionClicked() called");
        }

        final Tracks currentTracks = player.getExoPlayer().getCurrentTracks();
        final List<String> availableLanguages = currentTracks
                .getGroups()
                .stream()
                .filter(trackGroupInfo -> C.TRACK_TYPE_TEXT == trackGroupInfo.getType())
                .map(Tracks.Group::getMediaTrackGroup)
                .filter(textTrack -> textTrack.length > 0)
                .map(textTrack -> textTrack.getFormat(0).language)
                .collect(Collectors.toList());
        if (availableLanguages.isEmpty()) {
            return;
        }

        final String userPreferredLanguage =
                player.getPrefs().getString(context.getString(R.string.caption_user_set_key), null);
        final List<MaterialActionSheetDialog.ActionItem> actionItems = new ArrayList<>();
        actionItems.add(MaterialActionSheetDialog.ActionItem.checked(
                POPUP_MENU_ID_CAPTION,
                context.getString(R.string.caption_none),
                0,
                userPreferredLanguage == null,
                this::disableCaptionRenderer));
        for (int i = 0; i < availableLanguages.size(); i++) {
            final String captionLanguage = availableLanguages.get(i);
            actionItems.add(MaterialActionSheetDialog.ActionItem.checked(
                    POPUP_MENU_ID_CAPTION + i + 1,
                    captionLanguage,
                    0,
                    captionLanguage.equals(userPreferredLanguage),
                    () -> enableCaptionLanguage(captionLanguage)));
        }
        showActionSheet(binding.captionTextView.getText(), actionItems);
    }

    public boolean isSomeActionSheetVisible() {
        return isSomeActionSheetVisible;
    }

    private void disableCaptionRenderer() {
        final int textRendererIndex = player.getCaptionRendererIndex();
        if (textRendererIndex != RENDERER_UNAVAILABLE) {
            player.getTrackSelector().setParameters(player.getTrackSelector()
                    .buildUponParameters().setRendererDisabled(textRendererIndex, true));
        }
        player.getPrefs().edit().remove(context.getString(R.string.caption_user_set_key)).apply();
        binding.captionTextView.setText(R.string.caption_none);
    }

    private void enableCaptionLanguage(@NonNull final String captionLanguage) {
        final int textRendererIndex = player.getCaptionRendererIndex();
        if (textRendererIndex == RENDERER_UNAVAILABLE) {
            return;
        }

        player.getTrackSelector().setParameters(player.getTrackSelector()
                .buildUponParameters()
                .setPreferredTextLanguages(
                        captionLanguage,
                        PlayerHelper.captionLanguageStemOf(captionLanguage))
                .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION)
                .setRendererDisabled(textRendererIndex, false));
        player.getPrefs().edit()
                .putString(context.getString(R.string.caption_user_set_key), captionLanguage)
                .apply();
        binding.captionTextView.setText(captionLanguage);
    }

    private void showActionSheet(@Nullable final CharSequence title,
                                 @NonNull final List<MaterialActionSheetDialog.ActionItem> items) {
        if (items.isEmpty()) {
            return;
        }
        if (actionSheetDialog != null) {
            actionSheetDialog.dismiss();
        }
        @Nullable final Context actionSheetContext = resolveActionSheetContext();
        if (actionSheetContext == null) {
            isSomeActionSheetVisible = false;
            return;
        }
        actionSheetDialog = MaterialActionSheetDialog.show(
                actionSheetContext,
                title,
                items,
                this::onActionSheetDismissed);
        isSomeActionSheetVisible = actionSheetDialog != null;
    }

    @Nullable
    private Context resolveActionSheetContext() {
        final Context rootContext = binding.getRoot().getContext();
        if (findActivity(rootContext) != null) {
            return rootContext;
        }

        ViewParent parent = binding.getRoot().getParent();
        while (parent instanceof View) {
            final Context parentContext = ((View) parent).getContext();
            if (findActivity(parentContext) != null) {
                return parentContext;
            }
            parent = parent.getParent();
        }
        return null;
    }

    @Nullable
    private static Context findActivity(@Nullable final Context context) {
        Context currentContext = context;
        while (currentContext instanceof ContextWrapper) {
            if (currentContext instanceof android.app.Activity) {
                return currentContext;
            }
            final Context baseContext = ((ContextWrapper) currentContext).getBaseContext();
            if (baseContext == currentContext) {
                return null;
            }
            currentContext = baseContext;
        }
        return null;
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Captions (text tracks)
    //////////////////////////////////////////////////////////////////////////*/
    //region Captions (text tracks)

    @Override
    public void onTextTracksChanged(@NonNull final Tracks currentTracks) {
        super.onTextTracksChanged(currentTracks);

        final boolean trackTypeTextSupported = !currentTracks.containsType(C.TRACK_TYPE_TEXT)
                || currentTracks.isTypeSupported(C.TRACK_TYPE_TEXT, false);
        if (getPlayer().getTrackSelector().getCurrentMappedTrackInfo() == null
                || !trackTypeTextSupported) {
            binding.captionTextView.setVisibility(View.GONE);
            return;
        }

        // Extract all loaded languages
        final List<Tracks.Group> textTracks = currentTracks
                .getGroups()
                .stream()
                .filter(trackGroupInfo -> C.TRACK_TYPE_TEXT == trackGroupInfo.getType())
                .collect(Collectors.toList());
        final List<String> availableLanguages = textTracks.stream()
                .map(Tracks.Group::getMediaTrackGroup)
                .filter(textTrack -> textTrack.length > 0)
                .map(textTrack -> textTrack.getFormat(0).language)
                .collect(Collectors.toList());

        // Find selected text track
        final Optional<Format> selectedTracks = textTracks.stream()
                .filter(Tracks.Group::isSelected)
                .filter(info -> info.getMediaTrackGroup().length >= 1)
                .map(info -> info.getMediaTrackGroup().getFormat(0))
                .findFirst();

        // Build UI
        buildCaptionMenu(availableLanguages);
        if (player.getTrackSelector().getParameters().getRendererDisabled(
                player.getCaptionRendererIndex()) || selectedTracks.isEmpty()) {
            binding.captionTextView.setText(R.string.caption_none);
        } else {
            binding.captionTextView.setText(selectedTracks.get().language);
        }
        binding.captionTextView.setVisibility(
                availableLanguages.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onCues(@NonNull final List<Cue> cues) {
        super.onCues(cues);
        binding.subtitleView.setCues(cues);
    }

    private void setupSubtitleView() {
        setupSubtitleView(PlayerHelper.getCaptionScale(context));
        final CaptionStyleCompat captionStyle = PlayerHelper.getCaptionStyle(context);
        binding.subtitleView.setApplyEmbeddedStyles(captionStyle == CaptionStyleCompat.DEFAULT);
        binding.subtitleView.setStyle(captionStyle);
    }

    /**
     *
     * @param captionScale Value returned by {@link PlayerHelper#getCaptionScale}.
     */
    protected abstract void setupSubtitleView(float captionScale);
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Click listeners
    //////////////////////////////////////////////////////////////////////////*/
    //region Click listeners

    /**
     * Create on-click listener which manages the player controls after the view on-click action.
     *
     * @param runnable The action to be executed.
     * @return The view click listener.
     */
    protected View.OnClickListener makeOnClickListener(@NonNull final Runnable runnable) {
        return v -> {
            if (DEBUG) {
                Log.d(TAG, "onClick() called with: v = [" + v + "]");
            }

            runnable.run();

            // Manages the player controls after handling the view click.
            if (player.getCurrentState() == STATE_COMPLETED) {
                return;
            }
            controlsVisibilityHandler.removeCallbacksAndMessages(null);
            showHideShadow(true, DEFAULT_CONTROLS_DURATION);
            animate(binding.playbackControlRoot, true, DEFAULT_CONTROLS_DURATION,
                    AnimationType.ALPHA, 0, () -> {
                        if (player.getCurrentState() == STATE_PLAYING
                                && !isSomeActionSheetVisible) {
                            if (v == binding.playPauseButton
                                    // Hide controls in fullscreen immediately
                                    || (v == binding.screenRotationButton && isFullscreen())) {
                                hideControls(0, 0);
                            } else {
                                hideControls(DEFAULT_CONTROLS_DURATION, DEFAULT_CONTROLS_HIDE_TIME);
                            }
                        }
                    });
        };
    }

    public boolean onKeyDown(final int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (DeviceUtils.isTv(context) && isControlsVisible()) {
                    hideControls(0, 0);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if ((binding.getRoot().hasFocus() && !binding.playbackControlRoot.hasFocus())
                        || isAnyListViewOpen()) {
                    // do not interfere with focus in playlist and play queue etc.
                    break;
                }

                if (player.getCurrentState() == org.schabi.newpipe.player.Player.STATE_BLOCKED) {
                    return true;
                }

                if (isControlsVisible()) {
                    hideControls(DEFAULT_CONTROLS_DURATION, DPAD_CONTROLS_HIDE_TIME);
                } else {
                    binding.playPauseButton.requestFocus();
                    showControlsThenHide();
                    showSystemUIPartially();
                    return true;
                }
                break;
            default:
                break; // ignore other keys
        }

        return false;
    }

    private void onMoreOptionsClicked() {
        if (DEBUG) {
            Log.d(TAG, "onMoreOptionsClicked() called");
        }

        final boolean isMoreControlsVisible =
                binding.secondaryControls.getVisibility() == View.VISIBLE;

        animateRotation(binding.moreOptionsButton, DEFAULT_CONTROLS_DURATION,
                isMoreControlsVisible ? 0 : 180);
        animate(binding.secondaryControls, !isMoreControlsVisible, DEFAULT_CONTROLS_DURATION,
                AnimationType.SLIDE_AND_ALPHA, 0, () -> {
                    // Fix for a ripple effect on background drawable.
                    // When view returns from GONE state it takes more milliseconds than returning
                    // from INVISIBLE state. And the delay makes ripple background end to fast
                    if (isMoreControlsVisible) {
                        binding.secondaryControls.setVisibility(View.INVISIBLE);
                    }
                });
        showControls(DEFAULT_CONTROLS_DURATION);
    }

    private void onPlayWithKodiClicked() {
        if (player.getCurrentMetadata() != null) {
            player.pause();
            KoreUtils.playWithKore(context, Uri.parse(player.getVideoUrl()));
        }
    }

    private void onOpenInBrowserClicked() {
        player.getCurrentStreamInfo().ifPresent(streamInfo ->
                ShareUtils.openUrlInBrowser(player.getContext(), streamInfo.getOriginalUrl()));
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Video size
    //////////////////////////////////////////////////////////////////////////*/
    //region Video size

    protected void setResizeMode(@AspectRatioFrameLayout.ResizeMode final int resizeMode) {
        binding.surfaceView.setResizeMode(resizeMode);
        binding.resizeTextView.setText(PlayerHelper.resizeTypeOf(context, resizeMode));
    }

    void onResizeClicked() {
        setResizeMode(nextResizeModeAndSaveToPrefs(player, binding.surfaceView.getResizeMode()));
    }

    @Override
    public void onVideoSizeChanged(@NonNull final VideoSize videoSize) {
        super.onVideoSizeChanged(videoSize);
        // Starting with ExoPlayer 2.19.0, the VideoSize will report a width and height of 0
        // if the renderer is disabled. In that case, we skip updating the aspect ratio.
        if (videoSize.width == 0 || videoSize.height == 0) {
            return;
        }
        binding.surfaceView.setAspectRatio(((float) videoSize.width) / videoSize.height);
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // SurfaceHolderCallback helpers
    //////////////////////////////////////////////////////////////////////////*/
    //region SurfaceHolderCallback helpers

    /**
     * Connects the video surface to the exo player. This can be called anytime without the risk for
     * issues to occur, since the player will run just fine when no surface is connected. Therefore
     * the video surface will be setup only when all of these conditions are true: it is not already
     * setup (this just prevents wasting resources to setup the surface again), there is an exo
     * player, the root view is attached to a parent and the surface view is valid/unreleased (the
     * latter two conditions prevent "The surface has been released" errors). So this function can
     * be called many times and even while the UI is in unready states.
     */
    public void setupVideoSurfaceIfNeeded() {
        if (!surfaceIsSetup && player.getExoPlayer() != null
                && binding.getRoot().getParent() != null) {
            // make sure there is nothing left over from previous calls
            clearVideoSurface();

            surfaceHolderCallback = new SurfaceHolderCallback(context, player.getExoPlayer());
            binding.surfaceView.getHolder().addCallback(surfaceHolderCallback);

            // ensure player is using an unreleased surface, which the surfaceView might not be
            // when starting playback on background or during player switching
            if (binding.surfaceView.getHolder().getSurface().isValid()) {
                // initially set the surface manually otherwise
                // onRenderedFirstFrame() will not be called
                player.getExoPlayer().setVideoSurfaceHolder(binding.surfaceView.getHolder());
            }

            surfaceIsSetup = true;
        }
    }

    private void clearVideoSurface() {
        if (surfaceHolderCallback != null) {
            binding.surfaceView.getHolder().removeCallback(surfaceHolderCallback);
            surfaceHolderCallback.release();
            surfaceHolderCallback = null;
        }
        Optional.ofNullable(player.getExoPlayer()).ifPresent(ExoPlayer::clearVideoSurface);
        surfaceIsSetup = false;
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Getters
    //////////////////////////////////////////////////////////////////////////*/
    //region Getters

    public PlayerBinding getBinding() {
        return binding;
    }

    public GestureDetector getGestureDetector() {
        return gestureDetector;
    }
    //endregion
}
