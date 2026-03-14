package org.schabi.newpipe.settings;

import static android.app.Activity.RESULT_OK;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.Collections;
import java.util.Set;

public abstract class BaseAccountSettingsFragment extends BasePreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    protected static final int REQUEST_LOGIN = 1;

    protected Preference login;
    protected Preference logout;
    protected Preference overrideSwitch;
    protected Preference overrideValue;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(getPreferenceResource());
        initializePreferences();
        setupClickListeners();
        refreshPreferenceState();
    }

    @Override
    public void onResume() {
        super.onResume();
        defaultPreferences.registerOnSharedPreferenceChangeListener(this);
        refreshPreferenceState();
    }

    @Override
    public void onPause() {
        defaultPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    protected abstract int getPreferenceResource();

    protected abstract Class<?> getLoginActivityClass();

    protected abstract String getCookiesKey();

    protected abstract String getOverrideSwitchKey();

    protected abstract String getOverrideValueKey();

    protected abstract boolean shouldCheckOverrideKeys();

    protected abstract void handleLoginResult(@NonNull Intent data);

    protected abstract void performLogout();

    protected Set<String> getServiceRefreshPreferenceKeys() {
        return Collections.emptySet();
    }

    private void initializePreferences() {
        login = requirePreference(R.string.login_key);
        logout = requirePreference(R.string.logout_key);

        if (shouldCheckOverrideKeys()) {
            overrideSwitch = findPreference(getOverrideSwitchKey());
            overrideValue = findPreference(getOverrideValueKey());
        }
    }

    private void setupClickListeners() {
        login.setOnPreferenceClickListener(preference -> {
            final Intent intent = new Intent(requireContext(), getLoginActivityClass());
            startActivityForResult(intent, REQUEST_LOGIN);
            return true;
        });

        logout.setOnPreferenceClickListener(preference -> {
            performLogout();
            return true;
        });
    }

    protected final void refreshPreferenceState() {
        updateLoginLogoutState();
        configureOverridePreferences();
    }

    private void updateLoginLogoutState() {
        final boolean hasCredentials = !defaultPreferences.getString(getCookiesKey(), "")
                .isEmpty();
        login.setEnabled(!hasCredentials);
        logout.setEnabled(hasCredentials);
    }

    private void configureOverridePreferences() {
        if (!shouldCheckOverrideKeys() || overrideValue == null) {
            return;
        }

        overrideValue.setEnabled(defaultPreferences.getBoolean(getOverrideSwitchKey(), false));
    }

    protected final void onLoginSuccess() {
        ServiceHelper.initServices(requireContext());
        Toast.makeText(requireContext(), R.string.success, Toast.LENGTH_SHORT).show();
        refreshPreferenceState();
    }

    protected final void onLogoutSuccess() {
        ServiceHelper.initServices(requireContext());
        Toast.makeText(requireContext(), R.string.success, Toast.LENGTH_SHORT).show();
        refreshPreferenceState();
    }

    @Override
    public void onSharedPreferenceChanged(
            final SharedPreferences sharedPreferences,
            final String key
    ) {
        boolean shouldRefreshService = getServiceRefreshPreferenceKeys().contains(key);

        if (shouldCheckOverrideKeys()) {
            if (getOverrideSwitchKey().equals(key)) {
                configureOverridePreferences();
                shouldRefreshService = true;
            } else if (getOverrideValueKey().equals(key)) {
                shouldRefreshService = true;
            }
        }

        if (shouldRefreshService) {
            ServiceHelper.initServices(requireContext());
        }
    }

    @Override
    public void onActivityResult(
            final int requestCode,
            final int resultCode,
            final Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_LOGIN && resultCode == RESULT_OK && data != null) {
            handleLoginResult(data);
        }
    }
}
