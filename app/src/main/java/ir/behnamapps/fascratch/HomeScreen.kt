package ir.behnamapps.fascratch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private val HomeFontFamily = FontFamily(Font(R.font.shabnam))

@Composable
fun HomeScreen(
    onEnter: () -> Unit,
    onFollow: () -> Unit,
    onMyProjects: () -> Unit,
    onLunaPlayer: () -> Unit,
    onMafiaApp: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "home-motion")

    val glowAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "glow-alpha"
    )

    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = HomeFontFamily)
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0A1C))
    ) {
        HomeAnimatedBackground()

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val availableWidth = this.maxWidth
            val availableHeight = this.maxHeight
            val compactHeight = availableHeight < 400.dp
            val veryCompactHeight = availableHeight < 330.dp
            val compactWidth = availableWidth < 800.dp
            val showLeftColumn = availableWidth >= 720.dp
            val horizontalPadding = 18.dp
            val verticalPadding = when {
                veryCompactHeight -> 8.dp
                compactHeight -> 13.dp
                else -> 28.dp
            }
            val panelSpacing = 20.dp
            val rowWidth = availableWidth - horizontalPadding * 2 - panelSpacing
            val rightColumnWidth = minOf(
                if (compactHeight) 348.dp else 420.dp,
                rowWidth * .55f
            )
            val footerHeight = when {
                veryCompactHeight -> 54.dp
                compactHeight -> 60.dp
                else -> 72.dp
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = verticalPadding,
                        bottom = verticalPadding + footerHeight
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    panelSpacing, Alignment.CenterHorizontally
                )
            ) {
                if (showLeftColumn) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        val headerPainter = painterResource(R.drawable.meo_header)
                        val headerSize = headerPainter.intrinsicSize
                        val imageHeightRatio = if (headerSize.width > 0f && headerSize.height > 0f) {
                            headerSize.height / headerSize.width
                        } else {
                            462f / 1000f
                        }
                        val topGap = when {
                            veryCompactHeight -> 6.dp
                            compactHeight -> 10.dp
                            else -> 14.dp
                        }
                        val cardHeight = if (compactHeight) 76.dp else 88.dp
                        val imageHeightLimit =
                            (maxHeight - topGap - cardHeight - 4.dp).coerceAtLeast(96.dp)
                        val imageWidth = minOf(maxWidth, imageHeightLimit / imageHeightRatio)
                        val imageHeight = imageWidth * imageHeightRatio
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = topGap),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.Start
                        ) {
                            HomeHeaderImage(
                                painter = headerPainter,
                                modifier = Modifier.width(imageWidth).height(imageHeight)
                            )
                            OtherAppsSection(
                                modifier = Modifier.width(imageWidth),
                                compact = compactHeight,
                                onLunaPlayer = onLunaPlayer,
                                onMafiaApp = onMafiaApp
                            )
                        }
                    }
                }

                val contentModifier = if (showLeftColumn) {
                    Modifier
                        .width(rightColumnWidth)
                } else {
                    Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                }
                Column(
                    modifier = contentModifier.offset(
                        y = if (showLeftColumn) {
                            if (compactHeight) (-14).dp else (-20).dp
                        } else 0.dp
                    ),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "اسکرچ فارسی",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = when {
                                veryCompactHeight -> 24.sp
                                compactHeight -> 29.sp
                                else -> 36.sp
                            },
                            textAlign = TextAlign.Right,
                            fontFamily = HomeFontFamily,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = Shadow(
                                    Color.Black.copy(.28f), offset = Offset(2f, 3f), blurRadius = 10f
                                )
                            )
                        )
                        Spacer(Modifier.width(if (compactHeight) 8.dp else 11.dp))
                        Image(
                            painter = painterResource(R.drawable.fascratch),
                            contentDescription = "نشان اسکرچ فارسی",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(
                                when {
                                    veryCompactHeight -> 28.dp
                                    compactHeight -> 34.dp
                                    else -> 42.dp
                                }
                            )
                        )
                    }
                    Spacer(Modifier.height(if (compactHeight) 1.dp else 5.dp))
                    Text(
                        "خلاقیت، داستان‌سازی و برنامه‌نویسی به زبان فارسی",
                        color = Color.White.copy(.68f),
                        fontSize = if (compactHeight) 12.sp else 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(if (compactHeight) 13.dp else 22.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(.86f),
                        horizontalArrangement = Arrangement.spacedBy(if (compactWidth) 9.dp else 12.dp)
                    ) {
                        HomeProjectsButton(
                            onClick = onMyProjects,
                            modifier = Modifier.weight(.45f),
                            compact = compactHeight
                        )
                        HomePrimaryButton(
                            onClick = onEnter,
                            glowAlpha = glowAlpha,
                            modifier = Modifier.weight(.55f),
                            compact = compactHeight
                        )
                    }
                    if (!showLeftColumn) {
                        Spacer(Modifier.height(if (compactHeight) 10.dp else 14.dp))
                        OtherAppsSection(
                            compact = compactHeight,
                            onLunaPlayer = onLunaPlayer,
                            onMafiaApp = onMafiaApp
                        )
                    }
                }
            }
            HomeFollowFooter(
                onClick = onFollow,
                compact = compactHeight,
                horizontalPadding = horizontalPadding,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = verticalPadding)
            )
        }
    }
    }
}

