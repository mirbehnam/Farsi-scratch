import groovy.json.JsonSlurper
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class EditorAssetPackagerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun mergesOnlyIdenticalMediaAcrossEnginesAndPreservesEveryLogicalFile() {
        val input = temporary.newFolder("input").toPath()
        val output = temporary.root.toPath().resolve("output/assets")
        val originals = linkedMapOf(
            "build/index.html" to "scratch", "turbobuild/index.html" to "turbo",
            "build/static/assets/a.svg" to "same svg", "turbobuild/static/assets/b.svg" to "same svg",
            "build/static/assets/a.wav" to "same audio", "turbobuild/static/assets/a.wav" to "same audio",
            "build/different.png" to "one", "turbobuild/different.png" to "two",
            "build/main.js" to "same JS", "turbobuild/main.js" to "same JS",
            "build/extra.png" to "same within scratch", "build/extra2.png" to "same within scratch",
            "other/data.json" to "unrelated"
        )
        originals.forEach { (name, text) ->
            Files.createDirectories(input.resolve(name).parent)
            Files.writeString(input.resolve(name), text)
        }
        val report = EditorAssetPackager.prepare(input, output, "both")
        assertEquals(2, report["sharedMediaFiles"])
        val manifest = JsonSlurper().parse(output.resolve("editor-assets.json").toFile()) as Map<*, *>
        val aliases = manifest["shared"] as Map<*, *>
        originals.forEach { (name, text) ->
            assertEquals(text, Files.readString(output.resolve(aliases[name] as? String ?: name)))
            assertEquals(text, Files.readString(input.resolve(name)))
        }
        assertFalse(Files.exists(output.resolve("build/static/assets/a.svg")))
        assertTrue(Files.exists(output.resolve("build/main.js")))
        assertTrue(Files.exists(output.resolve("turbobuild/main.js")))
        assertTrue(Files.exists(output.resolve("build/extra2.png")))
        EditorAssetPackager.prepare(input, output, "scratch")
        assertFalse(Files.exists(output.resolve("turbobuild")))
        assertFalse(Files.exists(output.resolve("shared")))
        assertTrue(Files.exists(output.resolve("build/static/assets/a.svg")))
        EditorAssetPackager.prepare(input, output, "turbo")
        assertFalse(Files.exists(output.resolve("build")))
        assertTrue(Files.exists(output.resolve("turbobuild/static/assets/b.svg")))
        Files.writeString(input.resolve("turbobuild/static/assets/b.svg"), "changed")
        EditorAssetPackager.prepare(input, output, "both")
        assertTrue(Files.exists(output.resolve("build/static/assets/a.svg")))
        assertTrue(Files.exists(output.resolve("turbobuild/static/assets/b.svg")))
    }

    @Test fun rejectsInvalidSelectionAndMissingSelectedEditor() {
        val input = temporary.newFolder("input").toPath()
        val output = temporary.root.toPath().resolve("output/assets")
        assertThrows(IllegalStateException::class.java) { EditorAssetPackager.prepare(input, output, "wrong") }
        assertThrows(IllegalArgumentException::class.java) { EditorAssetPackager.prepare(input, output, "turbo") }
        Files.createDirectories(input.resolve("build"))
        Files.writeString(input.resolve("build/index.html"), "ok")
        EditorAssetPackager.prepare(input, output, "scratch")
        assertThrows(IllegalArgumentException::class.java) { EditorAssetPackager.prepare(input, input, "scratch") }
    }
}
