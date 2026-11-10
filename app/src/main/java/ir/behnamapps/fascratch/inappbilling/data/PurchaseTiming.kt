package ir.behnamapps.fascratch.inappbilling.data

import android.util.Log
import ir.behnamapps.fascratch.BuildConfig

/** Debug-only durations. Never include receipts, account credentials, URLs or response bodies. */
internal object PurchaseTiming {
    fun record(stage: String, elapsedMs: Long) {
        if (BuildConfig.DEBUG) runCatching { Log.i("ScratchPurchaseTiming", "stage=$stage elapsed_ms=$elapsedMs") }
    }
    fun since(stage: String, startedNanos: Long) = record(stage, (System.nanoTime() - startedNanos) / 1_000_000)
    fun server(header: String?) {
        if (!BuildConfig.DEBUG || header == null) return
        Regex("(?:^|,\\s*)(account|store_verify|profile|storage|total);dur=([0-9]+(?:\\.[0-9]+)?)")
            .findAll(header.take(512)).forEach { match ->
                match.groupValues[2].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }?.let {
                    record("server_" + match.groupValues[1], it.toLong())
                }
            }
    }
}
