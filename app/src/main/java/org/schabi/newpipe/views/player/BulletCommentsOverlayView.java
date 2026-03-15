package org.schabi.newpipe.views.player;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem;

public final class BulletCommentsOverlayView extends FrameLayout {
    private static final long DEFAULT_SCROLL_DURATION_MILLIS = 8000L;
    private static final long DEFAULT_FIXED_DURATION_MILLIS = 4000L;
    private static final float DEFAULT_TEXT_SIZE_SP = 20f;
    private static final float MIN_TEXT_SIZE_SP = 12f;

    private int nextRegularLane = 0;
    private int nextTopLane = 0;
    private int nextBottomLane = 0;

    public BulletCommentsOverlayView(@NonNull final Context context) {
        super(context);
        init();
    }

    public BulletCommentsOverlayView(@NonNull final Context context,
                                     @Nullable final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BulletCommentsOverlayView(@NonNull final Context context,
                                     @Nullable final AttributeSet attrs,
                                     final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClipChildren(true);
        setClipToPadding(true);
    }

    public void reset() {
        removeAllViews();
        nextRegularLane = 0;
        nextTopLane = 0;
        nextBottomLane = 0;
    }

    public void showBulletComment(@NonNull final BulletCommentsInfoItem item) {
        if (item.getCommentText() == null || item.getCommentText().isEmpty()) {
            return;
        }

        if (getWidth() == 0 || getHeight() == 0) {
            post(() -> showBulletComment(item));
            return;
        }

            final AppCompatTextView textView = buildTextView(item);
        switch (item.getPosition()) {
            case TOP:
                showFixedComment(
                        textView,
                        true,
                        resolveDuration(item, DEFAULT_FIXED_DURATION_MILLIS));
                break;
            case BOTTOM:
                showFixedComment(
                        textView,
                        false,
                        resolveDuration(item, DEFAULT_FIXED_DURATION_MILLIS));
                break;
            case SUPERCHAT:
            case REGULAR:
            default:
                showScrollingComment(
                        textView,
                        resolveDuration(item, DEFAULT_SCROLL_DURATION_MILLIS));
                break;
        }
    }

    @NonNull
    private AppCompatTextView buildTextView(@NonNull final BulletCommentsInfoItem item) {
        final AppCompatTextView textView = new AppCompatTextView(getContext());
        textView.setText(item.getCommentText());
        textView.setTextColor(item.getArgbColor() == 0 ? Color.WHITE : item.getArgbColor());
        textView.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                Math.max(
                        MIN_TEXT_SIZE_SP,
                        DEFAULT_TEXT_SIZE_SP * (float) item.getRelativeFontSize()));
        textView.setShadowLayer(6f, 2f, 2f, Color.BLACK);
        textView.setSingleLine(true);
        textView.setClickable(false);
        textView.setFocusable(false);
        return textView;
    }

    private void showScrollingComment(@NonNull final AppCompatTextView textView,
                                      final long durationMillis) {
        final int laneHeight = getLaneHeight();
        final int laneCount = Math.max(1, getHeight() / laneHeight);
        final int laneIndex = nextRegularLane++ % laneCount;

        final LayoutParams layoutParams =
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.START | Gravity.TOP;
        layoutParams.topMargin = laneIndex * laneHeight;
        addView(textView, layoutParams);

        textView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        final int startX = getWidth();
        final int endX = -textView.getMeasuredWidth();
        textView.setTranslationX(startX);

        textView.animate()
                .translationX(endX)
                .setDuration(durationMillis)
                .withEndAction(() -> removeView(textView))
                .start();
    }

    private void showFixedComment(@NonNull final AppCompatTextView textView,
                                  final boolean top,
                                  final long durationMillis) {
        final int laneHeight = getLaneHeight();
        final int laneCount = Math.max(1, Math.max(1, getHeight() / laneHeight) / 3);
        final int laneIndex = top
                ? nextTopLane++ % laneCount
                : nextBottomLane++ % laneCount;

        final LayoutParams layoutParams =
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = (top ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL;
        if (top) {
            layoutParams.topMargin = laneIndex * laneHeight;
        } else {
            layoutParams.bottomMargin = laneIndex * laneHeight;
        }
        addView(textView, layoutParams);

        textView.setAlpha(0f);
        textView.animate()
                .alpha(1f)
                .setDuration(200L)
                .withEndAction(() -> postDelayed(() -> {
                    textView.animate()
                            .alpha(0f)
                            .setDuration(200L)
                            .withEndAction(() -> removeView(textView))
                            .start();
                }, Math.max(200L, durationMillis - 200L)))
                .start();
    }

    private int getLaneHeight() {
        final float density = getResources().getDisplayMetrics().density;
        return Math.max((int) (density * 32f), 1);
    }

    private static long resolveDuration(@NonNull final BulletCommentsInfoItem item,
                                        final long defaultDurationMillis) {
        return item.getLastingTime() > 0 ? item.getLastingTime() : defaultDurationMillis;
    }
}
