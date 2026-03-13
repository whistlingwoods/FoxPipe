package org.schabi.newpipe.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ServiceHelper;

public class SponsorBlockSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        final Preference enablePreference = requirePreference(R.string.sponsor_block_enable_key);
        enablePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            ServiceHelper.initServices(requireContext());
            return true;
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        ServiceHelper.initServices(requireContext());
    }
}
