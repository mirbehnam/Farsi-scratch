package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FreePreviewPolicyTest {
    private val base = "https://api.behnamapp.ir/scratch/v1"
    private val id = "ae6b3b4c-1d11-43fe-bd17-1c80ad234666"
    private val asset = "7d6b3b4c-1d11-43fe-bd17-1c80ad234666"
    private val url = "$base/lessons/$id/video/$asset?expires=1900000000&signature=example"
    private val preview = Lesson(id, id, "", "Preview", 1, 100, "a".repeat(64), isPreview = true, previewUrl = url)

    @Test fun previewIsAccessibleWithoutPurchaseAndPaidLessonIsNot() {
        assertTrue(CoursePolicy.canLearn(preview, false))
        assertTrue(CoursePolicy.canLearn(preview, true))
        assertFalse(CoursePolicy.canLearn(preview.copy(isPreview = false), false))
        assertTrue(CoursePolicy.canLearn(preview.copy(isPreview = false), true))
    }

    @Test fun signedPreviewUsesOnlySameOriginLessonAssetRoute() {
        assertEquals(url, CoursePolicy.previewUrl(base, preview))
        val candidates = listOf(
            preview.copy(isPreview = false), preview.copy(previewUrl = null),
            preview.copy(previewUrl = url.replace("https:", "http:")),
            preview.copy(previewUrl = url.replace("api.behnamapp.ir", "evil.test")),
            preview.copy(previewUrl = url.replace("api.behnamapp.ir", "user@api.behnamapp.ir")),
            preview.copy(previewUrl = "$base/lessons/$id/purchased-video?signature=example"),
            preview.copy(previewUrl = url.replace(id, asset)),
            preview.copy(previewUrl = url.replace("video/$asset", "video/invalid")),
            preview.copy(previewUrl = url.substringBefore('?')),
            preview.copy(previewUrl = "$url#fragment")
        )
        candidates.forEach { lesson ->
            try { CoursePolicy.previewUrl(base, lesson); fail("Unsafe preview URL accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun previewDefaultsClosedForOldCachedMetadataAndVersionKeyIsUnchanged() {
        val old = preview.copy(isPreview = false, previewUrl = null)
        assertFalse(CoursePolicy.canLearn(old, false))
        assertEquals(CoursePolicy.downloadKey(old), CoursePolicy.downloadKey(preview))
    }

    @Test fun everyCoursePurchasesItsOwnServerSkuAndReceivesItsOwnAccess() = runBlocking {
        val requested = mutableListOf<String>()
        val granted = mutableListOf<String>()
        val gateway = object : BillingGateway {
            override val provider = "cafebazaar"
            override suspend fun owned(sku: String): Receipt? = null
            override suspend fun price(sku: String): String? = null
            override suspend fun purchase(sku: String): Receipt { requested += sku; return Receipt(sku, "receipt-$sku") }
            override fun close() = Unit
        }
        val receipts = object : ReceiptStore {
            override fun saveReceipt(courseId: String, receipt: Receipt) = Unit
            override fun receipt(courseId: String): Receipt? = null
            override fun saveAccess(access: CourseAccess) { granted += access.courseId }
        }
        val backend = object : PurchaseBackend {
            override suspend fun verify(course: Course, provider: String, receipt: Receipt, restore: Boolean): CourseAccess {
                assertEquals(course.sku, receipt.sku)
                return CourseAccess(course.id, "secret", 100)
            }
        }
        val useCase = BuyCourse(backend, receipts)
        useCase.execute(Course(id, "Basic", "", "scratch_basic"), gateway, false)
        useCase.execute(Course(asset, "Advanced", "", "scratch_advanced"), gateway, false)
        assertEquals(listOf("scratch_basic", "scratch_advanced"), requested)
        assertEquals(listOf(id, asset), granted)
    }
}
