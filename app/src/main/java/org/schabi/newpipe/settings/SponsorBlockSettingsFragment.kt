package org.schabi.newpipe.settings

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.Preference
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.local.sponsorblock.SponsorBlockDataManager

class SponsorBlockSettingsFragment : BasePreferenceFragment() {
    private var sponsorBlockDataManager: SponsorBlockDataManager? = null
    private var workerClearWhitelist: Disposable? = null
    private var userStatsDisposable: Disposable? = null

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
        sponsorBlockWebsitePreference.onPreferenceClickListener =
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
        sponsorBlockPrivacyPreference.onPreferenceClickListener =
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
        sponsorBlockClearWhitelistPreference.onPreferenceClickListener =
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

        val userStatsPreference: Preference = checkNotNull(
            findPreference(getString(R.string.sponsor_block_user_stats_key))
        )
        val localUserIdPreference = checkNotNull(
            findPreference<androidx.preference.EditTextPreference>(getString(R.string.sponsor_block_local_user_id_key))
        )

        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        var currentLocalUserId = prefs.getString(getString(R.string.sponsor_block_local_user_id_key), "")
        if (currentLocalUserId.isNullOrEmpty() || currentLocalUserId.trim().length < 32) {
            currentLocalUserId = java.util.UUID.randomUUID().toString().replace("-", "")
            prefs.edit {
                putString(
                    getString(R.string.sponsor_block_local_user_id_key),
                    currentLocalUserId
                )
            }
            localUserIdPreference.text = currentLocalUserId
        }

        fetchUserStats(currentLocalUserId, userStatsPreference)

        localUserIdPreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                fetchUserStats(newValue as String, userStatsPreference)
                true
            }
    }

    private fun fetchUserStats(localUserId: String?, preference: Preference) {
        if (localUserId.isNullOrEmpty() || localUserId.trim().length < 32) {
            preference.summary = getString(R.string.sponsor_block_user_stats_error)
            return
        }
        preference.summary = getString(R.string.sponsor_block_user_stats_loading)

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
                    val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                    val apiUrlPref = prefs.getString(getString(R.string.sponsor_block_api_url_key), null)
                    val apiUrl = if (apiUrlPref.isNullOrEmpty()) getString(R.string.sponsor_block_default_api_url) else apiUrlPref

                    val url = "${if (apiUrl.endsWith("/")) apiUrl else "$apiUrl/"}userInfo?publicUserID=$publicUserId"
                    val response = org.schabi.newpipe.extractor.NewPipe.getDownloader().get(url)
                    val responseBody = response.responseBody()
                    val obj = com.grack.nanojson.JsonParser.`object`().from(responseBody)
                    if (obj.has("userName") && obj.has("segmentCount")) {
                        val userName = obj.getString("userName")
                        val segmentCount = obj.getInt("segmentCount")
                        val ignoredSegmentCount = if (obj.has("ignoredSegmentCount")) obj.getInt("ignoredSegmentCount") else 0
                        val submissions = segmentCount + ignoredSegmentCount
                        return@fromCallable Pair(userName, submissions)
                    }
                    throw Exception("Invalid response")
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ (userName, submissions) ->
                preference.summary = getString(R.string.sponsor_block_user_stats_result, userName, submissions)
            }, { error ->
                preference.summary = getString(R.string.sponsor_block_user_stats_error) + " (" + error.javaClass.simpleName + ": " + error.message + ")"
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        userStatsDisposable?.dispose()
    }
}
