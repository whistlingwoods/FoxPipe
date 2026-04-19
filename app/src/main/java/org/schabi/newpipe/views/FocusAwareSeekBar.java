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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.ViewTreeObserver;
import android.widget.SeekBar;

import androidx.appcompat.widget.AppCompatSeekBar;

import org.schabi.newpipe.extractor.stream.StreamHeatmapEntry;
import org.schabi.newpipe.util.DeviceUtils;

import java.util.Collections;
import java.util.List;

/**
 * SeekBar, adapted for directional navigation. It emulates touch-related callbacks
 * (onStartTrackingTouch/onStopTrackingTouch), so existing code does not need to be changed to
 * work with it.
  */
public final class FocusAwareSeekBar extends AppCompatSeekBar {
    private NestedListener listener;

    private ViewTreeObserver treeObserver;

    private List<StreamHeatmapEntry> heatmapEntries = Collections.emptyList();
    private long heatmapTotalDurationMillis = 0;
    private final Paint heatmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path heatmapPath = new Path();

    public FocusAwareSeekBar(final Context context) {
        super(context);
        initHeatmapPaint();
    }

    public FocusAwareSeekBar(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        initHeatmapPaint();
    }

    public FocusAwareSeekBar(final Context context, final AttributeSet attrs,
                             final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initHeatmapPaint();
    }

    private void initHeatmapPaint() {
        heatmapPaint.setStyle(Paint.Style.FILL);
        heatmapPaint.setColor(0x55FF9500); // ~33% transparent orange
    }

    public void setHeatmap(final List<StreamHeatmapEntry> entries,
                           final long totalDurationMillis) {
        heatmapEntries = entries != null ? entries : Collections.emptyList();
        heatmapTotalDurationMillis = totalDurationMillis;
        invalidate();
    }

    public void clearHeatmap() {
        heatmapEntries = Collections.emptyList();
        heatmapTotalDurationMillis = 0;
        invalidate();
    }

    @Override
    protected synchronized void onDraw(final Canvas canvas) {
        if (!heatmapEntries.isEmpty() && heatmapTotalDurationMillis > 0) {
            final float trackLeft = getPaddingLeft();
            final float trackWidth = getWidth() - getPaddingLeft() - getPaddingRight();
            final float baseY = getHeight() / 2f;
            // Fill the full upper half, leaving 1px gap at top edge
            final float maxHeight = baseY - 1;

            if (maxHeight > 0 && trackWidth > 0) {
                final int n = heatmapEntries.size();
                final float[] px = new float[n];
                final float[] py = new float[n];
                for (int i = 0; i < n; i++) {
                    final StreamHeatmapEntry e = heatmapEntries.get(i);
                    final float startFrac =
                            (float) e.getStartTimeMillis() / heatmapTotalDurationMillis;
                    final float endFrac =
                            (float) (e.getStartTimeMillis() + e.getDurationMillis())
                                    / heatmapTotalDurationMillis;
                    // Center x of the segment, y grows upward from baseY
                    px[i] = trackLeft + (startFrac + endFrac) * 0.5f * trackWidth;
                    py[i] = baseY - (float) e.getHeatIntensity() * maxHeight;
                }

                heatmapPath.reset();
                // Start at bottom-left corner, rise to first peak
                heatmapPath.moveTo(px[0], baseY);
                heatmapPath.lineTo(px[0], py[0]);

                // Smooth quadratic bezier: data points are control points,
                // midpoints between adjacent data points are on-curve points
                for (int i = 0; i < n - 1; i++) {
                    final float midX = (px[i] + px[i + 1]) * 0.5f;
                    final float midPy = (py[i] + py[i + 1]) * 0.5f;
                    heatmapPath.quadTo(px[i], py[i], midX, midPy);
                }
                // Last peak, then descend to bottom-right corner
                heatmapPath.lineTo(px[n - 1], py[n - 1]);
                heatmapPath.lineTo(px[n - 1], baseY);
                heatmapPath.close();

                canvas.drawPath(heatmapPath, heatmapPaint);
            }
        }
        super.onDraw(canvas);
    }

    @Override
    public void setOnSeekBarChangeListener(final OnSeekBarChangeListener l) {
        this.listener = l == null ? null : new NestedListener(l);

        super.setOnSeekBarChangeListener(listener);
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
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
        if (listener != null && listener.isSeeking) {
            listener.onStopTrackingTouch(this);
        }
    }

    private static final class NestedListener implements OnSeekBarChangeListener {
        private final OnSeekBarChangeListener delegate;

        boolean isSeeking;

        private NestedListener(final OnSeekBarChangeListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onProgressChanged(final SeekBar seekBar, final int progress,
                                      final boolean fromUser) {
            if (!seekBar.isInTouchMode() && !isSeeking && fromUser) {
                isSeeking = true;

                onStartTrackingTouch(seekBar);
            }

            delegate.onProgressChanged(seekBar, progress, fromUser);
        }

        @Override
        public void onStartTrackingTouch(final SeekBar seekBar) {
            isSeeking = true;

            delegate.onStartTrackingTouch(seekBar);
        }

        @Override
        public void onStopTrackingTouch(final SeekBar seekBar) {
            isSeeking = false;

            delegate.onStopTrackingTouch(seekBar);
        }
    }
}