@Composable
private fun HomeFollowFooter(
    onClick: () -> Unit,
    compact: Boolean,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier
) {
    val transition = rememberInfiniteTransition(label = "follow-button")
    val borderAlpha by transition.animateFloat(
        initialValue = .42f,
        targetValue = .78f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "follow-border"
    )
    Column(modifier = modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF7865B0).copy(alpha = .65f))
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFF201D3D), Color(0xFF292347)))
                    )
                    .border(
                        1.dp,
                        Color(0xFFAA98E0).copy(alpha = borderAlpha),
                        RoundedCornerShape(14.dp)
                    )
                    .clickable(onClick = onClick)
                    .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = 8.dp)
            ) {
                Text(
                    "دنبال‌کردن سازنده",
                    color = Color(0xFFF5F0FF),
                    fontFamily = HomeFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 12.sp else 14.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "ساخته‌شده توسط بهنام تاج‌الدینی",
                modifier = Modifier.weight(1f),
                color = Color.White.copy(.78f),
                fontFamily = HomeFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 12.sp else 14.sp,
                textAlign = TextAlign.Right,
                maxLines = 1
            )
        }
    }
}

private data class OtherAppItem(
    val title: String,
    val subtitle: String,
    val badge: String,
    val iconRes: Int,
    val colors: List<Color>,
    val onClick: () -> Unit
)

@Composable
private fun OtherAppsSection(
    modifier: Modifier = Modifier.fillMaxWidth(),
    compact: Boolean,
    onLunaPlayer: () -> Unit,
    onMafiaApp: () -> Unit
) {
    val apps = remember(onLunaPlayer, onMafiaApp) {
        listOf(
            OtherAppItem(
                title = "پلیر لونا",
                subtitle = "پخش موسیقی با نمایش متن",
                badge = "ویژه",
                iconRes = R.drawable.lunamain,
                colors = listOf(Color(0xFF2A2340), Color(0xFF1B1B24)),
                onClick = onLunaPlayer
            ),
            OtherAppItem(
                title = "گرداننده مافیا",
                subtitle = "دستیار گرداننده بازی مافیا",
                badge = "کاربردی",
                iconRes = R.drawable.ic_mafia,
                colors = listOf(Color(0xFF38202A), Color(0xFF1B1B24)),
                onClick = onMafiaApp
            )
        )
    }
    var currentIndex by remember { mutableIntStateOf((0..1).random()) }
    LaunchedEffect(currentIndex) {
        delay(3000L)
        currentIndex = (currentIndex + 1) % apps.size
    }

    AnimatedContent(
        targetState = currentIndex,
        modifier = modifier,
        transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
        label = "other-apps"
    ) { index ->
        OtherAppCard(app = apps[index], compact = compact)
    }
}

@Composable
private fun OtherAppCard(app: OtherAppItem, compact: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 76.dp else 88.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(app.colors.reversed()))
            .border(1.dp, Color.White.copy(.12f), RoundedCornerShape(18.dp))
            .clickable(onClick = app.onClick)
            .padding(horizontal = if (compact) 9.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.badge,
                    color = Color(0xFF9EC9FF),
                    fontSize = 9.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ScratchBlue.copy(.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    app.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 12.sp else 14.sp,
                    maxLines = 1
                )
            }
            Text(
                app.subtitle,
                color = Color.White.copy(.62f),
                fontSize = if (compact) 10.sp else 11.sp,
                textAlign = TextAlign.Right,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(if (compact) 42.dp else 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(.09f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(app.iconRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(if (compact) 32.dp else 38.dp)
            )
        }
    }
}

