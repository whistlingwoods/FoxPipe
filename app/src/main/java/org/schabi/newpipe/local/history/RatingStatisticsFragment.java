package org.schabi.newpipe.local.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.playlist.dao.PlaylistDAO;
import org.schabi.newpipe.database.stream.dao.StreamDAO;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Fragment displaying rating and listening statistics.
 */
public class RatingStatisticsFragment extends Fragment {
    private final CompositeDisposable disposables = new CompositeDisposable();

    private TextView totalRatedTracks;
    private TextView totalUnratedTracks;
    private TextView totalListeningTime;
    private TextView totalTracksPlayed;
    private LinearLayout ratingDistributionContainer;
    private LinearLayout mostRatedArtistsContainer;
    private LinearLayout mostPlayedArtistsContainer;
    private LinearLayout unratedGemsContainer;
    private LinearLayout mostSkippedTracksContainer;
    private LinearLayout mostCompletedTracksContainer;
    private LinearLayout mostRestartedTracksContainer;
    private LinearLayout playlistStatsContainer;
    private TextView downloadsStats;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rating_statistics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize views
        totalRatedTracks = view.findViewById(R.id.total_rated_tracks);
        totalUnratedTracks = view.findViewById(R.id.total_unrated_tracks);
        totalListeningTime = view.findViewById(R.id.total_listening_time);
        totalTracksPlayed = view.findViewById(R.id.total_tracks_played);
        ratingDistributionContainer = view.findViewById(R.id.rating_distribution_container);
        mostRatedArtistsContainer = view.findViewById(R.id.most_rated_artists_container);
        mostPlayedArtistsContainer = view.findViewById(R.id.most_played_artists_container);
        unratedGemsContainer = view.findViewById(R.id.unrated_gems_container);
        mostSkippedTracksContainer = view.findViewById(R.id.most_skipped_tracks_container);
        mostCompletedTracksContainer = view.findViewById(R.id.most_completed_tracks_container);
        mostRestartedTracksContainer = view.findViewById(R.id.most_restarted_tracks_container);
        playlistStatsContainer = view.findViewById(R.id.playlist_stats_container);
        downloadsStats = view.findViewById(R.id.downloads_stats);

