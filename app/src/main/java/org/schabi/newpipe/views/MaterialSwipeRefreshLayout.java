package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.R;

public final class MaterialSwipeRefreshLayout extends SwipeRefreshLayout {
    public MaterialSwipeRefreshLayout(@NonNull final Context context) {
        this(context, null);
    }

    public MaterialSwipeRefreshLayout(@NonNull final Context context,
                                      @Nullable final AttributeSet attrs) {
        super(context, attrs);
        applyMaterialColors();
    }

    private void applyMaterialColors() {
        setColorSchemeColors(resolveColor(R.attr.colorPrimary, 0xFF6750A4));
        setProgressBackgroundColorSchemeColor(resolveColor(
                org.schabi.newpipe.R.attr.pilot_surface_container_high_color,
                0xFFFFFFFF));
        setDistanceToTriggerSync(dpToPx(96));
        setProgressViewOffset(false, dpToPx(24), dpToPx(88));
    }

    private int dpToPx(final int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @ColorInt
    private int resolveColor(@AttrRes final int attrResId, @ColorInt final int fallback) {
        final TypedValue typedValue = new TypedValue();
        if (!getContext().getTheme().resolveAttribute(attrResId, typedValue, true)) {
            return fallback;
        }

        if (typedValue.resourceId != 0) {
            return ContextCompat.getColor(getContext(), typedValue.resourceId);
        }

        return typedValue.data;
    }
}
