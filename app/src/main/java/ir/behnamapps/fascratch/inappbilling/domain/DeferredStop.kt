package ir.behnamapps.fascratch.inappbilling.domain

/** Incoming stops are pending until a new launch; removal can release an existing lock. */
internal class DeferredStop<T> {
    private var initialized = false
    var active: T? = null
        private set
    fun start(cached: T?) { if(!initialized) { initialized=true; active=cached } }
    fun received(next: T?) { if(active!=null) active=next }
}
