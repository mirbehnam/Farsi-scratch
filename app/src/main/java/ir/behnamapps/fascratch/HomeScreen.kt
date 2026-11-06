package ir.behnamapps.fascratch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private val HomeFontFamily = FontFamily(Font(R.font.shabnam))

/** نسبت تصویر کاور اصلی: ۱۰۰۰ × ۴۶۰ (عرض ÷ ارتفاع) */
private const val CoverAspectRatio = 1000f / 460f

/* ---------------- پالت اسکرچ تیره: زمینهٔ شبانهٔ آبی + نارنجیِ لوگو + رنگ دسته‌بندی بلاک‌ها ---------------- */
private val NightTop = Color(0xFF0E1A33)
private val NightMid = Color(0xFF14233F)
private val NightBottom = Color(0xFF1A2C50)

private val PanelDark = Color(0xFF1F2F52)      // سطح کارت‌ها
private val PanelBorder = Color(0xFF3B4D78)    // لبهٔ کارت‌ها

private val Amber = Color(0xFFFFAB19)
private val AmberDeep = Color(0xFFFF8C1A)
private val Teal = Color(0xFF9966FF)
private val Sky = Color(0xFF4C97FF)
private val ScratchText = Color(0xFFE8EDFF)    // متن روشن روی زمینهٔ تیره
private val Coral = Color(0xFFFF6680)
private val ScratchGreen = Color(0xFF59C059)
private val DeepInk = Color(0xFF3A2400)

/* ============================================================
 *  سبک پنل تیره
 * ============================================================ */

private fun Modifier.glass(
    shape: Shape,
    tint: Color = Color.White,
    strength: Float = 1f
): Modifier = this
    .clip(shape)
    .background(PanelDark)
    .border(
        width = 2.dp,
        color = if (tint == Color.White) PanelBorder else tint.copy(alpha = .75f),
        shape = shape
    )

/** شکل بلاک اسکرچ: بریدگی بالا، برآمدگی پایین و (برای دکمهٔ شروع) کلاهک گرد مثل بلاک «وقتی پرچم سبز زده شد» */
private class BlockShape(private val hat: Boolean = false, private val radius: Dp = 10.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        val d = with(density) { 4.dp.toPx() }
        val nx = with(density) { 18.dp.toPx() }
        val nw = with(density) { 24.dp.toPx() }
        val top = if (hat) with(density) { 24.dp.toPx() } else 0f
        val w = size.width
        val h = size.height
        val bottom = h - d
        val p = Path().apply {
            if (hat) {
                moveTo(0f, top)
                cubicTo(w * .08f, 0f, w * .40f, -top * .1f, w * .60f, top)
                lineTo(w - r, top)
            } else {
                moveTo(0f, r)
                quadraticBezierTo(0f, 0f, r, 0f)
                lineTo(nx, 0f); lineTo(nx + d, d); lineTo(nx + nw - d, d); lineTo(nx + nw, 0f)
                lineTo(w - r, 0f)
            }
            quadraticBezierTo(w, top, w, top + r)
            lineTo(w, bottom - r)
            quadraticBezierTo(w, bottom, w - r, bottom)
            lineTo(nx + nw, bottom); lineTo(nx + nw - d, h); lineTo(nx + d, h); lineTo(nx, bottom)
            lineTo(r, bottom)
            quadraticBezierTo(0f, bottom, 0f, bottom - r)
            close()
        }
        return Outline.Generic(p)
    }
}

/** کاشی به‌سبک بلاک اسکرچ: رنگ دسته‌بندی، درخشش ملایم بالا و لبهٔ تیره‌تر از همان رنگ */
private fun Modifier.scratchBlock(shape: Shape, color: Color): Modifier {
    val dark = Color(color.red * .76f, color.green * .76f, color.blue * .76f)
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .20f).compositeOver(color), color)))
        .border(2.dp, dark, shape)
}

@Composable
private fun pressScale(interaction: MutableInteractionSource, target: Float = .95f): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) target else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "press-scale"
    )
    return scale
}

/* ============================================================
 *  پیش‌نمایش‌ها
 * ============================================================ */

