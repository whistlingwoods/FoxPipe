package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;

import java.util.ArrayList;
import java.util.List;

public class MarkableSeekBar extends FocusAwareSeekBar {
    public final List<SeekBarMarker> seekBarMarkers = new ArrayList<>();
    private Drawable originalProgressDrawable;

    public MarkableSeekBar(final Context context) {
        super(context);
    }

    public MarkableSeekBar(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public MarkableSeekBar(final Context context,
                           final AttributeSet attrs,
                           final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setProgressDrawable(final Drawable drawable) {
        super.setProgressDrawable(drawable);
        originalProgressDrawable = drawable;
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldW, final int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        drawMarkers();
    }

    public void drawMarkers() {
        if (seekBarMarkers.isEmpty() || originalProgressDrawable == null) {
            return;
        }

        final int width = getMeasuredWidth() - (getPaddingStart() + getPaddingEnd());
        if (!(originalProgressDrawable instanceof LayerDrawable)) {
            return;
        }
        LayerDrawable layerDrawable = (LayerDrawable) originalProgressDrawable;

        final ArrayList<Drawable> markerDrawables = new ArrayList<>();
        markerDrawables.add(layerDrawable);

        for (final SeekBarMarker seekBarMarker : seekBarMarkers) {
            final Drawable markerDrawable = new ColorDrawable(seekBarMarker.color);
            markerDrawable.setAlpha(0x99);
            markerDrawables.add(markerDrawable);
        }

        layerDrawable = new LayerDrawable(markerDrawables.toArray(new Drawable[0]));
        for (int i = 1; i < layerDrawable.getNumberOfLayers(); i++) {
            final SeekBarMarker seekBarMarker = seekBarMarkers.get(i - 1);
            final int left = (int) (width * seekBarMarker.percentStart);
            final int right = (int) (width * (1.0 - seekBarMarker.percentEnd));
            layerDrawable.setLayerInset(i, left, 0, right, 0);
        }

        super.setProgressDrawable(layerDrawable);
    }

    public void clearMarkers() {
        seekBarMarkers.clear();
        if (originalProgressDrawable != null) {
            super.setProgressDrawable(originalProgressDrawable);
        }
    }
}
