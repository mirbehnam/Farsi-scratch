package ir.behnamapps.fascratch

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeScreen(onEnter: () -> Unit, onFollow: () -> Unit, onMyProjects: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "home-motion")

    val floatingOffset by transition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(3000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "floating-logo"
    )

    val glowAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "glow-alpha"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0A1C))
    ) {
        HomeAnimatedBackground()

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 42.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(44.dp)
        ) {
            HomeLogoPanel(
                modifier = Modifier.weight(.9f).fillMaxHeight(),
                floatingOffset = floatingOffset,
                glowAlpha = glowAlpha
            )

            Column(
                modifier = Modifier.weight(1.1f).widthIn(max = 560.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = ScratchBlue.copy(alpha = .13f),
                    border = BorderStroke(1.dp, ScratchBlue.copy(alpha = .35f)),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        "نسخهٔ فارسی اسکرچ",
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                        color = Color(0xFFAED0FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "اسکرچ فارسی",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 44.sp,
                    textAlign = TextAlign.Right,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = Shadow(Color.Black.copy(.28f), offset = Offset(2f, 3f), blurRadius = 10f)
                    )
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "خلاقیت، داستان‌سازی و برنامه‌نویسی به زبان فارسی",
                    color = Color.White.copy(.66f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "ساخته‌شده توسط بهنام تاج‌الدینی",
                    color = Color.White.copy(.42f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(22.dp))

                HomePrimaryButton(onClick = onEnter, glowAlpha = glowAlpha)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HomeSecondaryButton(
                        text = "پروژه‌های من",
                        onClick = onMyProjects,
                        accent = ScratchBlue,
                        icon = HomeActionIcon.Projects,
                        modifier = Modifier.weight(1f)
                    )
                    HomeSecondaryButton(
                        text = "دنبال‌کردن سازنده",
                        onClick = onFollow,
                        accent = ScratchPink,
                        icon = HomeActionIcon.Follow,
                        modifier = Modifier.weight(1f),
                        drawAttention = true
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeLogoPanel(modifier: Modifier, floatingOffset: Float, glowAlpha: Float) {
    Box(
        modifier = Modifier
            .then(modifier)
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(.09f), Color.White.copy(.035f)),
                    start = Offset.Zero,
                    end = Offset(800f, 700f)
                )
            )
            .border(1.dp, Color.White.copy(.12f), RoundedCornerShape(32.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(ScratchBlue.copy(alpha = .28f * glowAlpha), Color.Transparent)
                ),
                radius = size.minDimension * .54f
            )
            drawCircle(ScratchPink.copy(.13f), size.minDimension * .22f, Offset(size.width * .18f, size.height * .18f))
            drawCircle(ScratchOrange.copy(.14f), size.minDimension * .12f, Offset(size.width * .86f, size.height * .82f))
        }
        Image(
            painter = painterResource(R.drawable.ic_scratch_fa),
            contentDescription = "نشان اسکرچ فارسی",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(.72f).offset(y = floatingOffset.dp)
        )
    }
}

@Composable
private fun HomePrimaryButton(onClick: () -> Unit, glowAlpha: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .drawBehind {
                drawRoundRect(
                    color = ScratchOrange.copy(alpha = .19f * glowAlpha),
                    topLeft = Offset(-7f, -7f),
                    size = androidx.compose.ui.geometry.Size(size.width + 14f, size.height + 14f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(30f, 30f)
                )
            }
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFFFC638), Color(0xFFFF9D18))))
            .border(1.dp, Color.White.copy(.38f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HomeIconContainer(background = Color.White.copy(.34f)) {
                HomeActionIcon(HomeActionIcon.Start, Ink)
            }
            Spacer(Modifier.width(12.dp))
            Text("شروع اسکرچ", color = Ink, fontWeight = FontWeight.Black, fontSize = 19.sp)
        }
    }
}

private enum class HomeActionIcon { Start, Projects, Follow }

