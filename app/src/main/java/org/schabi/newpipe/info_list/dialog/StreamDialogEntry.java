package org.schabi.newpipe.info_list.dialog;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

public class StreamDialogEntry {

    @StringRes
    public final int resource;
    @NonNull
    public final StreamDialogEntryAction action;
    private final DynamicStringProvider dynamicStringProvider;

    public StreamDialogEntry(@StringRes final int resource,
                             @NonNull final StreamDialogEntryAction action) {
        this.resource = resource;
        this.action = action;
        this.dynamicStringProvider = null;
    }

    public StreamDialogEntry(@NonNull final DynamicStringProvider dynamicStringProvider,
                             @NonNull final StreamDialogEntryAction action) {
        this.resource = 0;
        this.action = action;
        this.dynamicStringProvider = dynamicStringProvider;
    }

    public String getString(@NonNull final Context context) {
        return context.getString(resource);
    }

    public String getString(@NonNull final Context context, @NonNull final StreamInfoItem item) {
        if (dynamicStringProvider != null) {
            return dynamicStringProvider.getString(context, item);
        }
        return context.getString(resource);
    }

    public interface StreamDialogEntryAction {
        void onClick(Fragment fragment, StreamInfoItem infoItem);
    }

    public interface DynamicStringProvider {
        String getString(Context context, StreamInfoItem item);
    }
}
