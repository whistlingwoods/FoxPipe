package org.schabi.newpipe.fragments.playlist;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.streams.io.StoredDirectoryHelper;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.FilenameUtils;
import org.schabi.newpipe.util.StreamItemAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import us.shandian.giga.get.MissionRecoveryInfo;
import us.shandian.giga.service.DownloadManager;
import us.shandian.giga.service.DownloadManagerService;

public class DownloadAllPlaylistDialog extends DialogFragment
        implements RadioGroup.OnCheckedChangeListener, AdapterView.OnItemSelectedListener {

    public static final String TAG = "DownloadAllPlaylistDialog";
    private static final String PLAYLIST_NAME_KEY = "playlist_name_key";
    private static final String PLAYLIST_URL_KEY = "playlist_url_key";
    private static final String SERVICE_ID_KEY = "service_id_key";
    private final CompositeDisposable disposables = new CompositeDisposable();

    private EditText playlistNameEditText;
    private RadioGroup videoAudioGroup;
    private Spinner qualitySpinner;
    private SeekBar threadsSeekBar;

    private String playlistName;
    private String playlistUrl;
    private int serviceId;
    private PlaylistInfo playlistInfo;

    private StoredDirectoryHelper mainStorageAudio = null;
    private StoredDirectoryHelper mainStorageVideo = null;
    private DownloadManager downloadManager = null;

    private StreamItemAdapter<AudioStream, Stream> audioStreamsAdapter;
    private StreamItemAdapter<VideoStream, AudioStream> videoStreamsAdapter;

    public static DownloadAllPlaylistDialog newInstance(final int serviceId,
                                                        final String playlistName,
                                                        final String playlistUrl) {
        final DownloadAllPlaylistDialog dialog = new DownloadAllPlaylistDialog();
        final Bundle args = new Bundle();
        args.putInt(SERVICE_ID_KEY, serviceId);
        args.putString(PLAYLIST_NAME_KEY, playlistName);
        args.putString(PLAYLIST_URL_KEY, playlistUrl);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            serviceId = getArguments().getInt(SERVICE_ID_KEY);
            playlistName = getArguments().getString(PLAYLIST_NAME_KEY);
            playlistUrl = getArguments().getString(PLAYLIST_URL_KEY);
        }

        final Intent intent = new Intent(getContext(), DownloadManagerService.class);
        getContext().startService(intent);
        getContext().bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName cname, final IBinder service) {
                final DownloadManagerService.DownloadManagerBinder
                        mgr = (DownloadManagerService.DownloadManagerBinder) service;
                mainStorageAudio = mgr.getMainStorageAudio();
                mainStorageVideo = mgr.getMainStorageVideo();
                downloadManager = mgr.getDownloadManager();
                getContext().unbindService(this);
            }

            @Override
            public void onServiceDisconnected(final ComponentName name) {
                // nothing to do
            }
        }, Context.BIND_AUTO_CREATE);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final View view = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_download_all_playlist, null);

        playlistNameEditText = view.findViewById(R.id.playlist_name_edit_text);
        videoAudioGroup = view.findViewById(R.id.video_audio_group);
        qualitySpinner = view.findViewById(R.id.quality_spinner);
        threadsSeekBar = view.findViewById(R.id.threads);

        if (playlistName != null) {
            playlistNameEditText.setText(playlistName);
        }

        videoAudioGroup.setOnCheckedChangeListener(this);
        qualitySpinner.setOnItemSelectedListener(this);

        fetchPlaylistInfo();

        return new AlertDialog.Builder(getContext())
                .setView(view)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    downloadAll();
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }

    private void fetchPlaylistInfo() {
        final Single<PlaylistInfo> a = ExtractorHelper.getPlaylistInfo(serviceId, playlistUrl,
                false)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(disposable -> disposables.add(disposable))
                .doOnSuccess(info -> {
                    playlistInfo = info;
                    setupSpinners();
                })
                .doOnError(throwable ->
                        ErrorUtil.showUiErrorSnackbar(requireActivity(),
                                "Failed to get playlist info", throwable));
        a.subscribe();
    }

    private void setupSpinners() {
        final List<VideoStream> videoStreams = new ArrayList<>();
        final List<AudioStream> audioStreams = new ArrayList<>();

        // Fetch stream info for each item in the playlist
        // This is simplified for now, as fetching all stream info can be slow
        // For a full implementation, consider a more efficient way to get stream info
        // (e.g., fetching only necessary details or doing it in a background thread with progress)
        for (final StreamInfoItem item : playlistInfo.getRelatedItems()) {
            // Here we assume that currentInfo in PlaylistFragment would have already
            // provided the needed serviceId and url, which are passed to the dialog
            final Single<StreamInfo> s = ExtractorHelper.getStreamInfo(item.getServiceId(),
                    item.getUrl(), false);
            disposables.add(s.observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            info -> {
                                videoStreams.addAll(info.getVideoStreams());
                                audioStreams.addAll(info.getAudioStreams());
                            },
                            throwable -> {
                                // Handle error for individual stream info fetching
                            }
                    ));
        }

        final List<VideoStream> uniqueVideoStreams =
                videoStreams.stream().distinct().collect(Collectors.toList());
        final List<AudioStream> uniqueAudioStreams =
                audioStreams.stream().distinct().collect(Collectors.toList());

        final StreamItemAdapter.StreamInfoWrapper<VideoStream> wrappedVideoStreams =
                new StreamItemAdapter.StreamInfoWrapper<>(uniqueVideoStreams, getContext());
        videoStreamsAdapter = new StreamItemAdapter<>(wrappedVideoStreams);
        final StreamItemAdapter.StreamInfoWrapper<AudioStream> wrappedAudioStreams =
                new StreamItemAdapter.StreamInfoWrapper<>(uniqueAudioStreams, getContext());
        audioStreamsAdapter = new StreamItemAdapter<>(wrappedAudioStreams);

        if (videoAudioGroup.getCheckedRadioButtonId() == R.id.video_button) {
            qualitySpinner.setAdapter(videoStreamsAdapter);
        } else {
            qualitySpinner.setAdapter(audioStreamsAdapter);
        }
    }

    @Override
    public void onCheckedChanged(final RadioGroup group, final int checkedId) {
        setupSpinners();
    }

    @Override
    public void onItemSelected(final AdapterView<?> parent, final View view, final int position,
                               final long id) {
        // Not needed for now
    }

    @Override
    public void onNothingSelected(final AdapterView<?> parent) {
        // Not needed for now
    }

    private void downloadAll() {
        if (playlistInfo == null) {
            Toast.makeText(getContext(), "Playlist information not loaded yet.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        for (final StreamInfoItem item : playlistInfo.getRelatedItems()) {
            final Single<StreamInfo> s = ExtractorHelper.getStreamInfo(item.getServiceId(),
                    item.getUrl(), false);
            disposables.add(s.observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            this::download,
                            throwable -> {
                                // Handle error for individual stream download initiation
                                Toast.makeText(getContext(),
                                        "Failed to download some items.",
                                        Toast.LENGTH_SHORT).show();
                            }
                    ));
        }
        dismiss(); // Dismiss the dialog once all downloads are initiated
    }

    private void download(final StreamInfo info) {
        final StoredDirectoryHelper mainStorage;
        final Stream selectedStream;
        final String filename;
        final String format;
        final char kind;

        if (videoAudioGroup.getCheckedRadioButtonId() == R.id.video_button) {
            mainStorage = mainStorageVideo;
            selectedStream = videoStreamsAdapter.getItem(
                    qualitySpinner.getSelectedItemPosition());
            kind = 'v';
            format = ((VideoStream) selectedStream).getFormat().getName();
            filename = FilenameUtils.createFilename(getContext(), info.getName())
                    + "." + format;
        } else {
            mainStorage = mainStorageAudio;
            selectedStream = audioStreamsAdapter.getItem(
                    qualitySpinner.getSelectedItemPosition());
            kind = 'a';
            format = ((AudioStream) selectedStream).getFormat().getName();
            filename = FilenameUtils.createFilename(getContext(), info.getName())
                    + "." + format;
        }

        if (mainStorage == null) {
            Toast.makeText(getContext(),
                    "Storage not available. Please configure download settings.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        final StoredFileHelper file = mainStorage.createFile(filename,
                selectedStream.getFormat().getMimeType());
        if (file == null) {
            Toast.makeText(getContext(),
                    "Could not create file for " + info.getName(),
                    Toast.LENGTH_LONG).show();
            return;
        }

        final String[] urls = {selectedStream.getContent()};
        final List<MissionRecoveryInfo> recoveryInfo =
                List.of(new MissionRecoveryInfo(selectedStream));

        DownloadManagerService.startMission(getContext(), urls, file, kind,
                threadsSeekBar.getProgress() + 1,
                info.getUrl(), null, null, 0,
                new ArrayList<>(recoveryInfo));

        Toast.makeText(getContext(),
                getString(R.string.download_has_started) + ": " + info.getName(),
                Toast.LENGTH_SHORT).show();
    }
}
