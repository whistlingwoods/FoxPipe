package org.schabi.newpipe.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.XmlRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreference;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.Objects;

public abstract class BasePreferenceFragment extends PreferenceFragmentCompat {
    protected final String TAG = getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
    protected static final boolean DEBUG = MainActivity.DEBUG;

    SharedPreferences defaultPreferences;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(requireActivity());
        super.onCreate(savedInstanceState);
    }

    protected void addPreferencesFromResourceRegistry() {
        inflatePreferences(
                SettingsResourceRegistry.getInstance().getPreferencesResId(this.getClass()));
    }

    protected final void inflatePreferences(@XmlRes final int preferencesResId) {
        addPreferencesFromResource(preferencesResId);
        applyMaterialSwitchWidgets(getPreferenceScreen());
    }

    @Override
    public void onViewCreated(@NonNull final View rootView,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        setDivider(null);
        ThemeHelper.setTitleToAppCompatActivity(getActivity(), getPreferenceScreen().getTitle());
    }

    @Override
    public void onResume() {
        super.onResume();
        ThemeHelper.setTitleToAppCompatActivity(getActivity(), getPreferenceScreen().getTitle());
    }

    @NonNull
    public final <T extends Preference> T requirePreference(@StringRes final int resId) {
        final T preference = findPreference(getString(resId));
        Objects.requireNonNull(preference);
        return preference;
    }

    private void applyMaterialSwitchWidgets(@Nullable final Preference preference) {
        if (preference == null) {
            return;
        }

        if (preference instanceof SwitchPreferenceCompat) {
            preference.setWidgetLayoutResource(R.layout.preference_widget_material_switch_compat);
        } else if (preference instanceof SwitchPreference) {
            preference.setWidgetLayoutResource(R.layout.preference_widget_material_switch);
        }

        if (!(preference instanceof PreferenceGroup)) {
            return;
        }

        final PreferenceGroup preferenceGroup = (PreferenceGroup) preference;
        for (int i = 0; i < preferenceGroup.getPreferenceCount(); i++) {
            applyMaterialSwitchWidgets(preferenceGroup.getPreference(i));
        }
    }
}
