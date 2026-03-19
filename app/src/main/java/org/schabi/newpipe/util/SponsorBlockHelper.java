package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.utils.RandomStringFromAlphabetGenerator;
import org.schabi.newpipe.views.MarkableSeekBar;
import org.schabi.newpipe.views.SeekBarMarker;

import java.security.SecureRandom;

public final class SponsorBlockHelper {
    private static final String USER_ID_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private SponsorBlockHelper() {
    }

    public static String getUserId(final Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String key = context.getString(R.string.sponsor_block_user_id_key);
        String userId = prefs.getString(key, null);
        if (userId == null) {
            userId = RandomStringFromAlphabetGenerator.generate(
                    USER_ID_ALPHABET, 32, new SecureRandom());
            prefs.edit().putString(key, userId).apply();
        }
        return userId;
    }

    public static void markSegments(@NonNull final Context context,
                                    @NonNull final MarkableSeekBar seekBar,
                                    @NonNull final StreamInfo streamInfo) {
        seekBar.clearMarkers();

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean(context.getString(R.string.sponsor_block_enable_key), false)) {
            return;
        }

        final SponsorBlockSegment[] sponsorBlockSegments = streamInfo.getSponsorBlockSegments();
        if (sponsorBlockSegments == null || sponsorBlockSegments.length == 0) {
            return;
        }

        final long length = streamInfo.getDuration() * 1000L;
        if (length <= 0) {
            return;
        }

        for (final SponsorBlockSegment sponsorBlockSegment : sponsorBlockSegments) {
            seekBar.seekBarMarkers.add(new SeekBarMarker(
                    sponsorBlockSegment.startTime,
                    sponsorBlockSegment.endTime,
                    length,
                    convertCategoryToColor(context, sponsorBlockSegment.category)));
        }
        seekBar.drawMarkers();
    }

    @ColorInt
    private static int convertCategoryToColor(final Context context,
                                              final SponsorBlockCategory category) {
        final int colorId = switch (category) {
            case SPONSOR -> R.color.sponsor_segment;
            case INTRO -> R.color.intro_segment;
            case OUTRO -> R.color.outro_segment;
            case INTERACTION -> R.color.interaction_segment;
            case HIGHLIGHT -> R.color.highlight_segment;
            case SELF_PROMO -> R.color.self_promo_segment;
            case NON_MUSIC -> R.color.non_music_segment;
            case PREVIEW -> R.color.preview_segment;
            case FILLER -> R.color.filler_segment;
            case PENDING -> R.color.pending_segment;
        };
        return ContextCompat.getColor(context, colorId);
    }
}
