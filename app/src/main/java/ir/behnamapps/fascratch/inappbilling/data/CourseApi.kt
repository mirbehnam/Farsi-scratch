package ir.behnamapps.fascratch.inappbilling.data

import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

class CourseApi : PurchaseBackend {
    private val base = BuildConfig.COURSE_API_BASE.trimEnd('/')

    fun connection(path: String, token: String? = null): HttpsURLConnection {
        val target = "$base/$path"
        require(CoursePolicy.sameOrigin(base, target))
        return (URL(target).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
            if (token != null) {
                require(token.matches(Regex("[a-f0-9]{64}")))
                setRequestProperty("Authorization", "Bearer $token")
            }
        }
    }

    private suspend fun request(path: String, token: String? = null, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val connection = connection(path, token)
        try {
            if (body != null) {
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytesBounded(2 * 1024 * 1024) } ?: byteArrayOf()
            val json = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull()
            if (status !in 200..299) {
                val code = json?.optJSONObject("error")?.optString("code").orEmpty()
                throw CourseFailure(when {
                    status == 429 -> "درخواست‌های زیادی ارسال شده؛ یک دقیقه صبر کنید."
                    code == "refunded" || code == "rejected" -> "خرید از طرف استور تأیید نشد؛ وضعیت خرید را بررسی کنید."
                    status == 401 || status == 403 -> "دسترسی نیاز به بازیابی و تأیید مجدد خرید دارد."
                    status == 404 -> "دوره یا درس در سرور پیدا نشد."
                    else -> "ارتباط با سرور یا استور برقرار نشد؛ دوباره تلاش کنید."
                }, status, code)
            }
            json ?: throw CourseFailure("پاسخ سرور معتبر نیست.")
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: CourseFailure) { throw error }
        catch (_: Exception) { throw CourseFailure("ارتباط امن با سرور برقرار نشد؛ اینترنت را بررسی کنید.") }
        finally { connection.disconnect() }
    }

    suspend fun course(provider: String): Course {
        for (page in 1..50) {
            val response = request("courses?page=$page")
            val rows = response.getJSONArray("data")
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                if (row.optJSONObject("products")?.optString(provider) == BuildConfig.COURSE_SKU) {
                    val stats = row.optJSONObject("stats")
                    return Course(CoursePolicy.uuid(row.getString("uuid")), row.getString("title"),
                        row.optString("description").takeIf { it.isNotBlank() } ?: row.optString("short_description"), BuildConfig.COURSE_SKU,
                        row.optString("instructor_name"), row.optInt("difficulty"), stats?.optDouble("duration_seconds", 0.0) ?: 0.0,
                        posterUrl(row.optJSONObject("cover")) ?: row.optString("banner_url").takeIf { it.startsWith("https://") },
                        if (stats != null && stats.has("confirmed_purchases") && !stats.isNull("confirmed_purchases")) stats.optInt("confirmed_purchases").coerceAtLeast(0) else null)
                }
            }
            if (response.optJSONObject("meta")?.optBoolean("has_more") != true) break
        }
        throw CourseFailure("دوره scratch_basic هنوز برای این استور در سرور منتشر نشده است.")
    }

    suspend fun lessons(course: Course, token: String? = null): List<Lesson> {
        val sections = request("courses/${CoursePolicy.uuid(course.id)}/content", token).getJSONObject("data").getJSONArray("sections")
        return buildList {
            for (sectionIndex in 0 until sections.length()) {
                val section = sections.getJSONObject(sectionIndex)
                val rows = section.getJSONArray("lessons")
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index)
                    val video = row.optJSONObject("video") ?: continue
                    val lesson = Lesson(CoursePolicy.uuid(row.getString("uuid")), course.id, section.getString("title"), row.getString("title"),
                        video.getInt("content_version"), video.getLong("file_size_bytes"), video.getString("content_hash"),
                        row.optInt("difficulty"), video.optDouble("duration_seconds", 0.0), posterUrl(row.optJSONObject("cover")), row.optString("description"))
                    CoursePolicy.downloadKey(lesson)
                    add(lesson)
                }
            }
        }
    }

    override suspend fun verify(course: Course, provider: String, receipt: Receipt, restore: Boolean): CourseAccess {
        val response = request("purchases/${if (restore) "restore" else "verify"}", body = JSONObject()
            .put("provider", provider).put("sku", receipt.sku).put("purchase_token", receipt.token)).getJSONObject("data")
        if (response.optString("status") != "verified" || !response.optBoolean("has_access")) throw CourseFailure("خرید هنوز تأیید نشده است.")
        val token = response.getString("access_token")
        require(token.matches(Regex("[a-f0-9]{64}")))
        val expiry = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { isLenient = false }.parse(response.getString("expires_at"))
            ?: throw CourseFailure("زمان اعتبار پاسخ معتبر نیست.")
        return CourseAccess(CoursePolicy.uuid(response.getString("course_uuid")), token, expiry.time)
    }
}

private fun posterUrl(cover: JSONObject?): String? = cover?.let {
    it.optString("poster_url").takeIf { url -> url.startsWith("https://") }
        ?: if (it.optString("type") == "image") it.optString("url").takeIf { url -> url.startsWith("https://") } else null
}

internal fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit)
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
