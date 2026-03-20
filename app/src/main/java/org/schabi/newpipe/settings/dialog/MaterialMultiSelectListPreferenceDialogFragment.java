package org.schabi.newpipe.settings.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.MultiSelectListPreference;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;

import org.schabi.newpipe.R;

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
        final RecyclerView recyclerView = (RecyclerView) LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_preference_choice_list, null, false);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(new MultiChoiceAdapter());
        return createBuilder(preference)
                .setNegativeButton(getNegativeButtonText(preference), null)
                .setPositiveButton(getPositiveButtonText(preference), (dialog, which) ->
                        persistValues())
                .setView(recyclerView)
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

    private final class MultiChoiceAdapter
            extends RecyclerView.Adapter<MultiChoiceAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                             final int viewType) {
            final MaterialCheckBox view = (MaterialCheckBox) LayoutInflater
                    .from(parent.getContext())
                    .inflate(R.layout.item_preference_multi_choice, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
            holder.bind(position);
        }

        @Override
        public int getItemCount() {
            return Objects.requireNonNull(entries).length;
        }

        final class ViewHolder extends RecyclerView.ViewHolder {
            private final MaterialCheckBox checkBox;

            ViewHolder(@NonNull final MaterialCheckBox itemView) {
                super(itemView);
                checkBox = itemView;
            }

            void bind(final int position) {
                final String value = Objects.requireNonNull(entryValues[position]).toString();
                checkBox.setText(entries[position]);
                checkBox.setChecked(newValues.contains(value));
                itemView.setOnClickListener(v -> syncValue(value, checkBox.isChecked()));
            }

            private void syncValue(@NonNull final String value, final boolean checked) {
                if (checked) {
                    preferenceChanged |= newValues.add(value);
                } else {
                    preferenceChanged |= newValues.remove(value);
                }
            }
        }
    }
}
