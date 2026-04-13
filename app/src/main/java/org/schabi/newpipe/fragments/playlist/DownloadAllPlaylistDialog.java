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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import org.schabi.newpipe.R;
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import us.shandian.giga.get.MissionRecoveryInfo;
import us.shandian.giga.service.DownloadManagerService;

public class DownloadAllPlaylistDialog extends DialogFragment
    implements RadioGroup.OnCheckedChangeListener, AdapterView.OnItemSelectedListener {

  public static final String TAG = "DownloadAllPlaylistDialog";
  private static final String PLAYLIST_INFO_KEY = "playlist_info_key";
  private static final String FIRST_STREAM_INFO_KEY = "first_stream_info_key";

  private final CompositeDisposable disposables = new CompositeDisposable();

  private EditText playlistNameEditText;
  private RadioGroup videoAudioGroup;
  private Spinner qualitySpinner;
  private SeekBar threadsSeekBar;
  private CheckBox useForAllCheckbox;
  private TextView threadsCountTextView;

  private PlaylistInfo playlistInfo;
  private StreamInfo firstStreamInfo;

  private StoredDirectoryHelper mainStorageAudio = null;
  private StoredDirectoryHelper mainStorageVideo = null;

  private StreamItemAdapter<AudioStream, Stream> audioStreamsAdapter;
  private StreamItemAdapter<VideoStream, AudioStream> videoStreamsAdapter;

  private AlertDialog dialog;

  public static DownloadAllPlaylistDialog newInstance(
          @NonNull final PlaylistInfo playlistInfo,
          @NonNull final StreamInfo firstStreamInfo) {
    final DownloadAllPlaylistDialog dialog = new DownloadAllPlaylistDialog();
    final Bundle args = new Bundle();
    args.putSerializable(PLAYLIST_INFO_KEY, playlistInfo);
    args.putSerializable(FIRST_STREAM_INFO_KEY, firstStreamInfo);
    dialog.setArguments(args);
    return dialog;
  }

  @Override
  public void onCreate(@Nullable final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (getArguments() != null) {
      final Serializable playlistInfoSerializable = getArguments()
              .getSerializable(PLAYLIST_INFO_KEY);
      if (playlistInfoSerializable instanceof PlaylistInfo) {
        playlistInfo = (PlaylistInfo) playlistInfoSerializable;
      }

      final Serializable firstStreamInfoSerializable = getArguments()
              .getSerializable(FIRST_STREAM_INFO_KEY);
      if (firstStreamInfoSerializable instanceof StreamInfo) {
        firstStreamInfo = (StreamInfo) firstStreamInfoSerializable;
      }
    }

    if (playlistInfo == null || firstStreamInfo == null) {
        Toast.makeText(getContext(), R.string.general_error, Toast.LENGTH_SHORT).show();
        dismiss();
        return;
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
        if (getContext() != null) {
          getContext().unbindService(this);
        }
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
    useForAllCheckbox = view.findViewById(R.id.use_for_all_checkbox);
    threadsCountTextView = view.findViewById(R.id.threads_count);

    playlistNameEditText.setText(playlistInfo.getName());

    videoAudioGroup.setOnCheckedChangeListener(this);
    qualitySpinner.setOnItemSelectedListener(this);
    threadsSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
            final String threadCount = String.valueOf(progress + 1);
            threadsCountTextView.setText(threadCount);
        }

        @Override
        public void onStartTrackingTouch(final SeekBar seekBar) { }

        @Override
        public void onStopTrackingTouch(final SeekBar seekBar) { }
    });
    threadsCountTextView.setText(String.valueOf(threadsSeekBar.getProgress() + 1));

    setupSpinners();

    dialog = new AlertDialog.Builder(getContext())
        .setView(view)
        .setPositiveButton(R.string.ok, null) // Set null to override dismissal behavior
        .setNegativeButton(R.string.cancel, null)
        .create();

    dialog.setOnShowListener(d -> {
        Button positiveButton = ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(v -> downloadAll());
        // Initially disable the button until spinners are set up
        positiveButton.setEnabled(false);
        // Also enable the button if spinners are already set up (e.g., on rotation)
        if (firstStreamInfo != null) {
            positiveButton.setEnabled(true);
        }
    });

    return dialog;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    disposables.clear();
  }

  private void setupSpinners() {
    final List<VideoStream> videoStreams = new ArrayList<>(firstStreamInfo.getVideoStreams());
    final List<AudioStream> audioStreams = new ArrayList<>(firstStreamInfo.getAudioStreams());

    final StreamItemAdapter.StreamInfoWrapper<VideoStream>
        wrappedVideoStreams = new StreamItemAdapter.StreamInfoWrapper<>(
        videoStreams, getContext());
    videoStreamsAdapter = new StreamItemAdapter<>(wrappedVideoStreams);

    final StreamItemAdapter.StreamInfoWrapper<AudioStream>
        wrappedAudioStreams = new StreamItemAdapter.StreamInfoWrapper<>(
        audioStreams, getContext());
    audioStreamsAdapter = new StreamItemAdapter<>(wrappedAudioStreams);

    if (videoStreams.isEmpty()) {
        videoAudioGroup.findViewById(R.id.video_button).setEnabled(false);
    }
    if (audioStreams.isEmpty()) {
        videoAudioGroup.findViewById(R.id.audio_button).setEnabled(false);
    }

    if (videoStreams.isEmpty() && audioStreams.isEmpty()) {
        Toast.makeText(getContext(), R.string.no_streams_available_download,
                Toast.LENGTH_SHORT).show();
        dismiss();
        return;
    }

    if (videoAudioGroup.getCheckedRadioButtonId() == R.id.video_button
            && !videoAudioGroup.findViewById(R.id.video_button).isEnabled()) {
        videoAudioGroup.check(R.id.audio_button);
    } else if (videoAudioGroup.getCheckedRadioButtonId() == R.id.audio_button
            && !videoAudioGroup.findViewById(R.id.audio_button).isEnabled()) {
        videoAudioGroup.check(R.id.video_button);
    }

    updateQualitySpinner();

    // Enable the positive button once spinners are set up
    if (dialog != null) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
    }
  }

  private void updateQualitySpinner() {
    if (videoAudioGroup.getCheckedRadioButtonId() == R.id.video_button) {
      qualitySpinner.setAdapter(videoStreamsAdapter);
    } else {
      qualitySpinner.setAdapter(audioStreamsAdapter);
    }
  }

  @Override
  public void onCheckedChanged(final RadioGroup group, final int checkedId) {
    updateQualitySpinner();
  }

  @Override
  public void onItemSelected(final AdapterView<?> parent,
      final View view,
      final int position,
      final long id) {
    // Not needed for now
  }

  @Override
  public void onNothingSelected(final AdapterView<?> parent) {
    // Not needed for now
  }

  private void downloadAll() {
    final String playlistName = playlistNameEditText.getText().toString();
    if (dialog != null) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    }

    if (useForAllCheckbox.isChecked()) {
      final Stream selectedStream = getSelectedStreamFromSpinner();
      if (selectedStream == null) {
        Toast.makeText(getContext(), "No quality selected.", Toast.LENGTH_SHORT).show();
        if (dialog != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
        }
        return;
      }
      downloadAllSequentially(new ArrayList<>(playlistInfo.getRelatedItems()), selectedStream, playlistName);
    } else {
      downloadIndividuallySequentially(new ArrayList<>(playlistInfo.getRelatedItems()), playlistName);
    }
  }

  private void downloadAllSequentially(final List<StreamInfoItem> items,
                                       final Stream template,
                                       final String playlistName) {
      if (items.isEmpty()) {
          Toast.makeText(getContext(), R.string.all_downloads_queued, Toast.LENGTH_SHORT).show();
          dismiss();
          return;
      }

      final StreamInfoItem item = items.remove(0);

      disposables.add(ExtractorHelper.getStreamInfo(item.getServiceId(), item.getUrl(), false)
              .subscribeOn(Schedulers.io())
              .observeOn(AndroidSchedulers.mainThread())
              .subscribe(
                      streamInfo -> {
                          final Stream streamToDownload = findMatchingStream(streamInfo, template);
                          if (streamToDownload != null) {
                              download(streamInfo, streamToDownload, playlistName);
                          } else {
                              Toast.makeText(getContext(), "No matching stream for "
                                      + item.getName(), Toast.LENGTH_SHORT).show();
                          }
                          downloadAllSequentially(items, template, playlistName);
                      },
                      throwable -> {
                          Toast.makeText(getContext(), "Failed to get info for "
                                  + item.getName(), Toast.LENGTH_SHORT).show();
                          downloadAllSequentially(items, template, playlistName);
                      }
              ));
  }


  private void downloadIndividuallySequentially(final List<StreamInfoItem> items,
                                                final String playlistName) {
    if (items.isEmpty()) {
      dismiss();
      return;
    }

    final StreamInfoItem item = items.remove(0);
    disposables.add(ExtractorHelper.getStreamInfo(item.getServiceId(), item.getUrl(), false)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(info -> showDialogForIndividualDownload(
                info, () -> downloadIndividuallySequentially(items, playlistName), playlistName),
            throwable -> {
              Toast.makeText(getContext(),
                  "Failed to get info for: " + item.getName(),
                  Toast.LENGTH_SHORT).show();
                downloadIndividuallySequentially(items, playlistName); // Continue with next
            }));
  }

  private void showDialogForIndividualDownload(@NonNull final StreamInfo info,
      @NonNull final Runnable onComplete,
      final String playlistName) {
    final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
    final View view = LayoutInflater.from(getContext())
        .inflate(R.layout.dialog_download_all_playlist, null);

    final EditText nameEditText = view.findViewById(R.id.playlist_name_edit_text);
    final RadioGroup itemVideoAudioGroup = view.findViewById(R.id.video_audio_group);
    final Spinner itemQualitySpinner = view.findViewById(R.id.quality_spinner);
    view.findViewById(R.id.threads_layout).setVisibility(View.GONE);
    view.findViewById(R.id.threads_text_view).setVisibility(View.GONE);
    view.findViewById(R.id.use_for_all_checkbox).setVisibility(View.GONE);

    nameEditText.setText(info.getName());

    final StreamItemAdapter<VideoStream, AudioStream> itemVideoAdapter;
    final StreamItemAdapter<AudioStream, Stream> itemAudioAdapter;

    final StreamItemAdapter.StreamInfoWrapper<VideoStream>
      wrappedVideo = new StreamItemAdapter.StreamInfoWrapper<>(
        info.getVideoStreams(), getContext());
    itemVideoAdapter = new StreamItemAdapter<>(wrappedVideo);

    final StreamItemAdapter.StreamInfoWrapper<AudioStream>
      wrappedAudio = new StreamItemAdapter.StreamInfoWrapper<>(
        info.getAudioStreams(), getContext());
    itemAudioAdapter = new StreamItemAdapter<>(wrappedAudio);

    itemVideoAudioGroup.setOnCheckedChangeListener((group, checkedId) -> {
      if (checkedId == R.id.video_button) {
        itemQualitySpinner.setAdapter(itemVideoAdapter);
      } else {
        itemQualitySpinner.setAdapter(itemAudioAdapter);
      }
    });

    if (itemVideoAudioGroup.getCheckedRadioButtonId() == R.id.video_button) {
      itemQualitySpinner.setAdapter(itemVideoAdapter);
    } else {
      itemQualitySpinner.setAdapter(itemAudioAdapter);
    }

    final AlertDialog individualDialog = builder.setView(view)
        .setTitle(R.string.download)
        .setPositiveButton(R.string.ok, null) // Override dismissal
        .setNegativeButton(R.string.cancel, null)
        .create();

    individualDialog.setOnShowListener(d -> {
        Button positiveButton = ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(v -> {
            final Stream streamToDownload = (Stream) itemQualitySpinner.getSelectedItem();
            download(info, streamToDownload, playlistName);
            onComplete.run(); // Continue sequential processing
            individualDialog.dismiss(); // Dismiss this individual dialog
        });
    });
    individualDialog.show();
  }

  private Stream getSelectedStreamFromSpinner() {
    final int position = qualitySpinner.getSelectedItemPosition();
    if (position == AdapterView.INVALID_POSITION) {
      return null;
    }

    if (videoAudioGroup.getCheckedRadioButtonId() == R.id.video_button) {
      return videoStreamsAdapter.getItem(position);
    } else {
      return audioStreamsAdapter.getItem(position);
    }
  }

  private Stream findMatchingStream(@NonNull final StreamInfo info,
      @NonNull final Stream templateStream) {
    if (templateStream instanceof VideoStream) {
      final VideoStream template = (VideoStream) templateStream;
      for (final VideoStream vs : info.getVideoStreams()) {
        if (vs.getResolution().equals(template.getResolution())
            && vs.getFormat().equals(template.getFormat())) {
          return vs;
        }
      }
      return info.getVideoStreams().isEmpty() ? null : info.getVideoStreams().get(0);
    } else if (templateStream instanceof AudioStream) {
      final AudioStream template = (AudioStream) templateStream;
      for (final AudioStream as : info.getAudioStreams()) {
        if (as.getFormat().equals(template.getFormat())
                && as.getAverageBitrate() == template.getAverageBitrate()) {
            return as;
        }
      }
      return info.getAudioStreams().isEmpty() ? null : info.getAudioStreams().get(0);
    }
    return null;
  }

  private void download(@NonNull final StreamInfo info,
                        @Nullable final Stream selectedStream,
                        @Nullable final String playlistName) {
      if (selectedStream == null) {
          Toast.makeText(getContext(),
                  "Could not find matching quality for " + info.getName(),
                  Toast.LENGTH_SHORT).show();
          return;
      }

      final StoredDirectoryHelper mainStorage;
      final String streamFilename;
      final char kind;

      final String finalName = (playlistName == null || playlistName.isEmpty())
              ? info.getName()
              : playlistName + "_" + info.getName();

      if (selectedStream instanceof VideoStream) {
          mainStorage = mainStorageVideo;
          kind = 'v';
          final VideoStream video = (VideoStream) selectedStream;
          streamFilename = FilenameUtils.createFilename(getContext(), finalName)
                  + "." + video.getFormat().getName().toLowerCase();
      } else {
          mainStorage = mainStorageAudio;
          kind = 'a';
          final AudioStream audio = (AudioStream) selectedStream;
          streamFilename = FilenameUtils.createFilename(getContext(), finalName)
                  + "." + audio.getFormat().getName().toLowerCase();
      }

      if (mainStorage == null) {
          Toast.makeText(getContext(), R.string.no_available_dir, Toast.LENGTH_LONG).show();
          return;
      }

      final StoredFileHelper file = mainStorage.createFile(streamFilename,
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
              info.getUrl(),
              null, // psName
              null, // psArgs
              0,
              new ArrayList<>(recoveryInfo));

      Toast.makeText(getContext(),
              getString(R.string.download_has_started) + ": " + info.getName(),
              Toast.LENGTH_SHORT).show();
    }
}