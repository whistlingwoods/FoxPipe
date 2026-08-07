package org.schabi.newpipe.settings.custom

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import org.schabi.newpipe.R

class EditColorPreference : EditTextPreference, Preference.OnPreferenceChangeListener {
    private var viewHolder: PreferenceViewHolder? = null

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        init()
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context) : super(context) {
        init()
    }

    private fun init() {
        widgetLayoutResource = R.layout.preference_edit_color
        onPreferenceChangeListener = this
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        viewHolder = holder

        val colorStr =
            preferenceManager
                .getSharedPreferences()!!
                .getString(key, null)

        if (colorStr == null) {
            return
        }

        val color = colorStr.toColorInt()

        val view = viewHolder!!.findViewById(R.id.sponsor_block_segment_color_view)
        view.setBackgroundColor(color)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        try {
            val color = Color.parseColor(newValue as String?)

            val view = viewHolder!!.findViewById(R.id.sponsor_block_segment_color_view)
            view.setBackgroundColor(color)

            return true
        } catch (e: Exception) {
            Toast.makeText(context, R.string.invalid_color_toast, Toast.LENGTH_SHORT).show()
            return false
        }
    }
}
