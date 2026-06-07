package org.schabi.newpipe.settings.custom

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.Preference
import org.schabi.newpipe.R

class SponsorBlockApiUrlPreference(context: Context, attrs: AttributeSet?) :
    Preference(context, attrs) {
    override fun onSetInitialValue(defaultValue: Any?) {
        // apparently this is how you're supposed to respect default values for a custom preference
        persistString(getPersistedString(defaultValue as String?))
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        return a.getString(index)
    }

    override fun onClick() {
        super.onClick()

        val context = getContext()

        val apiUrl = getPersistedString(null)

        val alertDialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_sponsor_block_api_url, null)

        val editText = alertDialogView.findViewById<EditText>(R.id.api_url_edit)
        editText.setText(apiUrl)
        editText.setOnFocusChangeListener { v: View, hasFocus: Boolean ->
            editText.post {
                val inputMethodManager = context
                    .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager
                    .showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        editText.requestFocus()

        alertDialogView.findViewById<View>(R.id.icon_api_url_help)
            .setOnClickListener { v: View ->
                val privacyPolicyUri = context
                    .getString(R.string.sponsor_block_privacy_policy_url).toUri()
                val helpDialogView = LayoutInflater.from(context)
                    .inflate(R.layout.dialog_sponsor_block_api_url_help, null)
                val privacyPolicyButton = helpDialogView
                    .findViewById<View>(R.id.sponsor_block_privacy_policy_button)
                privacyPolicyButton.setOnClickListener { v1: View ->
                    val i = Intent(Intent.ACTION_VIEW, privacyPolicyUri)
                    context.startActivity(i)
                }
                AlertDialog.Builder(context)
                    .setView(helpDialogView)
                    .setPositiveButton(
                        "Use Official"
                    ) { dialog: DialogInterface?, which: Int ->
                        editText.setText(
                            context
                                .getString(R.string.sponsor_block_default_api_url)
                        )
                        dialog!!.dismiss()
                    }
                    .setNeutralButton(
                        "Close"
                    ) { dialog: DialogInterface?, which: Int -> dialog!!.dismiss() }
                    .create()
                    .show()
            }

        val alertDialog =
            AlertDialog.Builder(context)
                .setView(alertDialogView)
                .setTitle(context.getString(R.string.sponsor_block_api_url_title))
                .setPositiveButton(
                    "OK"
                ) { dialog: DialogInterface?, which: Int ->
                    val newValue = editText.getText().toString()
                    if (!newValue.isEmpty()) {
                        preferenceManager.getSharedPreferences()!!.edit {
                            putString(key, newValue)
                        }

                        callChangeListener(newValue)
                    }
                    dialog!!.dismiss()
                }
                .setNegativeButton(
                    "Cancel"
                ) { dialog: DialogInterface?, which: Int -> dialog!!.cancel() }
                .create()

        alertDialog.show()
    }
}
