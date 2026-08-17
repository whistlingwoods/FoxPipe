package org.schabi.newpipe.database.sponsorblock.dao

import androidx.room.Dao
import androidx.room.Query
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.BasicDAO

@Dao
abstract class SponsorBlockWhitelistDAO : BasicDAO<SponsorBlockWhitelistEntry?> {
    @Query("SELECT * FROM " + SponsorBlockWhitelistEntry.SPONSORBLOCK_WHITELIST_TABLE)
    abstract override fun getAll(): Flowable<List<SponsorBlockWhitelistEntry?>>

    @Query("DELETE FROM " + SponsorBlockWhitelistEntry.SPONSORBLOCK_WHITELIST_TABLE)
    abstract override fun deleteAll(): Int

    override fun listByService(serviceId: Int): Flowable<List<SponsorBlockWhitelistEntry?>> {
        throw UnsupportedOperationException()
    }

    @Query("DELETE FROM " + SponsorBlockWhitelistEntry.SPONSORBLOCK_WHITELIST_TABLE + " WHERE " + SponsorBlockWhitelistEntry.UPLOADER + " = :uploader")
    abstract fun deleteByUploader(uploader: String?): Int

    @Query("SELECT 1 FROM " + SponsorBlockWhitelistEntry.SPONSORBLOCK_WHITELIST_TABLE + " WHERE " + SponsorBlockWhitelistEntry.UPLOADER + " = :uploader")
    abstract fun exists(uploader: String?): Boolean
}
