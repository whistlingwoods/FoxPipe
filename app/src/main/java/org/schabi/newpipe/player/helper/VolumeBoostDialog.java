package org.schabi.newpipe.player.helper;

import static org.schabi.newpipe.player.helper.VolumeBoostAudioProcessor.MAXIMUM_VOLUME_BOOST;
import static org.schabi.newpipe.player.helper.VolumeBoostAudioProcessor.MINIMUM_VOLUME_BOOST;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.DialogVolumeBoostBinding;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.util.SimpleOnSeekBarChangeListener;

public final class VolumeBoostDialog extends DialogFragment {
    private static final int MIN_PERCENT = Math.round(MINIMUM_VOLUME_BOOST * 100);
    private static final int MAX_PERCENT = Math.round(MAXIMUM_VOLUME_BOOST * 100);
    private static final int STEP_PERCENT = 25;

    // The player belongs to the service and can outlive a rotation of the dialog's activity.
    public static final class Session extends ViewModel {
        @Nullable private Player player;
        private float initialGain;
        private boolean initialAutomatic;
        private boolean initialMuted;
    }

    @Nullable private Player initialPlayer;
    private Session session;

    public static VolumeBoostDialog newInstance(@NonNull final Player player) {
        final VolumeBoostDialog dialog = new VolumeBoostDialog();
        dialog.initialPlayer = player;
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        session = new ViewModelProvider(this).get(Session.class);
        if (initialPlayer != null) {
            session.player = initialPlayer;
            session.initialGain = initialPlayer.getVolumeBoost();
            session.initialAutomatic = initialPlayer.isAutoVolumeBoostEnabled();
            session.initialMuted = initialPlayer.isMuted();
            initialPlayer = null;
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.volume_boost);
        final Player player = session.player;
        if (player == null || player.exoPlayerIsNull()) {
            // Android may restore the dialog after the playback service's process has died.
            dismiss();
            return builder.create();
        }

        final DialogVolumeBoostBinding binding = DialogVolumeBoostBinding.inflate(
                getLayoutInflater());
        binding.volumeBoostMinimumText.setText(PlayerHelper.formatPitch(MINIMUM_VOLUME_BOOST));
        binding.volumeBoostMaximumText.setText(PlayerHelper.formatPitch(MAXIMUM_VOLUME_BOOST));
        final String step = PlayerHelper.formatPitch(STEP_PERCENT / 100.0);
        binding.volumeBoostStepDown.setText("−" + step);
        binding.volumeBoostStepUp.setText("+" + step);
        final SeekBar slider = binding.volumeBoostSeekbar;
        slider.setMax(MAX_PERCENT - MIN_PERCENT);
        slider.setProgress(Math.round(player.getVolumeBoost() * 100) - MIN_PERCENT);
        slider.setKeyProgressIncrement(1);
        updateGainLabel(binding, player.getVolumeBoost());
        binding.autoVolumeBoostSwitch.setChecked(player.isAutoVolumeBoostEnabled());
        enableManualControls(binding, !player.isAutoVolumeBoostEnabled());
        binding.muteSwitch.setChecked(player.isMuted());

        slider.setOnSeekBarChangeListener(new SimpleOnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(@NonNull final SeekBar seekBar, final int progress,
                                          final boolean fromUser) {
                if (fromUser) {
                    applyGain(binding, player, progress);
                }
            }
        });
        binding.volumeBoostStepDown.setOnClickListener(view ->
                applyGain(binding, player, slider.getProgress() - STEP_PERCENT));
        binding.volumeBoostStepUp.setOnClickListener(view ->
                applyGain(binding, player, slider.getProgress() + STEP_PERCENT));
        binding.autoVolumeBoostSwitch.setOnCheckedChangeListener((view, checked) -> {
            enableManualControls(binding, !checked);
            player.setVolumeBoost(player.getVolumeBoost(), checked);
        });
        binding.muteSwitch.setOnCheckedChangeListener((view, checked) -> setMuted(player, checked));

        return builder.setView(binding.getRoot())
                .setNegativeButton(R.string.cancel, (dialog, which) -> restoreInitialSettings())
                .setNeutralButton(R.string.playback_reset, (dialog, which) -> {
                    player.setVolumeBoost(MINIMUM_VOLUME_BOOST, false);
                    setMuted(player, false);
                })
                .setPositiveButton(R.string.ok, null)
                .create();
    }

    private static void applyGain(final DialogVolumeBoostBinding binding, final Player player,
                                  final int progress) {
        binding.volumeBoostSeekbar.setProgress(progress);
        final float gain = (binding.volumeBoostSeekbar.getProgress() + MIN_PERCENT) / 100f;
        updateGainLabel(binding, gain);
        player.setVolumeBoost(gain, false);
    }

    private static void updateGainLabel(final DialogVolumeBoostBinding binding, final float gain) {
        final String label = PlayerHelper.formatPitch(gain);
        binding.volumeBoostCurrentText.setText(label);
        ViewCompat.setStateDescription(binding.volumeBoostSeekbar, label);
    }

    private static void enableManualControls(final DialogVolumeBoostBinding binding,
                                            final boolean enabled) {
        binding.volumeBoostSeekbar.setEnabled(enabled);
        binding.volumeBoostStepDown.setEnabled(enabled);
        binding.volumeBoostStepUp.setEnabled(enabled);
        binding.manualControls.setAlpha(enabled ? 1f : 0.4f);
    }

    private static void setMuted(final Player player, final boolean muted) {
        if (!player.exoPlayerIsNull() && player.isMuted() != muted) {
            player.toggleMute();
        }
    }

    private void restoreInitialSettings() {
        final Player player = session.player;
        if (player != null) {
            player.setVolumeBoost(session.initialGain, session.initialAutomatic);
            setMuted(player, session.initialMuted);
        }
    }

    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {
        super.onCancel(dialog);
        restoreInitialSettings();
    }
}
