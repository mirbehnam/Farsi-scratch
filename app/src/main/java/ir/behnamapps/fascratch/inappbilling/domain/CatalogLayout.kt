package ir.behnamapps.fascratch.inappbilling.domain

import java.security.MessageDigest

data class CatalogLayout(val revision: String, val document: String, val sha256: String)

sealed interface CatalogLayoutUpdate {
    data object Native : CatalogLayoutUpdate
    data object Unavailable : CatalogLayoutUpdate
    data class Html(val layout: CatalogLayout) : CatalogLayoutUpdate
}

/** A display-only payload. Never contains receipts, access tokens or SDK credentials. */
object CatalogLayoutPolicy {
    const val MAX_BYTES = 262144
    const val ORIGIN = "https://catalog.scratch.invalid"
    fun resolve(cached: CatalogLayout?, update: CatalogLayoutUpdate): CatalogLayout? = when (update) {
        CatalogLayoutUpdate.Native -> null
        CatalogLayoutUpdate.Unavailable -> cached
        is CatalogLayoutUpdate.Html -> update.layout
    }
    fun verified(schema: Int, mode: String, revision: String, document: String, hash: String): CatalogLayout? {
        if (schema != 1 || mode != "html" || !revision.matches(Regex("[0-9]{1,10}"))) return null
        val bytes = document.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MAX_BYTES || !hash.matches(Regex("[a-f0-9]{64}"))) return null
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return if (actual == hash) CatalogLayout(revision, document, hash) else null
    }
    fun courseAction(type: String, id: String?, mainFrame: Boolean, origin: String, busy: Boolean, courses: List<Course>): Course? {
        if (!mainFrame || origin != ORIGIN || busy || type != "view-course") return null
        return courses.firstOrNull { it.id == id }
    }
}
