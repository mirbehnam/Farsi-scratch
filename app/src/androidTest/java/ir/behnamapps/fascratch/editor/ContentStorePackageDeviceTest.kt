package ir.behnamapps.fascratch.inappbilling.learning

import androidx.test.platform.app.InstrumentationRegistry
import ir.behnamapps.fascratch.editor.DownloadedMediaPathHandler
import ir.behnamapps.fascratch.editor.DownloadedSpritesPathHandler
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ContentStorePackageDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { buffer -> ZipOutputStream(buffer).use { z -> entries.forEach { (name, bytes) -> z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() } } }.toByteArray()
    private fun sha(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun fixture(): Pair<File, JSONObject> {
        val id = UUID.randomUUID().toString()
        val svg = "<svg xmlns='http://www.w3.org/2000/svg' width='30' height='30'><rect width='30' height='30' fill='orange'/></svg>".toByteArray()
        val sprite = zip(mapOf("sprite.json" to JSONObject().put("name", "Original test sprite").put("isStage", false).put("costumes", JSONArray().put(JSONObject().put("name", "test").put("md5ext", "fixture.svg").put("assetId", "fixture").put("dataFormat", "svg"))).put("sounds", JSONArray()).put("blocks", JSONObject()).toString().toByteArray(), "fixture.svg" to svg))
        val wav = ByteArray(2044).apply { "RIFF".toByteArray().copyInto(this); "WAVE".toByteArray().copyInto(this, 8) }
        val files = JSONArray(); val entries = mutableMapOf<String, ByteArray>()
        for ((kind, ext, bytes) in listOf(Triple("sprites", "sprite3", sprite), Triple("backdrops", "svg", svg), Triple("sounds", "wav", wav))) {
            val uuid = UUID.randomUUID().toString(); val path = "files/$uuid.$ext"
            files.put(JSONObject().put("uuid", uuid).put("kind", kind).put("name", "fixture.$ext").put("path", path).put("bytes", bytes.size).put("sha256", sha(bytes)).put("names", JSONObject().put("en", "Example").put("fa", "نمونه").put("ar", "مثال")))
            entries[path] = bytes
        }
        val manifest = JSONObject().put("format", 1).put("item_uuid", id).put("files", files)
        entries["manifest.json"] = manifest.toString().toByteArray()
        val file = File(context.cacheDir, "content-store-fixture-$id.zip").apply { writeBytes(zip(entries)) }
        return file to JSONObject().put("item_uuid", id).put("download_uuid", UUID.randomUUID().toString()).put("bytes", file.length()).put("sha256", ContentPackageInstaller.hash(file)).put("manifest", manifest)
    }
    @Test fun installsAllThreeLibrariesMergesNamesAndRemovesOnlyItsOwnFiles() {
        val installer = ContentPackageInstaller.get(context.filesDir); val (zip, ticket) = fixture(); val id = ticket.getString("item_uuid")
        val backdropDir = File(context.filesDir, "downloaded_backdrops").apply { mkdirs() }
        val manualName = "manual-${UUID.randomUUID()}.svg"; val manual = File(backdropDir, manualName).apply { writeText("<svg/>") }
        val names = File(backdropDir, "names.json"); val old = if (names.exists()) names.readBytes() else null
        val prior = old?.let { JSONObject(it.toString(Charsets.UTF_8)) } ?: JSONObject()
        prior.put(manualName, JSONObject().put("en", "Independent").put("fa", "مستقل")); names.writeText(prior.toString())
        try {
            installer.install(zip, ticket, "بستهٔ آزمایش", "test-owner") { true }
            assertTrue(installer.installed(id))
            val records = installer.entries().getJSONObject(id).getJSONArray("files")
            for (i in 0 until records.length()) {
                val f = records.getJSONObject(i); val kind = f.getString("kind"); val local = f.getString("local")
                val manifest = JSONObject(File(context.filesDir, "downloaded_$kind/names.json").readText())
                assertEquals("نمونه", manifest.getJSONObject(local).getString("fa"))
                val catalog = if (kind == "sprites") DownloadedSpritesPathHandler(context).handle("catalog.json") else DownloadedMediaPathHandler(context, kind).handle("catalog.json")
                val rows = JSONArray(catalog.data.bufferedReader().readText()); assertTrue((0 until rows.length()).any { rows.getJSONObject(it).getString("fileName") == local })
            }
            val before = records.toString(); installer.install(zip, ticket, "دوباره", "test-owner") { true }; assertEquals(before, installer.entries().getJSONObject(id).getJSONArray("files").toString())
            // A process killed after the catalog commit must preserve its completed install.
            File(context.filesDir, "content_store/install-journal.json").writeText(installer.entries().getJSONObject(id).toString()); installer.recover(); assertTrue(installer.installed(id))
            installer.remove(id); assertFalse(installer.installed(id)); assertTrue(manual.exists())
            assertEquals("مستقل", JSONObject(names.readText()).getJSONObject(manualName).getString("fa"))
        } finally { installer.remove(id); zip.delete(); manual.delete(); if (old == null) names.delete() else names.writeBytes(old) }
    }
    @Test fun rejectsTamperingAndAccountChangesWithoutInstalling() {
        val installer = ContentPackageInstaller.get(context.filesDir); val (file, ticket) = fixture(); val id = ticket.getString("item_uuid")
        try {
            ticket.put("sha256", "0".repeat(64))
            try { installer.install(file, ticket, "bad", "test") { true }; fail("tampered archive accepted") } catch (_: IllegalArgumentException) { }
            assertFalse(installer.installed(id)); ticket.put("sha256", ContentPackageInstaller.hash(file))
            try { installer.install(file, ticket, "bad", "test") { false }; fail("switched account accepted") } catch (_: IllegalStateException) { }
            assertFalse(installer.installed(id))
        } finally { installer.remove(id); file.delete() }
    }
    @Test fun interruptedInstallRollsBackOnlyJournaledFiles() {
        val installer = ContentPackageInstaller.get(context.filesDir); val id = UUID.randomUUID().toString(); val uuid = UUID.randomUUID().toString()
        val dir = File(context.filesDir, "downloaded_sounds").apply { mkdirs() }; val local = "store-$id-$uuid.wav"
        val file = File(dir, local).apply { writeText("partial") }; val journal = File(context.filesDir, "content_store/install-journal.json")
        try {
            journal.parentFile!!.mkdirs(); journal.writeText(JSONObject().put("uuid", id).put("files", JSONArray().put(JSONObject().put("kind", "sounds").put("local", local))).toString())
            installer.recover(); assertFalse(file.exists()); assertFalse(journal.exists())
        } finally { file.delete(); journal.delete() }
    }
}
