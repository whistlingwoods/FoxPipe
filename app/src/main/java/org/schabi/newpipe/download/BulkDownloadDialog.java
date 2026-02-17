/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.BulkDownloadDialogBinding;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for configuring bulk playlist downloads.
 */
public class BulkDownloadDialog extends DialogFragment {
    private static final String KEY_PLAYLIST_INFO = "playlist_info";
    private static final String KEY_STREAM_ITEMS = "stream_items";

    private BulkDownloadDialogBinding binding;
    private PlaylistInfo playlistInfo;
    private List<StreamInfoItem> streamItems;

    /**
     * Creates a new instance of the bulk download dialog.
     *
     * @param playlistInfo the playlist info
     * @param streamItems  all stream items to download
     * @return new dialog instance
     */
    public static BulkDownloadDialog newInstance(
            final PlaylistInfo playlistInfo,
            final List<StreamInfoItem> streamItems) {
        final BulkDownloadDialog dialog = new BulkDownloadDialog();
        final Bundle args = new Bundle();

        // Store playlist info
        args.putSerializable(KEY_PLAYLIST_INFO, new SerializablePlaylistInfo(playlistInfo));

        // Store stream items (only essential data to avoid size issues)
        args.putSerializable(KEY_STREAM_ITEMS, new ArrayList<>(streamItems));

        dialog.setArguments(args);
        return dialog;
    }

    /**
     * Creates a new instance of the bulk download dialog for local playlists.
     * This overload allows creating a dialog without a full PlaylistInfo object.
     *
     * @param playlistName the name of the playlist
     * @param serviceId    the service ID
     * @param streamItems  all stream items to download
     * @return new dialog instance
     */
    public static BulkDownloadDialog newInstance(
            final String playlistName,
            final int serviceId,
            final List<StreamInfoItem> streamItems) {
        final BulkDownloadDialog dialog = new BulkDownloadDialog();
        final Bundle args = new Bundle();

        // Create minimal playlist info for local playlists
        final SerializablePlaylistInfo playlistInfo = new SerializablePlaylistInfo(
            playlistName,
            "", // No URL for local playlists
            serviceId,
            streamItems.size(),
            "", // No uploader for local playlists
            null // No thumbnail
        );

        args.putSerializable(KEY_PLAYLIST_INFO, playlistInfo);
        args.putSerializable(KEY_STREAM_ITEMS, new ArrayList<>(streamItems));

        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            @SuppressWarnings("unchecked")
            final ArrayList<StreamInfoItem> items =
                (ArrayList<StreamInfoItem>) getArguments().getSerializable(KEY_STREAM_ITEMS);
            if (items != null) {
                this.streamItems = items;
            }
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final Context context = requireContext();
        binding = BulkDownloadDialogBinding.inflate(LayoutInflater.from(context));

        setupViews();

        return new AlertDialog.Builder(context)
            .setTitle(getString(R.string.bulk_download_dialog_title, streamItems.size()))
            .setView(binding.getRoot())
            .setPositiveButton(R.string.download, (dialog, which) -> startBulkDownload())
            .setNegativeButton(R.string.cancel, null)
            .create();
    }

    private void setupViews() {
        // Set item count text
        binding.itemCountText.setText(
            getString(R.string.bulk_download_dialog_title, streamItems.size())
        );

        // Format selection listener
        binding.formatRadioGroup.setOnCheckedChangeListener(
            (group, checkedId) -> updateQualitySpinner()
        );

        // Download folder click listener
        binding.downloadFolderLayout.setOnClickListener(v -> {
            // TODO: Open folder picker
            Toast.makeText(requireContext(),
                "Folder picker coming soon!", Toast.LENGTH_SHORT).show();
        });

        // Initialize quality spinner with placeholder
        // TODO: Populate with actual quality options
        binding.downloadFolderText.setText(getDefaultDownloadPath());

        // Tag audio checkbox - only show for audio downloads
        binding.tagAudioCheckbox.setVisibility(View.VISIBLE);
        binding.formatRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            final boolean isAudio = checkedId == R.id.audio_radio_button;
            binding.tagAudioCheckbox.setVisibility(isAudio ? View.VISIBLE : View.GONE);
            if (!isAudio) {
                binding.tagAudioCheckbox.setChecked(false);
            }
        });
    }

    private void updateQualitySpinner() {
        // TODO: Update spinner with appropriate quality options based on format selection
        // For now, this is a placeholder
    }

    private String getDefaultDownloadPath() {
        // TODO: Get actual default download path from preferences
        return "/storage/emulated/0/NewPipe";
    }

    private void startBulkDownload() {
        final boolean isAudio = binding.audioRadioButton.isChecked();
        final boolean tagMetadata = binding.tagAudioCheckbox.isChecked() && isAudio;
        final boolean offlineMapping = binding.offlineMappingCheckbox.isChecked();
        final String downloadFolder = binding.downloadFolderText.getText().toString();

        // Create configuration
        final BulkDownloadInitiator.BulkDownloadConfig config =
            new BulkDownloadInitiator.BulkDownloadConfig(
                isAudio,
                tagMetadata,
                offlineMapping,
                downloadFolder
            );

        // Get playlist info from stored data
        final SerializablePlaylistInfo storedPlaylistInfo = getSerializablePlaylistInfo();

        // Start bulk download
        BulkDownloadInitiator.startBulkDownload(
            requireContext(),
            storedPlaylistInfo,
            streamItems,
            config
        );
    }

    private SerializablePlaylistInfo getSerializablePlaylistInfo() {
        // Get from serializable data stored in arguments
        if (getArguments() != null) {
            return (SerializablePlaylistInfo) getArguments().getSerializable(KEY_PLAYLIST_INFO);
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /**
     * Serializable wrapper for PlaylistInfo essentials.
     * Package-private to allow access from BulkDownloadInitiator.
     */
    static class SerializablePlaylistInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        final String name;
        final String url;
        final int serviceId;
        final long streamCount;
        final String uploaderName;
        final String thumbnailUrl;

        SerializablePlaylistInfo(final PlaylistInfo info) {
            this.name = info.getName();
            this.url = info.getUrl();
            this.serviceId = info.getServiceId();
            this.streamCount = info.getStreamCount();
            this.uploaderName = info.getUploaderName();
            this.thumbnailUrl = info.getThumbnails().isEmpty()
                ? null : info.getThumbnails().get(0).getUrl();
        }

        /**
         * Constructor for local playlists without a full PlaylistInfo object.
         *
         * @param name          the playlist name
         * @param url           the playlist URL (empty for local playlists)
         * @param serviceId     the service ID
         * @param streamCount   the number of streams
         * @param uploaderName  the uploader name (empty for local playlists)
         * @param thumbnailUrl  the thumbnail URL (null for local playlists)
         */
        SerializablePlaylistInfo(final String name, final String url, final int serviceId,
                                final long streamCount, final String uploaderName,
                                final String thumbnailUrl) {
            this.name = name;
            this.url = url;
            this.serviceId = serviceId;
            this.streamCount = streamCount;
            this.uploaderName = uploaderName;
            this.thumbnailUrl = thumbnailUrl;
        }
    }
}
