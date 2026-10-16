package ir.behnamapps.fascratch.inappbilling.learning

data class SupplementaryDetails(val available: Boolean = false, val version: Int = 0, val age: Int? = null, val province: String? = null, val gender: String? = null,
    val declinedFields: Set<String> = emptySet())

object SupplementaryPolicy {
    val provinces = linkedMapOf(
        "east_azerbaijan" to "آذربایجان شرقی", "west_azerbaijan" to "آذربایجان غربی", "ardabil" to "اردبیل", "isfahan" to "اصفهان",
        "alborz" to "البرز", "ilam" to "ایلام", "bushehr" to "بوشهر", "tehran" to "تهران", "chaharmahal_bakhtiari" to "چهارمحال و بختیاری",
        "south_khorasan" to "خراسان جنوبی", "razavi_khorasan" to "خراسان رضوی", "north_khorasan" to "خراسان شمالی", "khuzestan" to "خوزستان",
        "zanjan" to "زنجان", "semnan" to "سمنان", "sistan_baluchestan" to "سیستان و بلوچستان", "fars" to "فارس", "qazvin" to "قزوین", "qom" to "قم",
        "kurdistan" to "کردستان", "kerman" to "کرمان", "kermanshah" to "کرمانشاه", "kohgiluyeh_boyerahmad" to "کهگیلویه و بویراحمد",
        "golestan" to "گلستان", "gilan" to "گیلان", "lorestan" to "لرستان", "mazandaran" to "مازندران", "markazi" to "مرکزی",
        "hormozgan" to "هرمزگان", "hamedan" to "همدان", "yazd" to "یزد")
    val genders = linkedMapOf("female" to "👧 دختر", "male" to "👦 پسر")
    fun age(value: String): Int? = value.trim().map { c ->
        when (c) { in '۰'..'۹' -> '0' + (c - '۰'); in '٠'..'٩' -> '0' + (c - '٠'); else -> c }
    }.joinToString("").toIntOrNull()?.takeIf { it in 1..120 }
    fun validAge(value: String): Boolean = value.isBlank() || age(value) != null
    fun matchesProvince(label: String, query: String): Boolean {
        fun normalize(value: String) = value.trim().replace('ي', 'ی').replace('ك', 'ک').replace('آ', 'ا').replace('\u200c', ' ')
        return normalize(label).contains(normalize(query))
    }
    fun retainNewer(oldUuid: String?, old: SupplementaryDetails?, nextUuid: String, incoming: SupplementaryDetails): SupplementaryDetails =
        if (oldUuid == nextUuid && old != null && old.version > incoming.version) old else incoming
}
