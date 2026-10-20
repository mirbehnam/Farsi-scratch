package ir.behnamapps.fascratch.inappbilling.learning

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import ir.behnamapps.fascratch.inappbilling.presentation.persianDisplay
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

private val HallGold = Color(0xFFFFD99A)
private val HallInk = Color(0xFFF2EDFF)
private val HallMuted = Color(0xFFADA8C1)

@Composable internal fun ShowcaseHallWindow(onClose: () -> Unit, content: @Composable () -> Unit) {
    LearnerDialogTheme {
        MaterialTheme(colorScheme = darkColorScheme(primary = HallGold, onPrimary = Color(0xFF34220B),
            surface = Color(0xFF171627), onSurface = HallInk, background = Color(0xFF10101D)), typography = MaterialTheme.typography) {
            Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
                LearnerDialogWindowBounds()
                // Use the same centered, inset-aware bounds as the proven profile dialogs.
                // Do not change immersive flags while Compose is measuring an RTL dialog.
                Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
                    Surface(Modifier.learnerDialogPanelBounds().clipToBounds(), shape = RoundedCornerShape(20.dp), color = Color(0xFF10101D)) {
                        Box(Modifier.fillMaxSize()) {
                            HallArchitecture()
                            HallDust()
                            content()
                        }
                    }
                }
            }
        }
    }
}

/** Static architectural layers are separate from the small animated particle canvas. */
@Composable private fun HallArchitecture() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF17172C), Color(0xFF272136), Color(0xFF0D0D17))))
        for (i in 0..6) {
            val x = w * i / 6
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x338B739E), Color.Transparent)), Offset(x - w * .014f, 0f), Size(w * .028f, h * .82f))
            drawLine(Color(0x228F7D69), Offset(x, h * .06f), Offset(x, h * .79f), 1.dp.toPx())
        }
        for (spot in listOf(.18f, .54f, .84f)) {
            val beam = Path().apply { moveTo(w * spot, 0f); lineTo(w * (spot - .16f), h * .8f); lineTo(w * (spot + .16f), h * .8f); close() }
            drawPath(beam, Brush.verticalGradient(listOf(Color(0x20FFE6B3), Color.Transparent)))
            drawCircle(HallGold.copy(alpha = .35f), 3.dp.toPx(), Offset(w * spot, 0f))
        }
        drawRect(Color(0xFF12121E), Offset(0f, h * .81f), Size(w, h * .19f))
        drawLine(HallGold.copy(alpha = .15f), Offset(0f, h * .81f), Offset(w, h * .81f), 1.dp.toPx())
        for (i in -2..8) drawLine(Color(0x198D7D90), Offset(w * .5f, h * .81f), Offset(w * i / 6, h), 1.dp.toPx())
    }
}

private data class HallParticle(val x: Float, val y: Float, val radius: Float, val speed: Float)
@Composable private fun HallDust() {
    val particles = remember { Random(87).let { random -> List(24) { HallParticle(random.nextFloat(), random.nextFloat(), .7f + random.nextFloat(), .3f + random.nextFloat() * .4f) } } }
    val phase = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preview = LocalInspectionMode.current
    LaunchedEffect(lifecycle, preview) {
        if (!preview) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // 20fps, only while visible. No per-particle jobs, bitmaps, blur or network work.
            while (isActive) { delay(50); phase.floatValue = (phase.floatValue + .002f) % 1f }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val time = phase.floatValue
        particles.forEach { p ->
            val x = (p.x + time * .08f) % 1f
            val y = (p.y - time * p.speed + 1f) % 1f
            drawCircle(HallGold.copy(alpha = .12f + p.radius * .07f), p.radius.dp.toPx(), Offset(x * size.width, y * size.height))
        }
    }
}

@Composable internal fun ShowcaseHallContent(data: LearnerShowcase, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val railWidth = (maxWidth * .29f).coerceIn(180.dp, 270.dp)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.width(railWidth).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ShowcaseIdentity(data)
                val stats = listOf(
                    Triple("🎬", "تماشای آموزش", data.stats?.let { showcaseTime(it.watchMs) } ?: "—"),
                    Triple("🧩", "برنامه‌نویسی", data.stats?.let { showcaseTime(it.codingMs) } ?: "—"),
                    Triple("📅", "همراه اسکرچ", "${data.memberDays} روز"),
                    Triple("📚", "درس کامل‌شده", data.stats?.completedLessons?.toString() ?: "—"),
                    Triple("🖼", "آیکون‌های کلکسیون", data.avatars.size.toString()),
                    Triple("🏆", "دستاوردهای کسب‌شده", data.trophies.size.toString()))
                stats.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        row.forEach { (icon, label, value) -> HallStat(icon, label, value, Modifier.weight(1f)) }
                    }
                }
            }
            LazyVerticalGrid(columns = GridCells.Adaptive(128.dp), modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) { HallSection("کلکسیون شخصیت‌ها", data.avatars.size) }
                if (data.avatars.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { HallEmpty("این تالار منتظر اولین شخصیت است ✦") }
                items(data.avatars, key = { "hall-avatar-${it.id}" }) { avatar ->
                    HallExhibit(avatar.title, avatar.equipped) {
                        LearnerLevelBadge(data.learner.level, Modifier.fillMaxSize(), avatar.id, avatar.url, avatar.hash)
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { HallSection("گنجینهٔ دستاوردها", data.trophies.size) }
                if (data.trophies.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { HallEmpty("جای اولین جام اینجا محفوظ است 🏆") }
                items(data.trophies, key = { "hall-trophy-${it.id}" }) { trophy ->
                    HallExhibit(trophy.title, false, trophy = true) { AchievementTrophy(trophy) { TrophyArtifact(trophy.id) } }
                }
            }
        }
    }
}

