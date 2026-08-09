package org.schabi.newpipe.util.dearrow

import android.content.Context
import android.util.Log
import android.util.LruCache
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.dearrow.DeArrowApiSettings
import org.schabi.newpipe.extractor.dearrow.DeArrowInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.util.ExtractorHelper
import java.util.Locale
import java.util.regex.Pattern

object DeArrowHelper {

    private const val TAG = "DeArrowHelper"

    private val deArrowCache = LruCache<String, DeArrowInfo>(200)

    @JvmStatic
    fun fetchDeArrowInfoAsync(context: Context, videoId: String): io.reactivex.rxjava3.core.Maybe<DeArrowInfo> {
        return io.reactivex.rxjava3.core.Maybe.fromCallable {
            val cached = deArrowCache.get(videoId)
            if (cached != null) {
                return@fromCallable cached
            }
            val settings = ExtractorHelper.getDeArrowApiSettings(context)
                ?: return@fromCallable null

            val info = org.schabi.newpipe.extractor.dearrow.DeArrowExtractorHelper.getInfo(videoId, settings)
                ?: return@fromCallable null

            deArrowCache.put(videoId, info)
            info
        }
    }

    // Regex to match emojis (simple version)
    private val EMOJI_REGEX = Pattern.compile("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+")

    @JvmStatic
    fun getFormattedTitle(context: Context, streamInfo: StreamInfo): String? {
        val deArrowInfo = streamInfo.deArrowInfo ?: return null
        return getFormattedTitle(context, streamInfo.id, deArrowInfo)
    }

    @JvmStatic
    fun getFormattedTitle(context: Context, videoId: String, deArrowInfo: DeArrowInfo): String? {
        val bestTitle = deArrowInfo.bestTitle
        if (bestTitle == null) {
            Log.v(TAG, "No bestTitle found for video $videoId")
            return null
        }
        val cleanTitle = bestTitle.cleanTitle
        if (cleanTitle == null) {
            Log.v(TAG, "bestTitle.cleanTitle is null for video $videoId")
            return null
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val replaceTitles = prefs.getBoolean(context.getString(R.string.dearrow_replace_titles_key), true)
        if (!replaceTitles) {
            Log.v(TAG, "Title replacement is disabled in settings")
            return null
        }

        val formatOriginal = prefs.getBoolean(context.getString(R.string.dearrow_format_original_titles_key), true)
        val formatUser = prefs.getBoolean(context.getString(R.string.dearrow_format_user_submitted_titles_key), true)

        val shouldFormat = if (bestTitle.original) formatOriginal else formatUser
        Log.v(TAG, "Original title flag: ${bestTitle.original}, shouldFormat: $shouldFormat")

        if (!shouldFormat) {
            Log.v(TAG, "Formatting skipped. Returning cleanTitle: $cleanTitle")
            return cleanTitle
        }

        var processedTitle = cleanTitle

        val removeEmojis = prefs.getBoolean(context.getString(R.string.dearrow_remove_emojis_key), true)
        if (removeEmojis) {
            processedTitle = EMOJI_REGEX.matcher(processedTitle).replaceAll("").replace("  ", " ").trim()
        }

        val titleFormat = prefs.getString(context.getString(R.string.dearrow_title_format_key), "title_case")
        Log.v(TAG, "Applying title format: $titleFormat to '$processedTitle'")
        processedTitle = when (titleFormat) {
            "title_case" -> toTitleCase(processedTitle)
            "capitalize_first" -> processedTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            "lowercase" -> processedTitle.lowercase(Locale.getDefault())
            else -> processedTitle
        }

        Log.v(TAG, "Final processed title: '$processedTitle'")
        return processedTitle
    }

    @JvmStatic
    fun getThumbnailUrl(context: Context, streamInfo: StreamInfo): String? {
        val deArrowInfo = streamInfo.deArrowInfo ?: return null
        return getThumbnailUrl(context, streamInfo.id, deArrowInfo)
    }

    @JvmStatic
    fun getThumbnailUrl(context: Context, videoId: String, deArrowInfo: DeArrowInfo): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val replaceThumbnails = prefs.getBoolean(context.getString(R.string.dearrow_replace_thumbnails_key), true)

        if (!replaceThumbnails) {
            Log.v(TAG, "Thumbnail replacement is disabled in settings")
            return null
        }

        val bestThumbnail = deArrowInfo.bestThumbnail

        // If no submitted thumbnail exists (or it's the original one), fall back to YouTube's auto-generated thumbnail
        if (bestThumbnail == null || bestThumbnail.original) {
            Log.v(TAG, "No user-submitted thumbnail (or it is original). Falling back to YouTube auto-generated thumbnail (hq2).")
            return "https://i.ytimg.com/vi/$videoId/hq1.jpg"
        }

        // Custom timestamp thumbnail exists
        val settings = DeArrowApiSettings()
        settings.thumbnailUrl = prefs.getString(context.getString(R.string.dearrow_thumbnail_url_key), context.getString(R.string.dearrow_default_thumbnail_url))
        val customUrl = bestThumbnail.getThumbnailUrl(videoId, settings)
        Log.v(TAG, "Returning user-submitted custom thumbnail URL: $customUrl")
        return customUrl
    }

    private fun toTitleCase(str: String): String {
        if (str.isEmpty()) return str
        val words = str.split(" ")
        val sb = StringBuilder()
        for (word in words) {
            if (word.isEmpty()) continue
            sb.append(word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }).append(" ")
        }
        return sb.toString().trim()
    }
}