@Composable
private fun HomeHeaderImage(painter: Painter, modifier: Modifier) {
    Image(
        painter = painter,
        contentDescription = "نمایی از اسکرچ فارسی",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF11173D))
            .border(1.dp, Color.White.copy(.18f), RoundedCornerShape(16.dp))
    )
}

@Composable
private fun HomePrimaryButton(
    onClick: () -> Unit,
    glowAlpha: Float,
    modifier: Modifier,
    compact: Boolean
) {
    Box(
        modifier = modifier
            .height(if (compact) 64.dp else 74.dp)
            .drawBehind {
                drawRoundRect(
                    color = ScratchOrange.copy(alpha = .18f * glowAlpha),
                    topLeft = Offset(-6f, -6f),
                    size = androidx.compose.ui.geometry.Size(size.width + 12f, size.height + 12f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(26f, 26f)
                )
            }
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFFFC44E), Color(0xFFFF9130))))
            .border(1.dp, Color(0xFFFFDB81), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "شروع اسکرچ",
                color = Ink,
                fontWeight = FontWeight.Black,
                fontSize = if (compact) 16.sp else 19.sp,
                maxLines = 1
            )
            Spacer(Modifier.width(if (compact) 9.dp else 12.dp))
            HomeAddIcon(compact)
        }
    }
}

@Composable
private fun HomeProjectsButton(onClick: () -> Unit, modifier: Modifier, compact: Boolean) {
    Box(
        modifier = modifier
            .height(if (compact) 64.dp else 74.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF211C40), Color(0xFF141830))))
            .border(1.dp, Color(0xFF7463AD), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "پروژه‌های من",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = if (compact) 13.sp else 16.sp,
                maxLines = 1
            )
            Spacer(Modifier.width(if (compact) 8.dp else 11.dp))
            HomeProjectsIcon(compact)
        }
    }
}

@Composable
private fun HomeAddIcon(compact: Boolean) {
    Canvas(Modifier.size(if (compact) 36.dp else 44.dp)) {
        val stroke = Stroke(width = size.minDimension * .065f)
        drawCircle(Ink, radius = size.minDimension * .44f, style = stroke)
        val plusStroke = size.minDimension * .075f
        drawLine(
            Ink,
            Offset(size.width * .32f, size.height * .5f),
            Offset(size.width * .68f, size.height * .5f),
            plusStroke,
            StrokeCap.Round
        )
        drawLine(
            Ink,
            Offset(size.width * .5f, size.height * .32f),
            Offset(size.width * .5f, size.height * .68f),
            plusStroke,
            StrokeCap.Round
        )
    }
}

@Composable
private fun HomeProjectsIcon(compact: Boolean) {
    Canvas(Modifier.size(if (compact) 28.dp else 34.dp)) {
        val folder = Path().apply {
            moveTo(size.width * .08f, size.height * .28f)
            lineTo(size.width * .38f, size.height * .28f)
            lineTo(size.width * .48f, size.height * .38f)
            lineTo(size.width * .91f, size.height * .38f)
            lineTo(size.width * .84f, size.height * .79f)
            lineTo(size.width * .15f, size.height * .79f)
            close()
        }
        drawPath(
            folder,
            Color.White,
            style = Stroke(width = size.minDimension * .09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
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
                ), radius = radius, center = centerOffset
            )
        }

        // حرکت دایره‌ای گوی‌ها
        drawOrb(
            ScratchPurple, Offset(w * 0.2f + cos(time) * 50f, h * 0.3f + sin(time) * 50f), w * 0.4f
        )
        drawOrb(
            ScratchBlue, Offset(w * 0.8f + sin(time) * 60f, h * 0.7f + cos(time) * 40f), w * 0.5f
        )
        drawOrb(
            ScratchPink, Offset(w * 0.5f + cos(time * 0.5f) * 80f, h * 0.2f), w * 0.3f
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
        onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(.6f),
            color = Color(0xFF1A1635),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color.White.copy(0.1f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.End
            ) {
                Text(
                    "سازنده را دنبال کن",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp
                )
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(.05f)),
        border = BorderStroke(1.dp, Color.White.copy(0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(icon, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
    }
}
