package org.schabi.newpipe.download;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.views.NewPipeTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.view.ContextThemeWrapper; // تأكد من إضافة هذا الاستيراد
import org.schabi.newpipe.util.ThemeHelper; // NewPipe يستخدم هذا المساعد

public class PlaylistDownloadDialog extends BottomSheetDialogFragment {

    private List<StreamInfoItem> streamList;
    private RecyclerView recyclerView;
    private Spinner qualitySpinner;
    private CheckBox selectAllCheckbox;
    private Button startButton;
    private NewPipeTextView totalSizeTextView; // نص الحجم الإجمالي
    private ItemsAdapter adapter;
    
    // معدل البت التقريبي (kbps) لكل جودة
    private long currentBitrate = 0;

    public PlaylistDownloadDialog(List<StreamInfoItem> items) {
        this.streamList = new ArrayList<>(items);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        int themeId = ThemeHelper.isLightThemeSelected(getActivity()) 
                ? org.schabi.newpipe.R.style.LightTheme 
                : org.schabi.newpipe.R.style.DarkTheme;

        Context contextThemeWrapper = new ContextThemeWrapper(getActivity(), themeId);
        LayoutInflater localInflater = inflater.cloneInContext(contextThemeWrapper);
        
        return localInflater.inflate(R.layout.dialog_playlist_download, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ((View) view.getParent()).setBackgroundColor(Color.TRANSPARENT);

        qualitySpinner = view.findViewById(R.id.qualitySpinner);
        selectAllCheckbox = view.findViewById(R.id.selectAllCheckbox);
        recyclerView = view.findViewById(R.id.itemsRecyclerView);
        startButton = view.findViewById(R.id.startDownloadButton);
        totalSizeTextView = view.findViewById(R.id.totalSizeTextView); // ربط النص الجديد

        setupQualitySpinner();
        setupRecyclerView();

        selectAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (adapter != null) {
                adapter.selectAll(isChecked);
                updateTotalSize(); // تحديث الإجمالي عند تحديد الكل
            }
        });

