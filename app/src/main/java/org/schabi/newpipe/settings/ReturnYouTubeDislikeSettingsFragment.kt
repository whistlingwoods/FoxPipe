package org.schabi.newpipe.settings

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.preference.Preference
import org.schabi.newpipe.R

class ReturnYouTubeDislikeSettingsFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResourceRegistry()

        val rydWebsitePreference =
            findPreference<Preference?>(getString(R.string.return_youtube_dislike_home_page_key))
        rydWebsitePreference!!.setOnPreferenceClickListener { p: Preference ->
            val i = Intent(
                Intent.ACTION_VIEW,
                getString(R.string.return_youtube_dislike_home_page_url).toUri()
            )
            startActivity(i)
            true
        }

        val rydSecurityFaqPreference =
            findPreference<Preference?>(getString(R.string.return_youtube_dislike_security_faq_key))
        rydSecurityFaqPreference!!.setOnPreferenceClickListener { p: Preference ->
            val i = Intent(
                Intent.ACTION_VIEW,
                getString(R.string.return_youtube_dislike_security_faq_url).toUri()
            )
            startActivity(i)
            true
        }
    }
}
