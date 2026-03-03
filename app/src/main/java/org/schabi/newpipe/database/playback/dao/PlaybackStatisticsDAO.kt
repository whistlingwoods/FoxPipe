package org.schabi.newpipe.database.playback.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import java.time.OffsetDateTime
import org.schabi.newpipe.database.playback.model.PlaybackStatisticsEntity

@Dao
abstract class PlaybackStatisticsDAO {

    @Query("SELECT * FROM playback_statistics WHERE stream_id = :streamId")
    abstract fun getStatistics(streamId: Long): Maybe<PlaybackStatisticsEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    internal abstract fun silentInsert(stats: PlaybackStatisticsEntity): Long

    @Query(
        """
        UPDATE playback_statistics SET
            total_play_time_millis = :totalPlayTimeMillis,
            last_updated = :lastUpdated
        WHERE stream_id = :streamId
        """
    )
    internal abstract fun updatePlayTime(
        streamId: Long,
        totalPlayTimeMillis: Long,
        lastUpdated: OffsetDateTime
    ): Int

    @Query(
        """
        UPDATE playback_statistics SET
            skip_count = skip_count + 1,
            last_updated = :lastUpdated
        WHERE stream_id = :streamId
        """
    )
    internal abstract fun incrementSkipCount(streamId: Long, lastUpdated: OffsetDateTime): Int

    @Query(
        """
        UPDATE playback_statistics SET
            completion_count = completion_count + 1,
            last_updated = :lastUpdated
        WHERE stream_id = :streamId
        """
    )
    internal abstract fun incrementCompletionCount(
        streamId: Long,
        lastUpdated: OffsetDateTime
    ): Int

    @Query(
        """
        UPDATE playback_statistics SET
            restart_count = restart_count + 1,
            last_updated = :lastUpdated
        WHERE stream_id = :streamId
        """
    )
    internal abstract fun incrementRestartCount(streamId: Long, lastUpdated: OffsetDateTime): Int

    @Query(
        """
        UPDATE playback_statistics SET
            volume_change_count = volume_change_count + 1,
            last_updated = :lastUpdated
        WHERE stream_id = :streamId
        """
    )
    internal abstract fun incrementVolumeChangeCount(
        streamId: Long,
        lastUpdated: OffsetDateTime
    ): Int

    @Query(
        """
        UPDATE playback_statistics SET
            pause_count = pause_count + 1,
            last_updated = :lastUpdated
        WHERE stream_id = :streamId
        """
    )
    internal abstract fun incrementPauseCount(streamId: Long, lastUpdated: OffsetDateTime): Int

    @Query(
        """
        UPDATE playback_statistics SET
            play_count = play_count + 1,
            last_updated = :lastUpdated
        WHERE stream_id = :streamId
        """
    )
    internal abstract fun incrementPlayCount(streamId: Long, lastUpdated: OffsetDateTime): Int

    @Query(
        """
        UPDATE playback_statistics SET
            seek_count = seek_count + 1,
            last_updated = :lastUpdated
        WHERE stream_id = :streamId
        """
    )
    internal abstract fun incrementSeekCount(streamId: Long, lastUpdated: OffsetDateTime): Int

    @Query("SELECT SUM(total_play_time_millis) FROM playback_statistics")
    abstract fun getTotalPlayTime(): Flowable<Long>

    @Query(
        """
        SELECT ps.*, s.title, s.uploader
        FROM playback_statistics ps
        INNER JOIN streams s ON ps.stream_id = s.uid
        WHERE s.user_rating IS NULL
        ORDER BY ps.total_play_time_millis DESC
        LIMIT :limit
        """
    )
    abstract fun getMostPlayedUnratedStreams(limit: Int): Flowable<List<PlaybackStatsWithStream>>

    @Query(
        """
        SELECT ps.*, s.title, s.uploader
        FROM playback_statistics ps
        INNER JOIN streams s ON ps.stream_id = s.uid
        WHERE ps.skip_count > 0
        ORDER BY ps.skip_count DESC
        LIMIT :limit
        """
    )
    abstract fun getMostSkippedStreams(limit: Int): Flowable<List<PlaybackStatsWithStream>>

