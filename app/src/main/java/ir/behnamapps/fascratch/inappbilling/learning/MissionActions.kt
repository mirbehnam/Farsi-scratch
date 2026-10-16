package ir.behnamapps.fascratch.inappbilling.learning

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.zip.ZipFile

internal object MissionActions {
    fun storeUrl(flavor: String, packageName: String): String? = when (flavor) {
        "bazaar" -> "https://cafebazaar.ir/app/$packageName"
        "myket" -> "https://myket.ir/app/$packageName"
        else -> null
    }

    /** Only explicit market/browser intents; a failed launch never starts the reward timer. */
    fun openStore(context: Context, flavor: String): Boolean {
        val url = storeUrl(flavor, context.packageName) ?: return false
        val marketPackage = if (flavor == "myket") "ir.mservices.market" else "com.farsitel.bazaar"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(Intent(intent).setPackage(marketPackage)); true }.getOrElse {
            runCatching { context.startActivity(intent); true }.getOrDefault(false)
        }
    }

    /** Scan only app-private My Projects, never arbitrary storage or a project upload. Run on IO. */
    fun hasSavedProject(filesDir: File): Boolean = runCatching {
        File(filesDir, "scratch_projects").listFiles().orEmpty().any { file ->
            file.isFile && file.extension.equals("sb3", true) && file.length() > 0 && runCatching {
                ZipFile(file).use { it.getEntry("project.json") != null }
            }.getOrDefault(false)
        }
    }.getOrDefault(false)
}
