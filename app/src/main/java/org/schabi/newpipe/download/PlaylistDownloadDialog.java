package org.schabi.newpipe.download;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import java.util.ArrayList;
import java.util.List;

public class PlaylistDownloadDialog extends BottomSheetDialogFragment {

    private List<StreamInfoItem> streamList;
    private RecyclerView recyclerView;
    private Spinner qualitySpinner;
    private CheckBox selectAllCheckbox;
    private Button startButton;
    private ItemsAdapter adapter;

    public PlaylistDownloadDialog(List<StreamInfoItem> items) {
        this.streamList = new ArrayList<>(items);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_playlist_download, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        qualitySpinner = view.findViewById(R.id.qualitySpinner);
        selectAllCheckbox = view.findViewById(R.id.selectAllCheckbox);
        recyclerView = view.findViewById(R.id.itemsRecyclerView);
        startButton = view.findViewById(R.id.startDownloadButton);

        setupQualitySpinner();
        setupRecyclerView();
        
        selectAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
             adapter.selectAll(isChecked);
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
            PlaylistDownloadLogic.QUAL_BEST_AUDIO
        };
        
        // التغيير هنا: استخدام simple_spinner_item بدلاً من dropdown_item
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, options);
        
        // هذا السطر صحيح كما هو (للقائمة المنسدلة)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        qualitySpinner.setAdapter(adapter);
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ItemsAdapter(streamList);
        recyclerView.setAdapter(adapter);
    }

    private void startDownload() {
        List<StreamInfoItem> selectedItems = adapter.getSelectedItems();
        if (selectedItems.isEmpty()) {
            Toast.makeText(getContext(), "No items selected", Toast.LENGTH_SHORT).show();
            return;
        }

        String quality = (String) qualitySpinner.getSelectedItem();
        
        Intent serviceIntent = new Intent(getContext(), PlaylistEnqueuerService.class);
        serviceIntent.setAction(PlaylistEnqueuerService.ACTION_ENQUEUE_PLAYLIST);
        serviceIntent.putExtra(PlaylistEnqueuerService.EXTRA_QUALITY, quality);
        
        // Pass urls/titles. Passing whole StreamInfoItem might be too big for Intent
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

        ItemsAdapter(List<StreamInfoItem> items) {
            this.items = items;
            this.selected = new boolean[items.size()];
            for(int i=0; i<selected.length; i++) selected[i] = true; // default select all
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
            
            org.schabi.newpipe.util.image.PicassoHelper.loadThumbnail(item.getThumbnails())
                .into(holder.thumbnail);
            
            holder.itemView.setOnClickListener(v -> {
                holder.checkBox.toggle();
                int adapterPosition = holder.getBindingAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    selected[adapterPosition] = holder.checkBox.isChecked();
                }
            });
            
            holder.checkBox.setOnClickListener(v -> {
                int adapterPosition = holder.getBindingAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    selected[adapterPosition] = holder.checkBox.isChecked();
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final android.widget.CheckBox checkBox;
            final TextView title;
            final TextView uploader;
            final android.widget.ImageView thumbnail;

            ViewHolder(View itemView) {
                super(itemView);
                checkBox = itemView.findViewById(R.id.itemCheckBox);
                title = itemView.findViewById(R.id.itemVideoTitleView);
                uploader = itemView.findViewById(R.id.itemUploaderView);
                thumbnail = itemView.findViewById(R.id.itemThumbnailView);
            }
        }
    }
}
