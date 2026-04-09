package org.schabi.newpipe.settings.dialog;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;

import org.schabi.newpipe.databinding.DialogEditTextBinding;

public final class MaterialEditTextPreferenceDialogFragment
        extends MaterialPreferenceDialogFragment<EditTextPreference> {
    private static final String SAVE_STATE_TEXT = "MaterialEditTextPreferenceDialogFragment.text";

    @Nullable
    private DialogEditTextBinding dialogBinding;
    @Nullable
    private CharSequence text;

    @NonNull
    public static MaterialEditTextPreferenceDialogFragment newInstance(
            @NonNull final String key) {
        final MaterialEditTextPreferenceDialogFragment fragment =
                new MaterialEditTextPreferenceDialogFragment();
        final Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            text = savedInstanceState.getCharSequence(SAVE_STATE_TEXT);
        } else {
            text = requirePreference().getText();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final EditTextPreference preference = requirePreference();
        dialogBinding = DialogEditTextBinding.inflate(getLayoutInflater());
        dialogBinding.dialogEditText.setHint(preference.getTitle());
        dialogBinding.dialogEditText.setText(text);
        dialogBinding.dialogEditText.setSelection(dialogBinding.dialogEditText.length());

        final Dialog dialog = createBuilder(preference)
                .setView(dialogBinding.getRoot())
                .setNegativeButton(getNegativeButtonText(preference), null)
                .setPositiveButton(getPositiveButtonText(preference), (d, w) -> persistValue())
                .create();
        requestInputMethod(dialog);
        return dialog;
    }

    @Override
    public void onDestroy() {
        dialogBinding = null;
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        final CharSequence currentText = dialogBinding == null
                ? text
                : dialogBinding.dialogEditText.getText();
        outState.putCharSequence(SAVE_STATE_TEXT, currentText);
    }

    private void persistValue() {
        final DialogEditTextBinding binding = dialogBinding;
        if (binding == null) {
            return;
        }

        final EditTextPreference preference = requirePreference();
        final String value = binding.dialogEditText.getText().toString();
        if (preference.callChangeListener(value)) {
            preference.setText(value);
            text = value;
        }
    }
}