@Preview(name = "Home • landscape", group = "Home / Full screen", widthDp = 960, heightDp = 480)
@Preview(name = "Home • compact landscape", group = "Home / Full screen", widthDp = 740, heightDp = 320)
@Composable
private fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen({}, {}, {}, {}, {}, {})
    }
}

@Composable
private fun HomePreviewSurface(content: @Composable () -> Unit) {
    MaterialTheme {
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = HomeFontFamily)) {
            Box(Modifier.background(NightMid).padding(16.dp)) { content() }
        }
    }
}

@Preview(name = "Hero cover • 1000x460", group = "Home / Sections", widthDp = 560, heightDp = 280)
@Composable
private fun HomeHeaderPreview() = HomePreviewSurface {
    HomeHeroBanner(
        painter = painterResource(R.drawable.meo_header),
        onOpenCourses = {},
        breath = 1f,
        glowAlpha = .8f,
        modifier = Modifier.fillMaxWidth().aspectRatio(CoverAspectRatio)
    )
}

@Preview(name = "Brand + actions", group = "Home / Sections", widthDp = 420, heightDp = 340)
@Composable
private fun HomeControlsPreview() = HomePreviewSurface {
    Column(Modifier.fillMaxWidth()) {
        HomeBrandHeader(compact = false, veryCompact = false, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        HomeActions(
            onEnter = {}, onMyProjects = {}, onTraining = {},
            glowAlpha = .8f, compact = false, modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(name = "Other apps", group = "Home / Sections", widthDp = 460, heightDp = 140)
@Composable
private fun HomeOtherAppsPreview() = HomePreviewSurface {
    OtherAppsSection(compact = false, onLunaPlayer = {}, onMafiaApp = {})
}

@Preview(name = "Follow footer", group = "Home / Sections", widthDp = 740, heightDp = 92, locale = "fa")
@Composable
private fun HomeFooterPreview() = HomePreviewSurface {
    HomeFollowFooter({}, compact = true, horizontalPadding = 0.dp, modifier = Modifier.fillMaxWidth())
}

@Preview(name = "Level badge • ۱۲", group = "Home / Sections", widthDp = 150, heightDp = 170, locale = "fa")
@Composable
private fun HomeBadgePreview() = HomePreviewSurface {
    ir.behnamapps.fascratch.inappbilling.learning.LearnerLevelBadge(12, Modifier.size(110.dp))
}

/* ============================================================
 *  صفحه اصلی
 *  (در RTL) راست: کاور + اپ‌های دیگر ، چپ: عنوان + دکمه‌ها ، پایین: فوتر
 * ============================================================ */

@Composable
fun HomeScreen(
    onEnter: () -> Unit,
    onFollow: () -> Unit,
    onMyProjects: () -> Unit,
    onTraining: () -> Unit,
    onLunaPlayer: () -> Unit,
    onMafiaApp: () -> Unit
) {
    var showContentStore by remember { mutableStateOf(false) }
    if (showContentStore) ir.behnamapps.fascratch.inappbilling.learning.ContentStoreDialog(onClose = { showContentStore = false })
    val transition = rememberInfiniteTransition(label = "home-motion")

    val glowAlpha by transition.animateFloat(
        initialValue = .35f,
        targetValue = .9f,
        animationSpec = infiniteRepeatable(tween(2400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow-alpha"
    )
    val heroBreath by transition.animateFloat(
        initialValue = .992f,
        targetValue = 1.008f,
        animationSpec = infiniteRepeatable(tween(3600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "hero-breath"
    )

    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = HomeFontFamily)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(NightTop, NightMid, NightBottom)))
        ) {
            HomeAnimatedBackground()

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val availableWidth = this.maxWidth
                val availableHeight = this.maxHeight

                val compactHeight = availableHeight < 400.dp
                val veryCompactHeight = availableHeight < 330.dp
                val showCoverColumn = availableWidth >= 720.dp

                val horizontalPadding = 24.dp
                val verticalPadding = when {
                    veryCompactHeight -> 10.dp
                    compactHeight -> 14.dp
                    else -> 24.dp
                }
                val panelSpacing = 30.dp
                val footerHeight = when {
                    veryCompactHeight -> 46.dp
                    compactHeight -> 52.dp
                    else -> 60.dp
                }

                val rowWidth = availableWidth - horizontalPadding * 2 - panelSpacing
                val controlsWidth = minOf(
                    if (compactHeight) 330.dp else 380.dp,
                    rowWidth * .46f
                )

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
                    horizontalArrangement = Arrangement.spacedBy(panelSpacing, Alignment.CenterHorizontally)
                ) {
                    /* ---------- کاور + اپ‌های دیگر ---------- */
                    if (showCoverColumn) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            val headerPainter = painterResource(R.drawable.meo_header)
                            val intrinsic = headerPainter.intrinsicSize
                            val ratio = if (intrinsic.width > 0f && intrinsic.height > 0f &&
                                intrinsic.width.isFinite() && intrinsic.height.isFinite()
                            ) {
                                intrinsic.width / intrinsic.height
                            } else {
                                CoverAspectRatio
                            }

                            val groupGap = if (compactHeight) 8.dp else 14.dp
                            val appsBlockHeight = if (compactHeight) 86.dp else 100.dp

                            val imageHeightLimit =
                                (maxHeight - groupGap - appsBlockHeight).coerceAtLeast(88.dp)
                            val imageWidth = minOf(maxWidth, imageHeightLimit * ratio)
                            val imageHeight = imageWidth / ratio

                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                ir.behnamapps.fascratch.inappbilling.learning.HomeRankingsSlot(
                                    modifier = Modifier.width(imageWidth).height(imageHeight + groupGap + appsBlockHeight)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(groupGap, Alignment.CenterVertically),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        HomeHeroBanner(
                                            painter = headerPainter,
                                            onOpenCourses = onTraining,
                                            breath = heroBreath,
                                            glowAlpha = glowAlpha,
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
                        }
                    }

                    /* ---------- عنوان + دکمه‌ها ---------- */
                    val controlsModifier = if (showCoverColumn) {
                        Modifier.width(controlsWidth)
                    } else {
                        Modifier
                            .widthIn(max = 620.dp)
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    }

                    Column(
                        modifier = controlsModifier,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        HomeBrandHeader(
                            compact = compactHeight,
                            veryCompact = veryCompactHeight,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(if (compactHeight) 14.dp else 22.dp))

                        HomeActions(
                            onEnter = onEnter,
                            onMyProjects = onMyProjects,
                            onTraining = onTraining,
                            glowAlpha = glowAlpha,
                            compact = compactHeight,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (!showCoverColumn) {
                            Spacer(Modifier.height(if (compactHeight) 12.dp else 18.dp))
                            OtherAppsSection(
                                modifier = Modifier.fillMaxWidth(),
                                compact = compactHeight,
                                onLunaPlayer = onLunaPlayer,
                                onMafiaApp = onMafiaApp
                            )
                        }
                    }
                }

                /* ---------- فوتر ---------- */
                HomeFollowFooter(
                    onClick = onFollow,
                    onStore = { showContentStore = true },
                    compact = compactHeight,
                    horizontalPadding = horizontalPadding,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                        .absolutePadding(left = 136.dp)
                        .padding(bottom = verticalPadding)
                )
                ir.behnamapps.fascratch.inbox.HomeInboxButton(Modifier.align(androidx.compose.ui.AbsoluteAlignment.BottomLeft)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Left + WindowInsetsSides.Bottom))
                    .absolutePadding(left = horizontalPadding,bottom = verticalPadding))
                ir.behnamapps.fascratch.tour.HomeTourButton(Modifier.align(androidx.compose.ui.AbsoluteAlignment.BottomLeft)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Left + WindowInsetsSides.Bottom))
                    .absolutePadding(left = horizontalPadding + 56.dp,bottom = verticalPadding))
            }
        }
    }
}

