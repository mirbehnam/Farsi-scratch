package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class CatalogLayoutPolicyTest {
    private val doc = "<main>دوره‌ها</main>"
    private fun hash(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    @Test fun acceptsOnlySupportedVerifiedHtml() {
        assertNotNull(CatalogLayoutPolicy.verified(1, "html", "12", doc, hash(doc)))
        assertNull(CatalogLayoutPolicy.verified(2, "html", "12", doc, hash(doc)))
        assertNull(CatalogLayoutPolicy.verified(1, "native", "12", doc, hash(doc)))
        assertNull(CatalogLayoutPolicy.verified(1, "html", "bad", doc, hash(doc)))
        assertNull(CatalogLayoutPolicy.verified(1, "html", "12", doc, "a".repeat(64)))
    }
    @Test fun rejectsEmptyOversizedAndDamagedDocuments() {
        assertNull(CatalogLayoutPolicy.verified(1, "html", "1", "", hash("")))
        val large = "آ".repeat(CatalogLayoutPolicy.MAX_BYTES)
        assertNull(CatalogLayoutPolicy.verified(1, "html", "1", large, hash(large)))
        assertNull(CatalogLayoutPolicy.verified(1, "html", "1", doc + "x", hash(doc)))
    }
    @Test fun bridgeOnlySelectsKnownCoursesFromMainTrustedFrame() {
        val course = Course("11111111-1111-4111-8111-111111111111", "دوره", "", "scratch_basic")
        fun action(type: String = "view-course", id: String = course.id, main: Boolean = true, origin: String = CatalogLayoutPolicy.ORIGIN, busy: Boolean = false) =
            CatalogLayoutPolicy.courseAction(type, id, main, origin, busy, listOf(course))
        assertEquals(course, action())
        assertNull(action(type = "purchase")); assertNull(action(id = "unknown"))
        assertNull(action(main = false)); assertNull(action(origin = "https://evil.test")); assertNull(action(busy = true))
    }
}
