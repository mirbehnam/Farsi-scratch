package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.SupplementaryPolicy
import ir.behnamapps.fascratch.inappbilling.learning.SupplementaryDetails
import org.junit.Assert.*
import org.junit.Test

class SupplementaryPolicyTest {
    @Test fun delayedResponsesCannotUndoClearingButAccountsNeverSharePersonalFields() {
        val cleared = SupplementaryDetails(true, 2)
        val stale = SupplementaryDetails(true, 1, 13, "tehran", "female")
        assertEquals(cleared, SupplementaryPolicy.retainNewer("one", cleared, "one", stale))
        assertEquals(stale, SupplementaryPolicy.retainNewer("one", cleared, "two", stale))
        assertEquals(cleared, SupplementaryPolicy.retainNewer("one", stale, "one", cleared))
    }
    @Test fun optionalAgeSupportsPersianArabicAndLatinDigits() {
        assertTrue(SupplementaryPolicy.validAge("")); assertTrue(SupplementaryPolicy.validAge(" "))
        assertEquals(13, SupplementaryPolicy.age("۱۳")); assertEquals(14, SupplementaryPolicy.age("١٤"))
        assertEquals(12, SupplementaryPolicy.age("12")); assertNull(SupplementaryPolicy.age(""))
        for (invalid in listOf("0", "121", "-2", "12.5", "abc")) assertFalse(SupplementaryPolicy.validAge(invalid))
    }
    @Test fun provincesHaveStableUniqueApiIdsAndGenderIsNotRequired() {
        assertEquals(31, SupplementaryPolicy.provinces.size)
        assertEquals("البرز", SupplementaryPolicy.provinces["alborz"])
        assertEquals("تهران", SupplementaryPolicy.provinces["tehran"])
        assertTrue(SupplementaryPolicy.provinces.keys.all { it.matches(Regex("[a-z_]+")) })
        assertEquals(setOf("female", "male"), SupplementaryPolicy.genders.keys)
        assertTrue(SupplementaryPolicy.matchesProvince("ایلام", "ايلام"))
        assertTrue(SupplementaryPolicy.matchesProvince("آذربایجان شرقی", "اذربایجان"))
    }
}
