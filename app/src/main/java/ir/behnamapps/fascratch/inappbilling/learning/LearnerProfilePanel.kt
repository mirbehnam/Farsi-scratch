package ir.behnamapps.fascratch.inappbilling.learning

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.behnamapps.fascratch.inappbilling.presentation.persianDisplay
import kotlin.math.ceil

/** Native implementation of scratch_profile_preview.html's final CSS cascade. */
@Composable internal fun LearnerProfilePanel(
    profile: LearnerProfile,
    onAccount: () -> Unit,
    onEditName: () -> Unit,
    onGuide: () -> Unit,
    onClose: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val contentWidth = maxWidth.value
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp)) {
            Column(Modifier.fillMaxWidth()) {
                ProfilePanelContent(profile, onAccount, onEditName, onGuide, onClose, contentWidth)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ProfilePanelContent(
    profile: LearnerProfile,
    onAccount: () -> Unit,
    onEditName: () -> Unit,
    onGuide: () -> Unit,
    onClose: () -> Unit,
    contentWidth: Float,
) {
    val config = LocalConfiguration.current
    val narrow = contentWidth <= 700
    val short = config.screenHeightDp <= 470 && config.screenWidthDp >= 780
    val avatar = if (narrow) 88.dp else 104.dp
    val font = if (narrow) 9.5.sp else if (short) 10.sp else 11.sp
    // RTL Row puts the avatar at the physical right, never reversed by the app's locale.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        // No second frame, clipping shape or sample text over the artwork's own plaque.
        LearnerLevelBadge(profile.level, Modifier.size(avatar))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (!profile.registered) "کاربر مهمان" else if (profile.nameSet) profile.name else "برنامه‌نویس", color = Color(0xFF344F83),
                        fontSize = if (narrow) 15.sp else if (short) 17.sp else 19.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onEditName)
                            .semantics { contentDescription = "ویرایش نام نمایشی" })
                    ProfileRoundAction("×", "بستن پروفایل", true, onClose)
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                ProfileAction(if (profile.registered) "✎ تغییر نام نمایشی" else "ثبت‌نام / ورود", font,
                    listOf(Color(0xFF54ACFF), Color(0xFF387DE5)), Color.White, if (profile.registered) onEditName else onAccount)
                ProfileAction("✨ راهنمای کسب امتیاز", font,
                    listOf(Color(0xFFFFE28B), Color(0xFFFFC34B)), Color(0xFF795117), onGuide)
            }
            ReferenceProgress(profile, narrow)
        }
    }
    Spacer(Modifier.height(if (short) 8.dp else 12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (narrow) 7.dp else 10.dp)) {
        ReferenceStat("زمان مشاهده آموزش", persianDisplay(learningDuration(profile.watchMs)), 0, Modifier.weight(1f), narrow)
        ReferenceStat("زمان برنامه‌نویسی", persianDisplay(learningDuration(profile.codingMs)), 1, Modifier.weight(1f), narrow)
        ReferenceStat("رتبه در جدول کاربران", profile.rank?.takeIf { profile.registered }?.let { persianDisplay(it.toString()) } ?: "—", 2, Modifier.weight(1f), narrow,
            if (!profile.registered) "پس از تکمیل ثبت‌نام" else if (!profile.nameSet) "نام نمایشی را ثبت کن" else null)
    }
}

