package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.presentation.CatalogFont
import org.junit.Assert.*
import org.junit.Test

class CatalogFontTest {
    @Test fun usesLocalBinaryFontAndWaitsForReadiness() {
        val script = CatalogFont.bootstrap("AAEAAA==")
        assertTrue(script.contains("new FontFace('Shabnam',bytes.buffer"))
        assertTrue(script.contains("document.fonts.ready"))
        assertTrue(script.contains("window.__scratchFontReady=true"))
        assertTrue(script.contains("font-family:Shabnam,Tahoma,sans-serif!important"))
        assertFalse(script.contains("https://"))
        assertFalse(script.contains("file://"))
        assertFalse(script.contains("fetch("))
    }
    @Test fun refusesScriptInjectionOrUnboundedEncodedData() {
        for (invalid in listOf("", "';alert(1);//", "a".repeat(524289))) {
            assertTrue(runCatching { CatalogFont.bootstrap(invalid) }.isFailure)
        }
    }
}
