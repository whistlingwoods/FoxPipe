package org.schabi.newpipe.local.sponsorblock

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.Callable
import org.schabi.newpipe.NewPipeDatabase.getInstance
import org.schabi.newpipe.database.sponsorblock.dao.SponsorBlockWhitelistDAO
import org.schabi.newpipe.database.sponsorblock.dao.SponsorBlockWhitelistEntry

class SponsorBlockDataManager(context: Context) {
    private val sponsorBlockWhitelistTable: SponsorBlockWhitelistDAO

    init {
        val database = getInstance(context)
        sponsorBlockWhitelistTable = database.sponsorBlockWhitelistDAO()
    }

    fun addToWhitelist(uploader: String): Maybe<Long> {
        return Maybe.fromCallable<Long>(
            Callable {
                val entry = SponsorBlockWhitelistEntry(uploader)
                sponsorBlockWhitelistTable.insert(entry)
            }
        ).subscribeOn(Schedulers.io())
    }

    fun removeFromWhitelist(uploader: String?): Completable {
        return Completable.fromAction { sponsorBlockWhitelistTable.deleteByUploader(uploader ?: "") }
    }

    fun isWhiteListed(uploader: String?): Single<Boolean> {
        return Single.fromCallable<Boolean>(Callable { sponsorBlockWhitelistTable.exists(uploader ?: "") })
            .subscribeOn(Schedulers.io())
    }

    fun clearWhitelist(): Completable {
        return Completable.fromAction { sponsorBlockWhitelistTable.deleteAll() }
    }
}
