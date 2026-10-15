package ir.behnamapps.fascratch.inappbilling.presentation

import java.net.URI

internal enum class InstructorContactKind { INSTAGRAM, TELEGRAM, EMAIL, PHONE, WEBSITE }

internal object InstructorDialogPolicy {
    // Available width is already inset-safe. Leave additional breathing room on
    // both sides, even when an OEM gives a Dialog a full-screen measure spec.
    fun width(available: Float): Float = minOf(660f, available.coerceAtLeast(0f) * .88f)

    fun contactKind(url: String): InstructorContactKind = runCatching {
        val uri = URI(url)
        when {
            uri.scheme == "mailto" -> InstructorContactKind.EMAIL
            uri.scheme == "tel" -> InstructorContactKind.PHONE
            uri.host?.lowercase() in listOf("instagram.com", "www.instagram.com") -> InstructorContactKind.INSTAGRAM
            uri.host?.lowercase() in listOf("t.me", "telegram.me") -> InstructorContactKind.TELEGRAM
            else -> InstructorContactKind.WEBSITE
        }
    }.getOrDefault(InstructorContactKind.WEBSITE)
}
