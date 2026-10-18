/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.schabi.newpipe.util.InfoCache
import org.schabi.newpipe.util.image.PicassoHelper
import java.io.IOException

/**
 * Invisible activity that clears NewPipe's caches (metadata + image caches) and
 * immediately returns to the previous screen. It is used as the target of the
 * "Clear cache" long-press shortcut on the app icon, so that users can wipe the
 * caches without having to open the app and dig through the settings.
 *
 * See https://github.com/TeamNewPipe/NewPipe/issues/8981
 */
class ClearCacheActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Metadata cache (video info, streams metadata, ...)
        InfoCache.getInstance().clearCache()

        // Picasso image caches (memory + disk)
        val imageCacheCleared = try {
            PicassoHelper.clearCache(this)
            true
        } catch (e: IOException) {
            Log.e("ClearCacheActivity", "Unable to clear Picasso cache", e)
            false
        }

        if (imageCacheCleared) {
            Toast.makeText(
                applicationContext,
                R.string.clear_cache_complete_notice,
                Toast.LENGTH_SHORT
            ).show()
        }
        finish()
    }
}
