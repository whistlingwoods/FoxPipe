package org.schabi.newpipe.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.custom.EditColorPreference

class SponsorBlockCategoriesSettingsFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResourceRegistry()

        findPreference<Preference>(getString(R.string.sponsor_block_category_all_on_key))
            ?.setOnPreferenceClickListener {
                setAllCategoriesChecked(true)
                true
            }

        findPreference<Preference>(getString(R.string.sponsor_block_category_all_off_key))
            ?.setOnPreferenceClickListener {
                setAllCategoriesChecked(false)
                true
            }

        findPreference<Preference>(getString(R.string.sponsor_block_category_reset_key))
            ?.setOnPreferenceClickListener { p ->
                AlertDialog.Builder(p.context)
                    .setMessage(R.string.sponsor_block_confirm_reset_colors)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        preferenceManager.sharedPreferences?.edit {
                            setColorPreference(this, R.string.sponsor_block_category_sponsor_color_key, R.color.sponsor_segment)
                            setColorPreference(this, R.string.sponsor_block_category_intro_color_key, R.color.intro_segment)
                            setColorPreference(this, R.string.sponsor_block_category_outro_color_key, R.color.outro_segment)
                            setColorPreference(this, R.string.sponsor_block_category_interaction_color_key, R.color.interaction_segment)
                            setColorPreference(this, R.string.sponsor_block_category_highlight_color_key, R.color.highlight_segment)
                            setColorPreference(this, R.string.sponsor_block_category_self_promo_color_key, R.color.self_promo_segment)
                            setColorPreference(this, R.string.sponsor_block_category_non_music_color_key, R.color.non_music_segment)
                            setColorPreference(this, R.string.sponsor_block_category_preview_color_key, R.color.preview_segment)
                            setColorPreference(this, R.string.sponsor_block_category_filler_color_key, R.color.filler_segment)
                            setColorPreference(this, R.string.sponsor_block_category_pending_color_key, R.color.pending_segment)
                        }
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                    .show()
                true
            }
    }

    private fun setAllCategoriesChecked(checked: Boolean) {
        val keys = arrayOf(
            R.string.sponsor_block_category_sponsor_key,
            R.string.sponsor_block_category_intro_key,
            R.string.sponsor_block_category_outro_key,
            R.string.sponsor_block_category_interaction_key,
            R.string.sponsor_block_category_highlight_key,
            R.string.sponsor_block_category_self_promo_key,
            R.string.sponsor_block_category_non_music_key,
            R.string.sponsor_block_category_preview_key,
            R.string.sponsor_block_category_filler_key
        )
        for (keyRes in keys) {
            findPreference<SwitchPreference>(getString(keyRes))?.isChecked = checked
        }
    }

    private fun setColorPreference(
        editor: SharedPreferences.Editor,
        @StringRes resId: Int,
        @ColorRes colorId: Int
    ) {
        val colorStr = "#" + Integer.toHexString(ContextCompat.getColor(requireContext(), colorId))
        editor.putString(getString(resId), colorStr)
        findPreference<EditColorPreference>(getString(resId))?.text = colorStr
    }
}
