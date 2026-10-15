package ir.behnamapps.fascratch.inappbilling.domain

import java.net.URI

data class InstructorContact(val label: String, val value: String, val url: String)
data class InstructorProfile(val uuid: String, val name: String, val headline: String, val biography: String,
    val photoUrl: String?, val contacts: List<InstructorContact>)

object InstructorPolicy {
    fun contactAllowed(value: String): Boolean = runCatching {
        require(value.length in 1..520 && value.none { it.isISOControl() || it.isWhitespace() })
        require(!Regex("%(?:0[0-9a-f]|1[0-9a-f]|7f)", RegexOption.IGNORE_CASE).containsMatchIn(value))
        val uri = URI(value)
        when (uri.scheme) {
            "https" -> uri.host != null && uri.userInfo == null
            "mailto" -> uri.rawSchemeSpecificPart.matches(Regex("[A-Za-z0-9.!#$%&'*+/=_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}"))
            "tel" -> uri.rawSchemeSpecificPart.matches(Regex("\\+?[0-9]{7,15}"))
            else -> false
        }
    }.getOrDefault(false)

    fun photoAllowed(base: String, url: String): Boolean = runCatching {
        val uri = URI(url)
        val hash = uri.path.substringAfterLast('/')
        require(hash.matches(Regex("[a-f0-9]{64}")))
        CoursePolicy.sameOrigin(base, url) && uri.scheme == "https" && uri.userInfo == null && uri.query == null && uri.fragment == null &&
            uri.path == URI(base).path.trimEnd('/') + "/instructor-images/" + hash
    }.getOrDefault(false)
}
