package ir.behnamapps.fascratch.inappbilling.presentation

/** Presentation only: never apply numeral conversion to SKUs, URLs or API payloads. */
internal fun persianDisplay(value: String): String = value.map { character ->
    when (character) {
        in '0'..'9' -> '۰' + (character - '0')
        in '٠'..'٩' -> '۰' + (character - '٠')
        else -> character
    }
}.joinToString("")
