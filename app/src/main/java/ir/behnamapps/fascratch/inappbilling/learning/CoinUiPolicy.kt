package ir.behnamapps.fascratch.inappbilling.learning

import java.text.NumberFormat
import java.util.Locale

internal fun coinBalanceText(coins: Double): String = NumberFormat.getNumberInstance(Locale.US).apply {
    maximumFractionDigits = 3
    minimumFractionDigits = 0
}.format(if (coins.isFinite()) coins.coerceAtLeast(0.0) else 0.0)
