package ir.behnamapps.fascratch.inbox

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import ir.behnamapps.fascratch.BuildConfig
import java.io.File
import java.io.InputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** All files are installation-local and excluded from OS backup. Call only on Dispatchers.IO. */
internal class InboxDisk(context: Context) {
    private val root = File(context.noBackupFilesDir, "scratch-inbox-${BuildConfig.FLAVOR}")
    private val alias = "scratch-inbox-v1-${BuildConfig.FLAVOR}"
    private val aad = (context.packageName + ":inbox:v1:" + BuildConfig.FLAVOR).toByteArray()
    private fun file(name: String): File {
        require(name.matches(Regex("[a-zA-Z0-9_.-]{1,200}")))
        check(root.isDirectory || root.mkdirs())
        return File(root,name)
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    fun read(name: String, encrypted: Boolean, max: Int): ByteArray? = runCatching {
        val bytes = AtomicFile(file(name)).openRead().use { bounded(it,max + if(encrypted) 28 else 0) }
        if (!encrypted) bytes else {
            require(bytes.size >= 28)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12))); updateAAD(aad + name.toByteArray())
                doFinal(bytes.copyOfRange(12,bytes.size)).also { require(it.size <= max) }
            }
        }
    }.getOrNull()
    fun write(name: String, bytes: ByteArray, encrypted: Boolean) {
        val data = if(!encrypted) bytes else Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE,key()); updateAAD(aad + name.toByteArray()); iv + doFinal(bytes)
        }
        val atomic = AtomicFile(file(name)); val output = atomic.startWrite()
        try { output.write(data); atomic.finishWrite(output) } catch(error: Exception) { atomic.failWrite(output); throw error }
    }
    fun delete(name: String) { AtomicFile(file(name)).delete() }
    fun clearPrivate(keepSession: String? = null) {
        root.listFiles()?.filter { it.isFile && (it.name.startsWith("personal-") || it.name.startsWith("private-image-")) &&
            (keepSession == null || !it.name.contains(keepSession)) }?.forEach { AtomicFile(it).delete() }
    }
    fun pruneImages(keep: Set<String>) {
        var total = 0L
        root.listFiles()?.filter { it.isFile && (it.name.startsWith("public-image-") || it.name.startsWith("private-image-")) }
            ?.sortedByDescending { it.lastModified() }?.forEach { item ->
                if (item.name !in keep || total + item.length() > 32L*1024*1024) AtomicFile(item).delete()
                else total += item.length()
            }
        // Only encrypted read ids are retained across logins; bound even multi-account test devices.
        root.listFiles()?.filter { it.isFile && it.name.startsWith("reads-") }?.sortedByDescending { it.lastModified() }?.drop(20)?.forEach { AtomicFile(it).delete() }
    }
    companion object {
        fun bounded(input: InputStream, max: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while(true) { val count = input.read(buffer); if(count < 0) break; require(out.size() + count <= max); out.write(buffer,0,count) }
            return out.toByteArray()
        }
    }
}
