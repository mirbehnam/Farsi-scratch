package ir.behnamapps.fascratch.inappbilling.domain

import kotlinx.coroutines.*

/** Main-thread coordinator for server prices; never calls a store SDK. */
class CoursePriceRefresher(
    private val scope: CoroutineScope, private val fetch: suspend (Course) -> String?,
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
                val price = run {
                    try {
                        withTimeout(20_000) { fetch(query.course) }?.takeIf { it.isNotBlank() }
                    }
                    catch (_: TimeoutCancellationException) { null }
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
