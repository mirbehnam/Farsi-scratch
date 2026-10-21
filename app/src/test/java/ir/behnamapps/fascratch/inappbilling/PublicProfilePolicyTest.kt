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
