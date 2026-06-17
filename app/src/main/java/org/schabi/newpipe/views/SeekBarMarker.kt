package org.schabi.newpipe.views

class SeekBarMarker {
    var startTime: Double = 0.0
    var endTime: Double = 0.0
    var percentStart: Double
    var percentEnd: Double
    var color: Int

    constructor(
        startTime: Double,
        endTime: Double,
        maxTime: Long,
        color: Int
    ) {
        this.startTime = startTime
        this.endTime = endTime
        this.percentStart = ((startTime / maxTime) * 100.0) / 100.0
        this.percentEnd = ((endTime / maxTime) * 100.0) / 100.0
        this.color = color
    }

    constructor(percentStart: Double, percentEnd: Double, color: Int) {
        this.percentStart = percentStart
        this.percentEnd = percentEnd
        this.color = color
    }
}
