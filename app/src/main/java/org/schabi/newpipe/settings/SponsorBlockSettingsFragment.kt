package org.schabi.newpipe.settings

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.preference.Preference
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.local.sponsorblock.SponsorBlockDataManager

class SponsorBlockSettingsFragment : BasePreferenceFragment() {
    private var sponsorBlockDataManager: SponsorBlockDataManager? = null
    private var workerClearWhitelist: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sponsorBlockDataManager = SponsorBlockDataManager(requireContext())
    }

    override fun onDetach() {
        super.onDetach()
        if (workerClearWhitelist != null) {
            workerClearWhitelist!!.dispose()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResourceRegistry()

        val sponsorBlockWebsitePreference: Preference = checkNotNull(
            findPreference(getString(R.string.sponsor_block_home_page_key))
        )
        sponsorBlockWebsitePreference!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { p: Preference ->
                val i = Intent(
                    Intent.ACTION_VIEW,
                    getString(R.string.sponsor_block_homepage_url).toUri()
                )
                startActivity(i)
                true
            }

        val sponsorBlockPrivacyPreference: Preference = checkNotNull(
            findPreference(getString(R.string.sponsor_block_privacy_key))
        )
        sponsorBlockPrivacyPreference!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { p: Preference ->
                val i = Intent(
                    Intent.ACTION_VIEW,
                    getString(R.string.sponsor_block_privacy_policy_url).toUri()
                )
                startActivity(i)
                true
            }

        val sponsorBlockClearWhitelistPreference: Preference = checkNotNull(
            findPreference(getString(R.string.sponsor_block_clear_whitelist_key))
        )
        sponsorBlockClearWhitelistPreference!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { p: Preference ->
                AlertDialog.Builder(p.context)
                    .setMessage(R.string.sponsor_block_confirm_clear_whitelist)
                    .setPositiveButton(
                        R.string.yes
                    ) { dialog: DialogInterface?, which: Int ->
                        workerClearWhitelist =
                            sponsorBlockDataManager!!.clearWhitelist()
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe({
                                    Toast.makeText(
                                        p.context,
                                        R.string.sponsor_block_whitelist_cleared_toast,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }, Consumer { error: Throwable? -> })
                    }
                    .setNegativeButton(
                        R.string.cancel
                    ) { dialog: DialogInterface?, which: Int -> dialog!!.dismiss() }
                    .show()
                true
            }
    }
}
