package ir.behnamapps.fascratch.inappbilling.learning

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.*

/** Android scheduler, no Google Play dependency and no relation to the server FFmpeg worker. */
class LearningSyncJob : JobService() {
    private var task: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        task = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val retry = try { LearningRepository.get(this@LearningSyncJob).sync() }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { true }
            jobFinished(params, retry)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { task?.cancel(); return true }
    override fun onDestroy() { task?.cancel(); super.onDestroy() }
}
