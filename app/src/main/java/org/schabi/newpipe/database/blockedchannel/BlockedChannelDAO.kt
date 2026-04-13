/*
 * SPDX-FileCopyrightText: 2025 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.blockedchannel

import androidx.room.Dao
import androidx.room.Query
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.BasicDAO

@Dao
abstract class BlockedChannelDAO : BasicDAO<BlockedChannelEntity> {
    @Query("SELECT * FROM " + BlockedChannelEntity.BLOCKED_CHANNEL_TABLE)
    abstract override fun getAll(): Flowable<List<BlockedChannelEntity>>

    @Query("SELECT * FROM " + BlockedChannelEntity.BLOCKED_CHANNEL_TABLE + " WHERE " + BlockedChannelEntity.BLOCKED_CHANNEL_SERVICE_ID + " = :serviceId")
    abstract override fun listByService(serviceId: Int): Flowable<List<BlockedChannelEntity>>

    @Query("DELETE FROM " + BlockedChannelEntity.BLOCKED_CHANNEL_TABLE)
    abstract override fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM " + BlockedChannelEntity.BLOCKED_CHANNEL_TABLE)
    abstract fun getCount(): Flowable<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM " + BlockedChannelEntity.BLOCKED_CHANNEL_TABLE + " WHERE " + BlockedChannelEntity.BLOCKED_CHANNEL_SERVICE_ID + " = :serviceId AND " + BlockedChannelEntity.BLOCKED_CHANNEL_URL + " = :url)")
    abstract fun isChannelBlocked(serviceId: Int, url: String?): Boolean

    @Query("DELETE FROM " + BlockedChannelEntity.BLOCKED_CHANNEL_TABLE + " WHERE " + BlockedChannelEntity.BLOCKED_CHANNEL_SERVICE_ID + " = :serviceId AND " + BlockedChannelEntity.BLOCKED_CHANNEL_URL + " = :url")
    abstract fun deleteBlockedChannel(serviceId: Int, url: String?): Int
}
