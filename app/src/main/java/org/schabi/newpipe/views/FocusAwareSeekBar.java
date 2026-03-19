/*
 * Copyright (C) Eltex ltd 2019 <eltex@eltex-co.ru>
 * FocusAwareDrawerLayout.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.schabi.newpipe.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.ViewTreeObserver;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;

import org.schabi.newpipe.util.DeviceUtils;

/**
 * Material slider adapted for directional navigation. It emulates touch-related callbacks
 * (onStartTrackingTouch/onStopTrackingTouch), so existing code does not need to be changed to
 * work with it.
  */
public class FocusAwareSeekBar extends Slider {
    @Nullable
    private SeekBar.OnSeekBarChangeListener listener;
    @NonNull
    private final SeekBarProxy seekBarProxy;
    @NonNull
    private final CompatThumbDrawable compatThumbDrawable = new CompatThumbDrawable();
    @NonNull
    private final CompatProgressDrawable compatProgressDrawable = new CompatProgressDrawable();

    private ViewTreeObserver treeObserver;
    private int max = 100;
    private int secondaryProgress;
    private int keyProgressIncrement = 1;
    private boolean keyboardSeeking;
    private boolean keyboardChangeInProgress;
    private final int fallbackThumbSizePx;
    @Nullable
    private ColorStateList secondaryProgressTintList;

    public FocusAwareSeekBar(final Context context) {
        this(context, null);
    }