/* ============================================================
 *  سربرگ برند: نشان سطح + لوگو + عنوان + شعار (بدون کارت)
 * ============================================================ */

@Composable
private fun HomeBrandHeader(
    compact: Boolean,
    veryCompact: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (LocalInspectionMode.current) {
                ir.behnamapps.fascratch.inappbilling.learning.LearnerLevelBadge(
                    12,
                    Modifier.size(if (compact) 60.dp else 70.dp)
                )
            } else {
                ir.behnamapps.fascratch.inappbilling.learning.HomeLearnerBadge()
            }
            Spacer(Modifier.width(10.dp))
            ir.behnamapps.fascratch.home.HomeLogoImage(
                modifier = Modifier
                    .weight(1f)
                    .height(if (compact) 70.dp else 85.dp)
            )
        }

        Spacer(Modifier.height(if (compact) 3.dp else 6.dp))

        Text(
            text = "خلاقیت، داستان‌سازی و برنامه‌نویسی به زبان فارسی",
            modifier = Modifier.fillMaxWidth(),
            color = ScratchText.copy(alpha = .9f),
            fontSize = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Right,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))

        // خط تزئینی ظریف با جهت معکوس
        Box(
            Modifier
                .align(Alignment.End)
                .fillMaxWidth(.5f)
                .height(2.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Amber.copy(alpha = .8f))))
        )
    }
}

