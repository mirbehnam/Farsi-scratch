package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.domain.InstructorPolicy
import org.junit.Assert.*
import org.junit.Test

class InstructorPolicyTest {
    @Test fun contactIntentsAcceptOnlySafeWebEmailAndDialerTargets() {
        for (url in listOf("https://example.com/about", "https://t.me/example", "mailto:teacher@example.com", "tel:+989123456789")) assertTrue(url, InstructorPolicy.contactAllowed(url))
        for (url in listOf("javascript:alert(1)", "intent://anything", "file:///secret", "http://example.com", "mailto:a@b.com?body=bad", "tel:*123#", "https://user:pass@example.com", "https://example.com/%0aheader", "https://example.com/\n")) assertFalse(url, InstructorPolicy.contactAllowed(url))
    }

    @Test fun profileImageMustBeExactPublicContentHashOnOurApiOrigin() {
        val base = "https://api.behnamapp.ir/scratch/v1"
        val url = "$base/instructor-images/" + "a".repeat(64)
        assertTrue(InstructorPolicy.photoAllowed(base, url))
        assertFalse(InstructorPolicy.photoAllowed(base, "$base/instructor-images/null"))
        assertFalse(InstructorPolicy.photoAllowed(base, "$base/instructor-images/bad"))
        for (bad in listOf(url + "?token=x", url + "#fragment", url.replace("api.behnamapp.ir", "evil.example"), url.replace("instructor-images", "private"), url.replace("https:", "http:"))) assertFalse(bad, InstructorPolicy.photoAllowed(base, bad))
    }
}
