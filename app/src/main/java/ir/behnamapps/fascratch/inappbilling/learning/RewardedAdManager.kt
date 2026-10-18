package ir.behnamapps.fascratch.inappbilling.learning

import android.app.Application
import android.content.Context
import android.os.SystemClock
import com.adivery.sdk.Adivery
import com.adivery.sdk.AdiveryListener
import ir.behnamapps.fascratch.inappbilling.domain.CourseFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One application-scoped listener: reward delivery survives closing the missions dialog. */
internal object RewardedAdManager {
    data class State(val loaded: Boolean = false, val busy: Boolean = false, val message: String? = null)
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var configuredApp: String? = null
    private var placement: String? = null
    private var nextPrepare = 0L
    private data class Showing(val owner: String, val ticket: RewardedAdTicket, val repo: LearningRepository, var shown: Boolean = false)
    private var showing: Showing? = null
    private var registered = false

    fun prepare(context: Context, offer: RewardedAdOffer) {
        if (showing != null || mutable.value.busy) return
        try {
            if (configuredApp != null && configuredApp != offer.appId) {
                mutable.value = State(message = "برای دریافت تنظیمات جدید تبلیغ، برنامه را دوباره باز کن."); return
            }
            if (configuredApp == null) {
                Adivery.configure(context.applicationContext as Application, offer.appId)
                configuredApp = offer.appId
            }
            if (!registered) {
                Adivery.addGlobalListener(object : AdiveryListener() {
                    override fun onRewardedAdLoaded(placementId: String) {
                        if (placementId == placement && showing == null && !mutable.value.busy) mutable.value = State(loaded = true)
                    }
                    override fun onRewardedAdShown(placementId: String) {
                        showing?.takeIf { it.ticket.placement == placementId }?.shown = true
                    }
                    override fun onRewardedAdClosed(placementId: String, isRewarded: Boolean) {
                        val active = showing?.takeIf { it.ticket.placement == placementId } ?: return
                        showing = null
                        scope.launch {
                            try {
                                val earned = RewardedAdCompletion.earned(active.shown, isRewarded)
                                settle(active, earned)
                                mutable.value = State(message = if (earned) "نتیجهٔ تبلیغ ثبت شد؛ جایزه در حال همگام‌سازی است." else "برای دریافت جایزه، تبلیغ باید کامل دیده شود.")
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { mutable.value = State(message = "نتیجه محفوظ است؛ با اتصال اینترنت دوباره بررسی می‌شود.") }
                        }
                    }
                })
                registered = true
            }
            if (placement != offer.placementId) nextPrepare = 0
            placement = offer.placementId
            if (Adivery.isLoaded(offer.placementId)) { mutable.value = State(loaded = true); return }
            if (SystemClock.elapsedRealtime() < nextPrepare) return
            nextPrepare = SystemClock.elapsedRealtime() + 30_000
            mutable.value = State(message = "در حال بررسی موجودی تبلیغ…")
            Adivery.prepareRewardedAd(context, offer.placementId)
            scope.launch {
                delay(15_000)
                if (placement == offer.placementId && !mutable.value.busy && !isLoaded(offer))
                    mutable.value = State(message = "فعلاً ویدیویی در دسترس نیست؛ کمی بعد دوباره بررسی می‌شود.")
            }
        } catch (_: Exception) { mutable.value = State(message = "تبلیغ فعلاً آماده نیست.") }
    }

    fun isLoaded(offer: RewardedAdOffer): Boolean = runCatching { placement == offer.placementId && Adivery.isLoaded(offer.placementId) }.getOrDefault(false)

    private suspend fun settle(active: Showing, earned: Boolean) {
        for (attempt in 0..5) {
            try { active.repo.finishRewardedAd(active.owner, active.ticket.uuid, earned); return }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e is CourseFailure && e.status in setOf(401, 403, 404, 410, 422)) throw e
                if (attempt == 5 || active.repo.profiles.value[LearningIdentity.KEY]?.uuid != active.owner) throw e
                delay(minOf(120_000L, 5_000L shl attempt))
            }
        }
    }

    fun show(owner: String, ticket: RewardedAdTicket, repository: LearningRepository) {
        if (showing != null || mutable.value.busy) return
        mutable.value = State(busy = true)
        scope.launch {
            try {
                repository.startRewardedAd(owner, ticket.uuid)
                check(Adivery.isLoaded(ticket.placement))
                showing = Showing(owner, ticket, repository)
                val attempt = showing
                Adivery.showAd(ticket.placement)
                // SDK 4.9.0 has no onError override on AdiveryListener. Handle silent non-show safely.
                delay(10_000)
                if (showing === attempt && showing?.shown == false) {
                    showing = null
                    runCatching { repository.finishRewardedAd(owner, ticket.uuid, false) }
                    mutable.value = State(message = "تبلیغ نمایش داده نشد؛ دوباره تلاش کن.")
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                showing = null
                runCatching { repository.finishRewardedAd(owner, ticket.uuid, false) }
                mutable.value = State(message = "نمایش تبلیغ شروع نشد؛ سهمیه آزاد شد، دوباره تلاش کن.")
            }
        }
    }
}