    @Query(
        """
        SELECT ps.*, s.title, s.uploader
        FROM playback_statistics ps
        INNER JOIN streams s ON ps.stream_id = s.uid
        WHERE ps.completion_count > 0
        ORDER BY ps.completion_count DESC
        LIMIT :limit
        """
    )
    abstract fun getMostCompletedStreams(limit: Int): Flowable<List<PlaybackStatsWithStream>>

    @Query(
        """
        SELECT ps.*, s.title, s.uploader
        FROM playback_statistics ps
        INNER JOIN streams s ON ps.stream_id = s.uid
        WHERE ps.restart_count > 0
        ORDER BY ps.restart_count DESC
        LIMIT :limit
        """
    )
    abstract fun getMostRestartedStreams(limit: Int): Flowable<List<PlaybackStatsWithStream>>

    @Query("SELECT COUNT(*) FROM playback_statistics WHERE skip_count > 0 OR completion_count > 0")
    abstract fun getTotalTracksPlayed(): Flowable<Long>

    /**
     * Adds playtime to a stream's statistics.
     * Creates entry if it doesn't exist.
     */
    @Transaction
    open fun addPlayTime(streamId: Long, playTimeMillis: Long) {
        val now = OffsetDateTime.now()
        val existing = getStatistics(streamId).blockingGet()

        if (existing == null) {
            // Create new entry
            val stats = PlaybackStatisticsEntity(
                streamId = streamId,
                totalPlayTimeMillis = playTimeMillis,
                lastUpdated = now
            )
            silentInsert(stats)
        } else {
            // Update existing
            updatePlayTime(streamId, existing.totalPlayTimeMillis + playTimeMillis, now)
        }
    }

    /**
     * Records a skip event for a stream.
     */
    @Transaction
    open fun recordSkip(streamId: Long) {
        ensureStatsExist(streamId)
        incrementSkipCount(streamId, OffsetDateTime.now())
    }

    /**
     * Records a completion event for a stream.
     */
    @Transaction
    open fun recordCompletion(streamId: Long) {
        ensureStatsExist(streamId)
        incrementCompletionCount(streamId, OffsetDateTime.now())
    }

    /**
     * Records a restart event for a stream.
     */
    @Transaction
    open fun recordRestart(streamId: Long) {
        ensureStatsExist(streamId)
        incrementRestartCount(streamId, OffsetDateTime.now())
    }

    /**
     * Records a volume change event for a stream.
     */
    @Transaction
    open fun recordVolumeChange(streamId: Long) {
        ensureStatsExist(streamId)
        incrementVolumeChangeCount(streamId, OffsetDateTime.now())
    }

    /**
     * Records a pause event for a stream.
     */
    @Transaction
    open fun recordPause(streamId: Long) {
        ensureStatsExist(streamId)
        incrementPauseCount(streamId, OffsetDateTime.now())
    }

    /**
     * Records a play/resume event for a stream.
     */
    @Transaction
    open fun recordPlay(streamId: Long) {
        ensureStatsExist(streamId)
        incrementPlayCount(streamId, OffsetDateTime.now())
    }

    /**
     * Records a seek event for a stream.
     */
    @Transaction
    open fun recordSeek(streamId: Long) {
        ensureStatsExist(streamId)
        incrementSeekCount(streamId, OffsetDateTime.now())
    }

    private fun ensureStatsExist(streamId: Long) {
        val existing = getStatistics(streamId).blockingGet()
        if (existing == null) {
            silentInsert(
                PlaybackStatisticsEntity(
                    streamId = streamId,
                    lastUpdated = OffsetDateTime.now()
                )
            )
        }
    }

    /**
     * Data class combining playback stats with stream info.
     */
    data class PlaybackStatsWithStream(
        val stats_id: Long,
        val stream_id: Long,
        val total_play_time_millis: Long,
        val skip_count: Int,
        val completion_count: Int,
        val restart_count: Int,
        val volume_change_count: Int,
        val title: String,
        val uploader: String?
    )
}
