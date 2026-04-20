/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.ViewTreeObserver
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatSeekBar
import org.schabi.newpipe.extractor.stream.StreamHeatmapEntry
import org.schabi.newpipe.util.DeviceUtils

/**
 * SeekBar, adapted for directional navigation. It emulates touch-related callbacks
 * (onStartTrackingTouch/onStopTrackingTouch), so existing code does not need to be changed to
 * work with it.
 */
open class FocusAwareSeekBar : AppCompatSeekBar {

    private var listener: NestedListener? = null
    private var treeObserver: ViewTreeObserver? = null

    private var heatmapEntries: List<StreamHeatmapEntry> = emptyList()
    private var heatmapTotalDurationMillis: Long = 0
    private val heatmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x55FF9500
    }
    private val heatmapPath = Path()

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    fun setHeatmap(entries: List<StreamHeatmapEntry>?, totalDurationMillis: Long) {
        heatmapEntries = entries ?: emptyList()
        heatmapTotalDurationMillis = totalDurationMillis
        invalidate()
    }

    fun clearHeatmap() {
        heatmapEntries = emptyList()
        heatmapTotalDurationMillis = 0
        invalidate()
    }

    @Synchronized
    override fun onDraw(canvas: Canvas) {
        if (heatmapEntries.isNotEmpty() && heatmapTotalDurationMillis > 0) {
            val trackLeft = paddingLeft.toFloat()
            val trackWidth = (width - paddingLeft - paddingRight).toFloat()
            val baseY = height / 2f
            val maxHeight = baseY - 1f

            if (maxHeight > 0 && trackWidth > 0) {
                val n = heatmapEntries.size
                val px = FloatArray(n)
                val py = FloatArray(n)
                for (i in 0 until n) {
                    val e = heatmapEntries[i]
                    val startFrac = e.startTimeMillis.toFloat() / heatmapTotalDurationMillis
                    val endFrac = (e.startTimeMillis + e.durationMillis).toFloat() /
                        heatmapTotalDurationMillis
                    px[i] = trackLeft + (startFrac + endFrac) * 0.5f * trackWidth
                    py[i] = baseY - e.heatIntensity.toFloat() * maxHeight
                }

                heatmapPath.reset()
                heatmapPath.moveTo(px[0], baseY)
                heatmapPath.lineTo(px[0], py[0])
                for (i in 0 until n - 1) {
                    val midX = (px[i] + px[i + 1]) * 0.5f
                    val midPy = (py[i] + py[i + 1]) * 0.5f
                    heatmapPath.quadTo(px[i], py[i], midX, midPy)
                }
                heatmapPath.lineTo(px[n - 1], py[n - 1])
                heatmapPath.lineTo(px[n - 1], baseY)
                heatmapPath.close()

                canvas.drawPath(heatmapPath, heatmapPaint)
            }
        }
        super.onDraw(canvas)
    }

    override fun setOnSeekBarChangeListener(l: OnSeekBarChangeListener?) {
        listener = if (l == null) null else NestedListener(l)
        super.setOnSeekBarChangeListener(listener)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isInTouchMode && DeviceUtils.isConfirmKey(keyCode)) {
            releaseTrack()
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!isInTouchMode && !gainFocus) {
            releaseTrack()
        }
    }

    private val touchModeListener = ViewTreeObserver.OnTouchModeChangeListener { inTouchMode ->
        if (inTouchMode) {
            releaseTrack()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        treeObserver = viewTreeObserver
        treeObserver?.addOnTouchModeChangeListener(touchModeListener)
    }

    override fun onDetachedFromWindow() {
        if (treeObserver?.isAlive != true) {
            treeObserver = viewTreeObserver
        }
        treeObserver?.removeOnTouchModeChangeListener(touchModeListener)
        treeObserver = null
        super.onDetachedFromWindow()
    }

    private fun releaseTrack() {
        val l = listener
        if (l != null && l.isSeeking) {
            l.onStopTrackingTouch(this)
        }
    }

    private class NestedListener(private val delegate: OnSeekBarChangeListener) :
        OnSeekBarChangeListener {

        var isSeeking = false

        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (!seekBar.isInTouchMode && !isSeeking && fromUser) {
                isSeeking = true
                onStartTrackingTouch(seekBar)
            }
            delegate.onProgressChanged(seekBar, progress, fromUser)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            isSeeking = true
            delegate.onStartTrackingTouch(seekBar)
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            isSeeking = false
            delegate.onStopTrackingTouch(seekBar)
        }
    }
}