/* ============================================================
 *  دکمه‌ها: «شروع» بزرگ (بلاک پرچم سبز) + دو کاشی بلاکی (پروژه‌ها / آموزش)
 * ============================================================ */

@Composable
private fun HomeActions(
    onEnter: () -> Unit,
    onMyProjects: () -> Unit,
    onTraining: () -> Unit,
    glowAlpha: Float,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        KidActionButton(
            text = "شروع اسکرچ",
            onClick = onEnter,
            colors = listOf(Color(0xFFFFC61A), Color(0xFFFFAB19)),
            borderColor = Color(0xFFCF8B17),
            textColor = Color.White,
            glowColor = Color(0xFFFFBF00),
            glowStrength = glowAlpha,
            shimmer = true,
            compact = compact,
            modifier = Modifier.fillMaxWidth(),
            icon = { c -> HomeFlagIcon(c) }
        )

        Spacer(Modifier.height(if (compact) 12.dp else 16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
        ) {
            GlassTile(
                text = "پروژه‌های من",
                onClick = onMyProjects,
                tint = Sky,
                compact = compact,
                modifier = Modifier.weight(1f),
                icon = { c -> HomeProjectsIcon(c, Color.White) }
            )
            GlassTile(
                text = "آموزش گام‌به‌گام",
                onClick = onTraining,
                tint = Teal,
                compact = compact,
                modifier = Modifier.weight(1f),
                icon = { c -> HomeLearnIcon(c, Color.White) }
            )
        }
    }
}

@Composable
private fun KidActionButton(
    text: String,
    onClick: () -> Unit,
    colors: List<Color>,
    borderColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    glowColor: Color = Color.Transparent,
    glowStrength: Float = 0f,
    shimmer: Boolean = false,
    icon: (@Composable (Boolean) -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction, .95f)

    // نوار نوری که هر چند ثانیه یک‌بار از روی دکمه عبور می‌کند
    val sweep = if (shimmer) {
        rememberInfiniteTransition(label = "shimmer").animateFloat(
            initialValue = -.4f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 3800
                    -.4f at 0
                    1.4f at 1100 using FastOutSlowInEasing
                    1.4f at 3800
                }
            ),
            label = "shimmer-x"
        ).value
    } else -1f

    val shape = BlockShape(hat = true, radius = 14.dp)
    val height = if (compact) 80.dp else 100.dp

    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // سایهٔ ضخیم زیر بلاک برای حس سه‌بعدی (مثل بلاک‌های اسکرچ/لگو)
            .drawBehind {
                val outline = shape.createOutline(size, layoutDirection, this)
                if (outline is Outline.Generic) {
                    translate(top = 5.dp.toPx()) {
                        drawPath(outline.path, borderColor)
                    }
                }
            }
            // هالهٔ نرم که دقیقاً شکل بلاک را دنبال می‌کند (نه مستطیل)
            .drawBehind {
                if (glowStrength > 0f) {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    if (outline is Outline.Generic) {
                        repeat(3) { i ->
                            drawPath(
                                path = outline.path,
                                color = glowColor.copy(alpha = glowStrength * (0.16f - i * 0.045f)),
                                style = Stroke(
                                    width = (i + 1) * 7.dp.toPx(),
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
            }
            .clip(shape)
            .background(Brush.verticalGradient(colors))
            .border(2.5.dp, borderColor, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = .36f),
                        .55f to Color.Transparent
                    )
                )
        )
        if (shimmer) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val cx = size.width * sweep
                        val band = size.width * .16f
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = .45f),
                                    Color.Transparent
                                ),
                                start = Offset(cx - band, 0f),
                                end = Offset(cx + band, size.height)
                            )
                        )
                    }
            )
        }
        Row(modifier = Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = LocalTextStyle.current.copy(shadow = Shadow(Color(0xFF8A6A00).copy(alpha = .45f), Offset(0f, 3f), 4f)),
                color = textColor,
                fontWeight = FontWeight.Black,
                fontFamily = HomeFontFamily,
                fontSize = if (compact) 16.sp else 20.sp,
                maxLines = 1
            )
            Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
            icon?.invoke(compact)
        }
    }
}

