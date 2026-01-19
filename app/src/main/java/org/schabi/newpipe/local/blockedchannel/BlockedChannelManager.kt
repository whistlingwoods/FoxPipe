/*
 * SPDX-FileCopyrightText: 2025 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.blockedchannel

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.blockedchannel.BlockedChannelDAO
import org.schabi.newpipe.database.blockedchannel.BlockedChannelEntity

class BlockedChannelManager(context: Context) {
    private val database = NewPipeDatabase.getInstance(context)
    private val blockedChannelTable = database.blockedChannelDAO()

    fun blockedChannelTable(): BlockedChannelDAO = blockedChannelTable
    fun blockedChannels() = blockedChannelTable.getAll()

    fun isChannelBlocked(serviceId: Int, url: String?): Boolean {
        return blockedChannelTable.isChannelBlocked(serviceId, url)
    }

    fun isChannelBlockedAsync(serviceId: Int, url: String?): io.reactivex.rxjava3.core.Single<Boolean> {
        return io.reactivex.rxjava3.core.Single.fromCallable {
            blockedChannelTable.isChannelBlocked(serviceId, url)
        }.subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
    }

    fun blockChannel(serviceId: Int, url: String?, name: String?): Completable {
        return Completable.fromAction {
            val entity = BlockedChannelEntity(
                serviceId = serviceId,
                url = url,
                name = name
            )
            blockedChannelTable.insert(entity)
        }.subscribeOn(Schedulers.io())
    }

    fun unblockChannel(serviceId: Int, url: String?): Completable {
        return Completable.fromCallable {
            blockedChannelTable.deleteBlockedChannel(serviceId, url)
        }.subscribeOn(Schedulers.io())
    }

    fun deleteBlockedChannel(blockedChannelEntity: BlockedChannelEntity) {
        blockedChannelTable.delete(blockedChannelEntity)
    }
}
