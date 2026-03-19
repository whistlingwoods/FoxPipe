package org.schabi.newpipe.settings.dialog;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.MultiSelectListPreference;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class MaterialMultiSelectListPreferenceDialogFragment
        extends MaterialPreferenceDialogFragment<MultiSelectListPreference> {
    private static final String SAVE_STATE_VALUES =
            "MaterialMultiSelectListPreferenceDialogFragment.values";
    private static final String SAVE_STATE_CHANGED =
            "MaterialMultiSelectListPreferenceDialogFragment.changed";
    private static final String SAVE_STATE_ENTRIES =
            "MaterialMultiSelectListPreferenceDialogFragment.entries";
    private static final String SAVE_STATE_ENTRY_VALUES =
            "MaterialMultiSelectListPreferenceDialogFragment.entryValues";

    @NonNull
    private final Set<String> newValues = new HashSet<>();
    private boolean preferenceChanged;
    @Nullable
    private CharSequence[] entries;
    @Nullable
    private CharSequence[] entryValues;

    @NonNull
    public static MaterialMultiSelectListPreferenceDialogFragment newInstance(
            @NonNull final String key) {
        final MaterialMultiSelectListPreferenceDialogFragment fragment =
                new MaterialMultiSelectListPreferenceDialogFragment();
        final Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        newValues.clear();

        if (savedInstanceState == null) {
            final MultiSelectListPreference preference = requirePreference();
            newValues.addAll(preference.getValues());
            preferenceChanged = false;
            entries = preference.getEntries();
            entryValues = preference.getEntryValues();
        } else {
            newValues.addAll(Objects.requireNonNull(
                    savedInstanceState.getStringArrayList(SAVE_STATE_VALUES)));
            preferenceChanged = savedInstanceState.getBoolean(SAVE_STATE_CHANGED, false);
            entries = savedInstanceState.getCharSequenceArray(SAVE_STATE_ENTRIES);
            entryValues = savedInstanceState.getCharSequenceArray(SAVE_STATE_ENTRY_VALUES);
        }

        if (entries == null || entryValues == null) {
            throw new IllegalStateException(
                    "MultiSelectListPreference requires entries and entryValues");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final MultiSelectListPreference preference = requirePreference();
        final boolean[] checkedItems = buildCheckedItems();
        return createBuilder(preference)
                .setNegativeButton(getNegativeButtonText(preference), null)
                .setPositiveButton(getPositiveButtonText(preference), (dialog, which) ->
                        persistValues())
                .setMultiChoiceItems(entries, checkedItems, (dialog, which, isChecked) -> {
                    final String value = Objects.requireNonNull(entryValues[which]).toString();
                    if (isChecked) {
                        preferenceChanged |= newValues.add(value);
                    } else {
                        preferenceChanged |= newValues.remove(value);
                    }
                })
                .create();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(SAVE_STATE_VALUES, new ArrayList<>(newValues));
        outState.putBoolean(SAVE_STATE_CHANGED, preferenceChanged);
        outState.putCharSequenceArray(SAVE_STATE_ENTRIES, entries);
        outState.putCharSequenceArray(SAVE_STATE_ENTRY_VALUES, entryValues);
    }

    @NonNull
    private boolean[] buildCheckedItems() {
        final CharSequence[] values = Objects.requireNonNull(entryValues);
        final boolean[] checkedItems = new boolean[values.length];
        for (int i = 0; i < values.length; i++) {
            checkedItems[i] = newValues.contains(values[i].toString());
        }
        return checkedItems;
    }

    private void persistValues() {
        if (!preferenceChanged) {
            return;
        }

        final MultiSelectListPreference preference = requirePreference();
        final Set<String> persistedValues = new HashSet<>(newValues);
        if (preference.callChangeListener(persistedValues)) {
            preference.setValues(persistedValues);
        }
        preferenceChanged = false;
    }
}
