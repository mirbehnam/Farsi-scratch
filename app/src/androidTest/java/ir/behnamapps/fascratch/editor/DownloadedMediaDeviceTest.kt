package ir.behnamapps.fascratch.editor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.io.File
class DownloadedMediaDeviceTest {
    @Test fun catalogsAndFilesAreIsolated() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for ((kind, filename) in listOf("backdrops" to "fixture.svg", "sounds" to "fixture.wav")) {
            val handler = DownloadedMediaPathHandler(context, kind)
            val file = File(context.filesDir, "downloaded_$kind/$filename")
            file.writeText(if (kind == "backdrops") "<svg/>" else "RIFF")
            val names = File(file.parentFile, "names.json")
            val previous = if (names.exists()) names.readBytes() else null
            names.writeText("""{"$filename":{"en":"Test","fa":"آزمایش"}}""")
            try {
                val catalog = org.json.JSONArray(handler.handle("catalog.json").data.bufferedReader().readText())
                val entry = (0 until catalog.length()).map { catalog.getJSONObject(it) }.first { it.getString("fileName") == filename }
                assertEquals("آزمایش", entry.getJSONObject("displayNames").getString("fa"))
                assertTrue(handler.handle("catalog.json").data.bufferedReader().readText().contains(filename.substringBefore('.')))
                assertArrayEquals(file.readBytes(), handler.handle("file/$filename").data.readBytes())
                assertEquals(404, handler.handle("file/../outside.wav").statusCode)
            } finally { file.delete(); if (previous == null) names.delete() else names.writeBytes(previous) }
        }
    }
}
