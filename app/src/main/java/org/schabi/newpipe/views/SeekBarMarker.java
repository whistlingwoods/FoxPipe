package org.schabi.newpipe.views;

public class SeekBarMarker {
    public final double percentStart;
    public final double percentEnd;
    public final int color;

    public SeekBarMarker(final double startTime,
                         final double endTime,
                         final long maxTime,
                         final int color) {
        percentStart = ((startTime / maxTime) * 100.0) / 100.0;
        percentEnd = ((endTime / maxTime) * 100.0) / 100.0;
        this.color = color;
    }
}
