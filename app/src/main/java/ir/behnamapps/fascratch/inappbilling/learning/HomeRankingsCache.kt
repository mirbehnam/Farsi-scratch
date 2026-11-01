package ir.behnamapps.fascratch.inappbilling.learning

import android.content.Context
import android.util.AtomicFile
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Only public leaderboard cards. No account secret, purchase or viewer data is persisted. */
internal object HomeRankingsCache {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    @Volatile private var cards: List<RankedLearner> = emptyList()
    @Volatile private var policy = Policy()
    private data class Policy(val enabled: Boolean = true, val probability: Double = 100.0 / 3.0, val count: Int = 15)
    private var warmed = false
    private var lastAttempt = -HomeRankingPolicy.MIN_REFRESH_MS
    private val revision = MutableStateFlow(0)
    val artworkRevision = revision.asStateFlow()
    fun peek(): List<RankedLearner> = cards
    fun selection(draw: Double): List<RankedLearner> {
        val current = policy
        val snapshot = cards.take(current.count)
        return if (HomeRankingPolicy.show(snapshot.isNotEmpty(), current.enabled, current.probability, draw)) snapshot else emptyList()
    }
    private fun policy(json: JSONObject): Policy {
        val settings = json.optJSONObject("settings") ?: return Policy()
        val probability = settings.optDouble("probability_percent", 100.0 / 3.0)
        val count = settings.optInt("rankings_count", 15)
        require(probability.isFinite() && probability in 0.0..100.0 && count in 1..HomeRankingPolicy.MAX_PLAYERS)
        return Policy(settings.optBoolean("rankings_enabled", true), probability, count)
    }
    private fun file(context: Context) = AtomicFile(File(context.applicationContext.filesDir, "home-top-rankings.json"))

    @Synchronized fun warm(context: Context) {
        if (warmed) return
        warmed = true
        scope.launch {
            lock.withLock {
                cards = runCatching {
                    file(context).openRead().use { input ->
                        val bytes = input.readBytesBounded()
                        val json = JSONObject(String(bytes, Charsets.UTF_8))
                        require(json.optInt("version") == 1)
                        val parsed = parse(json)
                        policy = policy(json)
                        parsed
                    }
                }.getOrDefault(emptyList())
            }
        }
    }

    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) { val n = read(buffer); if (n < 0) break; require(out.size() + n <= 128 * 1024); out.write(buffer, 0, n) }
        return out.toByteArray()
    }

    private fun parse(json: JSONObject): List<RankedLearner> {
        val top = json.getJSONArray("top")
        require(top.length() <= HomeRankingPolicy.MAX_PLAYERS)
        val result = (0 until top.length()).map { index ->
            RankedLearner.parse(top.getJSONObject(index)).also {
                require(it.uuid.matches(Regex("[a-fA-F0-9-]{36}")) && it.rank == index + 1 && it.name.isNotBlank())
            }
        }
        require(result.distinctBy { it.uuid }.size == result.size)
        return result
    }

    suspend fun refresh(context: Context, repository: LearningRepository, uuid: String) = withContext(Dispatchers.IO) {
        val fresh = lock.withLock {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAttempt < HomeRankingPolicy.MIN_REFRESH_MS) return@withContext
            lastAttempt = now
            try {
                val response = repository.homeLeaderboard(uuid)
                val rows = parse(response)
                val nextPolicy = policy(response)
                val safe = JSONObject().put("version", 1).put("settings", JSONObject()
                    .put("rankings_enabled", nextPolicy.enabled).put("probability_percent", nextPolicy.probability)
                    .put("rankings_count", nextPolicy.count)).put("top", JSONArray().apply { rows.forEach { row ->
                    put(JSONObject().put("uuid", row.uuid).put("display_name", row.name).put("rank", row.rank)
                        .put("level", row.level).put("xp", row.xp).put("avatar_id", row.avatarId)
                        .put("image_url", row.imageUrl ?: JSONObject.NULL).put("image_sha256", row.imageHash ?: JSONObject.NULL))
                } })
                val target = file(context)
                val output = target.startWrite()
                try { output.write(safe.toString().toByteArray(Charsets.UTF_8)); target.finishWrite(output) }
                catch (error: Exception) { target.failWrite(output); throw error }
                cards = rows
                policy = nextPolicy
                rows
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { return@withContext } // Last good snapshot survives failures.
        }
        fresh.forEach { row ->
            AvatarImages.load(context, row.imageUrl, row.imageHash)
            revision.value += 1
        }
    }
}
