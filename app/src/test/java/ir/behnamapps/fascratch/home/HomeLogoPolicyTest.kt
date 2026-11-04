package ir.behnamapps.fascratch.home
import org.junit.Assert.*
import org.junit.Test
class HomeLogoPolicyTest {
    @Test fun onlyConfirmedAbsenceInvalidatesSelection() {
        assertFalse(HomeLogoPolicy.removed(null, emptyList()))
        assertFalse(HomeLogoPolicy.removed(2, listOf(1,2,3)))
        assertTrue(HomeLogoPolicy.removed(2, listOf(1,3)))
        assertTrue(HomeLogoPolicy.removed(2, emptyList()))
    }
}
