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

    /** Separate, short, anonymous request: layout failure never fails the actual catalog. */
    suspend fun catalogLayout(): CatalogLayoutUpdate = withContext(Dispatchers.IO) {
        val connection = connection("catalog-layout").apply { connectTimeout = 1000; readTimeout = 1000 }
        try {
            if (connection.responseCode != 200) return@withContext CatalogLayoutUpdate.Unavailable
            val json = JSONObject(connection.inputStream.use { String(it.readBytesBounded(512 * 1024), Charsets.UTF_8) }).getJSONObject("data")
            if (json.optInt("schema") == 1 && json.optString("mode") == "native") return@withContext CatalogLayoutUpdate.Native
            CatalogLayoutPolicy.verified(json.optInt("schema"), json.optString("mode"), json.optString("revision"), json.optString("document"), json.optString("sha256"))
                ?.let { CatalogLayoutUpdate.Html(it) } ?: CatalogLayoutUpdate.Unavailable
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { CatalogLayoutUpdate.Unavailable }
        finally { connection.disconnect() }
    }

    fun connection(path: String, token: String? = null): HttpsURLConnection {
        val target = "$base/$path"
        return connectionTo(target, token)
    }

    private fun connectionTo(target: String, token: String? = null): HttpsURLConnection {
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

    fun previewConnection(lesson: Lesson): HttpsURLConnection = connectionTo(CoursePolicy.previewUrl(base, lesson))

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
                    code == "access_disabled" -> "دسترسی این خرید توسط مدیر غیرفعال شده است؛ با پشتیبانی تماس بگیرید."
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

    suspend fun courses(provider: String): List<Course> {
        val courses = linkedMapOf<String, Course>()
        for (page in 1..50) {
            val response = request("courses?page=$page")
            val rows = response.getJSONArray("data")
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val sku = row.optJSONObject("products")?.optString(provider).orEmpty()
                if (sku.matches(Regex("[A-Za-z0-9_.-]{1,160}"))) {
                    val stats = row.optJSONObject("stats")
                    val course = Course(CoursePolicy.uuid(row.getString("uuid")), row.getString("title"),
                        row.optString("short_description").takeIf { it.isNotBlank() } ?: row.optString("description"), sku,
                        row.optString("instructor_name"), row.optInt("difficulty"), stats?.optDouble("duration_seconds", 0.0) ?: 0.0,
                        posterUrl(row.optJSONObject("cover")) ?: row.optString("banner_url").takeIf { it.startsWith("https://") },
                        if (stats != null && stats.has("confirmed_purchases") && !stats.isNull("confirmed_purchases")) stats.optInt("confirmed_purchases").coerceAtLeast(0) else null,
                        serverPrice(row, provider, sku))
                    courses[course.id] = withPricing(row, provider, course)
                }
            }
            if (response.optJSONObject("meta")?.optBoolean("has_more") != true) return courses.values.toList()
        }
        throw CourseFailure("فهرست دوره‌ها بیش از حد بزرگ است.")
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
                        row.optInt("difficulty"), video.optDouble("duration_seconds", 0.0), posterUrl(row.optJSONObject("cover")), row.optString("description"),
                        row.optBoolean("is_preview", false), video.optString("url").takeIf { row.optBoolean("is_preview", false) && it.startsWith("https://") })
                    CoursePolicy.downloadKey(lesson)
                    add(lesson)
                }
            }
        }
    }

    suspend fun coursePrice(course: Course, provider: String): Long? {
        return coursePricing(course, provider).serverPriceToman
    }

    suspend fun coursePricing(course: Course, provider: String): Course {
        val row = request("courses/${CoursePolicy.uuid(course.id)}").getJSONObject("data")
        if (row.optJSONObject("products")?.optString(provider) != course.sku) throw CourseFailure("محصول دوره تغییر کرده؛ فهرست را تازه‌سازی کنید.")
        return withPricing(row, provider, course)
    }

    private fun withPricing(row: JSONObject, provider: String, course: Course): Course {
        val price = row.optJSONObject("product_prices")?.optJSONObject(provider)
        val amount = serverPrice(row, provider, course.sku)
        val before = if (price?.optBoolean("discount_active") == true)
            CoursePolicy.serverPrice(course.sku, price.optString("sku"), price.opt("compare_at_toman").takeUnless { it == JSONObject.NULL }) else null
        val end = price?.optString("discount_ends_at")?.takeUnless { it.isBlank() || it == "null" }?.let {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).apply { isLenient = false }.parse(it)?.time
        }
        return course.copy(serverPriceToman = amount, compareAtToman = before, discountEndsAtMillis = end)
    }

    private fun serverPrice(row: JSONObject, provider: String, sku: String): Long? {
        val price = row.optJSONObject("product_prices")?.optJSONObject(provider) ?: return null
        return CoursePolicy.serverPrice(sku, price.optString("sku"), price.opt("amount_toman").takeUnless { it == JSONObject.NULL })
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