    public FocusAwareSeekBar(final Context context, final AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.sliderStyle);
    }

    public FocusAwareSeekBar(final Context context, final AttributeSet attrs,
                             final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        seekBarProxy = new SeekBarProxy(context);
        fallbackThumbSizePx =
                Math.max(1, Math.round(getResources().getDisplayMetrics().density * 20f));
        init(attrs, defStyleAttr);
    }

    private void init(@Nullable final AttributeSet attrs, final int defStyleAttr) {
        final int initialMax = attrs == null ? max : Math.max(0, Math.round(getValueTo()));
        final int initialProgress = attrs == null ? 0 : Math.round(getValue());

        max = initialMax;
        setValueFrom(0f);
        setValueTo(max > 0 ? max : 1f);
        setValue(clampToProgressRange(initialProgress));
        setStepSize(0f);
        setLabelBehavior(LabelFormatter.LABEL_GONE);

        loadCompatTints(attrs, defStyleAttr);
        addOnChangeListener(this::onValueChanged);
        addOnSliderTouchListener(new OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull final Slider slider) {
                releaseTrack();
                if (listener != null) {
                    listener.onStartTrackingTouch(seekBarProxy);
                }
            }

            @Override
            public void onStopTrackingTouch(@NonNull final Slider slider) {
                if (listener != null) {
                    listener.onStopTrackingTouch(seekBarProxy);
                }
            }
        });
    }

    private void loadCompatTints(@Nullable final AttributeSet attrs, final int defStyleAttr) {
        final TypedArray typedArray = getContext().obtainStyledAttributes(
                attrs,
                new int[]{
                        android.R.attr.progressTint,
                        android.R.attr.secondaryProgressTint,
                        android.R.attr.progressBackgroundTint,
                        android.R.attr.thumbTint
                },
                defStyleAttr,
                0
        );

        try {
            final ColorStateList progressTint = typedArray.getColorStateList(0);
            final ColorStateList secondaryProgressTint = typedArray.getColorStateList(1);
            final ColorStateList progressBackgroundTint = typedArray.getColorStateList(2);
            final ColorStateList thumbTint = typedArray.getColorStateList(3);

            if (progressTint != null) {
                setTrackActiveTintList(progressTint);
            }
            if (progressBackgroundTint != null) {
                setTrackInactiveTintList(progressBackgroundTint);
            }
            if (thumbTint != null) {
                setThumbTintList(thumbTint);
            }

            if (secondaryProgressTint != null) {
                setSecondaryProgressTintList(secondaryProgressTint);
            } else if (progressTint != null) {
                setSecondaryProgressTintList(ColorStateList.valueOf(
                        ColorUtils.setAlphaComponent(progressTint.getDefaultColor(), 160)));
            }
        } finally {
            typedArray.recycle();
        }
    }

    private void onValueChanged(@NonNull final Slider slider,
                                final float value,
                                final boolean fromUser) {
        updateThumbBounds();
        if (listener != null) {
            listener.onProgressChanged(
                    seekBarProxy,
                    Math.round(value),
                    fromUser || keyboardChangeInProgress
            );
        }
    }

    public void setOnSeekBarChangeListener(@Nullable final SeekBar.OnSeekBarChangeListener l) {
        listener = l;
    }

    public int getProgress() {
        return Math.round(getValue());
    }

    public void setProgress(final int progress) {
        final int clampedProgress = clampToProgressRange(progress);
        if (clampedProgress == getProgress()) {
            updateThumbBounds();
            invalidate();
            return;
        }
        setValue(clampedProgress);
    }

    public int getMax() {
        return max;
    }

    public void setMax(final int max) {
        this.max = Math.max(max, 0);

        setValueFrom(0f);
        setValueTo(this.max > 0 ? this.max : 1f);
        setValue(clampToProgressRange(getProgress()));
        setSecondaryProgress(secondaryProgress);
        updateThumbBounds();
    }

    public int getSecondaryProgress() {
        return secondaryProgress;
    }

    public void setSecondaryProgress(final int secondaryProgress) {
        this.secondaryProgress = clampToProgressRange(secondaryProgress);
        invalidate();
    }

    public void setSecondaryProgressTintList(@Nullable final ColorStateList tintList) {
        secondaryProgressTintList = tintList;
        invalidate();
    }

    public void setKeyProgressIncrement(final int increment) {
        keyProgressIncrement = Math.max(1, increment);
    }

    public Drawable getThumb() {
        updateThumbBounds();
        return compatThumbDrawable;
    }

    public Drawable getProgressDrawable() {
        return compatProgressDrawable;
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (!isInTouchMode() && isSeekKey(keyCode)) {
            final int layoutDirection = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? -1 : 1;
            final int direction =
                    (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1) * layoutDirection;
            final int newProgress = clampToProgressRange(getProgress()
                    + direction * keyProgressIncrement);
            if (newProgress != getProgress()) {
                if (!keyboardSeeking && listener != null) {
                    keyboardSeeking = true;
                    listener.onStartTrackingTouch(seekBarProxy);
                }

                keyboardChangeInProgress = true;
                setProgress(newProgress);
                keyboardChangeInProgress = false;
            }
            return true;
        }

        if (!isInTouchMode() && DeviceUtils.isConfirmKey(keyCode)) {
            releaseTrack();
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onFocusChanged(final boolean gainFocus, final int direction,
                                  final Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);

        if (!isInTouchMode() && !gainFocus) {
            releaseTrack();
        }
    }

    private final ViewTreeObserver.OnTouchModeChangeListener touchModeListener = isInTouchMode -> {
        if (isInTouchMode) {
            releaseTrack();
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        treeObserver = getViewTreeObserver();
        treeObserver.addOnTouchModeChangeListener(touchModeListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (treeObserver == null || !treeObserver.isAlive()) {
            treeObserver = getViewTreeObserver();
        }

        treeObserver.removeOnTouchModeChangeListener(touchModeListener);
        treeObserver = null;

        super.onDetachedFromWindow();
    }

    private void releaseTrack() {
        if (listener != null && keyboardSeeking) {
            keyboardSeeking = false;
            listener.onStopTrackingTouch(seekBarProxy);
        }
    }

    @Override
    protected void onDraw(@NonNull final Canvas canvas) {
        super.onDraw(canvas);
        drawSecondaryProgress(canvas);
        updateThumbBounds();
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldW, final int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        updateThumbBounds();
    }

    protected final int getCompatTrackStart() {
        return getTrackSidePadding();
    }

    protected final int getCompatTrackTop() {
        return (getHeight() - getTrackHeight()) / 2;
    }

    protected final int getCompatTrackBottom() {
        return getCompatTrackTop() + getTrackHeight();
    }

    protected final int getCompatTrackPositionForFraction(final float fraction) {
        final float resolvedFraction = getLayoutDirection() == LAYOUT_DIRECTION_RTL
                ? 1f - fraction
                : fraction;
        return Math.round(getCompatTrackStart() + (getTrackWidth() * resolvedFraction));
    }

    private void drawSecondaryProgress(@NonNull final Canvas canvas) {
        if (max <= 0 || secondaryProgress <= getProgress()) {
            return;
        }

        final ColorStateList tintList = secondaryProgressTintList;
        if (tintList == null) {
            return;
        }

        final int color = tintList.getColorForState(getDrawableState(), tintList.getDefaultColor());
        final int start = getCompatTrackPositionForFraction(getProgress() / (float) max);
        final int end = getCompatTrackPositionForFraction(secondaryProgress / (float) max);

        if (start == end) {
            return;
        }

        compatProgressDrawable.setResolvedColor(color);
        compatProgressDrawable.drawSecondaryProgress(
                canvas,
                Math.min(start, end),
                getCompatTrackTop(),
                Math.max(start, end),
                getCompatTrackBottom()
        );
    }

    private void updateThumbBounds() {
        final int thumbWidth = getCompatThumbSize();
        final int thumbHeight = thumbWidth;
        final int left = getCompatTrackPositionForFraction(max <= 0
                ? 0f
                : getProgress() / (float) max) - (thumbWidth / 2) - getPaddingLeft();
        final int top = (getHeight() - thumbHeight) / 2 - getPaddingTop();
        compatThumbDrawable.setBounds(left, top, left + thumbWidth, top + thumbHeight);
    }

    private int getCompatThumbSize() {
        return Math.max(getTrackHeight() * 2, fallbackThumbSizePx);
    }

    @Nullable
    private Integer resolveColorFromFilter(@Nullable final ColorFilter colorFilter) {
        if (!(colorFilter instanceof PorterDuffColorFilter)) {
            return null;
        }

        try {
            return (Integer) PorterDuffColorFilter.class
                    .getMethod("getColor")
                    .invoke(colorFilter);
        } catch (final ReflectiveOperationException ignored) {
            return null;
        }
    }

    private int clampToProgressRange(final int progress) {
        return Math.max(0, Math.min(progress, max));
    }

    private boolean isSeekKey(final int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private final class SeekBarProxy extends SeekBar {
        private SeekBarProxy(final Context context) {
            super(context);
        }

        @Override
        public synchronized int getProgress() {
            return FocusAwareSeekBar.this.getProgress();
        }

        @Override
        public synchronized void setProgress(final int progress) {
            FocusAwareSeekBar.this.setProgress(progress);
        }

        @Override
        public int getMax() {
            return FocusAwareSeekBar.this.getMax();
        }

        @Override
        public synchronized void setMax(final int max) {
            FocusAwareSeekBar.this.setMax(max);
        }

        @Override
        public synchronized int getSecondaryProgress() {
            return FocusAwareSeekBar.this.getSecondaryProgress();
        }

        @Override
        public synchronized void setSecondaryProgress(final int secondaryProgress) {
            FocusAwareSeekBar.this.setSecondaryProgress(secondaryProgress);
        }
    }

    private final class CompatThumbDrawable extends Drawable {
        @Override
        public void draw(@NonNull final Canvas canvas) {
            // Slider draws its own thumb.
        }

        @Override
        public void setAlpha(final int alpha) {
            // Slider manages alpha through its own tint state list.
        }

        @Override
        public void setColorFilter(@Nullable final ColorFilter colorFilter) {
            final Integer color = resolveColorFromFilter(colorFilter);
            if (color != null) {
                setThumbTintList(ColorStateList.valueOf(color));
                setHaloTintList(ColorStateList.valueOf(
                        ColorUtils.setAlphaComponent(color, 72)));
            }
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private final class CompatProgressDrawable extends Drawable {
        @Nullable
        private Integer resolvedColor;

        @Override
        public void draw(@NonNull final Canvas canvas) {
            // Slider draws its own track.
        }

        private void drawSecondaryProgress(@NonNull final Canvas canvas,
                                           final int left,
                                           final int top,
                                           final int right,
                                           final int bottom) {
            if (resolvedColor == null) {
                return;
            }

            final int color = isEnabled()
                    ? resolvedColor
                    : ColorUtils.setAlphaComponent(resolvedColor, 96);
            final android.graphics.Paint paint = new android.graphics.Paint(
                    android.graphics.Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            canvas.drawRoundRect(
                    left,
                    top,
                    right,
                    bottom,
                    getTrackHeight() / 2f,
                    getTrackHeight() / 2f,
                    paint
            );
        }

        private void setResolvedColor(final int color) {
            resolvedColor = color;
        }

        @Override
        public void setAlpha(final int alpha) {
            // Slider manages alpha through its own tint state list.
        }

        @Override
        public void setColorFilter(@Nullable final ColorFilter colorFilter) {
            final Integer color = resolveColorFromFilter(colorFilter);
            if (color == null) {
                return;
            }

            setTrackActiveTintList(ColorStateList.valueOf(color));
            setTrackInactiveTintList(ColorStateList.valueOf(
                    ColorUtils.setAlphaComponent(color, 72)));
            setSecondaryProgressTintList(ColorStateList.valueOf(
                    ColorUtils.setAlphaComponent(color, 144)));
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
