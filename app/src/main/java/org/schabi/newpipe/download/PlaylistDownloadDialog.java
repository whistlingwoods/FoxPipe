package org.schabi.newpipe.download;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color; // مهم جداً
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
import org.schabi.newpipe.views.NewPipeTextView; // استدعاء مكتبة نصوص NewPipe

import java.util.ArrayList;
import java.util.List;

import android.view.ContextThemeWrapper; // تأكد من إضافة هذا الاستيراد
import org.schabi.newpipe.util.ThemeHelper; // NewPipe يستخدم هذا المساعد

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
        // 1. نحصل على الثيم الحالي من الإعدادات باستخدام أدوات NewPipe
        // إذا لم يكن ThemeHelper متاحاً، يمكنك استخدام R.style.LightTheme مبدئياً للتجربة، 
        // لكن NewPipe غالباً يستخدم ThemeHelper.getTheme(context)
        
        // الحل الأبسط والأكثر فعالية: استخدام ContextThemeWrapper مع ثيم التطبيق العام
        // هذا سيجبر النافذة على استخدام نفس ألوان التطبيق (بما فيها الفاتح والغامق)
        Context contextThemeWrapper = new ContextThemeWrapper(getActivity(),  org.schabi.newpipe.R.style.LightTheme); 
        // ملاحظة: NewPipe يقوم بتبديل كلمة "LightTheme" داخلياً حسب الوضع، أو يمكنك استخدام getTheme() من الـ Activity

        LayoutInflater localInflater = inflater.cloneInContext(getContext());

        return localInflater.inflate(R.layout.dialog_playlist_download, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // --- السطر السحري ---
        // هذا يجعل خلفية النظام شفافة، لكي تظهر خلفية التصميم الخاص بنا فقط
        ((View) view.getParent()).setBackgroundColor(Color.TRANSPARENT);
        // --------------------

        qualitySpinner = view.findViewById(R.id.qualitySpinner);
        selectAllCheckbox = view.findViewById(R.id.selectAllCheckbox);
        recyclerView = view.findViewById(R.id.itemsRecyclerView);
        startButton = view.findViewById(R.id.startDownloadButton);

        setupQualitySpinner();
        setupRecyclerView();
        
        selectAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
             if(adapter != null) adapter.selectAll(isChecked);
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
        
        // استخدام R.layout.spinner_item_newpipe بدلاً من تصميم الأندرويد الافتراضي
        // تأكد من استيراد R الخاص بمشروعك بشكل صحيح
       ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), R.layout.spinner_item_newpipe, options);
        
        // استخدام نفس التصميم للقائمة المنسدلة أيضاً لضمان توحيد الألوان
        adapter.setDropDownViewResource(R.layout.spinner_item_newpipe);
        
        qualitySpinner.setAdapter(adapter);
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ItemsAdapter(streamList);
        recyclerView.setAdapter(adapter);
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

        ItemsAdapter(List<StreamInfoItem> items) {
            this.items = items;
            this.selected = new boolean[items.size()];
            for(int i=0; i<selected.length; i++) selected[i] = true;
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
                updateSelection(holder.getBindingAdapterPosition(), holder.checkBox.isChecked());
            });
            
            holder.checkBox.setOnClickListener(v -> {
                updateSelection(holder.getBindingAdapterPosition(), holder.checkBox.isChecked());
            });
        }

        private void updateSelection(int position, boolean isChecked) {
            if (position != RecyclerView.NO_POSITION) {
                selected[position] = isChecked;
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final android.widget.CheckBox checkBox;
            // استخدام NewPipeTextView هنا مهم جداً
            final NewPipeTextView title;
            final NewPipeTextView uploader;
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