/** کاشی بلاکی: آیکون در بالا، متن زیر آن */
@Composable
private fun GlassTile(
    text: String,
    onClick: () -> Unit,
    tint: Color,
    compact: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable (Boolean) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction, .95f)
    val shape = BlockShape(radius = 12.dp)

    Column(
        modifier = modifier
            .height(if (compact) 70.dp else 90.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .scratchBlock(shape, tint)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon(compact)
        Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontFamily = HomeFontFamily,
            fontSize = if (compact) 11.5.sp else 13.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* ============================================================
 *  کاور اصلی (۱۰۰۰ × ۴۶۰)
 * ============================================================ */

@Composable
private fun HomeHeroBanner(
    painter: Painter,
    onOpenCourses: () -> Unit,
    breath: Float,
    glowAlpha: Float,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // هاله‌ی گرم و ملایم پشت کاور
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Amber.copy(alpha = .20f * glowAlpha),
                                Teal.copy(alpha = .10f * glowAlpha),
                                Color.Transparent
                            ),
                            center = Offset(size.width * .5f, size.height * .5f),
                            radius = size.maxDimension * .78f
                        ),
                        cornerRadius = CornerRadius(44f, 44f)
                    )
                }
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = breath
                    scaleY = breath
                }
                .clip(shape)
        ) {
            ir.behnamapps.fascratch.home.HomeBannerHeader(
                default = painter,
                modifier = Modifier.fillMaxSize(),
                onOpenCourses = onOpenCourses
            )
            Box(
                Modifier
                    .matchParentSize()
                    .border(
                        2.5.dp,
                        Brush.verticalGradient(
                            listOf(Sky.copy(alpha = .95f), Sky.copy(alpha = .35f))
                        ),
                        shape
                    )
            )
        }
    }
}

/* ============================================================
 *  فوتر
 * ============================================================ */

@Composable
private fun HomeFollowFooter(
    onClick: () -> Unit,
    onStore: () -> Unit = {},
    compact: Boolean,
    horizontalPadding: Dp,
    modifier: Modifier
) {
    val transition = rememberInfiniteTransition(label = "follow-button")
    val heartScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(tween(850, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "heart-pulse"
    )
    val borderAlpha by transition.animateFloat(
        initialValue = .40f,
        targetValue = .85f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "follow-border"
    )

    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction, .95f)
    val pillShape = RoundedCornerShape(50)

    Column(modifier = modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Amber.copy(alpha = .35f), Color.Transparent)
                    )
                )
        )
        Spacer(Modifier.height(if (compact) 6.dp else 9.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ساخته‌شده توسط بهنام تاج‌الدینی",
                modifier = Modifier.weight(1f),
                color = ScratchText,
                fontFamily = HomeFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 12.sp else 14.sp,
                textAlign = TextAlign.Right,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            androidx.compose.material3.TextButton(onClick = onStore) {
                Text("دانلود اسپرایت، پس‌زمینه و صدا", fontFamily = HomeFontFamily, color = Amber,
                    fontWeight = FontWeight.Bold, fontSize = if (compact) 10.sp else 12.sp, maxLines = 1)
            }
            Spacer(Modifier.width(12.dp))

            Row(
                modifier = Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .glass(pillShape, tint = Teal, strength = 1.3f)
                    .border(1.dp, Teal.copy(alpha = borderAlpha), pillShape)
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                    .padding(
                        horizontal = if (compact) 14.dp else 18.dp,
                        vertical = if (compact) 6.dp else 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "دنبال‌کردن سازنده",
                    color = ScratchText,
                    fontFamily = HomeFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 12.sp else 13.5.sp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "♥",
                    color = Coral,
                    fontSize = if (compact) 13.sp else 15.sp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = heartScale
                        scaleY = heartScale
                    }
                )
            }
        }
    }
}

