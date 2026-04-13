package org.schabi.newpipe.settings;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.local.blockedchannel.BlockedChannelManager;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class BlockedChannelsSettingsFragment extends BasePreferenceFragment {
    private BlockedChannelManager blockedChannelManager;
    private CompositeDisposable disposables;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        blockedChannelManager = new BlockedChannelManager(getActivity());
        disposables = new CompositeDisposable();

        final Preference manageBlockedChannelsPreference =
                requirePreference(R.string.manage_blocked_channels_key);
        manageBlockedChannelsPreference.setOnPreferenceClickListener(preference -> {
            showBlockedChannelsDialog(requireContext(), blockedChannelManager, disposables);
            return true;
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposables != null) {
            disposables.dispose();
        }
    }

    private static void showBlockedChannelsDialog(@NonNull final Context context,
                                                  final BlockedChannelManager blockedChannelManager,
                                                  final CompositeDisposable disposables) {
        disposables.add(blockedChannelManager.blockedChannels()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(blockedChannels -> {
                    if (blockedChannels.isEmpty()) {
                        Toast.makeText(context, "No blocked channels", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    final String[] channelNames = blockedChannels.stream()
                            .map(channel -> channel.getName() != null ? channel.getName() : channel.getUrl())
                            .toArray(String[]::new);

                    new AlertDialog.Builder(context)
                            .setTitle(R.string.blocked_channels_list)
                            .setItems(channelNames, (dialog, which) -> {
                                final org.schabi.newpipe.database.blockedchannel.BlockedChannelEntity selectedChannel = blockedChannels.get(which);
                                showUnblockConfirmationDialog(context, blockedChannelManager,
                                        selectedChannel, disposables);
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                }, throwable -> {
                    Toast.makeText(context, "Error loading blocked channels",
                            Toast.LENGTH_SHORT).show();
                }));
    }

    private static void showUnblockConfirmationDialog(@NonNull final Context context,
                                                      final BlockedChannelManager blockedChannelManager,
                                                      final org.schabi.newpipe.database.blockedchannel.BlockedChannelEntity blockedChannel,
                                                      final CompositeDisposable disposables) {
        final String channelName = blockedChannel.getName() != null ? blockedChannel.getName() : blockedChannel.getUrl();

        new AlertDialog.Builder(context)
                .setTitle("Unblock channel")
                .setMessage("Do you want to unblock \"" + channelName + "\"?")
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    disposables.add(blockedChannelManager.unblockChannel(
                                    blockedChannel.getServiceId(), blockedChannel.getUrl())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(() -> {
                                Toast.makeText(context, "Channel unblocked",
                                        Toast.LENGTH_SHORT).show();
                            }, throwable -> {
                                Toast.makeText(context, "Failed to unblock channel",
                                        Toast.LENGTH_SHORT).show();
                            }));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
