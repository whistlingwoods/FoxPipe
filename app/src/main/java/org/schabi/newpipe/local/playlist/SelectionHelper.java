package org.schabi.newpipe.local.playlist;

import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.Set;

public class SelectionHelper implements RecyclerView.OnItemTouchListener {
    private RecyclerView recyclerView;
    private OnItemSelectedListener listener;
    private Set<Integer> selectedItems = new HashSet<>();

    public interface OnItemSelectedListener {
        void onItemSelected(int position);
        void onItemDeselected(int position);
    }

    public SelectionHelper(RecyclerView recyclerView, OnItemSelectedListener listener) {
        this.recyclerView = recyclerView;
        this.listener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        View child = recyclerView.findChildViewUnder(e.getX(), e.getY());
        if (child != null && e.getAction() == MotionEvent.ACTION_UP) {
            int position = recyclerView.getChildAdapterPosition(child);
            if (position != RecyclerView.NO_POSITION) {
                toggleSelection(position);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        // Not needed for selection
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // Not needed for selection
    }

    private void toggleSelection(int position) {
        if (selectedItems.contains(position)) {
            selectedItems.remove(position);
            listener.onItemDeselected(position);
        } else {
            selectedItems.add(position);
            listener.onItemSelected(position);
        }
    }

    public Set<Integer> getSelectedItems() {
        return selectedItems;
    }

    public void clearSelections() {
        selectedItems.clear();
    }
}