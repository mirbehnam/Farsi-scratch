package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.presentation.InstructorContactKind
import ir.behnamapps.fascratch.inappbilling.presentation.InstructorDialogPolicy
import org.junit.Assert.*
import org.junit.Test

class InstructorDialogPolicyTest {
    @Test fun widthAlwaysLeavesSpaceAndHasACompactTabletLimit() {
        for (available in listOf(0f, 240f, 448f, 608f, 808f, 1200f)) {
            val width = InstructorDialogPolicy.width(available)
            assertTrue(width >= 0f && width <= 660f)
            if (available > 0f) assertTrue(width < available)
        }
        assertEquals(660f, InstructorDialogPolicy.width(1200f), .01f)
    }
    @Test fun brandsAreDetectedFromTheRealHostNotFromMisleadingUrlText() {
        assertEquals(InstructorContactKind.INSTAGRAM, InstructorDialogPolicy.contactKind("https://www.instagram.com/teacher"))
        assertEquals(InstructorContactKind.TELEGRAM, InstructorDialogPolicy.contactKind("https://t.me/teacher"))
        assertEquals(InstructorContactKind.EMAIL, InstructorDialogPolicy.contactKind("mailto:teacher@example.com"))
        assertEquals(InstructorContactKind.PHONE, InstructorDialogPolicy.contactKind("tel:+989123456789"))
        assertEquals(InstructorContactKind.WEBSITE, InstructorDialogPolicy.contactKind("https://example.com/instagram.com"))
        assertEquals(InstructorContactKind.WEBSITE, InstructorDialogPolicy.contactKind("not a url"))
    }
}
