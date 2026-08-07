package org.schabi.newpipe.fragments.list.sponsorblock

import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment

interface SponsorBlockFragmentListener {
    fun onSkippingEnabledChanged(newValue: Boolean)
    fun onRequestNewPendingSegment(startTime: Int, endTime: Int)
    fun onRequestClearPendingSegment()
    fun onRequestSubmitPendingSegment(newSegment: SponsorBlockSegment)
    fun onSeekToRequested(positionMillis: Long)
}
