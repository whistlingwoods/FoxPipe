/*
 * SPDX-FileCopyrightText: 2018-2022 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.playlist.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.playlist.model.PlaylistEntity

@Dao
interface PlaylistDAO : BasicDAO<PlaylistEntity> {

    @Query("SELECT * FROM playlists")
    override fun getAll(): Flowable<List<PlaylistEntity>>

    @Query("DELETE FROM playlists")
    override fun deleteAll(): Int

    override fun listByService(serviceId: Int): Flowable<List<PlaylistEntity>> {
        throw UnsupportedOperationException()
    }

    @Query("SELECT * FROM playlists WHERE uid = :playlistId")
    fun getPlaylist(playlistId: Long): Flowable<MutableList<PlaylistEntity>>

    @Query("DELETE FROM playlists WHERE uid = :playlistId")
    fun deletePlaylist(playlistId: Long): Int

    @get:Query("SELECT COUNT(*) FROM playlists")
    val count: Flowable<Long>

    @Transaction
    fun upsertPlaylist(playlist: PlaylistEntity): Long {
        if (playlist.uid == -1L) {
            // This situation is probably impossible.
            return insert(playlist)
        } else {
            update(playlist)
            return playlist.uid
        }
    }

    // Statistics queries for rating dashboard

    @Query(
        """
        SELECT p.uid as playlistId, p.name as playlistName,
               AVG(s.user_rating) as averageRating,
               COUNT(s.uid) as totalTracks,
               COUNT(CASE WHEN s.user_rating IS NOT NULL THEN 1 END) as ratedTracks
        FROM playlists p
        INNER JOIN playlist_stream_join psj ON p.uid = psj.playlist_id
        INNER JOIN streams s ON psj.stream_id = s.uid
        WHERE p.is_thumbnail_permanent = 1
        GROUP BY p.uid
        HAVING COUNT(s.uid) > 0
        ORDER BY averageRating DESC
        """
    )
    fun getPlaylistRatingStatistics(): Flowable<List<PlaylistRatingStats>>

    @Query(
        """
        SELECT
            COUNT(DISTINCT s.uid) as downloadedCount,
            (SELECT COUNT(DISTINCT s2.uid)
             FROM playlist_stream_join psj2
             INNER JOIN streams s2 ON psj2.stream_id = s2.uid
             INNER JOIN playlists p2 ON psj2.playlist_id = p2.uid
             WHERE p2.is_thumbnail_permanent = 1) as totalCount
        FROM playlist_stream_join psj
        INNER JOIN streams s ON psj.stream_id = s.uid
        INNER JOIN playlists p ON psj.playlist_id = p.uid
        INNER JOIN offline_file_mappings ofm ON s.service_id = ofm.stream_service_id AND s.url = ofm.stream_url
        WHERE p.is_thumbnail_permanent = 1
        """
    )
    fun getLocalPlaylistDownloadStats(): Flowable<DownloadStats>

    /**
     * Statistics data classes for playlist rating dashboard.
     */
    data class PlaylistRatingStats(
        @ColumnInfo(name = "playlistId") val playlistId: Long,
        @ColumnInfo(name = "playlistName") val playlistName: String,
        @ColumnInfo(name = "averageRating") val averageRating: Double?,
        @ColumnInfo(name = "totalTracks") val totalTracks: Long,
        @ColumnInfo(name = "ratedTracks") val ratedTracks: Long
    )

    data class DownloadStats(
        @ColumnInfo(name = "downloadedCount") val downloadedCount: Long,
        @ColumnInfo(name = "totalCount") val totalCount: Long
    )
}