        startButton.setOnClickListener(v -> startDownload());
    }

    private void setupQualitySpinner() {
        String[] options = {
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

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(getActivity(), R.layout.spinner_item_newpipe, options);
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_item_newpipe);
        qualitySpinner.setAdapter(spinnerAdapter);

        // مستمع عند تغيير الجودة لتحديث الأحجام
        qualitySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedQuality = options[position];
                currentBitrate = getEstimatedBitrate(selectedQuality);
                
                if (adapter != null) {
                    adapter.updateBitrate(currentBitrate); // تحديث الأحجام الفردية
                }
                updateTotalSize(); // تحديث الحجم الإجمالي
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ItemsAdapter(streamList, this::updateTotalSize); // تمرير دالة التحديث
        recyclerView.setAdapter(adapter);
    }

    // حساب الحجم الإجمالي للعناصر المحددة
    private void updateTotalSize() {
        if (adapter == null) return;
        List<StreamInfoItem> selected = adapter.getSelectedItems();
        long totalBytes = 0;
        
        for (StreamInfoItem item : selected) {
            // الحجم = (المدة بالثواني * البت ريت) / 8 لتحويلها لبايت
            totalBytes += (item.getDuration() * (currentBitrate * 1000)) / 8;
        }
        
        totalSizeTextView.setText("Total Est. Size: " + formatFileSize(totalBytes));
    }

    // تقدير معدل البت (kbps) بناءً على الجودة (Video + Audio)
    private long getEstimatedBitrate(String quality) {
        switch (quality) {
            case PlaylistDownloadLogic.QUAL_1080P: return 4500; // ~4.5 Mbps
            case PlaylistDownloadLogic.QUAL_720P: return 2500;
            case PlaylistDownloadLogic.QUAL_480P: return 1200;
            case PlaylistDownloadLogic.QUAL_360P: return 700;
            case PlaylistDownloadLogic.QUAL_240P: return 350;
            case PlaylistDownloadLogic.QUAL_144P: return 150; // Video + Audio
            
            case PlaylistDownloadLogic.QUAL_AUDIO_HIGH: return 160;
            case PlaylistDownloadLogic.QUAL_AUDIO_MEDIUM: return 128;
            case PlaylistDownloadLogic.QUAL_AUDIO_LOW: return 48;
            
            case PlaylistDownloadLogic.QUAL_BEST_VIDEO: default: return 5000;
        }
    }

    // دالة مساعدة لتنسيق الحجم
    public static String formatFileSize(long size) {
        if (size <= 0) return "0 MB";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format(Locale.US, "%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private void startDownload() {
        if(adapter == null) return;
        List<StreamInfoItem> selectedItems = adapter.getSelectedItems();
        if (selectedItems.isEmpty()) {
            Toast.makeText(getContext(), "No items selected", Toast.LENGTH_SHORT).show();
            return;
        }

        String quality = (String) qualitySpinner.getSelectedItem();
        Intent serviceIntent = new Intent(getContext(), PlaylistEnqueuerService.class);
        serviceIntent.setAction(PlaylistEnqueuerService.ACTION_ENQUEUE_PLAYLIST);
        serviceIntent.putExtra(PlaylistEnqueuerService.EXTRA_QUALITY, quality);
        
        ArrayList<String> urls = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        for (StreamInfoItem item : selectedItems) {
            urls.add(item.getUrl());
            titles.add(item.getName());
        }
        
        serviceIntent.putStringArrayListExtra(PlaylistEnqueuerService.EXTRA_URLS, urls);
        serviceIntent.putStringArrayListExtra(PlaylistEnqueuerService.EXTRA_TITLES, titles);

        requireContext().startForegroundService(serviceIntent);
        dismiss();
    }

    private static class ItemsAdapter extends RecyclerView.Adapter<ItemsAdapter.ViewHolder> {
        private final List<StreamInfoItem> items;
        private final boolean[] selected;
        private long currentBitrate = 0;
        private final Runnable onSelectionChanged; // Callback لتحديث الإجمالي

        ItemsAdapter(List<StreamInfoItem> items, Runnable onSelectionChanged) {
            this.items = items;
            this.onSelectionChanged = onSelectionChanged;
            this.selected = new boolean[items.size()];
            for(int i=0; i<selected.length; i++) selected[i] = true;
        }

        void updateBitrate(long bitrate) {
            this.currentBitrate = bitrate;
            notifyDataSetChanged(); // إعادة رسم القائمة بالأحجام الجديدة
        }

        void selectAll(boolean isSelected) {
            for(int i=0; i<selected.length; i++) selected[i] = isSelected;
            notifyDataSetChanged();
        }
        
        List<StreamInfoItem> getSelectedItems() {
            List<StreamInfoItem> result = new ArrayList<>();
            for(int i=0; i<items.size(); i++) {
                if(selected[i]) result.add(items.get(i));
            }
            return result;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
             View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist_selection, parent, false);
             return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            StreamInfoItem item = items.get(position);
            
            holder.title.setText(item.getName());
            holder.uploader.setText(item.getUploaderName());
            holder.checkBox.setChecked(selected[position]);
            
            // حساب الحجم الفردي
            if (currentBitrate > 0 && item.getDuration() > 0) {
                long estimatedSize = (item.getDuration() * (currentBitrate * 1000)) / 8;
                holder.sizeText.setText(formatFileSize(estimatedSize));
                holder.sizeText.setVisibility(View.VISIBLE);
            } else {
                holder.sizeText.setVisibility(View.GONE);
            }

            org.schabi.newpipe.util.image.PicassoHelper.loadThumbnail(item.getThumbnails())
                .into(holder.thumbnail);
            
            holder.itemView.setOnClickListener(v -> {
                holder.checkBox.toggle();
                updateSelection(holder.getBindingAdapterPosition(), holder.checkBox.isChecked());
            });
            
            holder.checkBox.setOnClickListener(v -> {
                updateSelection(holder.getBindingAdapterPosition(), holder.checkBox.isChecked());
            });
        }

        private void updateSelection(int position, boolean isChecked) {
            if (position != RecyclerView.NO_POSITION) {
                selected[position] = isChecked;
                if (onSelectionChanged != null) onSelectionChanged.run(); // تحديث الإجمالي
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final android.widget.CheckBox checkBox;
            final NewPipeTextView title;
            final NewPipeTextView uploader;
            final NewPipeTextView sizeText; // النص الجديد للحجم
            final android.widget.ImageView thumbnail;

            ViewHolder(View itemView) {
                super(itemView);
                checkBox = itemView.findViewById(R.id.itemCheckBox);
                title = itemView.findViewById(R.id.itemVideoTitleView);
                uploader = itemView.findViewById(R.id.itemUploaderView);
                sizeText = itemView.findViewById(R.id.itemSizeView); // تأكد من إضافته للـ XML
                thumbnail = itemView.findViewById(R.id.itemThumbnailView);
            }
        }
    }
}