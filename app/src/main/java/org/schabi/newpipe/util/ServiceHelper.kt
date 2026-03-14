/*
 * SPDX-FileCopyrightText: 2018-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonParser
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockApiSettings
import org.schabi.newpipe.ktx.getStringSafe

object ServiceHelper {
    private const val DEFAULT_FALLBACK_SERVICE_ID = 0
    private const val DEFAULT_FALLBACK_SERVICE_NAME = "YouTube"

    private fun getKnownServiceId(serviceName: String?): Int? {
        return when (serviceName) {
            "YouTube" -> 0
            "SoundCloud" -> 1
            "media.ccc.de" -> 2
            "PeerTube" -> 3
            "Bandcamp" -> 4
            "BiliBili" -> 5
            "NicoNico" -> 6
            else -> null
        }
    }

    private fun getKnownServiceName(serviceId: Int): String? {
        return when (serviceId) {
            0 -> "YouTube"
            1 -> "SoundCloud"
            2 -> "media.ccc.de"
            3 -> "PeerTube"
            4 -> "Bandcamp"
            5 -> "BiliBili"
            6 -> "NicoNico"
            else -> null
        }
    }

    @JvmStatic
    @DrawableRes
    fun getIcon(serviceId: Int): Int {
        return when (serviceId) {
            0 -> R.drawable.ic_smart_display
            1 -> R.drawable.ic_cloud
            2 -> R.drawable.ic_placeholder_media_ccc
            3 -> R.drawable.ic_placeholder_peertube
            4 -> R.drawable.ic_placeholder_bandcamp
            5 -> R.drawable.ic_bilibili
            else -> R.drawable.ic_circle
        }
    }

    @JvmStatic
    fun getTranslatedFilterString(filter: String, context: Context): String {
        return when (filter) {
            "all" -> context.getString(R.string.all)
            "videos", "sepia_videos", "music_videos" -> context.getString(R.string.videos_string)
            "channels" -> context.getString(R.string.channels)
            "playlists", "music_playlists" -> context.getString(R.string.playlists)
            "tracks" -> context.getString(R.string.tracks)
            "users" -> context.getString(R.string.users)
            "conferences" -> context.getString(R.string.conferences)
            "events" -> context.getString(R.string.events)
            "lives" -> context.getString(R.string.lives)
            "animes" -> context.getString(R.string.animes)
            "movies_and_tv" -> context.getString(R.string.movies_and_tv)
            "tags_only" -> context.getString(R.string.tags_only)
            "music_songs" -> context.getString(R.string.songs)
            "music_albums" -> context.getString(R.string.albums)
            "music_artists" -> context.getString(R.string.artists)
            "sort_view" -> context.getString(R.string.sort_view)
            "sort_bookmark" -> context.getString(R.string.sort_bookmark)
            "sort_comments" -> context.getString(R.string.sort_comments)
            "sort_bullet_comments" -> context.getString(R.string.sort_bullet_comments)
            "sort_publish_time" -> context.getString(R.string.sort_publish_time)
            "sort_overall" -> context.getString(R.string.sort_overall)
            else -> filter
        }
    }

    /**
     * Get a resource string with instructions for importing subscriptions for each service.
     *
     * @param serviceId service to get the instructions for
     * @return the string resource containing the instructions or -1 if the service don't support it
     */
    @JvmStatic
    @StringRes
    fun getImportInstructions(serviceId: Int): Int {
        return when (serviceId) {
            0 -> R.string.import_youtube_instructions
            1 -> R.string.import_soundcloud_instructions
            else -> -1
        }
    }

    /**
     * For services that support importing from a channel url, return a hint that will
     * be used in the EditText that the user will type in his channel url.
     *
     * @param serviceId service to get the hint for
     * @return the hint's string resource or -1 if the service don't support it
     */
    @JvmStatic
    @StringRes
    fun getImportInstructionsHint(serviceId: Int): Int {
        return when (serviceId) {
            1 -> R.string.import_soundcloud_instructions_hint
            else -> -1
        }
    }

    @JvmStatic
    fun getSelectedServiceId(context: Context): Int {
        val serviceName: String = PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSafe(
                context.getString(R.string.current_service_key),
                context.getString(R.string.default_service_value)
            )

        return getKnownServiceId(serviceName)
            ?: getSelectedService(context)?.serviceId
            ?: DEFAULT_FALLBACK_SERVICE_ID
    }

    @JvmStatic
    fun getSelectedService(context: Context): StreamingService? {
        val serviceName: String = PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSafe(
                context.getString(R.string.current_service_key),
                context.getString(R.string.default_service_value)
            )

        return runCatching { NewPipe.getService(serviceName) }.getOrNull()
    }

    @JvmStatic
    fun getNameOfServiceById(serviceId: Int): String {
        return getKnownServiceName(serviceId) ?: ServiceList.all().stream()
            .filter { it.serviceId == serviceId }
            .findFirst()
            .map(StreamingService::getServiceInfo)
            .map(StreamingService.ServiceInfo::getName)
            .orElse("<unknown>")
    }

    /**
     * @param serviceId the id of the service
     * @return the service corresponding to the provided id
     * @throws java.util.NoSuchElementException if there is no service with the provided id
     */
    @JvmStatic
    fun getServiceById(serviceId: Int): StreamingService {
        return ServiceList.all().firstNotNullOf { it.takeIf { it.serviceId == serviceId } }
    }

    @JvmStatic
    fun setSelectedServiceId(context: Context, serviceId: Int) {
        val serviceName = getKnownServiceName(serviceId)
            ?: runCatching { NewPipe.getService(serviceId).serviceInfo.name }
                .getOrDefault(DEFAULT_FALLBACK_SERVICE_NAME)

        setSelectedServicePreferences(context, serviceName)
    }

    private fun setSelectedServicePreferences(context: Context, serviceName: String?) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.edit { putString(context.getString(R.string.current_service_key), serviceName) }
    }

    @JvmStatic
    fun getCacheExpirationMillis(serviceId: Int): Long {
        return if (serviceId == ServiceList.SoundCloud.serviceId) {
            TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES)
        } else {
            TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS)
        }
    }

    fun initService(context: Context, serviceId: Int) {
        if (serviceId == ServiceList.PeerTube.serviceId) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val json = sharedPreferences.getString(
                context.getString(R.string.peertube_selected_instance_key),
                null
            ) ?: return

            val jsonObject = runCatching { JsonParser.`object`().from(json) }
                .getOrElse { return@initService }

            ServiceList.PeerTube.instance = PeertubeInstance(
                jsonObject.getString("url"),
                jsonObject.getString("name")
            )
        }
    }

    @JvmStatic
    fun initServices(context: Context) {
        val sponsorBlockApiSettings = buildSponsorBlockApiSettings(context)
        ServiceList.all().forEach {
            initService(context, it.serviceId)
            it.sponsorBlockApiSettings = sponsorBlockApiSettings
        }
    }

    private fun buildSponsorBlockApiSettings(context: Context): SponsorBlockApiSettings? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean(context.getString(R.string.sponsor_block_enable_key), false)) {
            return null
        }

        return SponsorBlockApiSettings().apply {
            apiUrl = context.getString(R.string.sponsor_block_api_url_default)
            userId = SponsorBlockHelper.getUserId(context)
            includeSponsorCategory = true
            includeIntroCategory = true
            includeOutroCategory = true
            includeInteractionCategory = true
            includeHighlightCategory = true
            includeSelfPromoCategory = true
            includeMusicCategory = true
            includePreviewCategory = true
            includeFillerCategory = true
        }
    }
}
