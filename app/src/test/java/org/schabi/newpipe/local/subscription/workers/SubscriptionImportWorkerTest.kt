package org.schabi.newpipe.local.subscription.workers

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.withSettings
import org.mockito.junit.MockitoJUnitRunner
import org.schabi.newpipe.streams.io.StoredFileHelper

@RunWith(MockitoJUnitRunner::class)
class SubscriptionImportWorkerTest {

    @Test
    fun `Octet-stream csv import must resolve to csv`() {
        assertResolvedContentType("csv", StoredFileHelper.DEFAULT_MIME, "subscriptions.csv")
    }

    @Test
    fun `Missing content type csv import must resolve to csv`() {
        assertResolvedContentType("csv", null, "subscriptions.csv")
    }

    @Test
    fun `Octet-stream takeout archive must resolve to zip`() {
        assertResolvedContentType("zip", StoredFileHelper.DEFAULT_MIME, "takeout.zip")
    }

    @Test
    fun `Provider content type must be preserved`() {
        assertResolvedContentType("application/json", "application/json")
    }

    @Test
    fun `Unknown octet-stream import must stay octet-stream`() {
        assertResolvedContentType(StoredFileHelper.DEFAULT_MIME, StoredFileHelper.DEFAULT_MIME, "subscriptions")
    }

    private fun assertResolvedContentType(
        expectedContentType: String,
        contentType: String?,
        fileName: String? = null
    ) {
        val fileHelper = Mockito.mock(StoredFileHelper::class.java, withSettings().stubOnly())
        `when`(fileHelper.type).thenReturn(contentType)
        if (fileName != null) {
            `when`(fileHelper.name).thenReturn(fileName)
        }

        assertEquals(
            expectedContentType,
            SubscriptionImportWorker.getInputStreamContentType(fileHelper)
        )
    }
}
