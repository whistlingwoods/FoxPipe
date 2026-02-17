package org.schabi.newpipe.util;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Utility class for displaying stream ratings in list items.
 */
public final class RatingHelper {
    private RatingHelper() {
        // Utility class
    }

    /**
     * Formats a rating for display.
     *
     * @param rating the rating value (1-10)
     * @return formatted string like "⭐8"
     */
    @NonNull
    public static String formatRating(final int rating) {
        return "⭐" + rating;
    }

    /**
     * Updates a TextView to show rating badge, or hides it if no rating.
     *
     * @param textView the view to update (may be null if layout doesn't have rating view)
     * @param rating the rating (null if unrated)
     */
    public static void displayRating(@Nullable final TextView textView,
                                      @Nullable final Integer rating) {
        if (textView == null) {
            // View not present in this layout, skip silently
            return;
        }

        if (rating != null && rating >= 1 && rating <= 10) {
            textView.setText(formatRating(rating));
            textView.setVisibility(View.VISIBLE);
        } else {
            textView.setVisibility(View.GONE);
        }
    }
}
