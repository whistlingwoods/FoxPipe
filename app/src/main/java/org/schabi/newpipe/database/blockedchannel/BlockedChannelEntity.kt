/*
 * SPDX-FileCopyrightText: 2025 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.blockedchannel

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.schabi.newpipe.util.NO_SERVICE_ID
import org.schabi.newpipe.util.image.ImageStrategy

@Entity(
    tableName = BlockedChannelEntity.Companion.BLOCKED_CHANNEL_TABLE,
    indices = [
        Index(
            value = [BlockedChannelEntity.Companion.BLOCKED_CHANNEL_SERVICE_ID, BlockedChannelEntity.Companion.BLOCKED_CHANNEL_URL],
            unique = true
        )
    ]
)
data class BlockedChannelEntity(
    @PrimaryKey(autoGenerate = true)
    var uid: Long = 0,

    @ColumnInfo(name = BLOCKED_CHANNEL_SERVICE_ID)
    var serviceId: Int = NO_SERVICE_ID,

    @ColumnInfo(name = BLOCKED_CHANNEL_URL)
    var url: String? = null,

    @ColumnInfo(name = BLOCKED_CHANNEL_NAME)
    var name: String? = null
) {

    companion object {
        const val BLOCKED_CHANNEL_UID: String = "uid"
        const val BLOCKED_CHANNEL_TABLE: String = "blocked_channels"
        const val BLOCKED_CHANNEL_SERVICE_ID: String = "service_id"
        const val BLOCKED_CHANNEL_URL: String = "url"
        const val BLOCKED_CHANNEL_NAME: String = "name"
    }
}
