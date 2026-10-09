package ir.behnamapps.fascratch.inappbilling.learning

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import ir.behnamapps.fascratch.BuildConfig
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Private, excluded from backup; the grant capability is encrypted with a per-install key. */
internal class LearningStore(context: Context) : SQLiteOpenHelper(context,
    File(context.noBackupFilesDir, "learning-${BuildConfig.FLAVOR}.sqlite").absolutePath, null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE accounts(course TEXT PRIMARY KEY, profile TEXT, grant_data TEXT, celebration INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE events(id TEXT PRIMARY KEY, course TEXT NOT NULL, grant_id TEXT NOT NULL, payload TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'pending', reason TEXT, attempted INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX pending_learning ON events(status,course,grant_id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Existing receipts may have reached the server even without an ACK; freeze them.
        if (oldVersion < 2) db.execSQL("ALTER TABLE events ADD COLUMN attempted INTEGER NOT NULL DEFAULT 1")
    }
    private fun key(): SecretKey {
        val alias = "scratch-learning-${BuildConfig.FLAVOR}"
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (ks.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
    @Synchronized fun profiles(): Map<String, LearnerProfile> = buildMap {
        readableDatabase.rawQuery("SELECT course,profile FROM accounts WHERE profile IS NOT NULL", null).use { cursor ->
            while (cursor.moveToNext()) runCatching { LearnerProfile.parse(JSONObject(cursor.getString(1))) }.getOrNull()?.let { put(cursor.getString(0), it) }
        }
    }
    @Synchronized fun grant(course: String): JSONObject? = readableDatabase.rawQuery("SELECT grant_data FROM accounts WHERE course=?", arrayOf(course)).use {
        if (it.moveToFirst() && !it.isNull(0)) runCatching { JSONObject(decrypt(it.getString(0))) }.getOrNull() else null
    }
    @Synchronized fun saveGrant(course: String, value: JSONObject) {
        writableDatabase.execSQL("INSERT OR IGNORE INTO accounts(course) VALUES(?)", arrayOf(course))
        writableDatabase.update("accounts", ContentValues().apply { put("grant_data", encrypt(value.toString())) }, "course=?", arrayOf(course))
    }
    @Synchronized fun saveProfile(course: String, value: JSONObject) {
        val next = LearnerProfile.parse(value)
        val old = profiles()[course]
        writableDatabase.execSQL("INSERT OR IGNORE INTO accounts(course) VALUES(?)", arrayOf(course))
        writableDatabase.update("accounts", ContentValues().apply {
            put("profile", value.toString())
            if (old != null && old.uuid == next.uuid && next.level > old.level) put("celebration", next.level)
            if (old != null && (old.uuid != next.uuid || next.level < old.level)) put("celebration", 0)
        }, "course=?", arrayOf(course))
        // A learner has one aggregate profile, not a separate level for each course.
        for ((other, profile) in profiles()) if (other != course && profile.uuid == next.uuid)
            writableDatabase.update("accounts", ContentValues().apply { put("profile", value.toString()) }, "course=?", arrayOf(other))
    }
    @Synchronized fun clearCurrentGrants() {
        // Archived capabilities and pending events belong to their original accounts and survive recovery.
        writableDatabase.update("accounts", ContentValues().apply { putNull("grant_data"); putNull("profile"); put("celebration", 0) },
            "course NOT LIKE 'grant:%' AND course<>?", arrayOf("@identity"))
    }
    @Synchronized fun celebration(course: String): Int = readableDatabase.rawQuery("SELECT celebration FROM accounts WHERE course=?", arrayOf(course)).use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }
    @Synchronized fun clearCelebration(course: String) {
        writableDatabase.update("accounts", ContentValues().apply { put("celebration", 0) }, "course=?", arrayOf(course))
    }
    @Synchronized fun add(course: String, grant: String, event: JSONObject) {
        val previous = writableDatabase.rawQuery("SELECT payload FROM events WHERE course=? AND grant_id=? AND status='pending' AND attempted=0 ORDER BY rowid DESC LIMIT 1", arrayOf(course, grant)).use {
            if (it.moveToFirst()) JSONObject(it.getString(0)) else null
        }
        if (previous != null && previous.getString("lesson_uuid") == event.getString("lesson_uuid") &&
            previous.getInt("version") == event.getInt("version") && WatchEventMergePolicy.canMerge(
                previous.getLong("started_ms"), previous.getLong("ended_ms"), event.getLong("started_ms"), event.getLong("ended_ms"),
                previous.getLong("watch_ms"), event.getLong("watch_ms"), previous.getLong("to_ms"), event.getLong("from_ms"))) {
            previous.put("ended_ms", event.getLong("ended_ms")).put("to_ms", event.getLong("to_ms"))
                .put("watch_ms", previous.getLong("watch_ms") + event.getLong("watch_ms"))
            writableDatabase.update("events", ContentValues().apply { put("payload", previous.toString()) }, "id=?", arrayOf(previous.getString("uuid")))
            return
        }
        writableDatabase.insertOrThrow("events", null, ContentValues().apply {
            put("id", event.getString("uuid")); put("course", course); put("grant_id", grant); put("payload", event.toString()); put("attempted", 0)
        })
    }
    @Synchronized fun pending(): List<Pair<String, String>> = readableDatabase.rawQuery(
        "SELECT course,grant_id FROM events WHERE status='pending' GROUP BY course,grant_id ORDER BY MIN(rowid)", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
    }
    @Synchronized fun batch(course: String, grant: String): List<JSONObject> {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val rows = db.rawQuery("SELECT payload FROM events WHERE course=? AND grant_id=? AND status='pending' ORDER BY rowid LIMIT 60", arrayOf(course, grant)).use { cursor ->
                buildList { while (cursor.moveToNext()) add(JSONObject(cursor.getString(0))) }
            }
            // Freeze BEFORE networking: an uncertain HTTP result must retry the exact same body.
            rows.forEach { db.update("events", ContentValues().apply { put("attempted", 1) }, "id=?", arrayOf(it.getString("uuid"))) }
            db.setTransactionSuccessful()
            return rows
        } finally { db.endTransaction() }
    }
    @Synchronized fun acknowledge(id: String, status: String, reason: String?) {
        if (status == "accepted") writableDatabase.delete("events", "id=?", arrayOf(id))
        else if (status == "rejected") writableDatabase.update("events", ContentValues().apply { put("status", status); put("reason", reason) }, "id=?", arrayOf(id))
    }
    @Synchronized fun counts(course: String): Pair<Int, Int> = readableDatabase.rawQuery(
        "SELECT status,COUNT(*) FROM events WHERE course=? GROUP BY status", arrayOf(course)).use { cursor ->
        var pending = 0; var rejected = 0
        while (cursor.moveToNext()) { if (cursor.getString(0) == "pending") pending = cursor.getInt(1) else rejected += cursor.getInt(1) }
        pending to rejected
    }
    @Synchronized fun allCounts(): Pair<Int, Int> = readableDatabase.rawQuery("SELECT status,COUNT(*) FROM events GROUP BY status", null).use { cursor ->
        var pending = 0; var rejected = 0
        while (cursor.moveToNext()) { if (cursor.getString(0) == "pending") pending = cursor.getInt(1) else rejected += cursor.getInt(1) }
        pending to rejected
    }
}
