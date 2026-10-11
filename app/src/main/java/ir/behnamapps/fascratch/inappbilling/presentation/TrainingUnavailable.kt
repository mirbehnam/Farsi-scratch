package ir.behnamapps.fascratch.inappbilling.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun TrainingUnavailable(message: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.widthIn(max = 520.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("آموزش اسکرچ", style = MaterialTheme.typography.titleLarge)
                Text(message, style = MaterialTheme.typography.bodyLarge)
                Text("خریدها، فایل‌ها و پیشرفت شما محفوظ هستند.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onBack) { Text("بازگشت") }
            }
        }
    }
}
