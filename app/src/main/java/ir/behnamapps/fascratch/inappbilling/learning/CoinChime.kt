package ir.behnamapps.fascratch.inappbilling.learning

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Three short metallic coin notes; generated once locally, without a media download. */
internal object CoinChime {
    fun samples(rate: Int = 22050): ShortArray = ShortArray((rate * .45).toInt()) { index ->
        val t = index.toDouble() / rate
        val note = when { t < .075 -> 0; t < .15 -> 1; else -> 2 }
        val local = t - when (note) { 0 -> 0.0; 1 -> .075; else -> .15 }
        val frequency = when (note) { 0 -> 1318.5; 1 -> 1975.5; else -> 2637.0 }
        val envelope = minOf(local / .003, 1.0) * exp(-local * 17) * ((.45 - t) / .025).coerceIn(0.0, 1.0)
        ((sin(2 * PI * frequency * local) + .28 * sin(2 * PI * frequency * 2.76 * local)) * envelope * 11000)
            .toInt().coerceIn(-30000, 30000).toShort()
    }
}
