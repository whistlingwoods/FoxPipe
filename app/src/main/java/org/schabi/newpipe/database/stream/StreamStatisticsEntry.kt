package org.schabi.newpipe.database.stream

import androidx.room.ColumnInfo
import androidx.room.Embedded
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity.PLAYBACK_FINISHED_END_MILLISECONDS
import org.schabi.newpipe.database.stream.model.StreamStateEntity.STREAM_PROGRESS_MILLIS
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.image.ImageStrategy
import java.time.OffsetDateTime

class StreamStatisticsEntry(
    @Embedded
    val streamEntity: StreamEntity,

    @ColumnInfo(name = STREAM_PROGRESS_MILLIS, defaultValue = "0")
    val progressMillis: Long,

    @ColumnInfo(name = StreamHistoryEntity.JOIN_STREAM_ID)
    val streamId: Long,

    @ColumnInfo(name = STREAM_LATEST_DATE)
    val latestAccessDate: OffsetDateTime,

    @ColumnInfo(name = STREAM_WATCH_COUNT)
    val watchCount: Long
) : LocalItem {
    fun toStreamInfoItem(): StreamInfoItem {
        val item = StreamInfoItem(streamEntity.serviceId, streamEntity.url, streamEntity.title, streamEntity.streamType)
        item.duration = streamEntity.duration
        item.uploaderName = streamEntity.uploader
        item.uploaderUrl = streamEntity.uploaderUrl
        item.thumbnails = ImageStrategy.dbUrlToImageList(streamEntity.thumbnailUrl)

        return item
    }

    override fun getLocalItemType(): LocalItem.LocalItemType {
        return LocalItem.LocalItemType.STATISTIC_STREAM_ITEM
    }

    /**
     * The video will be considered as finished, if the time left is less than
     * [PLAYBACK_FINISHED_END_MILLISECONDS] and the progress is at least 3/4 of the video length.
     * The state will be saved anyway, so that it can be shown under stream info items, but the
     * player will not resume if a state is considered as finished. Finished streams are also the
     * ones that can be filtered out in the feed fragment.
     * @return whether the stream is finished or not
     */
    fun isFinished(): Boolean {
        val durationInSeconds = streamEntity.duration
        return progressMillis >= durationInSeconds * 1000 - PLAYBACK_FINISHED_END_MILLISECONDS &&
            progressMillis >= durationInSeconds * 1000 * 3 / 4
    }

    companion object {
        const val STREAM_LATEST_DATE = "latestAccess"
        const val STREAM_WATCH_COUNT = "watchCount"
    }
}
