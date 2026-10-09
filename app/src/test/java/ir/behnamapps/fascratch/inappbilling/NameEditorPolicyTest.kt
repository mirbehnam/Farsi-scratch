package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.NameEditorPolicy
import org.junit.Assert.*
import org.junit.Test

class NameEditorPolicyTest {
    @Test fun initialNameAndOptionalRealNameHaveSafeLimits() {
        assertTrue(NameEditorPolicy.canSave("کدنویس خلاق", "هنرجو", false, 3, false))
        assertFalse(NameEditorPolicy.validName(" ا "))
        assertFalse(NameEditorPolicy.validName("<نام>"))
        assertFalse(NameEditorPolicy.validName("نام\nجدید"))
        assertTrue(NameEditorPolicy.validFullName(""))
        assertFalse(NameEditorPolicy.validFullName("a".repeat(101)))
    }
    @Test fun blockedNameAndExhaustedQuotaCanOnlyKeepOriginalName() {
        assertFalse(NameEditorPolicy.canSave("نام تازه", "نام قبلی", true, 3, true))
        assertFalse(NameEditorPolicy.canSave("نام تازه", "نام قبلی", true, 0, false))
        assertTrue(NameEditorPolicy.canSave(" نام قبلی ", "نام قبلی", true, 0, true))
    }
}
