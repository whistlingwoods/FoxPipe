/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.schabi.newpipe.util.InfoCache
import org.schabi.newpipe.util.image.PicassoHelper

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

        try {
            PicassoHelper.clearCache(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Toast.makeText(applicationContext, R.string.clear_cache_complete_notice, Toast.LENGTH_SHORT)
            .show()

        // Do not open the app: the user just asked for the cache to be cleared from
        // the home screen, so go straight back to whatever was shown before us.
        finish()
    }
}
