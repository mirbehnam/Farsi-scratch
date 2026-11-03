package ir.behnamapps.fascratch.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.behnamapps.fascratch.R

@Composable
internal fun EditorEngineDialog(
    availableEngines: List<EditorEngine>,
    onDismiss: () -> Unit,
    onSelect: (EditorEngine) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = FontFamily(Font(R.font.shabnam)))
        ) {
            Surface(
                modifier = Modifier.padding(20.dp).widthIn(max = 500.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp), color = Color(0xFF14233F)
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("انتخاب نسخهٔ اسکرچ", Modifier.weight(1f).padding(top = 10.dp),
                            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onDismiss) { Text("بازگشت", color = Color(0xFFBBC8E5)) }
                    }
                    // Explicit ordering keeps the original first, regardless of manifest order.
                    EditorEngine.entries.filter { it in availableEngines }.forEach { engine ->
                        val turbo = engine == EditorEngine.TURBO
                        Surface(
                            onClick = { onSelect(engine) }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = if (turbo) Color(0xFF233F43) else Color(0xFF1F2F52),
                            border = BorderStroke(if (turbo) 2.dp else 1.dp,
                                if (turbo) Color(0xFF75DEB5) else Color(0xFF4A5D83))
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(if (turbo) "نسخه پرسرعت اسکرچ" else "نسخه اصلی اسکرچ",
                                        modifier = Modifier.weight(1f), color = Color.White,
                                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    if (turbo) Surface(color = Color(0xFF75DEB5), shape = RoundedCornerShape(8.dp)) {
                                        Text("پیشنهادی", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            color = Color(0xFF103C30), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(
                                    if (turbo) "این نسخه بهبود یافته تا سرعت بالایی داشته باشد."
                                    else "نسخهٔ استاندارد اسکرچ برای ساخت بازی و انیمیشن.",
                                    color = Color(0xFFD0DBE9), fontSize = 13.sp, lineHeight = 21.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
