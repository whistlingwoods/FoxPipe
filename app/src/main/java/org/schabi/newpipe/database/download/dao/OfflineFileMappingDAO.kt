package org.schabi.newpipe.database.download.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.download.model.OfflineFileMappingEntity

/**
 * DAO for offline file mapping operations.
 * Maps remote stream URLs to local downloaded files for offline playback.
 */
@Dao
interface OfflineFileMappingDAO {
    /**
     * Get the offline file mapping for a specific stream.
     *
     * @param serviceId the service ID (e.g., YouTube = 0)
     * @param url the stream URL
     * @return Flowable emitting list with mapping if exists, empty list otherwise
     */
    @Query(
        "SELECT * FROM offline_file_mappings " +
            "WHERE stream_service_id = :serviceId AND stream_url = :url " +
            "LIMIT 1"
    )
    fun getMapping(serviceId: Int, url: String): Flowable<List<OfflineFileMappingEntity>>

    /**
     * Insert or update an offline file mapping.
     *
     * @param mapping the mapping to insert
     * @return the row ID of the inserted/updated mapping
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMapping(mapping: OfflineFileMappingEntity): Long

    /**
     * Delete a specific offline file mapping.
     *
     * @param id the mapping ID to delete
     * @return number of rows deleted
     */
    @Query("DELETE FROM offline_file_mappings WHERE mapping_id = :id")
    fun deleteMapping(id: Long): Int

    /**
     * Update the availability status of a mapping.
     * Used to mark files as unavailable when they're deleted from storage.
     *
     * @param id the mapping ID
     * @param available true if file is available, false otherwise
     * @return number of rows updated
     */
    @Query("UPDATE offline_file_mappings SET is_available = :available WHERE mapping_id = :id")
    fun updateAvailability(id: Long, available: Boolean): Int

    /**
     * Get all available offline file mappings.
     *
     * @return Flowable emitting list of all available mappings
     */
    @Query("SELECT * FROM offline_file_mappings WHERE is_available = 1")
    fun getAllAvailableMappings(): Flowable<List<OfflineFileMappingEntity>>
}
