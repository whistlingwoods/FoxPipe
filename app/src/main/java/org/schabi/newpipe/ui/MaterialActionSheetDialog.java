package org.schabi.newpipe.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.schabi.newpipe.R;

import java.util.List;

public final class MaterialActionSheetDialog {
    private MaterialActionSheetDialog() {
    }

    public static BottomSheetDialog show(@NonNull final Context context,
                                         @Nullable final CharSequence title,
                                         @NonNull final List<ActionItem> items) {
        return show(context, title, items, null);
    }

    public static BottomSheetDialog show(@NonNull final Context context,
                                         @Nullable final CharSequence title,
                                         @NonNull final List<ActionItem> items,
                                         @Nullable final Runnable onDismiss) {
        final BottomSheetDialog dialog = new BottomSheetDialog(context);
        final View root = LayoutInflater.from(context)
                .inflate(R.layout.dialog_action_sheet, null, false);
        final TextView titleView = root.findViewById(R.id.actionSheetTitle);
        final ListView listView = root.findViewById(R.id.actionSheetList);

        if (title == null || title.length() == 0) {
            titleView.setVisibility(View.GONE);
        } else {
            titleView.setText(title);
            titleView.setVisibility(View.VISIBLE);
        }

        listView.setAdapter(new ActionItemAdapter(context, items));
        final boolean[] openingSubSheet = {false};
        listView.setOnItemClickListener((parent, view, position, id) -> {
            final ActionItem item = items.get(position);
            if (!item.enabled) {
                return;
            }

            if (!item.subItems.isEmpty()) {
                openingSubSheet[0] = true;
            }
            dialog.dismiss();

            if (!item.subItems.isEmpty()) {
                show(context, item.title, item.subItems, onDismiss);
                return;
            }

            if (item.action != null) {
                item.action.run();
            }
        });
        dialog.setOnDismissListener(unused -> {
            if (openingSubSheet[0]) {
                openingSubSheet[0] = false;
                return;
            }
            if (onDismiss != null) {
                onDismiss.run();
            }
        });
        dialog.setContentView(root);
        dialog.show();
        return dialog;
    }

    public static final class ActionItem {
        public final int id;
        @NonNull
        public final CharSequence title;
        @DrawableRes
        public final int iconResId;
        public final boolean checked;
        public final boolean enabled;
        @NonNull
        public final List<ActionItem> subItems;
        @Nullable
        public final Runnable action;

        public ActionItem(final int id,
                          @NonNull final CharSequence title,
                          @DrawableRes final int iconResId,
                          final boolean checked,
                          final boolean enabled,
                          @NonNull final List<ActionItem> subItems,
                          @Nullable final Runnable action) {
            this.id = id;
            this.title = title;
            this.iconResId = iconResId;
            this.checked = checked;
            this.enabled = enabled;
            this.subItems = subItems;
            this.action = action;
        }

        @NonNull
        public static ActionItem create(final int id,
                                        @NonNull final CharSequence title,
                                        @DrawableRes final int iconResId,
                                        @Nullable final Runnable action) {
            return new ActionItem(id, title, iconResId, false, true, List.of(), action);
        }

        @NonNull
        public static ActionItem checked(final int id,
                                         @NonNull final CharSequence title,
                                         @DrawableRes final int iconResId,
                                         final boolean checked,
                                         @Nullable final Runnable action) {
            return new ActionItem(id, title, iconResId, checked, true, List.of(), action);
        }

        @NonNull
        public static ActionItem disabled(final int id,
                                          @NonNull final CharSequence title,
                                          @DrawableRes final int iconResId) {
            return new ActionItem(id, title, iconResId, false, false, List.of(), null);
        }

        @NonNull
        public static ActionItem submenu(final int id,
                                         @NonNull final CharSequence title,
                                         @DrawableRes final int iconResId,
                                         @NonNull final List<ActionItem> subItems) {
            return new ActionItem(id, title, iconResId, false, true, subItems, null);
        }
    }

    private static final class ActionItemAdapter extends ArrayAdapter<ActionItem> {
        private final LayoutInflater inflater;

        private ActionItemAdapter(@NonNull final Context context,
                                  @NonNull final List<ActionItem> items) {
            super(context, 0, items);
            inflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public View getView(final int position,
                            @Nullable final View convertView,
                            @NonNull final ViewGroup parent) {
            final View itemView = convertView == null
                    ? inflater.inflate(R.layout.item_action_sheet, parent, false)
                    : convertView;
            final ActionItem item = getItem(position);
            if (item == null) {
                return itemView;
            }

            final ImageView iconView = itemView.findViewById(R.id.actionIcon);
            final TextView titleView = itemView.findViewById(R.id.actionTitle);
            final ImageView checkView = itemView.findViewById(R.id.actionCheck);

            titleView.setText(item.title);
            titleView.setEnabled(item.enabled);
            itemView.setEnabled(item.enabled);
            itemView.setAlpha(item.enabled ? 1f : 0.38f);

            if (item.iconResId == 0) {
                iconView.setVisibility(View.GONE);
            } else {
                iconView.setVisibility(View.VISIBLE);
                iconView.setImageResource(item.iconResId);
            }

            checkView.setVisibility(item.checked ? View.VISIBLE : View.GONE);
            return itemView;
        }
    }
}
