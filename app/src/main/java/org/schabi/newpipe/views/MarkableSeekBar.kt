package org.schabi.newpipe.views

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.content.ContextCompat
import org.schabi.newpipe.R

open class MarkableSeekBar : AppCompatSeekBar {
    var seekBarMarkers: ArrayList<SeekBarMarker> = ArrayList()
    private var originalProgressDrawable: Drawable? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)

    override fun setProgressDrawable(d: Drawable?) {
        super.setProgressDrawable(d)

        // stored for when we draw (and potentially re-draw) markers
        originalProgressDrawable = d
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)

        // re-draw markers since the progress bar may have a different width
        drawMarkers()
    }

    fun drawMarkers() {
        if (seekBarMarkers.isEmpty()) {
            return
        }

        // Markers are drawn like so:
        //
        //  - LayerDrawable (original drawable for the SeekBar)
        //    - GradientDrawable (background)
        //    - ScaleDrawable (secondaryProgress)
        //    - ScaleDrawable (progress)
        //    - LayerDrawable (we add our markers in a sub-LayerDrawable)
        //      - Drawable (marker)
        //      - Drawable (marker)
        //      - Drawable (marker)
        //      - etc...
        val width = measuredWidth - (paddingStart + paddingEnd)

        var layerDrawable = originalProgressDrawable as LayerDrawable?

        val markerDrawables = ArrayList<Drawable?>()
        markerDrawables.add(layerDrawable)

        for (seekBarMarker in seekBarMarkers) {
            val markerDrawable =
                ContextCompat.getDrawable(
                    context,
                    R.drawable.sponsor_block_scrubber
                )

            val colorFilter =
                PorterDuffColorFilter(seekBarMarker.color, PorterDuff.Mode.SRC_IN)

            checkNotNull(markerDrawable)
            markerDrawable.colorFilter = colorFilter

            markerDrawables.add(markerDrawable)
        }

        layerDrawable = LayerDrawable(markerDrawables.toTypedArray<Drawable?>())

        for (i in 1..<layerDrawable.numberOfLayers) {
            val seekBarMarker = seekBarMarkers[i - 1]
            val l = (width * seekBarMarker.percentStart).toInt()
            val r = (width * (1.0 - seekBarMarker.percentEnd)).toInt()

            layerDrawable.setLayerInset(i, l, 0, r, 0)
        }

        super.setProgressDrawable(layerDrawable)
    }

    fun clearMarkers() {
        seekBarMarkers.clear()
        super.setProgressDrawable(originalProgressDrawable)
    }
}
