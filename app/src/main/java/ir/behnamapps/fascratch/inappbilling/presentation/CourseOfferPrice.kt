@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ir.behnamapps.fascratch.inappbilling.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.behnamapps.fascratch.inappbilling.domain.Course
import ir.behnamapps.fascratch.inappbilling.domain.CoursePolicy
import java.text.NumberFormat
import java.util.Locale

internal data class CourseSavings(val amountToman: Long, val percent: Int)

/** Percentage is floored so a 99.9% display discount is not advertised as free. */
internal fun courseSavings(course: Course, now: Long): CourseSavings? {
    if (!CoursePolicy.discountVisible(course, now)) return null
    val difference = course.compareAtToman!! - course.serverPriceToman!!
    return CourseSavings(difference, (difference * 100 / course.compareAtToman).toInt())
}

private fun toman(value: Long) = persianDisplay(NumberFormat.getIntegerInstance(Locale.US).format(value).replace(',', '٬') + " تومان")

@Composable
internal fun CourseOfferPrice(course: Course, price: String?, fallback: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val saving = courseSavings(course, rememberDiscountClock(course)).takeIf { price != null }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (saving != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(color = Color(0xFFB42332), contentColor = Color.White, shape = RoundedCornerShape(7.dp)) {
                    Text(if (saving.percent > 0) persianDisplay("${saving.percent}٪ تخفیف") else "تخفیف ویژه",
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Text(toman(course.compareAtToman!!), Modifier.align(Alignment.CenterVertically),
                    color = Color(0xFF697570), style = MaterialTheme.typography.bodySmall, textDecoration = TextDecoration.LineThrough)
            }
        }
        Text(persianDisplay(price ?: fallback), color = if (saving != null) Color(0xFF992236) else Color(0xFF183F38),
            fontWeight = FontWeight.ExtraBold, fontSize = if (price != null) (if (compact) 20.sp else 22.sp) else 14.sp)
        if (saving != null && !compact) Text(toman(saving.amountToman) + " کمتر",
            color = Color(0xFF176D46), style = MaterialTheme.typography.labelSmall)
    }
}
