import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files

@CacheableTask
abstract class PrepareEditorAssetsTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Input
    abstract val selection: Property<String>

    @TaskAction
    fun prepare() {
        val report = EditorAssetPackager.prepare(
            inputDirectory.get().asFile.toPath(), outputDirectory.get().asFile.toPath(), selection.get()
        )
        val reportPath = reportFile.get().asFile.toPath()
        Files.createDirectories(reportPath.parent)
        Files.writeString(reportPath, EditorAssetPackager.json(report))
        logger.lifecycle("Android editor assets: ${report["selection"]}, ${report["sharedMediaFiles"]} shared media, " +
            "${report["savedBytes"]} bytes saved. Report: $reportPath")
    }
}
