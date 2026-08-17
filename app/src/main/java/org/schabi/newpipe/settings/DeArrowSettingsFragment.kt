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
    private var userStatsDisposable: Disposable? = null

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

        val userStatsPreference: Preference? = findPreference(getString(R.string.dearrow_user_stats_key))
        if (userStatsPreference != null) {
            val localUserId = prefs.getString(getString(R.string.sponsor_block_local_user_id_key), "")
            checkUserStats(localUserId, userStatsPreference)
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
            val obj = JsonParser.`object`().from(responseBody)
            val allowed = obj.getBoolean("allowed", false)
            if (allowed) {
                return@fromCallable getString(R.string.dearrow_license_key_summary_valid)
            }
            return@fromCallable getString(R.string.dearrow_license_key_summary_invalid)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ summary ->
                preference.summary = summary
            }, {
                // Fail-open strategy: assume valid if network fails
                preference.summary = getString(R.string.dearrow_license_key_summary_valid)
            })
    }

    private fun checkUserStats(localUserId: String?, preference: Preference) {
        if (localUserId.isNullOrEmpty()) {
            preference.summary = getString(R.string.dearrow_user_stats_error)
            return
        }
        preference.summary = getString(R.string.dearrow_user_stats_loading)

        userStatsDisposable?.dispose()
        userStatsDisposable = Single.fromCallable {
            val trimmedId = localUserId.trim()
            if (trimmedId.length == 64 && trimmedId.matches(Regex("[0-9a-fA-F]+"))) {
                return@fromCallable trimmedId
            }

            var hashHex = trimmedId
            val md = java.security.MessageDigest.getInstance("SHA-256")
            for (i in 0 until 5000) {
                val hashBuffer = md.digest(hashHex.toByteArray(Charsets.UTF_8))
                hashHex = hashBuffer.joinToString("") { "%02x".format(it) }
            }
            hashHex
        }
            .subscribeOn(Schedulers.computation())
            .observeOn(Schedulers.io())
            .flatMap { publicUserId ->
                Single.fromCallable {
                    val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    val apiUrlPref = prefs.getString(getString(R.string.dearrow_api_url_key), null)
                    val apiUrl = if (apiUrlPref.isNullOrEmpty()) getString(R.string.dearrow_default_api_url) else apiUrlPref

                    val url = "${if (apiUrl.endsWith("/")) apiUrl else "$apiUrl/"}api/userInfo?publicUserID=$publicUserId&values=%5B%22userName%22%2C%22titleSubmissionCount%22%2C%22thumbnailSubmissionCount%22%5D"
                    val response = NewPipe.getDownloader().get(url)
                    val responseBody = response.responseBody()
                    val obj = JsonParser.`object`().from(responseBody)
                    if (obj.has("userName") && (obj.has("titleSubmissionCount") || obj.has("thumbnailSubmissionCount"))) {
                        val userName = obj.getString("userName")
                        val titleCount = if (obj.has("titleSubmissionCount")) obj.getInt("titleSubmissionCount") else 0
                        val thumbnailCount = if (obj.has("thumbnailSubmissionCount")) obj.getInt("thumbnailSubmissionCount") else 0
                        return@fromCallable Triple(userName, titleCount, thumbnailCount)
                    }
                    throw Exception("Invalid response")
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ (userName, titleCount, thumbnailCount) ->
                preference.summary = getString(R.string.dearrow_user_stats_result, userName, titleCount, thumbnailCount)
            }, { error ->
                preference.summary = getString(R.string.dearrow_user_stats_error) + " (" + error.javaClass.simpleName + ": " + error.message + ")"
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        licenseCheckDisposable?.dispose()
        userStatsDisposable?.dispose()
    }
}
