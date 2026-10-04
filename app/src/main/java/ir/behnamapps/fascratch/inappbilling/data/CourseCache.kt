package ir.behnamapps.fascratch.inappbilling.data

import android.content.Context
import android.util.AtomicFile
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CourseCache(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "course-catalog-${BuildConfig.FLAVOR}.json"))
    fun save(course: Course, lessons: List<Lesson>) {
        val rows = JSONArray()
        lessons.forEach { rows.put(JSONObject().put("id", it.id).put("section", it.section).put("title", it.title)
            .put("version", it.version).put("bytes", it.bytes).put("sha256", it.sha256)) }
        val data = JSONObject().put("id", course.id).put("title", course.title).put("description", course.description)
            .put("sku", course.sku).put("lessons", rows)
        val output = file.startWrite()
        try { output.write(data.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
    fun load(): Pair<Course, List<Lesson>>? = runCatching {
        val data = JSONObject(file.openRead().use { String(it.readBytesBounded(2 * 1024 * 1024), Charsets.UTF_8) })
        val course = Course(CoursePolicy.uuid(data.getString("id")), data.getString("title"), data.getString("description"), data.getString("sku"))
        require(course.sku == BuildConfig.COURSE_SKU)
        val rows = data.getJSONArray("lessons")
        course to (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            Lesson(CoursePolicy.uuid(row.getString("id")), course.id, row.getString("section"), row.getString("title"),
                row.getInt("version"), row.getLong("bytes"), row.getString("sha256")).also { CoursePolicy.downloadKey(it) }
        }
    }.getOrNull()
}
