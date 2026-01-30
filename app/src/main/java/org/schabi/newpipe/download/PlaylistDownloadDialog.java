package org.schabi.newpipe.download;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard;
import org.schabi.newpipe.streams.io.StoredDirectoryHelper;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.util.image.PicassoHelper;
import org.schabi.newpipe.views.NewPipeTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A dialog fragment that allows the user to select streams from a playlist
 * and download them with specific quality settings.
 */
public class PlaylistDownloadDialog extends BottomSheetDialogFragment {

    private static final String TAG = "PlaylistDownloadDialog";
    private static final String ARG_STREAM_LIST = "arg_stream_list";
    private static final int BITRATE_MULTIPLIER = 1000;
    private static final int BITS_PER_BYTE = 8;
    private static final int BYTES_IN_KB = 1024;

    private List<StreamInfoItem> streamList;
    private RecyclerView recyclerView;
    private Spinner qualitySpinner;
    private CheckBox selectAllCheckbox;
    private Button startButton;
    private NewPipeTextView totalSizeTextView;
    private ItemsAdapter adapter;

    private long currentBitrate = 0;
    private boolean askForSavePath;
    private Uri selectedDirectoryUri = null;

    private final ActivityResultLauncher<Intent> requestPlaylistFolderLauncher =
            registerForActivityResult(
                    new StartActivityForResult(),
                    this::requestPlaylistFolderResult
            );

    /**
     * Required empty public constructor for Fragment re-instantiation.
     * Use {@link #newInstance(List)} to create a new instance.
     */
    public PlaylistDownloadDialog() {
        // Required empty public constructor
    }

    /**
     * Creates a new instance of the playlist download dialog.
     *
     * @param items The list of streams to be displayed for selection.
     * @return A new instance of PlaylistDownloadDialog.
     */
    public static PlaylistDownloadDialog newInstance(final List<StreamInfoItem> items) {
        final PlaylistDownloadDialog fragment = new PlaylistDownloadDialog();
        final Bundle args = new Bundle();
        args.putSerializable(ARG_STREAM_LIST, new ArrayList<>(items));
        fragment.setArguments(args);
        return fragment;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            streamList = (ArrayList<StreamInfoItem>) getArguments()
                    .getSerializable(ARG_STREAM_LIST);
        }
        if (streamList == null) {
            streamList = new ArrayList<>();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final int themeId = ThemeHelper.isLightThemeSelected(getActivity())
                ? org.schabi.newpipe.R.style.LightTheme
                : org.schabi.newpipe.R.style.DarkTheme;

        final Context contextThemeWrapper = new ContextThemeWrapper(getActivity(), themeId);
        final LayoutInflater localInflater = inflater.cloneInContext(contextThemeWrapper);

        return localInflater.inflate(R.layout.dialog_playlist_download, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (view.getParent() instanceof View) {
            ((View) view.getParent()).setBackgroundColor(Color.TRANSPARENT);
        }

        qualitySpinner = view.findViewById(R.id.qualitySpinner);
        selectAllCheckbox = view.findViewById(R.id.selectAllCheckbox);
        recyclerView = view.findViewById(R.id.itemsRecyclerView);
        startButton = view.findViewById(R.id.startDownloadButton);
        totalSizeTextView = view.findViewById(R.id.totalSizeTextView);

        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(requireContext());
        askForSavePath = prefs.getBoolean(
                getString(R.string.downloads_storage_ask),
                false
        );

        setupQualitySpinner();
        setupRecyclerView();

        selectAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (adapter != null) {
                adapter.selectAll(isChecked);
                updateTotalSize();
            }
        });

