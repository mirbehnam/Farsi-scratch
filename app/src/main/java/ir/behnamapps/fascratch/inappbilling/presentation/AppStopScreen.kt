package ir.behnamapps.fascratch.inappbilling.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.data.AppStopRule
import ir.behnamapps.fascratch.inappbilling.data.ClientCommands
import ir.behnamapps.fascratch.inappbilling.learning.LearnerDialogTheme
import kotlinx.coroutines.launch

@Composable internal fun AppStopScreen(rule: AppStopRule) {
    val context=LocalContext.current; val scope=rememberCoroutineScope(); var checking by remember { mutableStateOf(false) }; var note by remember { mutableStateOf("") }
    LearnerDialogTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF171C35),Color(0xFF293757)))).safeDrawingPadding(),contentAlignment=Alignment.Center) {
            Column(Modifier.widthIn(max=620.dp).padding(24.dp).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {
                if(rule.icon!="none") Text(when(rule.icon){"maintenance"->"🛠";"notice"->"📣";else->"🚀"},fontSize=42.sp)
                Text(if(rule.reason=="update") "وقت یک به‌روزرسانی تازه است" else "پیامی از تیم اسکرچ",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold)
                Text(rule.message,color=Color(0xFFE5EFFF),style=MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Button(onClick={
                        val fallback=when(BuildConfig.FLAVOR){"bazaar"->"https://cafebazaar.ir/app/${context.packageName}";"myket"->"https://myket.ir/app/${context.packageName}";else->"https://behnamapp.ir"}
                        try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(rule.updateUrl?:fallback)).addCategory(Intent.CATEGORY_BROWSABLE)) }
                        catch(_:Exception){note="لینک باز نشد؛ از فروشگاه برنامه را به‌روزرسانی کن."}
                    }) { Text("به‌روزرسانی نرم‌افزار") }
                    OutlinedButton(enabled=!checking,onClick={scope.launch { checking=true; try{ClientCommands.refresh(context,true);note="بررسی انجام شد؛ برای رفع محدودیت، اتصال اینترنت و نسخه برنامه را بررسی کن."}finally{checking=false} }}) { Text(if(checking) "در حال بررسی…" else "بررسی مجدد",color=Color.White) }
                }
                if(note.isNotBlank()) Text(note,color=Color(0xFFCEE5EF),style=MaterialTheme.typography.bodySmall)
            }
        }
    } }
}
