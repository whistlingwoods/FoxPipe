package org.schabi.newpipe.settings

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonParser
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.dearrow.DeArrowApiSettings
import org.schabi.newpipe.extractor.utils.Utils

class DeArrowSettingsFragment : BasePreferenceFragment() {
    private var licenseCheckDisposable: Disposable? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResourceRegistry()

        val dearrowWebsitePreference: Preference = checkNotNull(
            findPreference(getString(R.string.dearrow_home_page_key))
        )
        dearrowWebsitePreference.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val i = Intent(
                    Intent.ACTION_VIEW,
                    getString(R.string.dearrow_homepage_url).toUri()
                )
                startActivity(i)
                true
            }

        val dearrowPrivacyPreference: Preference = checkNotNull(
            findPreference(getString(R.string.dearrow_privacy_key))
        )
        dearrowPrivacyPreference.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val i = Intent(
                    Intent.ACTION_VIEW,
                    getString(R.string.dearrow_privacy_policy_url).toUri()
                )
                startActivity(i)
                true
            }

        val dearrowLicenseKeyPreference: Preference = checkNotNull(
            findPreference(getString(R.string.dearrow_license_key_key))
        )

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val currentKey = prefs.getString(getString(R.string.dearrow_license_key_key), "")
        checkLicenseKey(currentKey, dearrowLicenseKeyPreference)

        dearrowLicenseKeyPreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference, newValue ->
                checkLicenseKey(newValue as String, preference)
                true
            }
    }

    private fun checkLicenseKey(key: String?, preference: Preference) {
        if (key.isNullOrEmpty()) {
            preference.summary = getString(R.string.dearrow_license_key_summary_none)
            return
        }
        preference.summary = getString(R.string.dearrow_license_key_summary_checking)

        licenseCheckDisposable?.dispose()
        licenseCheckDisposable = Single.fromCallable {
            val apiUrl = DeArrowApiSettings.DEFAULT_API_URL
            val url = "$apiUrl/api/verifyToken?licenseKey=${Utils.encodeUrlUtf8(key)}"
            val response = NewPipe.getDownloader().get(url)
            val responseBody = response.responseBody()
            if (responseBody != null) {
                val obj = JsonParser.`object`().from(responseBody)
                val allowed = obj.getBoolean("allowed", false)
                if (allowed) {
                    val isLocal = key.matches(Regex("[A-Za-z0-9]{5}-[A-Za-z0-9]{5}"))
                    val isFree = isLocal && !key.startsWith("P")
                    if (isFree) {
                        return@fromCallable getString(R.string.dearrow_license_key_summary_valid_free)
                    } else {
                        return@fromCallable getString(R.string.dearrow_license_key_summary_valid_paid)
                    }
                }
            }
            return@fromCallable getString(R.string.dearrow_license_key_summary_invalid)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ summary ->
                preference.summary = summary
            }, {
                preference.summary = getString(R.string.dearrow_license_key_summary_error)
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        licenseCheckDisposable?.dispose()
    }
}