/* ============================================================
 *  اپ‌های دیگر
 * ============================================================ */

private data class OtherAppItem(
    val title: String,
    val subtitle: String,
    val badge: String,
    val iconRes: Int,
    val accent: Color,
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
                accent = ScratchGreen,
                onClick = onLunaPlayer
            ),
            OtherAppItem(
                title = "گرداننده مافیا",
                subtitle = "دستیار گرداننده بازی مافیا",
                badge = "کاربردی",
                iconRes = R.drawable.ic_mafia,
                accent = Coral,
                onClick = onMafiaApp
            )
        )
    }

    val preview = LocalInspectionMode.current
    var currentIndex by remember {
        mutableIntStateOf(if (preview) 0 else (0..1).random())
    }
    LaunchedEffect(currentIndex, preview) {
        if (!preview) {
            delay(3200L)
            currentIndex = (currentIndex + 1) % apps.size
        }
    }

    Column(modifier = modifier) {
        AnimatedContent(
            targetState = currentIndex,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                (fadeIn(tween(340)) + scaleIn(tween(340), initialScale = .96f)) togetherWith
                        (fadeOut(tween(240)) + scaleOut(tween(240), targetScale = 1.02f))
            },
            label = "other-apps"
        ) { index ->
            OtherAppCard(app = apps[index], compact = compact)
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            apps.indices.forEach { i ->
                val selected = i == currentIndex
                val dotWidth by animateDpAsState(
                    targetValue = if (selected) 18.dp else 6.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "dot-width"
                )
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(dotWidth)
                        .clip(CircleShape)
                        .background(if (selected) Amber else ScratchText.copy(alpha = .22f))
                )
            }
        }
    }
}

