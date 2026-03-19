package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;

import com.google.android.material.progressindicator.LinearProgressIndicator;

public final class AnimatedProgressBar extends LinearProgressIndicator {

    public AnimatedProgressBar(final Context context) {
        super(context);
    }

    public AnimatedProgressBar(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public AnimatedProgressBar(final Context context, final AttributeSet attrs,
                               final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public synchronized void setProgressAnimated(final int progress) {
        setProgressCompat(progress, true);
    }
}
