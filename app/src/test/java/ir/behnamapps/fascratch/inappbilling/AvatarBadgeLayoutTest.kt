package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.AvatarBadgeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarBadgeLayoutTest {
    @Test fun textUsesWebPreviewRatiosAtEveryBadgeSize() {
        for (size in listOf(42f, 61f, 76f, 100f, 110f, 220f, 600f)) {
            val center = size * (AvatarBadgeLayout.TEXT_TOP + AvatarBadgeLayout.TEXT_HEIGHT / 2)
            assertEquals(size * .75f, center, .001f)
            assertEquals(size * .8f, size * AvatarBadgeLayout.TEXT_WIDTH, .001f)
            assertEquals(size * .12f, size * AvatarBadgeLayout.fontRatio(12), .001f)
            assertTrue(size * AvatarBadgeLayout.TEXT_HEIGHT > size * AvatarBadgeLayout.fontRatio(12) * AvatarBadgeLayout.LINE_HEIGHT)
        }
    }

    @Test fun longLevelsUseTheSameSmallerFontAsWeb() {
        assertEquals(.12f, AvatarBadgeLayout.fontRatio(0), 0f)
        assertEquals(.12f, AvatarBadgeLayout.fontRatio(999), 0f)
        assertEquals(.10f, AvatarBadgeLayout.fontRatio(1000), 0f)
        assertEquals(.10f, AvatarBadgeLayout.fontRatio(100000), 0f)
    }

    @Test fun rectangularParentsDoNotMoveTextAwayFromFittedArtwork() {
        assertEquals(76f, AvatarBadgeLayout.side(76f, 120f), 0f)
        assertEquals(76f, AvatarBadgeLayout.side(120f, 76f), 0f)
    }
}
