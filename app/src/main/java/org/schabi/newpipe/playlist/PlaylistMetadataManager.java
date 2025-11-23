package org.schabi.newpipe.playlist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class PlaylistMetadataManager {

  private static final String METADATA_FILE_NAME = ".playlist_metadata";

  private final Gson gson;

  public PlaylistMetadataManager() {
    this.gson = new GsonBuilder().setPrettyPrinting().create();
  }

  public void createMetadataFile(final String downloadPath, final String playlistName,
      final String playlistUrl, final List<StreamInfoItem> streams)
      throws IOException {
    final List<PlaylistMetadata.StreamMetadata> streamMetadata = streams.stream()
        .map(stream -> new PlaylistMetadata.StreamMetadata(stream.getUrl(), "pending"))
        .collect(Collectors.toList());

    final PlaylistMetadata md = new PlaylistMetadata(playlistName, playlistUrl, streamMetadata);

    final File metadataFile = new File(downloadPath, METADATA_FILE_NAME);
    try (FileWriter writer = new FileWriter(metadataFile)) {
      gson.toJson(md, writer);
    }
  }

  public PlaylistMetadata readMetadataFile(final String downloadPath) throws IOException {
    final File metadataFile = new File(downloadPath, METADATA_FILE_NAME);
    try (FileReader reader = new FileReader(metadataFile)) {
      return gson.fromJson(reader, PlaylistMetadata.class);
    }
  }

  public void updateMetadataFile(final String downloadPath, final PlaylistMetadata metadata)
      throws IOException {
    final File metadataFile = new File(downloadPath, METADATA_FILE_NAME);
    try (FileWriter writer = new FileWriter(metadataFile)) {
      gson.toJson(metadata, writer);
    }
  }
}
