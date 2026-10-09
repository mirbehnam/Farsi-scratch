package ir.behnamapps.fascratch.inappbilling.learning

internal object NameEditorPolicy {
    private fun safeText(value: String) = value.none { it.code < 32 || it.code == 127 || it == '<' || it == '>' }
    fun validName(value: String) = value.trim().length in 2..30 && safeText(value)
    fun validFullName(value: String) = value.length <= 100 && safeText(value)
    fun canSave(value: String, original: String, wasSet: Boolean, changesLeft: Int, blocked: Boolean): Boolean =
        validName(value) && (!blocked || value.trim() == original) && (!wasSet || changesLeft > 0 || value.trim() == original)
}
