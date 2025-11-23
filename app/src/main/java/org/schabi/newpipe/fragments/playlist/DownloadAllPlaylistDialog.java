package org.schabi.newpipe.fragments.playlist;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import org.schabi.newpipe.R;

public class DownloadAllPlaylistDialog extends DialogFragment {

    public static final String TAG = "DownloadAllPlaylistDialog";

    private EditText playlistNameEditText;
    private Button chooseLocationButton;

    public interface OnDownloadAllPlaylistListener {
        void onDownloadAll(String playlistName, String downloadPath);
    }

    private OnDownloadAllPlaylistListener listener;

    public void setListener(final OnDownloadAllPlaylistListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final View view = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_download_all_playlist, null);
        playlistNameEditText = view.findViewById(R.id.playlist_name_edit_text);
        chooseLocationButton = view.findViewById(R.id.choose_location_button);

        chooseLocationButton.setOnClickListener(v -> {
            // Open a file picker to choose the download location
            // For now, we will just use a placeholder path
        });

        return new AlertDialog.Builder(getContext())
                .setView(view)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    if (listener != null) {
                        final String playlistName = playlistNameEditText.getText().toString();
                        // For now, we will use a placeholder path
                        final String downloadPath = "/downloads/" + playlistName;
                        listener.onDownloadAll(playlistName, downloadPath);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
    }
}

