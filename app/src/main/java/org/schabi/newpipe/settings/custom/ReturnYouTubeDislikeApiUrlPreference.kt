package org.schabi.newpipe.settings.custom

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnFocusChangeListener
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.Preference
import org.schabi.newpipe.R

class ReturnYouTubeDislikeApiUrlPreference(
    context: Context,
    attrs: AttributeSet?
) : Preference(context, attrs) {
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
            .inflate(R.layout.dialog_return_youtube_dislike_api_url, null)

        val editText = alertDialogView.findViewById<EditText?>(R.id.api_url_edit)
        if (editText != null) {
            editText.setText(apiUrl)
            editText.onFocusChangeListener = OnFocusChangeListener { v: View?, hasFocus: Boolean ->
                // The keyboard will be shown in the dialog's setOnShowListener instead
            }
            editText.requestFocus()
        }

        val helpBtn = alertDialogView.findViewById<View?>(R.id.icon_api_url_help)
        if (helpBtn != null && editText != null) {
            helpBtn.setOnClickListener { v: View? ->
                val privacyPolicyUri = context
                    .getString(R.string.return_youtube_dislike_security_faq_url).toUri()
                val helpDialogView = LayoutInflater.from(context)
                    .inflate(R.layout.dialog_return_youtube_dislike_api_url_help, null)
                val privacyPolicyButton = helpDialogView
                    .findViewById<View?>(R.id.return_youtube_dislike_security_faq_button)
                if (privacyPolicyButton != null) {
                    privacyPolicyButton.setOnClickListener { v1: View? ->
                        val i = Intent(Intent.ACTION_VIEW, privacyPolicyUri)
                        context.startActivity(i)
                    }
                }
                AlertDialog.Builder(context)
                    .setView(helpDialogView)
                    .setPositiveButton(
                        "Use Official"
                    ) { dialog: DialogInterface?, which: Int ->
                        editText.setText(
                            context.getString(
                                R.string.return_youtube_dislike_default_api_url
                            )
                        )
                        dialog!!.dismiss()
                    }
                    .setNeutralButton(
                        "Close"
                    ) { dialog: DialogInterface?, which: Int -> dialog!!.dismiss() }
                    .create()
                    .show()
            }
        }

        val alertDialog = AlertDialog.Builder(context)
            .setView(alertDialogView)
            .setTitle(context.getString(R.string.return_youtube_dislike_api_url_title))
            .setPositiveButton(
                "OK"
            ) { dialog: DialogInterface?, which: Int ->
                if (editText != null) {
                    val newValue = editText.text.toString()
                    if (!newValue.isEmpty()) {
                        preferenceManager.getSharedPreferences()!!.edit {
                            putString(key, newValue)
                        }
                        callChangeListener(newValue)
                    }
                }
                dialog!!.dismiss()
            }
            .setNegativeButton(
                "Cancel"
            ) { dialog: DialogInterface?, which: Int -> dialog!!.cancel() }
            .create()

        alertDialog.setOnShowListener {
            if (editText != null) {
                editText.requestFocus()
                alertDialog.window?.let { window ->
                    WindowCompat.getInsetsController(window, editText).show(WindowInsetsCompat.Type.ime())
                }
            }
        }

        alertDialog.show()
    }
}
