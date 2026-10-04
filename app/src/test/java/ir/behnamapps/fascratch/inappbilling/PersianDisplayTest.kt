package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.presentation.persianDisplay
import org.junit.Assert.assertEquals
import org.junit.Test

class PersianDisplayTest {
    @Test fun formatsLatinAndArabicDigitsWithoutChangingText() {
        assertEquals("قیمت ۲۵۰,۰۰۰ تومان", persianDisplay("قیمت 250,000 تومان"))
        assertEquals("۰۱۲۳۴۵۶۷۸۹", persianDisplay("٠١٢٣٤٥٦٧٨٩"))
        assertEquals("۰۱۲۳۴۵۶۷۸۹", persianDisplay("0123456789"))
    }
    @Test fun alreadyPersianAndNonNumericTextStayUnchanged() {
        assertEquals("درس ۱۲ 🌱", persianDisplay("درس ۱۲ 🌱"))
        assertEquals("", persianDisplay(""))
        assertEquals("اسکرچ", persianDisplay("اسکرچ"))
    }
    @Test fun playbackTimesAndPercentagesKeepTheirSeparators() {
        assertEquals("۰۲:۰۵ / ۱:۲۳:۴۵", persianDisplay("02:05 / 1:23:45"))
        assertEquals("۷۵٪", persianDisplay("75٪"))
    }
}
