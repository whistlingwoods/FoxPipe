package org.schabi.newpipe.settings

import android.content.DialogInterface
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResourceRegistry()

        val allOnPreference =
            findPreference<Preference?>(getString(R.string.sponsor_block_category_all_on_key))
        allOnPreference!!.onPreferenceClickListener = Preference.OnPreferenceClickListener { p: Preference? ->
            val sponsorCategoryPreference =
                findPreference<SwitchPreference?>(getString(R.string.sponsor_block_category_sponsor_key))
            val introCategoryPreference =
                findPreference<SwitchPreference?>(getString(R.string.sponsor_block_category_intro_key))
            val outroCategoryPreference =
                findPreference<SwitchPreference?>(getString(R.string.sponsor_block_category_outro_key))
            val interactionCategoryPreference =
                findPreference<SwitchPreference?>(getString(R.string.sponsor_block_category_interaction_key))
            val highlightCategoryPreference =
                findPreference<SwitchPreference?>(getString(R.string.sponsor_block_category_highlight_key))
            val selfPromoCategoryPreference =
                findPreference<SwitchPreference?>(getString(R.string.sponsor_block_category_self_promo_key))
            val nonMusicCategoryPreference =
                findPreference<SwitchPreference?>(getString(R.string.sponsor_block_category_non_music_key))
            val previewCategoryPreference =
                findPreference<SwitchPreference?>(getString(R.string.sponsor_block_category_preview_key))
            val fillerCategoryPreference =
                findPreference<SwitchPreference?>(getString(R.string.sponsor_block_category_filler_key))

            sponsorCategoryPreference!!.setChecked(true)
            introCategoryPreference!!.setChecked(true)
            outroCategoryPreference!!.setChecked(true)
            interactionCategoryPreference!!.setChecked(true)
            highlightCategoryPreference!!.setChecked(true)
            selfPromoCategoryPreference!!.setChecked(true)
            nonMusicCategoryPreference!!.setChecked(true)
            previewCategoryPreference!!.setChecked(true)
            fillerCategoryPreference!!.setChecked(true)
            true
        }

        val allOffPreference =
            findPreference<Preference?>(getString(R.string.sponsor_block_category_all_off_key))
        allOffPreference!!.onPreferenceClickListener = Preference.OnPreferenceClickListener { p: Preference? ->
            val sponsorCategoryPreference =
                findPreference<SwitchPreference?>(getString(R.string.sponsor_block_category_sponsor_key))
            val introCategoryPreference =
                findPreference<SwitchPreference?>(getString(R.string.sponsor_block_category_intro_key))
            val outroCategoryPreference =
                findPreference<SwitchPreference?>(getString(R.string.sponsor_block_category_outro_key))
            val interactionCategoryPreference =
                findPreference<SwitchPreference?>(getString(R.string.sponsor_block_category_interaction_key))
            val highlightCategoryPreference =
                findPreference<SwitchPreference?>(getString(R.string.sponsor_block_category_highlight_key))
            val selfPromoCategoryPreference =
                findPreference<SwitchPreference?>(getString(R.string.sponsor_block_category_self_promo_key))
            val nonMusicCategoryPreference =
                findPreference<SwitchPreference?>(getString(R.string.sponsor_block_category_non_music_key))
            val previewCategoryPreference =
                findPreference<SwitchPreference?>(getString(R.string.sponsor_block_category_preview_key))
            val fillerCategoryPreference =
                findPreference<SwitchPreference?>(getString(R.string.sponsor_block_category_filler_key))

            sponsorCategoryPreference!!.setChecked(false)
            introCategoryPreference!!.setChecked(false)
            outroCategoryPreference!!.setChecked(false)
            interactionCategoryPreference!!.setChecked(false)
            highlightCategoryPreference!!.setChecked(false)
            selfPromoCategoryPreference!!.setChecked(false)
            nonMusicCategoryPreference!!.setChecked(false)
            previewCategoryPreference!!.setChecked(false)
            fillerCategoryPreference!!.setChecked(false)
            true
        }

        val resetPreference =
            findPreference<Preference?>(getString(R.string.sponsor_block_category_reset_key))
        resetPreference!!.onPreferenceClickListener = Preference.OnPreferenceClickListener { p: Preference? ->
            AlertDialog.Builder(p!!.context)
                .setMessage(R.string.sponsor_block_confirm_reset_colors)
                .setPositiveButton(
                    R.string.yes
                ) { dialog: DialogInterface?, which: Int ->
                    preferenceManager
                        .getSharedPreferences()!!
                        .edit {
                            setColorPreference(
                                this,
                                R.string.sponsor_block_category_sponsor_color_key,
                                R.color.sponsor_segment
                            )
                            setColorPreference(
                                this,
                                R.string.sponsor_block_category_intro_color_key,
                                R.color.intro_segment
                            )
                            setColorPreference(
                                this,
                                R.string.sponsor_block_category_outro_color_key,
                                R.color.outro_segment
                            )
                            setColorPreference(
                                this,
                                R.string.sponsor_block_category_interaction_color_key,
                                R.color.interaction_segment
                            )
                            setColorPreference(
                                this,
                                R.string.sponsor_block_category_highlight_color_key,
                                R.color.highlight_segment
                            )
                            setColorPreference(
                                this,
                                R.string.sponsor_block_category_self_promo_color_key,
                                R.color.self_promo_segment
                            )
                            setColorPreference(
                                this,
                                R.string.sponsor_block_category_non_music_color_key,
                                R.color.non_music_segment
                            )
                            setColorPreference(
                                this,
                                R.string.sponsor_block_category_preview_color_key,
                                R.color.preview_segment
                            )
                            setColorPreference(
                                this,
                                R.string.sponsor_block_category_filler_color_key,
                                R.color.filler_segment
                            )
                            setColorPreference(
                                this,
                                R.string.sponsor_block_category_pending_color_key,
                                R.color.pending_segment
                            )
                        }
                }
                .setNegativeButton(
                    R.string.cancel
                ) { dialog: DialogInterface?, which: Int -> dialog!!.dismiss() }
                .show()
            true
        }
    }

    private fun setColorPreference(
        editor: SharedPreferences.Editor,
        @StringRes resId: Int,
        @ColorRes colorId: Int
    ) {
        val colorStr = "#" + Integer.toHexString(ContextCompat.getColor(requireContext(), colorId))
        editor.putString(getString(resId), colorStr)
        val colorPreference = findPreference<EditColorPreference?>(getString(resId))
        colorPreference!!.setText(colorStr)
    }
}