@Composable private fun ProfileAction(label: String, font: androidx.compose.ui.unit.TextUnit, colors: List<Color>, ink: Color, onClick: () -> Unit) {
    Box(Modifier.shadow(3.dp, RoundedCornerShape(11.dp)).clip(RoundedCornerShape(11.dp))
        .background(Brush.verticalGradient(colors)).border(2.dp, Color.White.copy(alpha = .77f), RoundedCornerShape(11.dp))
        .clickable(role = Role.Button, onClick = onClick).heightIn(min = 32.dp).padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = ink, fontSize = font, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun ProfileRoundAction(label: String, description: String, close: Boolean, action: () -> Unit) {
    Box(Modifier.size(44.dp).shadow(2.dp, RoundedCornerShape(9.dp)).clip(RoundedCornerShape(9.dp))
        .background(Color(if (close) 0xFFFFF0F0 else 0xFFF0F3FF))
        .border(1.dp, Color(if (close) 0xFFFFE0E0 else 0xFFDFE6FD), RoundedCornerShape(9.dp))
        .semantics { contentDescription = description }.clickable(role = Role.Button, onClick = action), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 19.sp, color = Color(if (close) 0xFFD46977 else 0xFF6177AC))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ReferenceProgress(profile: LearnerProfile, narrow: Boolean) {
    val fraction = (profile.xpIntoLevel / profile.xpForNext.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    val progress by animateFloatAsState(fraction, tween(800), label = "profile-progress")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(27.dp).background(Brush.verticalGradient(listOf(Color(0xFFFFD96E), Color(0xFFFFB92F))), CircleShape)
            .border(2.dp, Color(0xFFFFF7D5), CircleShape), contentAlignment = Alignment.Center) {
            Text(persianDisplay(profile.level.toString()), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF67420F))
        }
        Canvas(Modifier.weight(1f).height(12.dp).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            contentDescription = "پیشرفت تا سطح بعدی"
        }) {
            drawRoundRect(Color(0xFFE9EDFF), cornerRadius = CornerRadius(size.height / 2))
            drawRoundRect(Color(0xFFDFE4FC), cornerRadius = CornerRadius(size.height / 2), style = Stroke(2.dp.toPx()))
            if (progress > 0f) drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFA67AFF), Color(0xFF6B7AFA), Color(0xFF4C97FF))),
                topLeft = Offset(2.dp.toPx(), 2.dp.toPx()), size = Size((size.width - 4.dp.toPx()) * progress, size.height - 4.dp.toPx()),
                cornerRadius = CornerRadius(size.height / 2))
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf("${persianDisplay(profile.xpIntoLevel.toLong().toString())} امتیاز فعلی",
            "${persianDisplay((fraction * 100).toInt().toString())}٪ پیشرفت این سطح",
            "${persianDisplay(ceil((profile.xpForNext - profile.xpIntoLevel).coerceAtLeast(0.0)).toLong().toString())} امتیاز تا سطح بعدی").forEach {
            Text(it, color = Color(0xFF6974A0), fontSize = if (narrow) 9.sp else 10.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable private fun ReferenceStat(title: String, value: String, kind: Int, modifier: Modifier, narrow: Boolean, hint: String? = null) {
    val backgrounds = listOf(0xFFFFF2DF, 0xFFE4F4FF, 0xFFEAF7E7)
    val borders = listOf(0xFFFFDDA5, 0xFFC2E5FA, 0xFFCCEAC8)
    val icons = listOf(0xFFFFE0AB, 0xFFC7EAFF, 0xFFD1EFCB)
    val inks = listOf(0xFFD58A20, 0xFF258CCC, 0xFF4F9E53)
    val labels = listOf(0xFF956927, 0xFF387497, 0xFF59845B)
    val values = listOf(0xFF995B18, 0xFF236B9A, 0xFF407D48)
    Surface(modifier.heightIn(min = if (narrow) 56.dp else 66.dp), shape = RoundedCornerShape(16.dp),
        color = Color(backgrounds[kind]), border = BorderStroke(2.dp, Color(borders[kind])), shadowElevation = 2.dp) {
        Row(Modifier.padding(horizontal = if (narrow) 9.dp else 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(if (narrow) 31.dp else 36.dp).background(Color(icons[kind]), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                ProfileStatIcon(kind, Color(inks[kind]))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color(labels[kind]), fontSize = if (narrow) 11.sp else 12.sp)
                Text(value, color = Color(values[kind]), fontSize = if (narrow) 16.sp else 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                hint?.let { Text(it, color = Color(0xFF728881), fontSize = 9.sp) }
            }
        }
    }
}

/** Same outlined play, clock and trophy icons as the reference SVGs. */
@Composable private fun ProfileStatIcon(kind: Int, ink: Color) {
    Canvas(Modifier.size(20.dp)) {
        val scale = size.width / 24f
        val stroke = Stroke(1.9f * scale, cap = StrokeCap.Round)
        if (kind < 2) drawCircle(ink, 9f * scale, center = Offset(12f * scale, 12f * scale), style = stroke)
        val path = Path()
        fun move(x: Float, y: Float) = path.moveTo(x * scale, y * scale)
        fun line(x: Float, y: Float) = path.lineTo(x * scale, y * scale)
        when (kind) {
            0 -> { move(10f, 8f); line(16f, 12f); line(10f, 16f); path.close() }
            1 -> { move(12f, 6f); line(12f, 12f); line(16f, 15f) }
            else -> {
                move(6f, 4f); line(18f, 4f); line(18f, 8f)
                path.cubicTo(18f * scale, 16f * scale, 6f * scale, 16f * scale, 6f * scale, 8f * scale); path.close()
                move(7f, 20f); line(17f, 20f); move(12f, 14f); line(12f, 20f)
                move(6f, 7f); line(2f, 7f); path.quadraticTo(2f * scale, 11f * scale, 6f * scale, 11f * scale)
                move(18f, 7f); line(22f, 7f); path.quadraticTo(22f * scale, 11f * scale, 18f * scale, 11f * scale)
            }
        }
        drawPath(path, ink, style = stroke)
    }
}
