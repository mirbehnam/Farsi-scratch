import groovy.json.JsonOutput
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale

object EditorAssetPackager {
    val engines = linkedMapOf("scratch" to "build", "turbo" to "turbobuild")
    private val mediaExtensions = setOf(
        "svg", "png", "jpg", "jpeg", "webp", "gif", "bmp", "ico", "avif",
        "wav", "mp3", "ogg", "m4a", "aac", "flac"
    )

    fun selectedEngines(selection: String): List<String> = when (selection) {
        "both" -> engines.keys.toList()
        "scratch", "turbo" -> listOf(selection)
        else -> error("Invalid scratchEngines '$selection'. Use scratch, turbo or both.")
    }

    fun prepare(source: Path, destination: Path, selection: String): Map<String, Any> {
        val input = source.toAbsolutePath().normalize()
        val output = destination.toAbsolutePath().normalize()
        require(!output.startsWith(input) && !input.startsWith(output)) {
            "Editor assets output must be separate from its input: $output"
        }
        val selected = selectedEngines(selection)
        selected.forEach { engine ->
            val entry = input.resolve("${engines.getValue(engine)}/index.html")
            require(Files.isRegularFile(entry)) {
                "Selected editor '$engine' is missing $entry. Copy its complete build into app/src/main/assets."
            }
        }
        val files = Files.walk(input).use { paths ->
            paths.filter { Files.isRegularFile(it) }.map { file ->
                require(!Files.isSymbolicLink(file)) { "Editor assets must not contain symbolic links: $file" }
                input.relativize(file).toString().replace('\\', '/')
            }.sorted().toList()
        }
        require(files.none { it == "editor-assets.json" || it.startsWith("shared/media/") }) {
            "editor-assets.json and shared/media/ are reserved for generated Android assets."
        }
        val selectedRoots = selected.map { engines.getValue(it) }.toSet()
        val included = files.filter { name ->
            val root = name.substringBefore('/')
            root !in engines.values || root in selectedRoots
        }
        val aliases = sortedMapOf<String, String>()
        val sharedSources = sortedMapOf<String, String>()
        if (selected.size > 1) {
            val groups = included.filter { name ->
                name.substringBefore('/') in selectedRoots && extension(name) in mediaExtensions
            }.groupBy { name ->
                val file = input.resolve(name)
                "${hash(file)}.${extension(name)}"
            }
            groups.forEach { (key, names) ->
                if (names.map { it.substringBefore('/') }.distinct().size > 1) {
                    val shared = "shared/media/$key"
                    sharedSources[shared] = names.first()
                    names.forEach { aliases[it] = shared }
                }
            }
        }

        // Only the normalized, disjoint task output is removed; input files are never modified.
        if (Files.exists(output)) {
            require(!Files.isSymbolicLink(output)) { "Generated assets output cannot be a symbolic link: $output" }
            Files.walk(output).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
            }
        }
        Files.createDirectories(output)
        fun copy(from: String, to: String) {
            val target = output.resolve(to)
            Files.createDirectories(target.parent)
            Files.copy(input.resolve(from), target, StandardCopyOption.REPLACE_EXISTING)
        }
        included.filterNot { it in aliases }.forEach { copy(it, it) }
        sharedSources.forEach { (target, original) -> copy(original, target) }
        val manifest = linkedMapOf(
            "version" to 1,
            "engines" to selected.associateWith { "${engines.getValue(it)}/index.html" },
            "shared" to aliases
        )
        val manifestBytes = json(manifest).toByteArray(Charsets.UTF_8)
        Files.write(output.resolve("editor-assets.json"), manifestBytes)
        val inputBytes = included.sumOf { Files.size(input.resolve(it)) }
        val physicalBytes = included.filterNot { it in aliases }.sumOf { Files.size(input.resolve(it)) } +
            sharedSources.values.sumOf { Files.size(input.resolve(it)) } + manifestBytes.size
        return linkedMapOf(
            "selection" to selection,
            "inputFiles" to included.size,
            "outputFiles" to included.size - aliases.size + sharedSources.size + 1,
            "sharedMediaFiles" to sharedSources.size,
            "mappedPaths" to aliases.size,
            "inputBytes" to inputBytes,
            "outputBytes" to physicalBytes,
            "savedBytes" to inputBytes - physicalBytes
        )
    }

    fun json(value: Any): String = JsonOutput.prettyPrint(JsonOutput.toJson(value)) + "\n"

    private fun extension(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    private fun hash(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
