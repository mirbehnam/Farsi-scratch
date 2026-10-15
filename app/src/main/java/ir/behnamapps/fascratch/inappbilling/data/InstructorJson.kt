package ir.behnamapps.fascratch.inappbilling.data

import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.domain.*
import org.json.JSONArray
import org.json.JSONObject

internal object InstructorJson {
    fun parse(row: JSONObject?): InstructorProfile? = runCatching {
        if (row == null) return null
        val uuid = CoursePolicy.uuid(row.getString("uuid"))
        val name = row.optString("name").trim().take(161).takeUnless { it.isBlank() || it == "null" } ?: return null
        val contacts = row.optJSONArray("contacts") ?: JSONArray()
        InstructorProfile(uuid, name, row.optString("headline", "").take(120), row.optString("biography", "").take(3000),
            row.optString("photo_url").takeIf { InstructorPolicy.photoAllowed(BuildConfig.COURSE_API_BASE, it) },
            (0 until minOf(contacts.length(), 6)).mapNotNull { i ->
                val item = contacts.optJSONObject(i) ?: return@mapNotNull null
                val url = item.optString("url")
                if (!InstructorPolicy.contactAllowed(url)) return@mapNotNull null
                InstructorContact(item.optString("label", "ارتباط").take(60), item.optString("value", "").take(500), url)
            })
    }.getOrNull()

    fun encode(profile: InstructorProfile?): JSONObject? = profile?.let {
        JSONObject().put("uuid", it.uuid).put("name", it.name).put("headline", it.headline).put("biography", it.biography)
            .put("photo_url", it.photoUrl).put("contacts", JSONArray().apply {
                it.contacts.forEach { c -> put(JSONObject().put("label", c.label).put("value", c.value).put("url", c.url)) }
            })
    }
}
