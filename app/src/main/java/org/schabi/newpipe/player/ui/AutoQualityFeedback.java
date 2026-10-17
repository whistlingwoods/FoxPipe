package org.schabi.newpipe.player.ui;

import java.util.Objects;

/** Tracks actual resolution changes, without announcing initial or manual selection. */
final class AutoQualityFeedback {
    record Change(String from, String to) { }

    private String streamKey;
    private String resolution;

    Change update(final String key, final boolean automaticLivePlayback,
                  final String currentResolution) {
        if (key == null || !automaticLivePlayback) {
            reset();
            return null;
        }
        if (!Objects.equals(key, streamKey)) {
            streamKey = key;
            resolution = null;
        }
        if (currentResolution == null) {
            return null;
        }
        final String previous = resolution;
        resolution = currentResolution;
        return previous == null || previous.equals(currentResolution)
                ? null : new Change(previous, currentResolution);
    }

    void reset() {
        streamKey = null;
        resolution = null;
    }
}
