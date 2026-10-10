package ir.behnamapps.fascratch.inappbilling.learning

import java.security.SecureRandom
import java.util.Locale

internal object AccountAuthPolicy {
    fun sameSession(expected: String?, current: String) = expected == null || expected == current
    fun googleUnavailableMessage(nativeFieldPresent: Boolean, enabled: Boolean): String? = when {
        enabled -> null
        !nativeFieldPresent -> "سرور هنوز به نسخه ورود گوگل به‌روز نشده است؛ فعلاً با نام کاربری وارد شو."
        else -> "ورود گوگل هنوز در سرور تنظیم نشده است؛ فعلاً با نام کاربری وارد شو."
    }
    fun username(value: String) = value.trim().lowercase(Locale.ROOT).matches(Regex("[a-z0-9][a-z0-9_]{2,29}"))
    fun password(value: String, register: Boolean) = value.codePointCount(0, value.length) in (if (register) 15 else 1)..64 && value.toByteArray(Charsets.UTF_8).size <= 72
    fun registration(name: String, displayName: String, secret: String, confirmation: String, available: Boolean?, checkedName: String?) =
        username(name) && NameEditorPolicy.validName(displayName) && password(secret, true) && secret == confirmation &&
            available == true && checkedName == name
    fun secret() = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
}
