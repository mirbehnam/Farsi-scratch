package ir.behnamapps.fascratch.inappbilling.learning

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import ir.behnamapps.fascratch.R

private val bundledAchievementHashes = mapOf(
    "first_code" to "7397b99a4a430192503c07fba38d676643f94a4829186718d7c33df1163db0e9",
    "coding_30" to "d113ef37542867d043a4623b9c7cc8aaefbbadef939229f1a468a91515494b7b",
    "watch_10" to "b5944fa030922a320ff3ed3e6d90d51addfe87111f8bb1f8bab519f2d6e1eb2d",
    "watch_60" to "c6b939c5a00f47e3e7af7e2894a152890f79dccd742066fbc7611fc6d76d4bce",
    "daily" to "8c6e5f8106d0fbb3ed9b481fadf35336cfd9515a76cab7a55983c513e11635c5",
    "details" to "b6ddd29cba62d94b4a58cadaaadaa7314f201a35cde9d3a4c94e500a5fa28458",
    "coding_week" to "a28c346d070f3b9d05f36a39543ca32792d10894adcce56e9274a211a6ecb3f5",
    "store_review" to "96d4b83ccf29ea3c65cb77fc58ef1f4b116fbbe50b9586e43fb7a4d7cd7f4461",
    "lessons_10" to "30fae4adc3c314654ff2bdfed27cdbaf504447002dff90dbafbbfa675e2a9ddb",
    "coding_5h" to "b25a8875797c908545aecb1d096387062fc77a19d8831f075fe59a48351d8769",
    "saved_project" to "d1291ed585e339faad254cf1c1b640118b2bdcd88720ffcdbd230631c074ec8b",
    "coding_15h" to "32d5b7679301936ff26bba8b58dc4d5eec19b327f0771fc52eb335299cd7bc50",
    "coding_month" to "50bc0513a4bf0827325aab2abf7b28b32ae152bf07cb33e3be1da9df318620c4",
    "avatars_8" to "de04c49483503a20a771f7e91d48e143bbaa437bfd2ae15890256b4986bbb91d",
    "lessons_17" to "c95e9d1fd8d8f3df7dd9979e58af004202be10805943c8df38446bf921707e6b",
)

/** Bundled WebP is immediate and offline; optional public updates reuse the bounded hash cache. */
@Composable internal fun AchievementTrophy(trophy: ShowcaseTrophy, fallback: @Composable () -> Unit) {
    val local = when (trophy.id) {
        "first_code" -> R.drawable.achievement_first_code
        "coding_30" -> R.drawable.achievement_coding_30
        "watch_10" -> R.drawable.achievement_watch_10
        "watch_60" -> R.drawable.achievement_watch_60
        "daily" -> R.drawable.achievement_daily
        "details" -> R.drawable.achievement_details
        "coding_week" -> R.drawable.achievement_coding_week
        "store_review" -> R.drawable.achievement_store_review
        "lessons_10" -> R.drawable.achievement_lessons_10
        "coding_5h" -> R.drawable.achievement_coding_5h
        "saved_project" -> R.drawable.achievement_saved_project
        "coding_15h" -> R.drawable.achievement_coding_15h
        "coding_month" -> R.drawable.achievement_coding_month
        "avatars_8" -> R.drawable.achievement_avatars_8
        "lessons_17" -> R.drawable.achievement_lessons_17
        else -> 0
    }
    val context = LocalContext.current.applicationContext
    val preview = LocalInspectionMode.current
    var remote by remember(trophy.imageHash) { mutableStateOf(AvatarImages.peek(trophy.imageHash)?.asImageBitmap()) }
    LaunchedEffect(trophy.imageUrl, trophy.imageHash, preview) {
        if (!preview && AvatarImages.validHash(trophy.imageHash) && bundledAchievementHashes[trophy.id] != trophy.imageHash) {
            AvatarImages.load(context, trophy.imageUrl, trophy.imageHash)?.let { remote = it.asImageBitmap() }
        }
    }
    when {
        remote != null -> Image(remote!!, contentDescription = trophy.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        local != 0 -> Image(painterResource(local), contentDescription = trophy.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else -> fallback()
    }
}
