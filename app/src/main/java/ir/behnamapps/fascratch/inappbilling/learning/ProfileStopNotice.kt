package ir.behnamapps.fascratch.inappbilling.learning

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.behnamapps.fascratch.inappbilling.data.ProfileStop

/** Only composed in response to opening the profile, never on application startup. */
@Composable internal fun ProfileStopNotice(stop: ProfileStop, onClose: () -> Unit) {
    val context = LocalContext.current
    var artwork by remember(stop.imageHash) { mutableStateOf(AvatarImages.peek(stop.imageHash)?.asImageBitmap()) }
    LaunchedEffect(stop.imageHash, stop.icon) {
        if (stop.icon == "custom") artwork = AvatarImages.load(context, stop.imageUrl, stop.imageHash)?.asImageBitmap()
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        LearnerDialogWindowBounds()
        Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 620.dp).fillMaxWidth().heightIn(max = 340.dp), shape = RoundedCornerShape(28.dp), color = Color(0xFFFFFCF5)) {
                Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFFFFF6DD), Color(0xFFF2F6FF)))).padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onClose) { Text("بستن ×", color = Color(0xFF344767)) }
                    }
                    Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (stop.icon == "custom" && artwork != null) Image(artwork!!, null, Modifier.heightIn(max = 110.dp).fillMaxWidth(), contentScale = ContentScale.Fit)
                        else if (stop.icon in setOf("update", "maintenance", "notice")) {
                            Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFFFFE6A6)) {
                                Text(when(stop.icon) { "update" -> "↻"; "maintenance" -> "⚙"; else -> "ⓘ" }, Modifier.padding(horizontal = 22.dp, vertical = 4.dp), fontSize = 42.sp, color = Color(0xFF805000))
                            }
                        }
                        Text(stop.message, Modifier.fillMaxWidth(), fontSize = 17.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, color = Color(0xFF263C57))
                    }
                }
            }
        }
    }
}
