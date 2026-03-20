package org.schabi.newpipe.util;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SortedList;

import com.nononsenseapps.filepicker.AbstractFilePickerFragment;
import com.nononsenseapps.filepicker.FilePickerFragment;

import org.schabi.newpipe.R;

import java.io.File;

public class FilePickerActivityHelper extends com.nononsenseapps.filepicker.FilePickerActivity {
    private CustomFilePickerFragment currentFragment;
    @Nullable
    private OnBackPressedCallback backPressedCallback;

    public static boolean isOwnFileUri(@NonNull final Context context, @NonNull final Uri uri) {
        if (uri.getAuthority() == null) {
            return false;
        }
        return uri.getAuthority().startsWith(context.getPackageName());
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        ThemeHelper.setDayNightMode(this);
        ThemeHelper.setThemeResource(this, resolveFilePickerTheme());
        super.onCreate(savedInstanceState);
        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPressed();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
    }

    private int resolveFilePickerTheme() {
        final int selectedTheme = ThemeHelper.getThemeForService(this, -1);
        if (selectedTheme == R.style.LightTheme) {
            return R.style.FilePickerThemeLight;
        }
        if (selectedTheme == R.style.BlackTheme) {
            return R.style.FilePickerThemeBlack;
        }
        return R.style.FilePickerThemeDark;
    }

    private void handleBackPressed() {
        // If at top most level, normal behaviour
        if (currentFragment == null || currentFragment.isBackTop()) {
            performDefaultBackNavigation();
        } else {
            // Else go up
            currentFragment.goUp();
        }
    }

    @SuppressWarnings("deprecation")
    private void performDefaultBackNavigation() {
        if (backPressedCallback == null) {
            FilePickerActivityHelper.super.onBackPressed();
            return;
        }

        backPressedCallback.setEnabled(false);
        try {
            FilePickerActivityHelper.super.onBackPressed();
        } finally {
            backPressedCallback.setEnabled(true);
        }
    }

    @Override
    protected AbstractFilePickerFragment<File> getFragment(@Nullable final String startPath,
                                                           final int mode,
                                                           final boolean allowMultiple,
                                                           final boolean allowCreateDir,
                                                           final boolean allowExistingFile,
                                                           final boolean singleClick) {
        final CustomFilePickerFragment fragment = new CustomFilePickerFragment();
        fragment.setArgs(startPath != null ? startPath
                        : Environment.getExternalStorageDirectory().getPath(),
                mode, allowMultiple, allowCreateDir, allowExistingFile, singleClick);
        currentFragment = fragment;
        return currentFragment;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Internal
    //////////////////////////////////////////////////////////////////////////*/

    public static class CustomFilePickerFragment extends FilePickerFragment {
        @Override
        public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                                 final Bundle savedInstanceState) {
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                          final int viewType) {
            final RecyclerView.ViewHolder viewHolder = super.onCreateViewHolder(parent, viewType);

            final View view = viewHolder.itemView.findViewById(android.R.id.text1);
            if (view instanceof TextView) {
                ((TextView) view).setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimension(R.dimen.file_picker_items_text_size));
            }

            return viewHolder;
        }

        @Override
        public void onClickOk(@NonNull final View view) {
            if (mode == MODE_NEW_FILE && getNewFileName().isEmpty()) {
                if (mToast != null) {
                    mToast.cancel();
                }
                mToast = Toast.makeText(getActivity(), R.string.file_name_empty_error,
                        Toast.LENGTH_SHORT);
                mToast.show();
                return;
            }

            super.onClickOk(view);
        }

        @Override
        protected boolean isItemVisible(@NonNull final File file) {
            if (file.isDirectory() && file.isHidden()) {
                return true;
            }
            return super.isItemVisible(file);
        }

        public File getBackTop() {
            if (getArguments() == null) {
                return Environment.getExternalStorageDirectory();
            }

            final String path = getArguments().getString(KEY_START_PATH, "/");
            if (path.contains(Environment.getExternalStorageDirectory().getPath())) {
                return Environment.getExternalStorageDirectory();
            }

            return getPath(path);
        }

        public boolean isBackTop() {
            return compareFiles(mCurrentPath,
                    getBackTop()) == 0 || compareFiles(mCurrentPath, new File("/")) == 0;
        }

        @Override
        public void onLoadFinished(@NonNull final Loader<SortedList<File>> loader,
                                   final SortedList<File> data) {
            super.onLoadFinished(loader, data);
            layoutManager.scrollToPosition(0);
        }
    }
}
