package ir.behnamapps.fascratch.inappbilling.learning

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.behnamapps.fascratch.R

internal data class StoreCategory(val kind: String, val title: String, val hint: String, val icon: Int, val color: Color)
internal val storeCategories = listOf(
    StoreCategory("sprites","دانلود اسپرایت","شخصیت‌های تازه برای ایده‌هات",R.drawable.store_category_sprites,Color(0xFFFFE8CF)),
    StoreCategory("backdrops","دانلود پس‌زمینه","دنیای پروژه‌ات را بساز",R.drawable.store_category_backdrops,Color(0xFFDDF4E7)),
    StoreCategory("sounds","دانلود آهنگ","به ساخته‌هات صدا بده",R.drawable.store_category_sounds,Color(0xFFECE2FF)),
    StoreCategory("projects","دانلود پروژهٔ کامل","ببین، تغییر بده، یاد بگیر",R.drawable.store_category_projects,Color(0xFFDDEFFF))
)

@Composable internal fun ContentStoreEntryDialog(onClose: () -> Unit) {
    var category by remember { mutableStateOf<String?>(null) }
    val selected = category
    if (selected == null) ContentCategoryDialog(onClose) { category = it }
    else key(selected) { ContentStoreDialog(onClose = { category = null }, category = selected) }
}

@Composable internal fun ContentCategoryDialog(onClose: () -> Unit, onSelect: (String) -> Unit) {
    LearnerDialogTheme {
        Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=true)) {
            LearnerDialogWindowBounds()
            Box(Modifier.learnerSafeDialogBounds(),contentAlignment=Alignment.Center) {
                Surface(Modifier.learnerDialogPanelBounds(),shape=RoundedCornerShape(26.dp),color=Color(0xFFFFFBF4)) {
                    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("برای ایدهٔ بعدی‌ات چی لازم داری؟",fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                                Text("یک بخش را انتخاب کن و شروع به ساختن کن ✨",style=MaterialTheme.typography.bodySmall,color=Color(0xFF667B7A))
                            }
                            TextButton(onClick=onClose) { Text("بستن ×") }
                        }
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            val columns=if(maxWidth>=720.dp || (maxWidth>=560.dp && maxWidth>maxHeight*1.5f)) 4 else 2
                            val rows=4/columns
                            val cardHeight=((maxHeight-12.dp*(rows-1))/rows).coerceIn(120.dp,220.dp)
                            val compact=cardHeight<190.dp || LocalDensity.current.fontScale>1.3f
                            LazyVerticalGrid(columns=GridCells.Fixed(columns),
                                horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                items(storeCategories,key={it.kind}) { item ->
                                    Surface(onClick={onSelect(item.kind)},shape=RoundedCornerShape(22.dp),color=item.color) {
                                        Column(Modifier.fillMaxWidth().height(cardHeight).padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp,Alignment.CenterVertically)) {
                                            Image(painterResource(item.icon),null,Modifier.fillMaxWidth().height((cardHeight*.45f).coerceIn(48.dp,100.dp)))
                                            Text(item.title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                                            if(!compact) Text(item.hint,style=MaterialTheme.typography.labelSmall,color=Color(0xFF556C74),maxLines=2,overflow=TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
