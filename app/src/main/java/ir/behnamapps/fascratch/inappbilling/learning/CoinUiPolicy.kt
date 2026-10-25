package ir.behnamapps.fascratch.inappbilling.learning

import java.text.NumberFormat
import java.util.Locale
import java.math.RoundingMode

internal fun coinBalanceText(coins: Double): String = NumberFormat.getNumberInstance(Locale.US).apply {
    maximumFractionDigits = 0
    minimumFractionDigits = 0
    roundingMode = RoundingMode.DOWN
}.format(if (coins.isFinite()) coins.coerceAtLeast(0.0) else 0.0)
