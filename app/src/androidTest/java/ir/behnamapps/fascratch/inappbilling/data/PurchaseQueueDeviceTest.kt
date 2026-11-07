package ir.behnamapps.fascratch.inappbilling.data

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.behnamapps.fascratch.inappbilling.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PurchaseQueueDeviceTest {
    @Test fun encryptedQueueSurvivesReopeningAndCompletesAtomically() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.noBackupFilesDir, "purchase-queue-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = directory }
        try {
            val course = Course(UUID.randomUUID().toString(), "", "", "test-sku")
            val first = PurchaseVault(context)
            first.savePendingReceipt(course, "cafebazaar", Receipt(course.sku, "test-private-token", "test-owner"))
            val queued = first.pending().single()
            val reopened = PurchaseVault(context)
            assertTrue(reopened.hasPendingReceipt(course.id))
            assertFalse(reopened.wasVerified(course.id))
            assertEquals("test-owner", reopened.receipt(course.id)?.ownerUuid)
            // A second invocation cannot rebind the same receipt to another account.
            assertEquals("test-owner", reopened.beginConfirmation(course, "cafebazaar", "other").owner)
            reopened.defer(queued, CourseFailure("wait", 429, retryAfterSeconds = 3600))
            val retried = PurchaseVault(context).pending().single()
            assertEquals(1, retried.attempt)
            assertTrue(retried.next >= System.currentTimeMillis() + 3_590_000)
            assertTrue(PurchaseVault(context).cooldownUntil() >= retried.next)
            assertFalse(directory.listFiles()!!.any { String(it.readBytes(), Charsets.ISO_8859_1).contains("test-private-token") })
            reopened.complete(retried, CourseAccess(course.id, "test-access", 123))
            val completed = PurchaseVault(context)
            assertFalse(completed.hasPendingReceipt(course.id))
            assertTrue(completed.wasVerified(course.id))
            assertEquals("test-access", completed.access(course.id)?.token)
            assertEquals("test-private-token", completed.receipt(course.id)?.token)
        } finally { directory.deleteRecursively() }
    }
    @Test fun permanentRejectionPreservesReceiptAndStopsAutomaticRetry() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.noBackupFilesDir, "purchase-queue-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = directory }
        try {
            val course = Course(UUID.randomUUID().toString(), "", "", "test-sku")
            val vault = PurchaseVault(context)
            vault.saveReceipt(course.id, Receipt(course.sku, "test-rejected-token"))
            val pending = vault.beginConfirmation(course, "myket", "owner")
            vault.saveAccess(CourseAccess(course.id, "old-access", 123))
            vault.defer(pending, CourseFailure("rejected", 422, "refunded"))
            val reopened = PurchaseVault(context)
            assertTrue(reopened.pending().single().blocked)
            assertFalse(reopened.wasVerified(course.id))
            assertNull(reopened.access(course.id))
            assertNotNull(reopened.receipt(course.id))
        } finally { directory.deleteRecursively() }
    }
}
