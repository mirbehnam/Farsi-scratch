package ir.behnamapps.fascratch.inappbilling.data

import android.content.Context
import android.util.AtomicFile
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CourseCache(context: Context) {
    private val root = context.noBackupFilesDir
    private val legacyFile = AtomicFile(File(root, "course-catalog-${BuildConfig.FLAVOR}.json"))
    private val catalog = AtomicFile(File(root, "course-list-${BuildConfig.FLAVOR}.json"))
    private val layoutFile = AtomicFile(File(root, "course-layout-${BuildConfig.FLAVOR}.json"))
    fun loadCatalogLayout(): CatalogLayout? = runCatching {
        val data = JSONObject(layoutFile.openRead().use { String(it.readBytesBounded(2 * CatalogLayoutPolicy.MAX_BYTES + 16384), Charsets.UTF_8) })
        require(data.getString("api_base") == BuildConfig.COURSE_API_BASE)
        CatalogLayoutPolicy.verified(data.getInt("schema"), "html", data.getString("revision"), data.getString("document"), data.getString("sha256"))
    }.getOrNull()
    fun saveCatalogLayout(layout: CatalogLayout) {
        require(CatalogLayoutPolicy.verified(1, "html", layout.revision, layout.document, layout.sha256) != null)
        val data = JSONObject().put("schema", 1).put("api_base", BuildConfig.COURSE_API_BASE)
            .put("revision", layout.revision).put("document", layout.document).put("sha256", layout.sha256)
        val output = layoutFile.startWrite()
        try { output.write(data.toString().toByteArray(Charsets.UTF_8)); layoutFile.finishWrite(output) }
        catch (error: Exception) { layoutFile.failWrite(output); throw error }
    }
    fun clearCatalogLayout() { layoutFile.delete() }
    private fun courseFile(id: String) = AtomicFile(File(root, "course-${BuildConfig.FLAVOR}-${CoursePolicy.uuid(id)}.json"))
    private fun encode(course: Course) = JSONObject().put("id", course.id).put("title", course.title).put("description", course.description)
        .put("sku", course.sku).put("instructor", course.instructor).put("difficulty", course.difficulty).put("difficulty_label", course.difficultyLabel)
        .put("duration", course.durationSeconds).put("cover", course.coverUrl).put("purchases", course.confirmedPurchases).put("price_toman", course.serverPriceToman)
        .put("compare_at_toman", course.compareAtToman).put("discount_ends_at_ms", course.discountEndsAtMillis)
    private fun decode(data: JSONObject) = Course(CoursePolicy.uuid(data.getString("id")), data.getString("title"), data.getString("description"), data.getString("sku"),
        data.optString("instructor"), data.optInt("difficulty"), data.optDouble("duration", 0.0), data.optString("cover").takeIf { it.startsWith("https://") },
        if (data.has("purchases") && !data.isNull("purchases")) data.optInt("purchases") else null,
        CoursePolicy.serverPrice(data.getString("sku"), data.getString("sku"), data.opt("price_toman").takeUnless { it == JSONObject.NULL }),
        CoursePolicy.serverPrice(data.getString("sku"), data.getString("sku"), data.opt("compare_at_toman").takeUnless { it == JSONObject.NULL }),
        if (data.has("discount_ends_at_ms") && !data.isNull("discount_ends_at_ms")) data.getLong("discount_ends_at_ms") else null,
        DifficultyLevel.label(data.optInt("difficulty"), data.optString("difficulty_label")))
    fun saveCatalog(courses: List<Course>) {
        val output = catalog.startWrite()
        try { output.write(JSONArray().apply { courses.forEach { put(encode(it)) } }.toString().toByteArray(Charsets.UTF_8)); catalog.finishWrite(output) }
        catch (error: Exception) { catalog.failWrite(output); throw error }
    }
    fun loadCatalog(): List<Course> = runCatching {
        val rows = JSONArray(catalog.openRead().use { String(it.readBytesBounded(2 * 1024 * 1024), Charsets.UTF_8) })
        (0 until rows.length()).map { decode(rows.getJSONObject(it)) }
    }.getOrElse { load()?.let { listOf(it.first) } ?: emptyList() }
    fun save(course: Course, lessons: List<Lesson>) {
        val file = courseFile(course.id)
        val rows = JSONArray()
        lessons.forEach { rows.put(JSONObject().put("id", it.id).put("section", it.section).put("title", it.title)
            .put("version", it.version).put("bytes", it.bytes).put("sha256", it.sha256)
            .put("difficulty", it.difficulty).put("difficulty_label", it.difficultyLabel).put("duration", it.durationSeconds).put("cover", it.coverUrl).put("description", it.description).put("is_preview", it.isPreview)) }
        val data = encode(course).put("lessons", rows)
        val output = file.startWrite()
        try { output.write(data.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
    fun load(id: String? = null): Pair<Course, List<Lesson>>? = runCatching {
        val file = if (id == null) legacyFile else courseFile(id).takeIf { it.baseFile.exists() } ?: legacyFile
        val data = JSONObject(file.openRead().use { String(it.readBytesBounded(2 * 1024 * 1024), Charsets.UTF_8) })
        val course = decode(data)
        require(id == null || course.id == id)
        val rows = data.getJSONArray("lessons")
        course to (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            Lesson(CoursePolicy.uuid(row.getString("id")), course.id, row.getString("section"), row.getString("title"),
                row.getInt("version"), row.getLong("bytes"), row.getString("sha256"), row.optInt("difficulty"), row.optDouble("duration", 0.0),
                row.optString("cover").takeIf { it.startsWith("https://") }, row.optString("description"), row.optBoolean("is_preview", false),
                difficultyLabel = DifficultyLevel.label(row.optInt("difficulty"), row.optString("difficulty_label"))).also { CoursePolicy.downloadKey(it) }
        }
    }.getOrNull()
}
