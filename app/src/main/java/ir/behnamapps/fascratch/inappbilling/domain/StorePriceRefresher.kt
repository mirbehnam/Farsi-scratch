package ir.behnamapps.fascratch.inappbilling.domain

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Main-thread coordinator: queue refreshes instead of dropping them or cancelling SDK inventory. */
class StorePriceRefresher(
    private val scope: CoroutineScope, private val billing: BillingGateway, private val mutex: Mutex,
    private val onLoading: (Course) -> Unit, private val onResult: (Course, String?) -> Unit
) {
    private data class Query(val course: Course, val revision: Long)
    private val pending = linkedMapOf<String, Query>()
    private val revisions = mutableMapOf<String, Long>()
    private var worker: Job? = null

    fun refresh(courses: List<Course>) {
        courses.forEach { course ->
            val revision = (revisions[course.id] ?: 0) + 1
            revisions[course.id] = revision
            pending[course.id] = Query(course, revision)
            onLoading(course)
        }
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (pending.isNotEmpty()) {
                val query = pending.remove(pending.keys.first()) ?: continue
                val price = mutex.withLock {
                    try {
                        // Rebind the store connection before reading current details, under the same SDK lock.
                        billing.close()
                        withTimeout(20_000) { billing.price(query.course.sku) }?.takeIf { it.isNotBlank() }
                    }
                    catch (_: TimeoutCancellationException) { runCatching { billing.close() }; null }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { null }
                }
                // A result from before a fresh request must never overwrite the new loading/result state.
                if (revisions[query.course.id] == query.revision) onResult(query.course, price)
            }
        }
    }

    suspend fun awaitIdle() { worker?.join() }
}
