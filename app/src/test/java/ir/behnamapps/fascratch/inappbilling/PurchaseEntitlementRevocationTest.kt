package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.LearnerCourse
import org.junit.Assert.*
import org.junit.Test

class PurchaseEntitlementRevocationTest {
    @Test fun explicitServerRevocationCannotFallBackToAnOldPaidReceipt() {
        val course = LearnerCourse("course", "Course", 0, purchased = true, enabled = false)
        assertTrue(course.paidAccessRevoked())
        assertFalse(course.canAccess())
    }
    @Test fun missingPurchaseMetadataIsNotATransientRevocation() {
        assertFalse(LearnerCourse("course", "Course", 0, purchased = false, enabled = false).paidAccessRevoked())
        assertFalse(LearnerCourse("course", "Course", 0, purchased = true, enabled = true).paidAccessRevoked())
    }
    @Test fun activeGiftAccessIsPreservedEvenWhenThePaidPurchaseIsNotActive() {
        val course = LearnerCourse("course", "Course", 0, purchased = true, enabled = true,
            accessSource = "gift", accessUntilMs = System.currentTimeMillis() + 3600000)
        assertTrue(course.canAccess()); assertFalse(course.paidAccessRevoked())
    }
}