@Composable
private fun OtherAppCard(app: OtherAppItem, compact: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction, .97f)
    val shape = RoundedCornerShape(if (compact) 18.dp else 22.dp)
    val iconBoxSize = if (compact) 42.dp else 52.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 74.dp else 86.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glass(shape, tint = app.accent, strength = 1f)
            .clickable(interactionSource = interaction, indication = null, onClick = app.onClick)
            .padding(horizontal = if (compact) 10.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(iconBoxSize)
                .clip(RoundedCornerShape(16.dp))
                .background(app.accent.copy(alpha = .18f))
                .border(1.5.dp, app.accent.copy(alpha = .6f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(app.iconRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(if (compact) 28.dp else 34.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.title,
                    color = ScratchText,
                    fontWeight = FontWeight.Black,
                    fontFamily = HomeFontFamily,
                    fontSize = if (compact) 13.sp else 15.sp,
                    maxLines = 1
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = app.badge,
                    color = Color.White,
                    fontFamily = HomeFontFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(app.accent.copy(alpha = .85f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = app.subtitle,
                color = ScratchText.copy(alpha = .75f),
                fontFamily = HomeFontFamily,
                fontSize = if (compact) 10.sp else 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = "‹",
            color = ScratchText.copy(alpha = .5f),
            fontSize = 22.sp
        )
    }
}

/* ============================================================
 *  آیکون‌ها
 * ============================================================ */

/** پرچم سبز اسکرچ: میلهٔ تیره + پرچم موجدار سبز با لبهٔ سبز تیره */
@Composable
private fun HomeFlagIcon(compact: Boolean) {
    Canvas(Modifier.size(if (compact) 34.dp else 42.dp)) {
        val s = size.minDimension
        val flagGreen = Color(0xFF4CBF56)
        val flagDark = Color(0xFF2E8B3A)
        val strokeW = s * .07f

        // میله
        drawLine(
            flagDark,
            Offset(s * .24f, s * .10f),
            Offset(s * .24f, s * .92f),
            s * .10f,
            StrokeCap.Round
        )

        // پرچم موجدار
        val flag = Path().apply {
            moveTo(s * .24f, s * .14f)
            cubicTo(s * .42f, s * .04f, s * .60f, s * .26f, s * .88f, s * .14f)
            lineTo(s * .80f, s * .58f)
            cubicTo(s * .56f, s * .70f, s * .40f, s * .46f, s * .24f, s * .58f)
            close()
        }
        drawPath(flag, flagGreen)
        drawPath(
            flag,
            flagDark,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun HomeProjectsIcon(compact: Boolean, tint: Color) {
    Canvas(Modifier.size(if (compact) 24.dp else 30.dp)) {
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
            tint,
            style = Stroke(
                width = size.minDimension * .09f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/** آیکون آموزش: دایره‌ی دور یک مثلث پخش */
@Composable
private fun HomeLearnIcon(compact: Boolean, tint: Color) {
    Canvas(Modifier.size(if (compact) 24.dp else 30.dp)) {
        drawCircle(
            tint,
            radius = size.minDimension * .44f,
            style = Stroke(width = size.minDimension * .085f)
        )
        val play = Path().apply {
            moveTo(size.width * .42f, size.height * .32f)
            lineTo(size.width * .70f, size.height * .50f)
            lineTo(size.width * .42f, size.height * .68f)
            close()
        }
        drawPath(play, tint)
    }
}

/* ============================================================
 *  پس‌زمینه‌ی آرام (حلقه‌ی بدون پرش؛ ضریب‌های زمان همگی عدد صحیح‌اند)
 * ============================================================ */

@Composable
private fun HomeAnimatedBackground() {
    val transition = rememberInfiniteTransition(label = "nebula")

    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing)),
        label = "time"
    )

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val phase = time / (2 * Math.PI).toFloat() // ۰..۱

        fun orb(color: Color, center: Offset, radius: Float, alpha: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        // نورهای ملایم روی زمینهٔ تیره
        orb(Teal, Offset(w * .16f + cos(time) * w * .04f, h * .30f + sin(time) * h * .06f), w * .36f, .20f)
        orb(Sky, Offset(w * .86f + sin(time) * w * .05f, h * .72f + cos(time) * h * .05f), w * .42f, .18f)
        orb(Amber, Offset(w * .55f + cos(time) * w * .06f, h * .05f), w * .26f, .14f)

        // ستاره‌های کم‌نور
        repeat(34) { index ->
            val x = (index * 137.5f) % w
            val y = (index * 91.3f + phase * h) % h
            val twinkle = (sin(time * 2f + index) + 1f) / 2f
            drawCircle(
                color = Color.White.copy(alpha = .10f + twinkle * .30f),
                radius = if (index % 5 == 0) 2.2f else 1.1f,
                center = Offset(x, y)
            )
        }
    }
}

/* ============================================================
 *  دیالوگ شبکه‌های اجتماعی
 * ============================================================ */

@Composable
fun SocialDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(.6f),
            color = PanelDark,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(2.dp, Sky.copy(alpha = .7f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    "سازنده را دنبال کن",
                    color = ScratchText,
                    fontWeight = FontWeight.Black,
                    fontFamily = HomeFontFamily,
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
                    Text(
                        "بستن",
                        color = Sky,
                        fontWeight = FontWeight.Bold,
                        fontFamily = HomeFontFamily
                    )
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A3B63)),
        border = BorderStroke(1.dp, Sky.copy(alpha = .35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("‹", color = ScratchText.copy(.5f), fontSize = 24.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        name,
                        color = ScratchText,
                        fontWeight = FontWeight.Bold,
                        fontFamily = HomeFontFamily
                    )
                    Text(
                        id,
                        color = ScratchText.copy(.7f),
                        fontSize = 12.sp,
                        fontFamily = HomeFontFamily
                    )
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
