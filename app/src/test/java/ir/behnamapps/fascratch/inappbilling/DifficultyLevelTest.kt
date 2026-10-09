package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.domain.DifficultyLevel
import org.junit.Assert.*
import org.junit.Test

class DifficultyLevelTest {
    @Test fun allTenLevelsHaveDistinctDefaultsAndEmojis() {
        assertEquals(10, (1..10).map { DifficultyLevel.label(it) }.toSet().size)
        (1..10).forEach { assertTrue(DifficultyLevel.emoji(it).isNotBlank()) }
        assertEquals("حرفه‌ای", DifficultyLevel.label(5))
    }
    @Test fun serverTitlesAreUsedAndOldCachesRemainReadable() {
        assertEquals("حل مسئله", DifficultyLevel.label(8, " حل مسئله "))
        assertEquals("فوق‌تخصصی", DifficultyLevel.label(10, "null"))
        assertEquals("مقدماتی", DifficultyLevel.label(1, ""))
        assertEquals("تخصصی", DifficultyLevel.label(9, "x".repeat(41)))
        assertEquals("متوسط", DifficultyLevel.label(3, "bad\nlabel"))
    }
    @Test fun invalidLevelsAreNotMisrepresented() {
        assertEquals("", DifficultyLevel.label(0, "عنوان"))
        assertEquals("", DifficultyLevel.label(11))
        assertEquals("", DifficultyLevel.emoji(-1))
    }
}