        loadStatistics();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }

    private void loadStatistics() {
        final StreamDAO streamDAO = NewPipeDatabase.getInstance(requireContext()).streamDAO();
        final PlaylistDAO playlistDAO =
                NewPipeDatabase.getInstance(requireContext()).playlistDAO();

        // Load overall stats
        disposables.add(
                streamDAO.getRatedTracksCount()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(count -> totalRatedTracks.setText(
                                String.format(Locale.getDefault(),
                                        "Rated tracks: %d", count)))
        );

        disposables.add(
                streamDAO.getUnratedTracksCount()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(count -> totalUnratedTracks.setText(
                                String.format(Locale.getDefault(),
                                        "Unrated tracks: %d", count)))
        );

        // Load total listening time from actual playback statistics
        disposables.add(
                NewPipeDatabase.getInstance(requireContext())
                        .playbackStatisticsDAO()
                        .getTotalPlayTime()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(millis -> totalListeningTime.setText(
                                formatListeningTime(millis)))
        );

        // Load total tracks played
        disposables.add(
                NewPipeDatabase.getInstance(requireContext())
                        .playbackStatisticsDAO()
                        .getTotalTracksPlayed()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(count -> totalTracksPlayed.setText(
                                String.format(Locale.getDefault(),
                                        "Tracks played: %d", count)))
        );

        // Load rating distribution
        disposables.add(
                streamDAO.getRatingDistribution()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::displayRatingDistribution)
        );

        // Load most rated artists
        disposables.add(
                streamDAO.getMostRatedArtists(5)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::displayMostRatedArtists)
        );

        // Load most played artists
        disposables.add(
                streamDAO.getMostPlayedArtists(5)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::displayMostPlayedArtists)
        );

        // Load unrated gems (most played unrated tracks from actual playback stats)
        disposables.add(
                NewPipeDatabase.getInstance(requireContext())
                        .playbackStatisticsDAO()
                        .getMostPlayedUnratedStreams(5)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::displayUnratedGemsFromPlayback)
        );

        // Load most skipped tracks
        disposables.add(
                NewPipeDatabase.getInstance(requireContext())
                        .playbackStatisticsDAO()
                        .getMostSkippedStreams(5)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::displayMostSkippedTracks)
        );

        // Load most completed tracks
        disposables.add(
                NewPipeDatabase.getInstance(requireContext())
                        .playbackStatisticsDAO()
                        .getMostCompletedStreams(5)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::displayMostCompletedTracks)
        );

        // Load most restarted tracks
        disposables.add(
                NewPipeDatabase.getInstance(requireContext())
                        .playbackStatisticsDAO()
                        .getMostRestartedStreams(5)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::displayMostRestartedTracks)
        );

        // Load playlist statistics
        disposables.add(
                playlistDAO.getPlaylistRatingStatistics()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::displayPlaylistStats)
        );

        // Load download statistics
        disposables.add(
                playlistDAO.getLocalPlaylistDownloadStats()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(stats -> downloadsStats.setText(
                                String.format(Locale.getDefault(),
                                        "Downloaded: %d of %d tracks in local playlists",
                                        stats.getDownloadedCount(), stats.getTotalCount())))
        );
    }

    private String formatListeningTime(final long millis) {
        final long hours = TimeUnit.MILLISECONDS.toHours(millis);
        final long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        return String.format(Locale.getDefault(),
                "Total listening time: %dh %dm", hours, minutes);
    }

    private void displayRatingDistribution(
            final List<StreamDAO.RatingDistributionEntry> distribution) {
        ratingDistributionContainer.removeAllViews();

        if (distribution.isEmpty()) {
            addTextToContainer(ratingDistributionContainer, "No rated tracks yet");
            return;
        }

        for (final StreamDAO.RatingDistributionEntry entry : distribution) {
            final String text = String.format(Locale.getDefault(),
                    "%d★: %d tracks", entry.getRating(), entry.getCount());
            addTextToContainer(ratingDistributionContainer, text);
        }
    }

    private void displayMostRatedArtists(final List<StreamDAO.ArtistRatingCount> artists) {
        mostRatedArtistsContainer.removeAllViews();

        if (artists.isEmpty()) {
            addTextToContainer(mostRatedArtistsContainer, "No rated artists yet");
            return;
        }

        for (final StreamDAO.ArtistRatingCount artist : artists) {
            final String artistName = cleanArtistName(artist.getArtistName());
            final String text = String.format(Locale.getDefault(),
                    "%s: %d rated", artistName, artist.getRatedCount());
            addTextToContainer(mostRatedArtistsContainer, text);
        }
    }

    private void displayMostPlayedArtists(final List<StreamDAO.ArtistPlayCount> artists) {
        mostPlayedArtistsContainer.removeAllViews();

        if (artists.isEmpty()) {
            addTextToContainer(mostPlayedArtistsContainer, "No play history yet");
            return;
        }

        for (final StreamDAO.ArtistPlayCount artist : artists) {
            final String artistName = cleanArtistName(artist.getArtistName());
            final String text = String.format(Locale.getDefault(),
                    "%s: %d plays", artistName, artist.getPlayCount());
            addTextToContainer(mostPlayedArtistsContainer, text);
        }
    }

    private void displayUnratedGems(final List<StreamDAO.UnratedTrackEntry> tracks) {
        unratedGemsContainer.removeAllViews();

        if (tracks.isEmpty()) {
            addTextToContainer(unratedGemsContainer, "All played tracks are rated!");
            return;
        }

        for (final StreamDAO.UnratedTrackEntry track : tracks) {
            final String text = String.format(Locale.getDefault(),
                    "%s (%d plays)", track.getTitle(), track.getWatchCount());
            addTextToContainer(unratedGemsContainer, text);
        }
    }

    private void displayUnratedGemsFromPlayback(
            final List<org.schabi.newpipe.database.playback.dao.PlaybackStatisticsDAO
                    .PlaybackStatsWithStream> tracks) {
        unratedGemsContainer.removeAllViews();

        if (tracks.isEmpty()) {
            addTextToContainer(unratedGemsContainer, "All played tracks are rated!");
            return;
        }

        for (final org.schabi.newpipe.database.playback.dao.PlaybackStatisticsDAO
                .PlaybackStatsWithStream track : tracks) {
            // Format playtime as hours and minutes
            final long hours = track.getTotal_play_time_millis() / (1000 * 60 * 60);
            final long minutes = (track.getTotal_play_time_millis() / (1000 * 60)) % 60;
            final String playTime = hours > 0
                    ? String.format(Locale.getDefault(), "%dh %dm", hours, minutes)
                    : String.format(Locale.getDefault(), "%dm", minutes);

            final String text = String.format(Locale.getDefault(),
                    "%s (%s played)", track.getTitle(), playTime);
            addTextToContainer(unratedGemsContainer, text);
        }
    }

    private void displayMostSkippedTracks(
            final List<org.schabi.newpipe.database.playback.dao.PlaybackStatisticsDAO
                    .PlaybackStatsWithStream> tracks) {
        mostSkippedTracksContainer.removeAllViews();

        if (tracks.isEmpty()) {
            addTextToContainer(mostSkippedTracksContainer, "No tracks skipped yet");
            return;
        }

        for (final org.schabi.newpipe.database.playback.dao.PlaybackStatisticsDAO
                .PlaybackStatsWithStream track : tracks) {
            final String text = String.format(Locale.getDefault(),
                    "%s (%d skips)", track.getTitle(), track.getSkip_count());
            addTextToContainer(mostSkippedTracksContainer, text);
        }
    }

    private void displayMostCompletedTracks(
            final List<org.schabi.newpipe.database.playback.dao.PlaybackStatisticsDAO
                    .PlaybackStatsWithStream> tracks) {
        mostCompletedTracksContainer.removeAllViews();

        if (tracks.isEmpty()) {
            addTextToContainer(mostCompletedTracksContainer, "No tracks completed yet");
            return;
        }

        for (final org.schabi.newpipe.database.playback.dao.PlaybackStatisticsDAO
                .PlaybackStatsWithStream track : tracks) {
            final String text = String.format(Locale.getDefault(),
                    "%s (%d completions)", track.getTitle(), track.getCompletion_count());
            addTextToContainer(mostCompletedTracksContainer, text);
        }
    }

    private void displayMostRestartedTracks(
            final List<org.schabi.newpipe.database.playback.dao.PlaybackStatisticsDAO
                    .PlaybackStatsWithStream> tracks) {
        mostRestartedTracksContainer.removeAllViews();

        if (tracks.isEmpty()) {
            addTextToContainer(mostRestartedTracksContainer, "No tracks replayed yet");
            return;
        }

        for (final org.schabi.newpipe.database.playback.dao.PlaybackStatisticsDAO
                .PlaybackStatsWithStream track : tracks) {
            final String text = String.format(Locale.getDefault(),
                    "%s (%d replays)", track.getTitle(), track.getRestart_count());
            addTextToContainer(mostRestartedTracksContainer, text);
        }
    }

    private void displayPlaylistStats(final List<PlaylistDAO.PlaylistRatingStats> playlists) {
        playlistStatsContainer.removeAllViews();

        if (playlists.isEmpty()) {
            addTextToContainer(playlistStatsContainer, "No playlists with tracks yet");
            return;
        }

        // Show top 5 playlists by average rating
        final int displayCount = Math.min(5, playlists.size());
        for (int i = 0; i < displayCount; i++) {
            final PlaylistDAO.PlaylistRatingStats playlist = playlists.get(i);
            final String avgRating = playlist.getAverageRating() != null
                    ? String.format(Locale.getDefault(), "%.1f★", playlist.getAverageRating())
                    : "No ratings";
            final String text = String.format(Locale.getDefault(),
                    "%s: %s (%d/%d rated)",
                    playlist.getPlaylistName(),
                    avgRating,
                    playlist.getRatedTracks(),
                    playlist.getTotalTracks());
            addTextToContainer(playlistStatsContainer, text);
        }
    }

    private void addTextToContainer(final LinearLayout container, final String text) {
        final TextView textView = new TextView(requireContext());
        textView.setText(text);
        textView.setPadding(0, 4, 0, 4);
        container.addView(textView);
    }

    /**
     * Cleans artist name by removing common YouTube channel suffixes.
     * Removes: " - Topic", " VEVO", " - Official", etc.
     *
     * @param artistName the original artist/uploader name
     * @return cleaned artist name, or "Unknown" if input is null
     */
    private String cleanArtistName(final String artistName) {
        if (artistName == null) {
            return "Unknown";
        }

        String cleaned = artistName;

        // Remove common YouTube music channel suffixes (case-insensitive)
        cleaned = cleaned.replaceAll("(?i)\\s*-\\s*Topic$", "");
        cleaned = cleaned.replaceAll("(?i)\\s*VEVO$", "");
        cleaned = cleaned.replaceAll("(?i)\\s*-\\s*Official$", "");
        cleaned = cleaned.replaceAll("(?i)\\s*Official$", "");

        return cleaned.trim();
    }
}