@Composable
private fun RowScope.HomeSecondaryButton(
    text: String,
    onClick: () -> Unit,
    accent: Color,
    icon: HomeActionIcon,
    modifier: Modifier,
    drawAttention: Boolean = false
) {
    val attention = rememberInfiniteTransition(label = "secondary-attention")
    val buttonOffset by attention.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 4800
            0f at 0
            0f at 3500
            if (drawAttention) -2.5f at 3580 else 0f at 3580
            if (drawAttention) 2.5f at 3660 else 0f at 3660
            if (drawAttention) -1.7f at 3740 else 0f at 3740
            if (drawAttention) 1.2f at 3820 else 0f at 3820
            0f at 3900
        }),
        label = "secondary-button-shake"
    )
    val contentBrush = if (drawAttention) {
        Brush.horizontalGradient(listOf(Color(0xFFB23FE1).copy(.34f), Color(0xFF694DDB).copy(.28f)))
    } else {
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    }

    Surface(
        modifier = modifier
            .height(58.dp)
            .offset(x = buttonOffset.dp)
            .drawBehind {
                if (drawAttention) {
                    drawRoundRect(
                        color = accent.copy(alpha = .13f),
                        topLeft = Offset(-6f, -6f),
                        size = androidx.compose.ui.geometry.Size(size.width + 12f, size.height + 12f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(25f, 25f)
                    )
                }
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(if (drawAttention) .09f else .07f),
        border = BorderStroke(1.dp, if (drawAttention) accent.copy(.58f) else Color.White.copy(.12f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().background(contentBrush).padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            HomeIconContainer(background = accent.copy(if (drawAttention) .28f else .17f)) {
                HomeActionIcon(icon, if (drawAttention) Color.White else accent)
            }
            Spacer(Modifier.width(9.dp))
            Text(text, color = Color.White.copy(.92f), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
private fun HomeIconContainer(
    background: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(background),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun HomeActionIcon(icon: HomeActionIcon, color: Color) {
    Canvas(Modifier.size(21.dp)) {
        val strokeWidth = size.minDimension * .09f
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (icon) {
            HomeActionIcon.Start -> {
                drawCircle(color, radius = size.minDimension * .43f, style = stroke)
                val play = Path().apply {
                    moveTo(size.width * .43f, size.height * .33f)
                    lineTo(size.width * .7f, size.height * .5f)
                    lineTo(size.width * .43f, size.height * .67f)
                    close()
                }
                drawPath(play, color)
            }
            HomeActionIcon.Projects -> {
                val folder = Path().apply {
                    moveTo(size.width * .12f, size.height * .3f)
                    lineTo(size.width * .39f, size.height * .3f)
                    lineTo(size.width * .48f, size.height * .4f)
                    lineTo(size.width * .88f, size.height * .4f)
                    lineTo(size.width * .82f, size.height * .78f)
                    lineTo(size.width * .18f, size.height * .78f)
                    close()
                }
                drawPath(folder, color, style = stroke)
            }
            HomeActionIcon.Follow -> {
                drawCircle(color, radius = size.minDimension * .17f, center = Offset(size.width * .39f, size.height * .34f), style = stroke)
                drawArc(color, 195f, 150f, false, Offset(size.width * .14f, size.height * .45f), androidx.compose.ui.geometry.Size(size.width * .5f, size.height * .42f), style = stroke)
                drawLine(color, Offset(size.width * .71f, size.height * .42f), Offset(size.width * .71f, size.height * .72f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(size.width * .56f, size.height * .57f), Offset(size.width * .86f, size.height * .57f), strokeWidth, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun HomeAnimatedBackground() {
    val transition = rememberInfiniteTransition(label = "nebula")

    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing)),
        label = "time"
    )

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // رسم گوی‌های نوری شناور (Orbs)
        fun drawOrb(color: Color, centerOffset: Offset, radius: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(0.15f), Color.Transparent),
                    center = centerOffset,
                    radius = radius
                ),
                radius = radius,
                center = centerOffset
            )
        }

        // حرکت دایره‌ای گوی‌ها
        drawOrb(
            ScratchPurple,
            Offset(w * 0.2f + cos(time) * 50f, h * 0.3f + sin(time) * 50f),
            w * 0.4f
        )
        drawOrb(
            ScratchBlue,
            Offset(w * 0.8f + sin(time) * 60f, h * 0.7f + cos(time) * 40f),
            w * 0.5f
        )
        drawOrb(
            ScratchPink,
            Offset(w * 0.5f + cos(time * 0.5f) * 80f, h * 0.2f),
            w * 0.3f
        )

        // رسم ستاره‌های چشمک‌زن
        repeat(40) { index ->
            val x = (index * 137.5f) % w
            val y = (index * 91.3f + time * 20f) % h
            val alpha = (sin(time * 2f + index) + 1f) / 2f // چشمک‌زن

            drawCircle(
                color = Color.White.copy(alpha = 0.1f + (alpha * 0.3f)),
                radius = if (index % 5 == 0) 2.5f else 1.2f,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun SocialDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(.6f),
            color = Color(0xFF1A1635),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color.White.copy(0.1f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text("سازنده را دنبال کن", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SocialItem("اینستاگرام", "@behnamtjd", Color(0xFFE1306C), "◎") {
                        onOpen("https://www.instagram.com/behnamtjd")
                    }
                    SocialItem("تلگرام", "@behnamt20", Color(0xFF229ED9), "➤") {
                        onOpen("https://t.me/behnamt20")
                    }
                    SocialItem("پیام‌رسان بله", "@behnamtjd", Color(0xFF31B46D), "ب") {
                        onOpen("https://ble.ir/behnamtjd")
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss) {
                    Text("بستن", color = ScratchOrange, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SocialItem(name: String, id: String, color: Color, icon: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(.05f)),
        border = BorderStroke(1.dp, Color.White.copy(0.05f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("‹", color = Color.White.copy(.3f), fontSize = 24.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(name, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(id, color = Color.White.copy(.5f), fontSize = 12.sp)
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(38.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    Text(icon, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
    }
}
