package org.schabi.newpipe.local.sponsorblock

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import org.schabi.newpipe.NewPipeDatabase.getInstance
import org.schabi.newpipe.database.sponsorblock.dao.SponsorBlockWhitelistDAO

class SponsorBlockDataManager(context: Context) {
    private val sponsorBlockWhitelistTable: SponsorBlockWhitelistDAO? = null

    init {
        val database = getInstance(context)
    }

    fun addToWhitelist(uploader: String?): Maybe<Long> {
//      return Maybe.fromCallable(() -> {
//          final SponsorBlockWhitelistEntry entry = new SponsorBlockWhitelistEntry(uploader);
//          return sponsorBlockWhitelistTable.insert(entry);
//      }).subscribeOn(Schedulers.io());
        return Maybe.empty()
    }

    fun removeFromWhitelist(uploader: String?): Completable {
//      return Completable.fromAction(() -> sponsorBlockWhitelistTable.deleteByUploader(uploader));
        return Completable.complete()
    }

    fun isWhiteListed(uploader: String?): Single<Boolean> {
//      return Single.fromCallable(() -> sponsorBlockWhitelistTable.exists(uploader))
//              .subscribeOn(Schedulers.io());
        return Single.just(false)
    }

    fun clearWhitelist(): Completable {
//      return Completable.fromAction(sponsorBlockWhitelistTable::deleteAll);
        return Completable.complete()
    }
}
