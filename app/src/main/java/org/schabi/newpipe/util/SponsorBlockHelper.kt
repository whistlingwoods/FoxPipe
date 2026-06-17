package org.schabi.newpipe.util

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.views.MarkableSeekBar
import org.schabi.newpipe.views.SeekBarMarker

object SponsorBlockHelper {
    @JvmStatic
    fun convertCategoryToColor(
        category: SponsorBlockCategory,
        context: Context
    ): Int {
        val key: String
        val colorStr: String?
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        when (category) {
            SponsorBlockCategory.SPONSOR -> {
                key = context.getString(R.string.sponsor_block_category_sponsor_color_key)
                colorStr = prefs.getString(key, null)
                return colorStr?.toColorInt() ?: ContextCompat.getColor(context, R.color.sponsor_segment)
            }

            SponsorBlockCategory.INTRO -> {
                key = context.getString(R.string.sponsor_block_category_intro_color_key)
                colorStr = prefs.getString(key, null)
                return colorStr?.toColorInt() ?: ContextCompat.getColor(context, R.color.intro_segment)
            }

            SponsorBlockCategory.OUTRO -> {
                key = context.getString(R.string.sponsor_block_category_outro_color_key)
                colorStr = prefs.getString(key, null)
                return colorStr?.toColorInt() ?: ContextCompat.getColor(context, R.color.outro_segment)
            }

            SponsorBlockCategory.INTERACTION -> {
                key = context.getString(R.string.sponsor_block_category_interaction_color_key)
                colorStr = prefs.getString(key, null)
                return colorStr?.toColorInt() ?: ContextCompat.getColor(context, R.color.interaction_segment)
            }

            SponsorBlockCategory.HIGHLIGHT -> {
                key = context.getString(R.string.sponsor_block_category_highlight_color_key)
                colorStr = prefs.getString(key, null)
                return colorStr?.toColorInt() ?: ContextCompat.getColor(context, R.color.highlight_segment)
            }

            SponsorBlockCategory.SELF_PROMO -> {
                key = context.getString(R.string.sponsor_block_category_self_promo_color_key)
                colorStr = prefs.getString(key, null)
                return colorStr?.toColorInt() ?: ContextCompat.getColor(context, R.color.self_promo_segment)
            }

            SponsorBlockCategory.NON_MUSIC -> {
                key = context.getString(R.string.sponsor_block_category_non_music_color_key)
                colorStr = prefs.getString(key, null)
                return colorStr?.toColorInt() ?: ContextCompat.getColor(context, R.color.non_music_segment)
            }

            SponsorBlockCategory.PREVIEW -> {
                key = context.getString(R.string.sponsor_block_category_preview_color_key)
                colorStr = prefs.getString(key, null)
                return colorStr?.toColorInt() ?: ContextCompat.getColor(context, R.color.preview_segment)
            }

            SponsorBlockCategory.FILLER -> {
                key = context.getString(R.string.sponsor_block_category_filler_color_key)
                colorStr = prefs.getString(key, null)
                return colorStr?.toColorInt() ?: ContextCompat.getColor(context, R.color.filler_segment)
            }

            SponsorBlockCategory.PENDING -> {
                key = context.getString(R.string.sponsor_block_category_pending_color_key)
                colorStr = prefs.getString(key, null)
                return colorStr?.toColorInt() ?: ContextCompat.getColor(context, R.color.pending_segment)
            }
        }
    }

    @JvmStatic
    fun markSegments(
        context: Context,
        seekBar: MarkableSeekBar,
        streamInfo: StreamInfo
    ) {
        seekBar.clearMarkers()

        val sponsorBlockSegments = streamInfo.sponsorBlockSegments ?: return

        for (sponsorBlockSegment in sponsorBlockSegments) {
            val color = convertCategoryToColor(
                sponsorBlockSegment.category,
                context
            )

            // if null, then this category should not be marked

            // duration is in seconds, we need milliseconds
            val length = streamInfo.duration * 1000

            val seekBarMarker =
                SeekBarMarker(
                    sponsorBlockSegment.startTime,
                    sponsorBlockSegment.endTime,
                    length,
                    color
                )
            seekBar.seekBarMarkers.add(seekBarMarker)
        }

        seekBar.drawMarkers()
    }

    @JvmStatic
    fun convertCategoryToFriendlyName(
        context: Context,
        category: SponsorBlockCategory
    ): String {
        return when (category) {
            SponsorBlockCategory.SPONSOR -> context.getString(
                R.string.sponsor_block_category_sponsor
            )

            SponsorBlockCategory.INTRO -> context.getString(
                R.string.sponsor_block_category_intro
            )

            SponsorBlockCategory.OUTRO -> context.getString(
                R.string.sponsor_block_category_outro
            )

            SponsorBlockCategory.INTERACTION -> context.getString(
                R.string.sponsor_block_category_interaction
            )

            SponsorBlockCategory.HIGHLIGHT -> context.getString(
                R.string.sponsor_block_category_highlight
            )

            SponsorBlockCategory.SELF_PROMO -> context.getString(
                R.string.sponsor_block_category_self_promo
            )

            SponsorBlockCategory.NON_MUSIC -> context.getString(
                R.string.sponsor_block_category_non_music
            )

            SponsorBlockCategory.PREVIEW -> context.getString(
                R.string.sponsor_block_category_preview
            )

            SponsorBlockCategory.FILLER -> context.getString(
                R.string.sponsor_block_category_filler
            )

            SponsorBlockCategory.PENDING -> context.getString(
                R.string.sponsor_block_category_pending
            )
        }
    }

    @JvmStatic
    fun convertCategoryToSkipMessage(
        context: Context,
        category: SponsorBlockCategory
    ): String {
        return when (category) {
            SponsorBlockCategory.SPONSOR ->
                context
                    .getString(R.string.sponsor_block_skip_sponsor_toast)

            SponsorBlockCategory.INTRO ->
                context
                    .getString(R.string.sponsor_block_skip_intro_toast)

            SponsorBlockCategory.OUTRO ->
                context
                    .getString(R.string.sponsor_block_skip_outro_toast)

            SponsorBlockCategory.INTERACTION ->
                context
                    .getString(R.string.sponsor_block_skip_interaction_toast)

            SponsorBlockCategory.HIGHLIGHT -> ""

            SponsorBlockCategory.SELF_PROMO ->
                context
                    .getString(R.string.sponsor_block_skip_self_promo_toast)

            SponsorBlockCategory.NON_MUSIC ->
                context
                    .getString(R.string.sponsor_block_skip_non_music_toast)

            SponsorBlockCategory.PREVIEW ->
                context
                    .getString(R.string.sponsor_block_skip_preview_toast)

            SponsorBlockCategory.FILLER ->
                context
                    .getString(R.string.sponsor_block_skip_filler_toast)

            SponsorBlockCategory.PENDING ->
                context
                    .getString(R.string.sponsor_block_skip_pending_toast)
        }
    }
}
