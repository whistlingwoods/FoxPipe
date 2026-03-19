package org.schabi.newpipe.settings.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;

import java.util.Objects;

abstract class MaterialPreferenceDialogFragment<T extends DialogPreference> extends DialogFragment {
    static final String ARG_KEY = "key";

    @SuppressWarnings("unchecked")
    @NonNull
    protected final T requirePreference() {
        Fragment targetFragment = getTargetFragment();
        if (!(targetFragment instanceof PreferenceFragmentCompat)) {
            targetFragment = getParentFragmentManager()
                    .findFragmentById(R.id.settings_fragment_holder);
        }

        final PreferenceFragmentCompat preferenceFragment =
                (PreferenceFragmentCompat) targetFragment;
        Objects.requireNonNull(preferenceFragment);

        final String key = Objects.requireNonNull(requireArguments().getString(ARG_KEY));
        final Preference preference = preferenceFragment.findPreference(key);
        Objects.requireNonNull(preference);

        return (T) preference;
    }

    @NonNull
    protected final MaterialAlertDialogBuilder createBuilder(@NonNull final DialogPreference pref) {
        final Context context = requireContext();
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(pref.getDialogTitle() != null ? pref.getDialogTitle() : pref.getTitle());
        final Drawable icon = pref.getDialogIcon();
        if (icon != null) {
            builder.setIcon(icon);
        }
        return builder;
    }

    @NonNull
    protected final CharSequence getPositiveButtonText(@NonNull final DialogPreference pref) {
        return pref.getPositiveButtonText() != null
                ? pref.getPositiveButtonText()
                : getText(android.R.string.ok);
    }

    @NonNull
    protected final CharSequence getNegativeButtonText(@NonNull final DialogPreference pref) {
        return pref.getNegativeButtonText() != null
                ? pref.getNegativeButtonText()
                : getText(android.R.string.cancel);
    }

    protected final void requestInputMethod(@NonNull final Dialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }
}
