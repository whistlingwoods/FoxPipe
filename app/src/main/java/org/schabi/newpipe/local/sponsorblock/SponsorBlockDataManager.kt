package org.schabi.newpipe.local.sponsorblock

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase.getInstance
import org.schabi.newpipe.database.sponsorblock.dao.SponsorBlockWhitelistDAO
import org.schabi.newpipe.database.sponsorblock.dao.SponsorBlockWhitelistEntry

class SponsorBlockDataManager(context: Context) {
    private val sponsorBlockWhitelistTable: SponsorBlockWhitelistDAO = getInstance(context).sponsorBlockWhitelistDAO()

    fun addToWhitelist(uploader: String?): Maybe<Long> {
        return if (uploader == null) {
            Maybe.empty()
        } else {
            Maybe.fromCallable {
                sponsorBlockWhitelistTable.insert(SponsorBlockWhitelistEntry(uploader))
            }.subscribeOn(Schedulers.io())
        }
    }

    fun removeFromWhitelist(uploader: String?): Completable {
        return if (uploader == null) {
            Completable.complete()
        } else {
            Completable.fromAction {
                sponsorBlockWhitelistTable.deleteByUploader(uploader)
            }.subscribeOn(Schedulers.io())
        }
    }

    fun isWhiteListed(uploader: String?): Single<Boolean> {
        return if (uploader == null) {
            Single.just(false)
        } else {
            Single.fromCallable {
                sponsorBlockWhitelistTable.exists(uploader)
            }.subscribeOn(Schedulers.io())
        }
    }

    fun clearWhitelist(): Completable {
        return Completable.fromAction {
            sponsorBlockWhitelistTable.deleteAll()
        }.subscribeOn(Schedulers.io())
    }
}
