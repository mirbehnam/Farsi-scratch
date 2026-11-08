package ir.behnamapps.fascratch.inappbilling.learning

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Owns only store-prefixed files listed in its private journal, never a user's independent files. */
internal class ContentPackageInstaller(private val root: File) {
    private val folder = File(root, "content_store").apply { mkdirs() }
    private val catalog = AtomicFile(File(folder, "installed.json"))
    private val journal = AtomicFile(File(folder, "install-journal.json"))
    private fun read(file: AtomicFile): JSONObject = runCatching { file.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) } }.getOrDefault(JSONObject())
    private fun write(file: AtomicFile, json: JSONObject) {
        val output = file.startWrite()
        try { output.write(json.toString().toByteArray()); file.finishWrite(output) }
        catch (e: Exception) { file.failWrite(output); throw e }
    }
    private fun target(kind: String, name: String): File {
        require(kind in setOf("sprites", "backdrops", "sounds", "projects"))
        require(name.startsWith("store-") && !name.contains('/') && !name.contains('\\'))
        val dir = File(root, "downloaded_$kind").apply { mkdirs() }
        return File(dir, name).also { require(it.canonicalFile.parentFile == dir.canonicalFile && it.canonicalFile == File(dir.canonicalFile, name)) }
    }
    private fun namesFile(kind: String): AtomicFile = AtomicFile(File(target(kind, "store-name-probe").parentFile, "names.json"))
    private fun names(kind: String): JSONObject {
        val f = namesFile(kind)
        if (!f.baseFile.exists() && !File(f.baseFile.path + ".bak").exists()) return JSONObject()
        require(f.baseFile.length() <= 1048576) { "فایل نام‌های کتابخانه بیش از حد بزرگ است." }
        return f.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
    }
    private fun setName(kind: String, key: String, value: JSONObject?) {
        val all = names(kind); if (value == null) all.remove(key) else all.put(key, value)
        require(all.toString().toByteArray().size <= 1048576) { "ظرفیت نام‌های کتابخانه پر شده است." }
        val destination = namesFile(kind).baseFile
        val temporary = File(destination.parentFile, ".store-names-${java.util.UUID.randomUUID()}.part")
        try {
            FileOutputStream(temporary).use { out -> out.write(all.toString().toByteArray()); out.fd.sync() }
            // Readers in both editors use a plain File: POSIX rename keeps their view atomic even on API 24.
            android.system.Os.rename(temporary.absolutePath, destination.absolutePath)
        } finally { temporary.delete() }
    }
    private fun restoreName(f: JSONObject) {
        val kind = f.getString("kind"); val local = f.getString("local")
        if (names(kind).optJSONObject(local)?.toString() == f.optJSONObject("names")?.toString()) setName(kind, local, f.optJSONObject("names_before"))
    }
    @Synchronized fun recover() {
        val j = read(journal)
        if (!j.has("uuid")) return
        val saved = read(catalog)
        if (!saved.has(j.getString("uuid"))) {
            val files = j.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) files.getJSONObject(i).let { f -> runCatching { target(f.getString("kind"), f.getString("local")).delete(); restoreName(f) } }
        }
        journal.delete()
    }
    @Synchronized fun installed(uuid: String): Boolean {
        val entry = read(catalog).optJSONObject(uuid) ?: return false
        val files = entry.optJSONArray("files") ?: return false
        return files.length() > 0 && (0 until files.length()).all { i ->
            val f = files.getJSONObject(i)
            runCatching { target(f.getString("kind"), f.getString("local")).let { it.isFile && it.length() == f.getLong("bytes") } }.getOrDefault(false)
        }
    }
    @Synchronized fun entries(): JSONObject = read(catalog)
    @Synchronized fun acknowledge(uuid: String) {
        val all = read(catalog); val item = all.optJSONObject(uuid) ?: return
        item.remove("ack_uuid"); item.remove("ack_hash"); item.remove("ack_owner"); write(catalog, all)
    }
    @Synchronized fun remove(uuid: String) {
        val all = read(catalog); val item = all.optJSONObject(uuid) ?: return
        val files = item.getJSONArray("files")
        for (i in 0 until files.length()) files.getJSONObject(i).let { target(it.getString("kind"), it.getString("local")).delete(); restoreName(it) }
        all.remove(uuid); write(catalog, all)
    }
    @Synchronized fun install(archive: File, ticket: JSONObject, title: String, owner: String,
        importProject: ((File, String, String) -> Unit)? = null, sameAccount: () -> Boolean) {
        recover()
        require(archive.length() == ticket.getLong("bytes") && hash(archive) == ticket.getString("sha256")) { "فایل کامل دریافت نشده؛ دوباره تلاش کن." }
        val uuid = ticket.getString("item_uuid"); java.util.UUID.fromString(uuid)
        if (installed(uuid)) return
        if (read(catalog).has(uuid)) remove(uuid)
        val expected = ticket.getJSONObject("manifest"); require(expected.getString("item_uuid") == uuid && expected.getInt("format") == 1)
        val files = expected.getJSONArray("files"); require(files.length() in 1..100)
        val stage = File(folder, "stage-$uuid").apply { mkdirs() }
        val records = JSONArray(); var total = 0L
        try {
            ZipFile(archive).use { zip ->
                require(zip.size() == files.length() + 1)
                val meta = zip.getEntry("manifest.json") ?: error("فرادادهٔ بسته وجود ندارد.")
                require(meta.size in 1..1048576)
                val manifest = zip.getInputStream(meta).use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
                require(manifest.getString("item_uuid") == uuid)
                val seen = mutableSetOf<String>()
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i); val fileId = f.getString("uuid"); java.util.UUID.fromString(fileId)
                    val kind = f.getString("kind"); val name = f.getString("name"); val ext = name.substringAfterLast('.').lowercase()
                    require(ext in when (kind) { "sprites" -> setOf("sprite3"); "backdrops" -> setOf("svg", "png", "jpg", "jpeg"); "sounds" -> setOf("wav", "mp3"); "projects" -> setOf("sb3"); else -> emptySet() })
                    require(kind != "projects" || importProject != null) { "این نسخه از نصب پروژه پشتیبانی نمی‌کند." }
                    val key = "files/$fileId.$ext"; require(f.getString("path") == key && seen.add(key))
                    val entry = zip.getEntry(key) ?: error("یکی از فایل‌های بسته ناقص است.")
                    val bytes = f.getLong("bytes"); total += bytes; require(bytes in 1..134217728 && total <= 536870912 && entry.size == bytes)
                    val local = "store-$uuid-$fileId.$ext"; val out = File(stage, local)
                    zip.getInputStream(entry).use { input -> FileOutputStream(out).use { output ->
                        val buffer = ByteArray(65536); var count = 0L
                        while (true) { val n = input.read(buffer); if (n < 0) break; count += n; require(count <= bytes); output.write(buffer, 0, n) }
                        output.fd.sync(); require(count == bytes)
                    } }
                    require(hash(out) == f.getString("sha256")) { "بررسی سلامت فایل ناموفق بود." }
                    records.put(JSONObject().put("kind", kind).put("local", local).put("bytes", bytes).put("sha256", f.getString("sha256"))
                        .put("names", JSONObject((f.optJSONObject("names") ?: JSONObject()).toString()).apply { if (optString("en").isBlank() || isNull("en")) put("en", name.substringBeforeLast('.', name)) }).put("names_before", names(kind).optJSONObject(local) ?: JSONObject.NULL))
                }
            }
            check(sameAccount()) { "حساب تغییر کرده؛ دریافت را از حساب فعلی دوباره شروع کن." }
            val item = JSONObject().put("uuid", uuid).put("title", title).put("files", records).put("ack_uuid", ticket.getString("download_uuid"))
                .put("ack_hash", ticket.getString("sha256")).put("ack_owner", owner)
            // Recovering a killed process rolls back the complete, named set before reopening a library.
            for (i in 0 until records.length()) {
                val f = records.getJSONObject(i); require(!target(f.getString("kind"), f.getString("local")).exists()) { "فایل هم‌نامی وجود دارد؛ آن را بازنویسی نکردیم." }
            }
            write(journal, item)
            for (i in 0 until records.length()) {
                val f = records.getJSONObject(i); val dest = target(f.getString("kind"), f.getString("local"))
                require(!dest.exists()) { "فایل هم‌نامی وجود دارد؛ آن را بازنویسی نکردیم." }
                check(File(stage, f.getString("local")).renameTo(dest)) { "ذخیرهٔ فایل در کتابخانه کامل نشد." }
            }
            for (i in 0 until records.length()) {
                val f = records.getJSONObject(i); setName(f.getString("kind"), f.getString("local"), f.getJSONObject("names"))
            }
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                if (f.getString("kind") == "projects") {
                    check(sameAccount()) { "حساب تغییر کرده؛ دریافت را دوباره انجام بده." }
                    val local = "store-$uuid-${f.getString("uuid")}.sb3"
                    importProject!!(target("projects", local), contentDisplayName(f.optJSONObject("names"), f.getString("name")), "$uuid:${f.getString("uuid")}")
                }
            }
            val all = read(catalog); all.put(uuid, item); write(catalog, all); journal.delete()
        } catch (e: Exception) { recover(); throw e }
        finally { stage.listFiles()?.forEach { it.delete() }; stage.delete() }
    }
    companion object {
        @Volatile private var instance: ContentPackageInstaller? = null
        fun get(root: File): ContentPackageInstaller = instance ?: synchronized(this) { instance ?: ContentPackageInstaller(root).also { instance = it } }

        fun hash(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val b = ByteArray(65536); while (true) { val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) } }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
    }
}
