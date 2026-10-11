package ir.behnamapps.fascratch.home

import ir.behnamapps.fascratch.BuildConfig
import org.junit.Assert.*
import org.junit.Test

class HomeBannerCachePolicyTest {
    private val base = BuildConfig.COURSE_API_BASE.trimEnd('/')
    private val id = "7d6b3b4c-1d11-43fe-bd17-1c80ad234666"
    @Test fun onlyAnonymousPublicUuidMediaPathsAreAccepted() {
        val url = "$base/media/$id"
        assertTrue(HomeBannerCache.allowedMedia(url))
        listOf(url.replace("https:", "http:"), "$url?token=private", "$url#fragment", "$url/extra",
            "$base/media/invalid", "$base/lessons/$id/purchased-video", url.replace("api.behnamapp.ir", "evil.test"),
            url.replace("api.behnamapp.ir", "user@api.behnamapp.ir")).forEach { assertFalse(it, HomeBannerCache.allowedMedia(it)) }
    }
}
