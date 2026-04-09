package org.schabi.newpipe.database.download.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.OffsetDateTime

/**
 * Entity for mapping remote stream URLs to local downloaded files.
 * Enables offline playback by checking if a stream has already been downloaded.
 */
@Entity(
    tableName = "offline_file_mappings",
    indices = [
        Index(value = ["stream_service_id", "stream_url"], unique = true)
    ]
)
data class OfflineFileMappingEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "mapping_id")
    var uid: Long = 0,

    @ColumnInfo(name = "stream_service_id")
    var serviceId: Int,

    @ColumnInfo(name = "stream_url")
    var streamUrl: String,

    @ColumnInfo(name = "local_file_uri")
    var localFileUri: String,

    @ColumnInfo(name = "file_size")
    var fileSize: Long,

    @ColumnInfo(name = "mime_type")
    var mimeType: String,

    @ColumnInfo(name = "download_timestamp")
    var downloadTimestamp: OffsetDateTime,

    @ColumnInfo(name = "is_available")
    var isAvailable: Boolean = true
)
