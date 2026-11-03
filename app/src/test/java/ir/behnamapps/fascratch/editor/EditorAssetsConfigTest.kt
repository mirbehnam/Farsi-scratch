package ir.behnamapps.fascratch.editor

import org.junit.Assert.*
import org.junit.Test

class EditorAssetsConfigTest {
    @Test fun selectsOnlyPackagedEnginesAndDefaultsToScratchWhenBothExist() {
        val scratch = EditorAssetsConfig(mapOf(EditorEngine.SCRATCH to "build/index.html"), emptyMap())
        val turbo = EditorAssetsConfig(mapOf(EditorEngine.TURBO to "turbobuild/index.html"), emptyMap())
        assertEquals(EditorEngine.SCRATCH, scratch.defaultEngine)
        assertEquals(EditorEngine.TURBO, turbo.defaultEngine)
        assertEquals("https://appassets.androidplatform.net/assets/turbobuild/index.html", turbo.entryUrl())
        assertThrows(IllegalArgumentException::class.java) { turbo.entryUrl(EditorEngine.SCRATCH) }
        val both = EditorAssetsConfig(
            mapOf(EditorEngine.TURBO to "turbobuild/index.html", EditorEngine.SCRATCH to "build/index.html"), emptyMap()
        )
        assertEquals(listOf(EditorEngine.SCRATCH, EditorEngine.TURBO), both.availableEngines)
        assertEquals(EditorEngine.SCRATCH, both.defaultEngine)
    }

    @Test fun rejectsPathsThatEscapeAssetsOrReferToAnExcludedEngine() {
        val entries = mapOf(EditorEngine.SCRATCH to "build/index.html")
        assertThrows(IllegalArgumentException::class.java) {
            EditorAssetsConfig(entries, mapOf("build/x.svg" to "shared/media/../../outside.svg"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EditorAssetsConfig(entries, mapOf("turbobuild/x.svg" to "shared/media/x.svg"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EditorAssetsConfig(mapOf(EditorEngine.TURBO to "build/index.html"), emptyMap())
        }
    }
}