@Composable private fun ShowcaseIdentity(data: LearnerShowcase) {
    val p = data.learner
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xD9252238), border = BorderStroke(1.dp, Color(0x607A6555))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LearnerLevelBadge(p.level, Modifier.size(76.dp), p.avatarId, p.imageUrl, p.imageHash)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.name, color = HallInk, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(persianDisplay("رتبه ${p.rank} · سطح ${p.level}"), color = HallGold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Text(persianDisplay(coinBalanceText(p.xp)) + " XP", color = Color(0xFFB3D8EB), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            Text(persianDisplay(membershipDuration(data.memberDays)), color = HallMuted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable private fun HallStat(icon: String, label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color(0xE51E1D31), border = BorderStroke(1.dp, Color(0xFF383248))) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(persianDisplay(value), color = HallInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("$icon $label", color = HallMuted, fontSize = 10.sp, maxLines = 2)
        }
    }
}

@Composable private fun HallSection(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("✦ $title", Modifier.weight(1f), color = HallGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(persianDisplay(count.toString()), color = HallMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable private fun HallEmpty(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(18.dp), color = HallMuted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
}

/** Every exhibit has the same square footprint; image scale is independent of name length. */
@Composable private fun HallExhibit(title: String, equipped: Boolean, trophy: Boolean = false, art: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(15.dp), color = Color(0xE51C1B2E),
        border = BorderStroke(if (equipped) 2.dp else 1.dp, if (equipped) HallGold else Color(0xFF514355)), shadowElevation = 3.dp) {
        Box(Modifier.background(Brush.verticalGradient(listOf(Color(0xFF30273E), Color(0xFF171727), Color(0xFF40302B))))) {
            Column(Modifier.fillMaxSize().padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { art() }
                Text(title, Modifier.fillMaxWidth().height(31.dp), color = if (trophy) HallGold else HallInk,
                    fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (equipped) Text("✓", Modifier.align(Alignment.TopEnd).padding(7.dp), color = HallGold, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
    }
}

@Composable private fun TrophyArtifact(id: String) {
    val emblem = when {
        id == "coding_month" -> "👑"
        id == "coding_week" -> "🔥"
        id.startsWith("coding") -> "⚡"
        id.startsWith("watch") || id == "lessons_10" -> "★"
        id == "saved_project" -> "◆"
        id == "avatars_8" -> "🛠"
        else -> "✦"
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
            val w = size.width; val h = size.height
            drawCircle(Brush.radialGradient(listOf(Color(0x55766A37), Color.Transparent)), w * .48f)
            val handles = Path().apply {
                moveTo(w*.24f,h*.27f); cubicTo(w*.03f,h*.2f,w*.07f,h*.6f,w*.35f,h*.57f)
                moveTo(w*.76f,h*.27f); cubicTo(w*.97f,h*.2f,w*.93f,h*.6f,w*.65f,h*.57f)
            }
            drawPath(handles, Color(0xFFD6A64E), style = androidx.compose.ui.graphics.drawscope.Stroke(w*.065f))
            val cup = Path().apply {
                moveTo(w*.25f,h*.2f); lineTo(w*.75f,h*.2f); lineTo(w*.68f,h*.51f)
                quadraticTo(w*.5f,h*.77f,w*.32f,h*.51f); close()
            }
            drawPath(cup, Brush.horizontalGradient(listOf(Color(0xFFB57B2C), Color(0xFFFFE7A5), Color(0xFFE7B34C))))
            drawRect(Color(0xFFE3AE4F), Offset(w*.46f,h*.59f), Size(w*.08f,h*.16f))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF9D381), Color(0xFF8C5D30))), Offset(w*.28f,h*.75f), Size(w*.44f,h*.12f), androidx.compose.ui.geometry.CornerRadius(w*.035f))
        }
        Text(emblem, color = Color(0xFF6D4518), fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.offset(y = (-5).dp))
    }
}

@Preview(name = "تالار افتخارات · لنداسکیپ", widthDp = 900, heightDp = 420, locale = "fa", showBackground = true)
@Preview(name = "تالار افتخارات · صفحه کوچک", widthDp = 640, heightDp = 320, locale = "fa", showBackground = true)
@Composable private fun ShowcaseHallPreview() {
    LearnerDialogTheme {
        Box(Modifier.fillMaxSize().background(Color(0xFF10101D))) {
            HallArchitecture(); HallDust()
            ShowcaseHallContent(LearnerShowcase(RankedLearner("demo", "برنامه‌نویس خلاق", 12, 15, 8295.0, "junior", null, null), 72,
                listOf(ShowcaseAvatar("junior", "جونیور", true, null, null), ShowcaseAvatar("wizard", "جادوگر کد", false, null, null), ShowcaseAvatar("full_stack", "فول‌استک", false, null, null)),
                listOf(ShowcaseTrophy("first_code", "اولین قدم برنامه‌نویسی"), ShowcaseTrophy("coding_week", "یک هفته برنامه‌نویسی"), ShowcaseTrophy("coding_month", "یک ماه برنامه‌نویسی")),
                ShowcaseStats(14_400_000, 54_000_000, 10)), Modifier.padding(12.dp))
        }
    }
}
