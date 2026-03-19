package org.schabi.newpipe.settings.dialog;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;

import java.util.Objects;

public final class MaterialListPreferenceDialogFragment
        extends MaterialPreferenceDialogFragment<ListPreference> {
    private static final String SAVE_STATE_INDEX =
            "MaterialListPreferenceDialogFragment.index";
    private static final String SAVE_STATE_ENTRIES =
            "MaterialListPreferenceDialogFragment.entries";
    private static final String SAVE_STATE_ENTRY_VALUES =
            "MaterialListPreferenceDialogFragment.entryValues";

    private int clickedDialogEntryIndex;
    @Nullable
    private CharSequence[] entries;
    @Nullable
    private CharSequence[] entryValues;

    @NonNull
    public static MaterialListPreferenceDialogFragment newInstance(@NonNull final String key) {
        final MaterialListPreferenceDialogFragment fragment =
                new MaterialListPreferenceDialogFragment();
        final Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            final ListPreference preference = requirePreference();
            clickedDialogEntryIndex = preference.findIndexOfValue(preference.getValue());
            entries = preference.getEntries();
            entryValues = preference.getEntryValues();
        } else {
            clickedDialogEntryIndex = savedInstanceState.getInt(SAVE_STATE_INDEX, 0);
            entries = savedInstanceState.getCharSequenceArray(SAVE_STATE_ENTRIES);
            entryValues = savedInstanceState.getCharSequenceArray(SAVE_STATE_ENTRY_VALUES);
        }

        if (entries == null || entryValues == null) {
            throw new IllegalStateException("ListPreference requires entries and entryValues");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final ListPreference preference = requirePreference();
        return createBuilder(preference)
                .setNegativeButton(getNegativeButtonText(preference), null)
                .setSingleChoiceItems(entries, clickedDialogEntryIndex, (dialog, which) -> {
                    clickedDialogEntryIndex = which;
                    persistValue();
                    dialog.dismiss();
                })
                .create();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SAVE_STATE_INDEX, clickedDialogEntryIndex);
        outState.putCharSequenceArray(SAVE_STATE_ENTRIES, entries);
        outState.putCharSequenceArray(SAVE_STATE_ENTRY_VALUES, entryValues);
    }

    private void persistValue() {
        if (clickedDialogEntryIndex < 0 || entryValues == null) {
            return;
        }

        final ListPreference preference = requirePreference();
        final String value = Objects.requireNonNull(
                entryValues[clickedDialogEntryIndex]).toString();
        if (preference.callChangeListener(value)) {
            preference.setValue(value);
        }
    }
}
