package org.schabi.newpipe.playlist;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PlaylistMetadata {

  @SerializedName("playlist_name")
  private final String playlistName;

  @SerializedName("playlist_url")
  private final String playlistUrl;

  @SerializedName("streams")
  private final List<StreamMetadata> streams;

  public PlaylistMetadata(final String playlistName, final String playlistUrl,
      final List<StreamMetadata> streams) {
    this.playlistName = playlistName;
    this.playlistUrl = playlistUrl;
    this.streams = streams;
  }

  public String getPlaylistName() {
    return playlistName;
  }

  public String getPlaylistUrl() {
    return playlistUrl;
  }

  public List<StreamMetadata> getStreams() {
    return streams;
  }

  public static class StreamMetadata {
    @SerializedName("video_url")
    private final String videoUrl;

    @SerializedName("download_status")
    private String downloadStatus; // "pending", "downloaded", "failed"

    public StreamMetadata(final String videoUrl, final String downloadStatus) {
      this.videoUrl = videoUrl;
      this.downloadStatus = downloadStatus;
    }

    public String getVideoUrl() {
      return videoUrl;
    }

    public String getDownloadStatus() {
      return downloadStatus;
    }

    public void setDownloadStatus(final String downloadStatus) {
      this.downloadStatus = downloadStatus;
    }
  }
}

