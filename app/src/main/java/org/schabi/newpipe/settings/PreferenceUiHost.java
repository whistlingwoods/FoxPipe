package org.schabi.newpipe.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.recyclerview.widget.RecyclerView;

public interface PreferenceUiHost {
    @Nullable
    Preference findPreferenceByKey(@NonNull String key);

    @NonNull
    RecyclerView getPreferenceListView();

    void scrollToPreferenceItem(@NonNull Preference preference);

    @Nullable
    FragmentActivity getPreferenceHostActivity();
}
