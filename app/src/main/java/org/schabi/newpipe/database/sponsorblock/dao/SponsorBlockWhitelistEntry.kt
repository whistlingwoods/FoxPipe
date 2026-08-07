package org.schabi.newpipe.database.sponsorblock.dao

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = SponsorBlockWhitelistEntry.SPONSORBLOCK_WHITELIST_TABLE,
    primaryKeys = [SponsorBlockWhitelistEntry.UPLOADER]
)
class SponsorBlockWhitelistEntry(@field:ColumnInfo(name = UPLOADER) var uploader: String) {
    companion object {
        const val SPONSORBLOCK_WHITELIST_TABLE: String = "sponsorblock_whitelist"
        const val UPLOADER: String = "uploader"
    }
}
