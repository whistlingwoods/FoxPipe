package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

public final class PilotIconButton extends MaterialButton {
    private int imageAlpha = 255;

    public PilotIconButton(@NonNull final Context context) {
        super(context);
        init();
    }

    public PilotIconButton(@NonNull final Context context,
                           @Nullable final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PilotIconButton(@NonNull final Context context,
                           @Nullable final AttributeSet attrs,
                           final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setText("");
        setIconPadding(0);
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (getIcon() != null && getIconSize() == 0) {
            final int iconSize = Math.max(
                    0,
                    Math.min(w - getPaddingLeft() - getPaddingRight(),
                            h - getPaddingTop() - getPaddingBottom()));
            if (iconSize > 0) {
                setIconSize(iconSize);
            }
        }

        applyImageAlpha();
    }

    public void setImageResource(@DrawableRes final int resId) {
        setIconResource(resId);
        applyImageAlpha();
    }

    public void setImageDrawable(@Nullable final Drawable drawable) {
        setIcon(drawable);
        applyImageAlpha();
    }

    public void setImageAlpha(final int alpha) {
        imageAlpha = alpha;
        applyImageAlpha();
    }

    private void applyImageAlpha() {
        final Drawable icon = getIcon();
        if (icon == null) {
            return;
        }

        icon.mutate().setAlpha(imageAlpha);
    }
}
