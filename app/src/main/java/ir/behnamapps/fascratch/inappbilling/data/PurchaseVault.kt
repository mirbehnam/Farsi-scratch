package ir.behnamapps.fascratch.inappbilling.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.domain.*
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.asStateFlow

/** Keystore-encrypted, atomic, excluded from Android backup. Never stores server API secrets. */
class PurchaseVault(context: Context) : ReceiptStore {
    private val app = context.applicationContext
    private val identity get() = ir.behnamapps.fascratch.inappbilling.learning.LearningIdentity.get(app)
    private val file = AtomicFile(File(context.noBackupFilesDir, "course-purchases-${BuildConfig.FLAVOR}.bin"))
    private val alias = "scratch-courses-${BuildConfig.FLAVOR}"
    private val aad = (context.packageName + ":" + BuildConfig.FLAVOR + ":v1").toByteArray()
    private var values: JSONObject = read()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }

    private fun read(): JSONObject = runCatching {
        val bytes = file.openRead().use { it.readBytesBounded(1024 * 1024) }
        require(bytes.size in 29..(1024 * 1024))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(aad)
        JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    }.getOrElse { JSONObject() } // Reinstall/key loss: recover from the store, never grant access.

    private fun write() {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(aad)
        val bytes = cipher.iv + cipher.doFinal(values.toString().toByteArray(Charsets.UTF_8))
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) } catch (error: Exception) { file.failWrite(output); throw error }
        revision.value += 1
    }

    override fun saveReceipt(courseId: String, receipt: Receipt) = synchronized(lock) {
        values = read()
        updateReceipt(courseId, receipt)
        write()
    }
    private fun updateReceipt(courseId: String, receipt: Receipt): JSONObject {
        val record = values.optJSONObject(courseId) ?: JSONObject().also { values.put(courseId, it) }
        if (record.optString("receipt") != receipt.token) { record.remove("pending"); record.remove("receipt_owner") }
        record.put("sku", receipt.sku).put("receipt", receipt.token)
        receipt.ownerUuid?.let { record.put("receipt_owner", it) }
        return record
    }
    override fun savePendingReceipt(course: Course, provider: String, receipt: Receipt) = savePending(course, provider, receipt, false)
    override fun saveDiscoveredReceipt(course: Course, provider: String, receipt: Receipt) = savePending(course, provider, receipt, true)
    override fun requiresStoreRediscovery(courseId: String): Boolean {
        val owner = identity.profile()?.uuid
        return pending().any { it.courseId == courseId && it.owner != owner }
    }
    private fun savePending(course: Course, provider: String, receipt: Receipt, discovered: Boolean) {
        synchronized(identity) { synchronized(lock) {
            values = read()
            val record = updateReceipt(course.id, receipt)
            val owner = if (discovered) identity.profile()?.uuid
                else receipt.ownerUuid ?: record.optString("receipt_owner").takeIf(String::isNotBlank) ?: identity.profile()?.uuid
            // Rebinding requires a fresh SDK result. The background server route still rejects any existing different owner.
            if (discovered && owner != null && record.optJSONObject("pending")?.optString("owner") != owner) {
                record.remove("pending"); record.put("receipt_owner", owner)
            }
            if (owner != null && record.optJSONObject("pending") == null) record.put("pending", JSONObject()
                .put("provider", provider).put("owner", owner).put("next", System.currentTimeMillis() + 90_000)
                .put("attempt", 0).put("blocked", false))
            // One atomic write for both receipt and retry intent, before the first HTTP request.
            write()
        } }
        scheduleConfirmation()
    }
    override fun receipt(courseId: String): Receipt? = synchronized(lock) { values = read(); values.optJSONObject(courseId)?.let {
        if (it.optString("receipt").isBlank()) null else Receipt(it.getString("sku"), it.getString("receipt"), it.optString("receipt_owner").takeIf(String::isNotBlank))
    } }
    override fun hasPendingReceipt(courseId: String): Boolean = synchronized(lock) {
        values = read(); values.optJSONObject(courseId)?.optJSONObject("pending") != null
    }

    /** Metadata shares the encrypted atomic receipt file; no second token database. */
    internal fun beginConfirmation(course: Course, provider: String, owner: String): PendingPurchase = synchronized(lock) {
        values = read()
        val record = requireNotNull(values.optJSONObject(course.id))
        val old = record.optJSONObject("pending")
        if (old == null) record.put("pending", JSONObject().put("provider", provider).put("owner", owner)
            .put("next", System.currentTimeMillis() + 90_000).put("attempt", 0).put("blocked", false))
        write()
        pendingRecord(course.id, record)!!
    }

    internal fun pending(): List<PendingPurchase> = synchronized(lock) {
        values = read(); values.keys().asSequence().mapNotNull { id -> values.optJSONObject(id)?.let { pendingRecord(id, it) } }.toList()
    }
    private fun pendingRecord(id: String, record: JSONObject): PendingPurchase? {
        val pending = record.optJSONObject("pending") ?: return null
        val token = record.optString("receipt").takeIf(String::isNotBlank) ?: return null
        return PendingPurchase(id, record.optString("sku"), token, pending.optString("provider"), pending.optString("owner"),
            pending.optLong("next"), pending.optInt("attempt"), pending.optBoolean("blocked"))
    }
    internal fun defer(purchase: PendingPurchase, failure: CourseFailure) = synchronized(lock) {
        values = read()
        val record = values.optJSONObject(purchase.courseId) ?: return@synchronized
        if (record.optString("receipt") != purchase.token) return@synchronized
        val pending = record.optJSONObject("pending") ?: return@synchronized
        val retry = PurchaseRetryPolicy.retryable(failure)
        val delay = PurchaseRetryPolicy.delayMillis(purchase.attempt, failure.retryAfterSeconds)
        val now = System.currentTimeMillis()
        pending.put("next", now + minOf(delay, Long.MAX_VALUE - now)).put("attempt", (purchase.attempt + 1).coerceAtMost(1000))
            .put("blocked", !retry).put("code", failure.code.take(80))
        if (retry && (failure.status == 429 || failure.code == "ip_blocked" || failure.retryAfterSeconds != null)) {
            val next = maxOf(values.optJSONObject("@confirmation")?.optLong("next") ?: 0L, pending.getLong("next"))
            values.put("@confirmation", JSONObject().put("next", next))
            values.keys().forEach { id -> values.optJSONObject(id)?.optJSONObject("pending")?.let {
                it.put("next", maxOf(next, it.optLong("next")))
            } }
        }
        if (CoursePolicy.revokesAccess(failure.code)) { record.remove("access"); record.remove("expires"); record.put("verified", false) }
        write()
    }
    internal fun complete(purchase: PendingPurchase, access: CourseAccess) = synchronized(identity) { synchronized(lock) {
        checkAccessOwner(access)
        values = read()
        val record = values.optJSONObject(purchase.courseId) ?: return@synchronized
        if (record.optString("receipt") != purchase.token) return@synchronized
        record.put("access", access.token).put("expires", access.expiresAtMillis).put("verified", true).remove("pending")
        access.ownerUuid?.let { record.put("receipt_owner", it) }
        write()
    } }
    fun scheduleConfirmation() = PurchaseConfirmationJob.schedule(app)
    val changes get() = revision.asStateFlow()
    fun pendingCourseIds(): Set<String> = pending().map { it.courseId }.toSet()
    fun pendingReviewIds(): Set<String> = pending().filter { it.blocked }.map { it.courseId }.toSet()
    internal fun resumeAccount(owner: String) = synchronized(lock) {
        values = read()
        var changed = false
        values.keys().forEach { id -> values.optJSONObject(id)?.optJSONObject("pending")?.let {
            if (it.optString("owner") == owner && it.optBoolean("blocked") && it.optString("code") == "purchase_account_required") {
                it.put("blocked", false); changed = true
            }
        } }
        if (changed) write()
    }
    internal fun cooldownUntil(): Long = synchronized(lock) { values = read(); values.optJSONObject("@confirmation")?.optLong("next") ?: 0L }
    private fun checkAccessOwner(access: CourseAccess) {
        if (access.ownerUuid != null && (identity.requiresRecovery() || identity.profile()?.uuid != access.ownerUuid))
            throw CourseFailure("حساب تغییر کرده؛ برای ادامه دوباره وارد حساب هنگام خرید شو.", 409, "purchase_account_required")
    }
    override fun saveAccess(access: CourseAccess) = synchronized(identity) { synchronized(lock) {
        checkAccessOwner(access)
        values = read()
        val record = values.optJSONObject(access.courseId) ?: JSONObject().also { values.put(access.courseId, it) }
        record.put("access", access.token).put("expires", access.expiresAtMillis).put("verified", true)
        write()
    } }
    fun access(courseId: String): CourseAccess? = synchronized(lock) { values = read(); values.optJSONObject(courseId)?.let {
        if (it.optString("access").isBlank()) null else CourseAccess(courseId, it.getString("access"), it.optLong("expires"))
    } }
    fun wasVerified(courseId: String): Boolean = synchronized(lock) { values = read(); values.optJSONObject(courseId)?.optBoolean("verified") == true }
    fun revoke(courseId: String) = synchronized(lock) {
        values = read()
        values.optJSONObject(courseId)?.apply { remove("access"); put("verified", false); remove("expires") }
        write()
    }
    /** Revoke authorization only. Receipts and downloaded video files are not removed. */
    fun revokeAll() = synchronized(lock) {
        values = read()
        values.keys().forEach { course -> values.optJSONObject(course)?.apply { remove("access"); remove("expires"); put("verified", false) } }
        write()
    }
    companion object {
        private val lock = Any()
        private val revision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    }
}
