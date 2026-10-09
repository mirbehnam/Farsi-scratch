package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.domain.Lesson
import ir.behnamapps.fascratch.inappbilling.presentation.LessonPlayerPolicy
import org.junit.Assert.*
import org.junit.Test

class LessonPlayerPolicyTest {
    @Test fun doubleTapUsesPhysicalScreenSides() {
        assertEquals(-5000, LessonPlayerPolicy.doubleTapDelta(100f, 1000))
        assertEquals(5000, LessonPlayerPolicy.doubleTapDelta(900f, 1000))
        assertEquals(5000, LessonPlayerPolicy.doubleTapDelta(500f, 1000))
    }
    @Test fun seekNeverLeavesTheVideoOrOverflows() {
        assertEquals(0, LessonPlayerPolicy.seek(1000, -10000, 60000))
        assertEquals(60000, LessonPlayerPolicy.seek(58000, 10000, 60000))
        assertEquals(15000, LessonPlayerPolicy.seek(10000, 5000, 60000))
        assertEquals(Int.MAX_VALUE, LessonPlayerPolicy.seek(Int.MAX_VALUE - 100, 10000, Int.MAX_VALUE))
    }
    @Test fun exactlyFiveSpeedsAndOnlyImmediateNextLesson() {
        assertEquals(listOf(1f, 1.25f, 1.5f, 1.75f, 2f), LessonPlayerPolicy.speeds)
        val lessons = listOf("first", "next", "last").map { Lesson(it, "course", "section", it, 1, 100, "hash") }
        assertEquals("next", LessonPlayerPolicy.next(lessons, "first")?.id)
        assertNull(LessonPlayerPolicy.next(lessons, "last"))
        assertNull(LessonPlayerPolicy.next(lessons, "missing"))
    }
}
