package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.PublicProfilePolicy
import org.junit.Assert.*
import org.junit.Test

class PublicProfilePolicyTest {
    @Test fun unicodeCounterDoesNotSplitEmojiAtTheLimit() {
        val value = "گ".repeat(199) + "😺"
        assertEquals(200, PublicProfilePolicy.length(value))
        assertEquals(value, PublicProfilePolicy.text(value + "متن اضافه"))
        assertEquals(400, PublicProfilePolicy.text("😺".repeat(201)).length)
        assertEquals("", PublicProfilePolicy.text(null))
        assertEquals("", PublicProfilePolicy.text("null"))
    }

    @Test fun allFourNetworksUseOnlyTheirOwnPublicHttpsProfileUrls() {
        assertEquals("https://t.me/scratch_user", PublicProfilePolicy.safeUrl("telegram", "https://t.me/scratch_user"))
        assertEquals("https://rubika.ir/scratch_user", PublicProfilePolicy.safeUrl("rubika", "https://rubika.ir/scratch_user"))
        assertEquals("https://ble.ir/scratch_user", PublicProfilePolicy.safeUrl("bale", "https://ble.ir/scratch_user"))
        assertEquals("https://www.instagram.com/my.cat", PublicProfilePolicy.safeUrl("instagram", "https://instagram.com/my.cat"))
    }

    @Test fun editPriceIsPerChangedSectionAndNeverChargesForRemovalOrEquivalentUrls() {
        val oldLinks = mapOf("telegram" to "https://t.me/scratch_user", "bale" to "https://ble.ir/scratch_user")
        assertEquals(0, PublicProfilePolicy.editCost("سلام", oldLinks, "سلام", oldLinks, 2000))
        assertEquals(0, PublicProfilePolicy.editCost("سلام", oldLinks, "", emptyMap(), 2000))
        assertEquals(0, PublicProfilePolicy.editCost("سلام", oldLinks, "سلام", mapOf("telegram" to "@scratch_user"), 2000))
        assertEquals(2000, PublicProfilePolicy.editCost("سلام", oldLinks, "متن تازه", oldLinks, 2000))
        assertEquals(2000, PublicProfilePolicy.editCost("سلام", oldLinks, "سلام", mapOf("telegram" to "new_user", "bale" to "other_user"), 2000))
        assertEquals(4000, PublicProfilePolicy.editCost("سلام", oldLinks, "متن تازه", mapOf("telegram" to "new_user"), 2000))
        assertEquals(0, PublicProfilePolicy.editCost("سلام", oldLinks, " سلام ", mapOf("telegram" to "https://t.me/scratch_user/"), 2000))
    }

    @Test fun inputNormalizationAcceptsHandlesAndOnlyValidPlatformUrls() {
        assertEquals("https://t.me/scratch_user", PublicProfilePolicy.normalizeInput("telegram", " @scratch_user "))
        assertEquals("https://www.instagram.com/my.cat", PublicProfilePolicy.normalizeInput("instagram", "my.cat"))
        assertEquals("https://ble.ir/scratch_user", PublicProfilePolicy.normalizeInput("bale", "ble.ir/scratch_user"))
        assertEquals("", PublicProfilePolicy.normalizeInput("telegram", " "))
        assertNull(PublicProfilePolicy.normalizeInput("telegram", "https://evil.test/user"))
        assertNull(PublicProfilePolicy.normalizeInput("telegram", "javascript:alert(1)"))
    }

    @Test fun malformedOrDangerousLinksNeverReachAnIntent() {
        listOf("javascript:alert(1)", "http://t.me/scratch_user", "https://t.me.evil.test/scratch_user", "https://evil.test@t.me/scratch_user",
            "https://t.me:443/scratch_user", "https://t.me/scratch_user?start=1", "https://t.me/scratch_user#fragment", "https://t.me/+123456",
            "https://t.me/scratch_user/12", "https://t.me/%75ser", "https://t.me/joinchat", "intent://scratch_user", "file:///tmp/a").forEach {
            assertNull(it, PublicProfilePolicy.safeUrl("telegram", it))
        }
        assertNull(PublicProfilePolicy.safeUrl("instagram", "https://t.me/scratch_user"))
        assertNull(PublicProfilePolicy.safeUrl("unknown", "https://t.me/scratch_user"))
    }
}
