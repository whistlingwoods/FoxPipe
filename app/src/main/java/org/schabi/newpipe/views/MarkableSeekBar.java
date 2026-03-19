package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class MarkableSeekBar extends FocusAwareSeekBar {
    public final List<SeekBarMarker> seekBarMarkers = new ArrayList<>();
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int markerMinWidthPx;
    private final int markerExtraHeightPx;

    public MarkableSeekBar(final Context context) {
        this(context, null);
    }

    public MarkableSeekBar(final Context context, final AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.sliderStyle);
    }

    public MarkableSeekBar(final Context context,
                           final AttributeSet attrs,
                           final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        markerMinWidthPx = Math.max(1, Math.round(getResources().getDisplayMetrics().density * 2f));
        markerExtraHeightPx = Math.max(1, Math.round(getResources().getDisplayMetrics().density));
    }

    @Override
    protected void onDraw(@NonNull final Canvas canvas) {
        super.onDraw(canvas);
        drawMarkersInternal(canvas);
    }

    public void clearMarkers() {
        seekBarMarkers.clear();
        invalidate();
    }

    public void drawMarkers() {
        invalidate();
    }

    private void drawMarkersInternal(@NonNull final Canvas canvas) {
        if (seekBarMarkers.isEmpty()) {
            return;
        }

        final int trackTop = getCompatTrackTop() - markerExtraHeightPx;
        final int trackBottom = getCompatTrackBottom() + markerExtraHeightPx;
        final int rawTrackStart = getCompatTrackPositionForFraction(0f);
        final int rawTrackEnd = getCompatTrackPositionForFraction(1f);
        final int trackStart = Math.min(rawTrackStart, rawTrackEnd);
        final int trackEnd = Math.max(rawTrackStart, rawTrackEnd);

        for (final SeekBarMarker seekBarMarker : seekBarMarkers) {
            int start = getCompatTrackPositionForFraction((float) seekBarMarker.percentStart);
            int end = getCompatTrackPositionForFraction((float) seekBarMarker.percentEnd);
            if (start > end) {
                final int temp = start;
                start = end;
                end = temp;
            }

            if (end - start < markerMinWidthPx) {
                end = start + markerMinWidthPx;
            }

            start = Math.max(trackStart, start);
            end = Math.min(trackEnd, end);
            if (end <= start) {
                continue;
            }

            markerPaint.setColor(seekBarMarker.color);
            canvas.drawRoundRect(
                    start,
                    trackTop,
                    end,
                    trackBottom,
                    getTrackHeight() / 2f,
                    getTrackHeight() / 2f,
                    markerPaint
            );
        }
    }
}