        startButton.setOnClickListener(v -> startDownload());
    }

    private void setupQualitySpinner() {
        final String[] options = {
            PlaylistDownloadLogic.QUAL_BEST_VIDEO,
            PlaylistDownloadLogic.QUAL_1080P,
            PlaylistDownloadLogic.QUAL_720P,
            PlaylistDownloadLogic.QUAL_480P,
            PlaylistDownloadLogic.QUAL_360P,
            PlaylistDownloadLogic.QUAL_240P,
            PlaylistDownloadLogic.QUAL_144P,
            PlaylistDownloadLogic.QUAL_AUDIO_HIGH,
            PlaylistDownloadLogic.QUAL_AUDIO_MEDIUM,
            PlaylistDownloadLogic.QUAL_AUDIO_LOW
        };

        final ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireActivity(),
                R.layout.spinner_item_newpipe,
                options
        );
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_item_newpipe);
        qualitySpinner.setAdapter(spinnerAdapter);

        qualitySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parent,
                                       final View view,
                                       final int position,
                                       final long id) {
                final String selectedQuality = options[position];
                currentBitrate = getEstimatedBitrate(selectedQuality);

                if (adapter != null) {
                    adapter.updateBitrate(currentBitrate);
                }
                updateTotalSize();
            }

            @Override
            public void onNothingSelected(final AdapterView<?> parent) {
                // No action needed
            }
        });
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ItemsAdapter(streamList, this::updateTotalSize);
        recyclerView.setAdapter(adapter);
    }

    /**
     * calculates the total estimated size of the selected items.
     */
    private void updateTotalSize() {
        if (adapter == null) {
            return;
        }
        final List<StreamInfoItem> selected = adapter.getSelectedItems();
        long totalBytes = 0;

        for (final StreamInfoItem item : selected) {
            totalBytes += (item.getDuration()
                    * (currentBitrate * BITRATE_MULTIPLIER)) / BITS_PER_BYTE;
        }

        totalSizeTextView.setText("Total Est. Size: " + formatFileSize(totalBytes));
    }

    /**
     * Estimates the bitrate in kbps based on the quality string.
     *
     * @param quality The quality string constant.
     * @return The estimated bitrate.
     */
    private long getEstimatedBitrate(final String quality) {
        switch (quality) {
            case PlaylistDownloadLogic.QUAL_1080P:
                return 4500; // ~4.5 Mbps
            case PlaylistDownloadLogic.QUAL_720P:
                return 2500;
            case PlaylistDownloadLogic.QUAL_480P:
                return 1200;
            case PlaylistDownloadLogic.QUAL_360P:
                return 700;
            case PlaylistDownloadLogic.QUAL_240P:
                return 350;
            case PlaylistDownloadLogic.QUAL_144P:
                return 150; // Video + Audio
            case PlaylistDownloadLogic.QUAL_AUDIO_HIGH:
                return 160;
            case PlaylistDownloadLogic.QUAL_AUDIO_MEDIUM:
                return 128;
            case PlaylistDownloadLogic.QUAL_AUDIO_LOW:
                return 48;
            case PlaylistDownloadLogic.QUAL_BEST_VIDEO:
            default:
                return 5000;
        }
    }

    /**
     * Formats a file size in bytes into a human-readable string.
     *
     * @param size The size in bytes.
     * @return Formatted string (e.g., "5.2 MB").
     */
    public static String formatFileSize(final long size) {
        if (size <= 0) {
            return "0 MB";
        }
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        final int digitGroups = (int) (Math.log10(size) / Math.log10(BYTES_IN_KB));
        return String.format(
                Locale.US,
                "%.1f %s",
                size / Math.pow(BYTES_IN_KB, digitGroups),
                units[digitGroups]
        );
    }

    private void startDownload() {
        if (adapter == null) {
            return;
        }
        final List<StreamInfoItem> selectedItems = adapter.getSelectedItems();
        if (selectedItems.isEmpty()) {
            Toast.makeText(getContext(), "No items selected", Toast.LENGTH_SHORT).show();
            return;
        }

        if (askForSavePath) {
            launchFolderPicker();
            return;
        }

        proceedWithDownload(null);
    }

    /**
     * Launches the system folder picker safely.
     */
    private void launchFolderPicker() {
        NoFileManagerSafeGuard.launchSafe(
                requestPlaylistFolderLauncher,
                StoredDirectoryHelper.getPicker(getContext()),
                TAG,
                requireContext()
        );
    }

    /**
     * Handles the result from the folder picker activity.
     *
     * @param result The activity result.
     */
    private void requestPlaylistFolderResult(final ActivityResult result) {
        if (result.getData() == null || result.getResultCode() != Activity.RESULT_OK) {
            return;
        }

        final Uri selectedUri = result.getData().getData();
        if (selectedUri == null) {
            Toast.makeText(getContext(), "Failed to select folder", Toast.LENGTH_SHORT).show();
            return;
        }

        selectedDirectoryUri = selectedUri;
        proceedWithDownload(selectedDirectoryUri);
    }

    /**
     * Enqueues the selected items for download.
     *
     * @param customDirectory The URI of a custom directory to save files to, or null for default.
     */
    private void proceedWithDownload(@Nullable final Uri customDirectory) {
        final List<StreamInfoItem> selectedItems = adapter.getSelectedItems();
        final String quality = (String) qualitySpinner.getSelectedItem();

        final Intent serviceIntent = new Intent(getContext(), PlaylistEnqueuerService.class);
        serviceIntent.setAction(PlaylistEnqueuerService.ACTION_ENQUEUE_PLAYLIST);
        serviceIntent.putExtra(PlaylistEnqueuerService.EXTRA_QUALITY, quality);

        if (customDirectory != null) {
            serviceIntent.putExtra(
                    PlaylistEnqueuerService.EXTRA_CUSTOM_DIRECTORY,
                    customDirectory.toString()
            );
        }

        final ArrayList<String> urls = new ArrayList<>();
        final ArrayList<String> titles = new ArrayList<>();
        for (final StreamInfoItem item : selectedItems) {
            urls.add(item.getUrl());
            titles.add(item.getName());
        }

        serviceIntent.putStringArrayListExtra(PlaylistEnqueuerService.EXTRA_URLS, urls);
        serviceIntent.putStringArrayListExtra(PlaylistEnqueuerService.EXTRA_TITLES, titles);

        requireContext().startForegroundService(serviceIntent);
        dismiss();
    }

    /**
     * Adapter for displaying the list of stream items in the RecyclerView.
     */
    private static class ItemsAdapter extends RecyclerView.Adapter<ItemsAdapter.ViewHolder> {
        private final List<StreamInfoItem> items;
        private final boolean[] selected;
        private long currentBitrate = 0;
        private final Runnable onSelectionChanged;

        /**
         * Creates a new ItemsAdapter.
         *
         * @param items              The list of items to display.
         * @param onSelectionChanged Callback invoked when selection changes.
         */
        ItemsAdapter(final List<StreamInfoItem> items, final Runnable onSelectionChanged) {
            this.items = items;
            this.onSelectionChanged = onSelectionChanged;
            this.selected = new boolean[items.size()];
            for (int i = 0; i < selected.length; i++) {
                selected[i] = true;
            }
        }

        void updateBitrate(final long bitrate) {
            this.currentBitrate = bitrate;
            notifyDataSetChanged();
        }

        void selectAll(final boolean isSelected) {
            for (int i = 0; i < selected.length; i++) {
                selected[i] = isSelected;
            }
            notifyDataSetChanged();
        }

        List<StreamInfoItem> getSelectedItems() {
            final List<StreamInfoItem> result = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                if (selected[i]) {
                    result.add(items.get(i));
                }
            }
            return result;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
            final View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_playlist_selection, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
            final StreamInfoItem item = items.get(position);

            holder.title.setText(item.getName());
            holder.uploader.setText(item.getUploaderName());
            holder.checkBox.setChecked(selected[position]);

            if (currentBitrate > 0 && item.getDuration() > 0) {
                final long estimatedSize = (item.getDuration()
                        * (currentBitrate * BITRATE_MULTIPLIER)) / BITS_PER_BYTE;
                holder.sizeText.setText(formatFileSize(estimatedSize));
                holder.sizeText.setVisibility(View.VISIBLE);
            } else {
                holder.sizeText.setVisibility(View.GONE);
            }

            PicassoHelper.loadThumbnail(item.getThumbnails()).into(holder.thumbnail);

            holder.itemView.setOnClickListener(v -> {
                holder.checkBox.toggle();
                updateSelection(holder.getBindingAdapterPosition(), holder.checkBox.isChecked());
            });

            holder.checkBox.setOnClickListener(v -> {
                updateSelection(holder.getBindingAdapterPosition(), holder.checkBox.isChecked());
            });
        }

        private void updateSelection(final int position, final boolean isChecked) {
            if (position != RecyclerView.NO_POSITION) {
                selected[position] = isChecked;
                if (onSelectionChanged != null) {
                    onSelectionChanged.run();
                }
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        /**
         * ViewHolder class for stream items.
         */
        static class ViewHolder extends RecyclerView.ViewHolder {
            private final CheckBox checkBox;
            private final NewPipeTextView title;
            private final NewPipeTextView uploader;
            private final NewPipeTextView sizeText;
            private final android.widget.ImageView thumbnail;

            ViewHolder(final View itemView) {
                super(itemView);
                checkBox = itemView.findViewById(R.id.itemCheckBox);
                title = itemView.findViewById(R.id.itemVideoTitleView);
                uploader = itemView.findViewById(R.id.itemUploaderView);
                sizeText = itemView.findViewById(R.id.itemSizeView);
                thumbnail = itemView.findViewById(R.id.itemThumbnailView);
            }
        }
    }
}
