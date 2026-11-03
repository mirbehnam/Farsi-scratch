package ir.behnamapps.fascratch.editor

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import ir.behnamapps.fascratch.MainActivity
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.CompletableFuture

class PackagedExportBridge(private val activity: MainActivity) {
    private data class Transfer(
        val owner: WebView, val file: File, val mime: String, val expected: Long,
        var output: FileOutputStream?, var received: Long = 0
    )
    private val worker = Executors.newSingleThreadExecutor()
    private val transfers = mutableMapOf<String, Transfer>()
    @Volatile private var stopped = false
    private val directory = File(activity.cacheDir, "packaged_html").apply { mkdirs() }
    private var permissionJob: (() -> Unit)? = null
    private var permissionDenied: (() -> Unit)? = null
    private val permission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        val job = if (allowed) permissionJob else permissionDenied
        permissionJob = null
        permissionDenied = null
        job?.invoke()
    }

    fun attach(webView: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(webView, "AndroidPackagedExport", setOf("https://appassets.androidplatform.net")) {
                view, message, _, isMainFrame, reply ->
            val json = try { JSONObject(message.data ?: "") } catch (error: Exception) {
                Log.e("PackagedExport", "Invalid export message", error)
                return@addWebMessageListener
            }
            val id = json.optString("id")
            if (!isMainFrame || !view.url.orEmpty().startsWith("https://appassets.androidplatform.net/assets/turbobuild/")) {
                respond(reply, id, error = "Untrusted export sender")
                return@addWebMessageListener
            }
            if (json.optString("op") == "save" && Build.VERSION.SDK_INT < 29 &&
                ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                if (permissionJob != null) {
                    respond(reply, id, error = "Permission request already open")
                } else {
                    permissionJob = { submit(view, json, reply) }
                    permissionDenied = { respond(reply, id, error = "Storage permission denied") }
                    permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            } else submit(view, json, reply)
        }
        worker.execute {
            directory.listFiles().orEmpty().filter { it.isDirectory && UUID_PATTERN.matches(it.name) &&
                System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }.forEach(::deleteOwned)
        }
    }

    private fun submit(view: WebView, message: JSONObject, reply: JavaScriptReplyProxy) {
        worker.execute {
            val id = message.optString("id")
            try {
                val result = process(view, message)
                respond(reply, id, result)
            } catch (error: Exception) {
                Log.e("PackagedExport", "Export failed: op=${message.optString("op")} id=$id", error)
                respond(reply, id, error = error.message ?: "Export failed")
            }
        }
    }

    private fun process(view: WebView, message: JSONObject): JSONObject {
        val operation = message.getString("op")
        if (operation == "begin") {
            val name = message.getString("name")
            val mime = message.getString("mime")
            val bytes = message.getLong("bytes")
            val extension = when (mime) { "text/html" -> ".html"; "application/json" -> ".json"; else -> error("Invalid export MIME") }
            require(name.endsWith(extension) && name == File(name).name && !FORBIDDEN.containsMatchIn(name)) { "Invalid export name" }
            require(bytes in 1..1073741824L) { "Invalid export size" }
            transfers.values.forEach { it.output?.close(); deleteOwned(it.file.parentFile!!) }
            transfers.clear()
            val transfer = UUID.randomUUID().toString()
            val folder = File(directory, transfer).apply { check(mkdirs()) { "Cannot create export cache" } }
            val file = File(folder, name)
            transfers[transfer] = Transfer(view, file, mime, bytes, FileOutputStream(file))
            return JSONObject().put("transfer", transfer)
        }
        val key = message.getString("transfer")
        val transfer = transfers[key] ?: error("Export transfer not found")
        require(transfer.owner === view) { "Export belongs to another WebView" }
        if (operation == "cancel") {
            transfer.output?.close()
            deleteOwned(transfer.file.parentFile!!)
            transfers.remove(key)
            return JSONObject()
        }
        if (operation == "append") {
            val encoded = message.getString("data")
            require(encoded.length <= 262144 && transfer.output != null) { "Invalid export chunk" }
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            require(Base64.encodeToString(bytes, Base64.NO_WRAP) == encoded && transfer.received + bytes.size <= transfer.expected) {
                "Export chunk does not match expected data"
            }
            transfer.output!!.write(bytes)
            transfer.received += bytes.size
            return JSONObject()
        }
        require(operation == "save" || operation == "share" || operation == "preview") { "Unknown export operation" }
        require(transfer.received == transfer.expected) { "Incomplete export transfer" }
        transfer.output?.close()
        transfer.output = null
        if (operation == "preview") {
            require(transfer.mime == "text/html") { "Only HTML can be played" }
            val launched = CompletableFuture<Unit>()
            activity.runOnUiThread {
                try {
                    activity.startActivity(Intent(activity, PackagedPlayerActivity::class.java).apply {
                        putExtra("file", transfer.file.absolutePath)
                        putExtra("locale", message.optString("locale"))
                        putExtra("dark", message.optBoolean("dark"))
                    })
                    launched.complete(Unit)
                } catch (error: Exception) { launched.completeExceptionally(error) }
            }
            launched.get()
            transfers.remove(key)
            return JSONObject().put("name", transfer.file.name)
        }
        if (operation == "save") {
            val savedName = saveToDownloads(transfer)
            deleteOwned(transfer.file.parentFile!!)
            transfers.remove(key)
            return JSONObject().put("name", savedName)
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", transfer.file)
        val title = when (message.optString("locale")) { "fa" -> "اشتراک‌گذاری خروجی"; "ar" -> "مشاركة الملف"; else -> "Share export" }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = transfer.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri(transfer.file.name, uri)
        }
        val launched = CompletableFuture<Unit>()
        activity.runOnUiThread {
            try { activity.startActivity(Intent.createChooser(intent, title)); launched.complete(Unit) }
            catch (error: Exception) { launched.completeExceptionally(error) }
        }
        launched.get()
        transfers.remove(key)
        return JSONObject().put("name", transfer.file.name)
    }

    private fun saveToDownloads(transfer: Transfer): String {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = activity.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, transfer.file.name)
                put(MediaStore.Downloads.MIME_TYPE, transfer.mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Cannot create download")
            try {
                val output = resolver.openOutputStream(uri) ?: error("Cannot open download")
                output.use { target -> transfer.file.inputStream().use { it.copyTo(target) } }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else transfer.file.name
                } ?: transfer.file.name
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                throw error
            }
        }
        @Suppress("DEPRECATION")
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
        val stem = transfer.file.nameWithoutExtension
        val extension = transfer.file.extension
        for (index in 0..9999) {
            val name = if (index == 0) transfer.file.name else "$stem ($index).$extension"
            val destination = File(downloads, name)
            if (destination.createNewFile()) {
                try { transfer.file.copyTo(destination, overwrite = true) } catch (error: Exception) { destination.delete(); throw error }
                return name
            }
        }
        error("Too many files with this name")
    }

    private fun respond(reply: JavaScriptReplyProxy, id: String, result: JSONObject = JSONObject(), error: String? = null) {
        result.put("id", id).put("ok", error == null)
        if (error != null) result.put("error", error)
        activity.runOnUiThread {
            try { reply.postMessage(result.toString()) }
            catch (failure: Exception) { Log.w("PackagedExport", "Export reply unavailable: id=$id", failure) }
        }
    }

    private fun deleteOwned(folder: File) {
        require(folder.canonicalFile.parentFile == directory.canonicalFile && UUID_PATTERN.matches(folder.name)) {
            "Invalid export cache cleanup path"
        }
        if (!folder.deleteRecursively()) Log.w("PackagedExport", "Cannot remove export cache: ${folder.name}")
    }

    fun detach(webView: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.removeWebMessageListener(webView, "AndroidPackagedExport")
        }
        if (stopped) return
        worker.execute {
            val owned = transfers.filterValues { it.owner === webView }
            owned.forEach { (key, value) -> value.output?.close(); deleteOwned(value.file.parentFile!!); transfers.remove(key) }
        }
    }

    fun dispose() {
        if (stopped) return
        stopped = true
        worker.execute {
            transfers.values.forEach { it.output?.close(); deleteOwned(it.file.parentFile!!) }
            transfers.clear()
        }
        worker.shutdown()
    }

    companion object {
        private val UUID_PATTERN = Regex("[0-9a-f-]{36}")
        private val FORBIDDEN = Regex("[<>:\"/\\\\|?*\\x00-\\x1f]")
    }
}
