package ir.behnamapps.fascratch.inappbilling.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import ir.behnamapps.fascratch.inappbilling.domain.*
import ir.behnamapps.fascratch.inappbilling.learning.LearningIdentity
import kotlinx.coroutines.*

// Not a data class: toString must never include the receipt token.
internal class PendingPurchase(val courseId: String, val sku: String, val token: String, val provider: String,
    val owner: String, val next: Long, val attempt: Int, val blocked: Boolean)

/** Uses the same Android scheduler as learning sync; survives reboot and needs no store SDK/UI. */
class PurchaseConfirmationJob : JobService() {
    private var task: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        task = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val retry = try {
                val identity = LearningIdentity.get(this@PurchaseConfirmationJob)
                val vault = PurchaseVault(this@PurchaseConfirmationJob)
                val api = CourseApi(this@PurchaseConfirmationJob)
                // Sequential and bounded: no simultaneous purchase verification or request burst.
                for (purchase in vault.pending().filter { eligible(it, identity) }.take(3)) {
                    if (purchase.next > System.currentTimeMillis()) continue
                    try { api.confirmPending(purchase) }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { /* The API atomically retains/defer the receipt. */ }
                }
                vault.pending().any { eligible(it, identity) }
            } catch (error: CancellationException) { throw error }
              catch (_: Exception) { true }
            jobFinished(params, retry)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { task?.cancel(); return true }
    override fun onDestroy() { task?.cancel(); super.onDestroy() }

    companion object {
        private const val JOB_ID = 73105
        private fun eligible(purchase: PendingPurchase, identity: LearningIdentity) = !purchase.blocked &&
            PurchaseRetryPolicy.sameOwner(purchase.owner, identity.profile()?.uuid, identity.requiresRecovery())

        fun schedule(context: Context) {
            runCatching {
                val app = context.applicationContext
                val identity = LearningIdentity.get(app)
                val next = PurchaseVault(app).pending().filter { eligible(it, identity) }.minOfOrNull { it.next } ?: return
                val scheduler = app.getSystemService(JobScheduler::class.java)
                if (scheduler.getPendingJob(JOB_ID) != null) return
                scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(app, PurchaseConfirmationJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
                    .setMinimumLatency((next - System.currentTimeMillis()).coerceAtLeast(1_000))
                    .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build())
            } // Scheduling failures leave the receipt intact; next app start/identity refresh retries scheduling.
        }
    }
}
