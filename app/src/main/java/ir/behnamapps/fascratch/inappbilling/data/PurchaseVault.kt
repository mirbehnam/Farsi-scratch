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

/** Keystore-encrypted, atomic, excluded from Android backup. Never stores server API secrets. */
class PurchaseVault(context: Context) : ReceiptStore {
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
    }

    @Synchronized override fun saveReceipt(courseId: String, receipt: Receipt) {
        val record = values.optJSONObject(courseId) ?: JSONObject().also { values.put(courseId, it) }
        record.put("sku", receipt.sku).put("receipt", receipt.token)
        write()
    }
    @Synchronized override fun receipt(courseId: String): Receipt? = values.optJSONObject(courseId)?.let {
        if (it.optString("receipt").isBlank()) null else Receipt(it.getString("sku"), it.getString("receipt"))
    }
    @Synchronized override fun saveAccess(access: CourseAccess) {
        val record = values.optJSONObject(access.courseId) ?: JSONObject().also { values.put(access.courseId, it) }
        record.put("access", access.token).put("expires", access.expiresAtMillis).put("verified", true)
        write()
    }
    @Synchronized fun access(courseId: String): CourseAccess? = values.optJSONObject(courseId)?.let {
        if (it.optString("access").isBlank()) null else CourseAccess(courseId, it.getString("access"), it.optLong("expires"))
    }
    @Synchronized fun wasVerified(courseId: String): Boolean = values.optJSONObject(courseId)?.optBoolean("verified") == true
    @Synchronized fun revoke(courseId: String) {
        values.optJSONObject(courseId)?.apply { remove("access"); put("verified", false); remove("expires") }
        write()
    }
}
