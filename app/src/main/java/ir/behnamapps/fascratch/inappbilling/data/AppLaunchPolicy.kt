package ir.behnamapps.fascratch.inappbilling.data

import android.content.Context
import androidx.lifecycle.ViewModel
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inbox.InboxPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

internal data class AppStopRule(val message: String, val reason: String, val icon: String, val updateUrl: String?)
/** One root-Activity launch, retained through configuration changes. No Context retained. */
internal class AppLaunchPolicy : ViewModel() {
    private var started = false
    private val latch = ir.behnamapps.fascratch.inappbilling.domain.DeferredStop<AppStopRule>()
    private val mutable = MutableStateFlow<AppStopRule?>(null)
    val state = mutable.asStateFlow()
    fun start(context: Context) {
        if(started) return
        started = true
        val cached = context.getSharedPreferences("scratch-client-commands",Context.MODE_PRIVATE).getString("config",null)
        latch.start(runCatching { cached?.let { select(JSONObject(it),BuildConfig.FLAVOR,BuildConfig.VERSION_CODE) } }.getOrNull())
        mutable.value = latch.active
    }
    /** A newly received stop NEVER interrupts this launch; a valid removal can unlock it. */
    fun received(config: JSONObject) {
        latch.received(select(config,BuildConfig.FLAVOR,BuildConfig.VERSION_CODE)); mutable.value = latch.active
    }
    companion object {
        fun select(config: JSONObject, flavor: String, version: Int): AppStopRule? {
            if(config.optInt("schema") != 1) return null
            val rules = config.optJSONArray("app_rules") ?: return null
            require(rules.length()<=30)
            var selected: AppStopRule? = null
            for(i in 0 until rules.length()) {
                val row=rules.getJSONObject(i)
                val store=row.getString("store"); val min=row.getInt("min_version"); val max=row.getInt("max_version")
                val message=row.getString("message"); val enabled=row.getBoolean("enabled")
                val reason=row.getString("reason"); val icon=row.getString("icon")
                val url=row.optString("update_url").takeUnless { it.isBlank() || it=="null" }
                require(store in setOf("all","bazaar","myket","website") && min>=1 && max>=min && message.isNotBlank() && message.length<=1000)
                require(reason in setOf("update","maintenance","notice") && icon in setOf("update","maintenance","notice","none"))
                require(url==null || InboxPolicy.safeLink(url))
                if(selected==null && enabled && (store=="all"||store==flavor) && version in min..max) selected=AppStopRule(message,reason,icon,url)
            }
            return selected
        }
    }
}
