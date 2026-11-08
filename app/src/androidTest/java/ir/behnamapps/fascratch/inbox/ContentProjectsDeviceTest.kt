package ir.behnamapps.fascratch.inbox

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import ir.behnamapps.fascratch.ProjectRepository
import ir.behnamapps.fascratch.inappbilling.learning.ContentPackageInstaller
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ContentProjectsDeviceTest {
    @Test fun nativeSaveAndStoreImportWithTheSameNamePreserveBothFiles() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(app.cacheDir,"project-race-test-${UUID.randomUUID()}").apply{mkdirs()}
        val context=object:ContextWrapper(app){override fun getFilesDir()=root}
        val repository=ProjectRepository(context)
        val source=File(root,"download.sb3").apply{app.assets.open("turbobuild/packager/example.sb3").use{input->outputStream().use{input.copyTo(it)}}}
        val native=File(root,"native.sb3").apply{source.copyTo(this);appendText("native save")}
        val expected=setOf(ContentPackageInstaller.hash(source),ContentPackageInstaller.hash(native))
        val executor=java.util.concurrent.Executors.newFixedThreadPool(2)
        val ready=java.util.concurrent.CountDownLatch(1)
        try {
            val first=executor.submit<File>{ready.await();repository.importStoreProject(source,"Same name","${UUID.randomUUID()}:${UUID.randomUUID()}")}
            val second=executor.submit<File>{ready.await();repository.commitProject(native,"Same name").projectFile}
            ready.countDown()
            assertNotEquals(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS))
            val projects=repository.listProjects();assertEquals(2,projects.size)
            assertEquals(expected,projects.map{ContentPackageInstaller.hash(it.projectFile)}.toSet())
        }finally{executor.shutdownNow();root.deleteRecursively()}
    }
    @Test fun interruptedProjectInstallRetriesWithoutDuplicatesAndPreservesEditsAfterPackageRemoval() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(app.cacheDir,"project-package-test-${UUID.randomUUID()}").apply{mkdirs()}
        val context=object:ContextWrapper(app){override fun getFilesDir()=root}
        val repository=ProjectRepository(context)
        val installer=ContentPackageInstaller(root)
        val id=UUID.randomUUID().toString();val fileId=UUID.randomUUID().toString()
        val project=File(root,"example.sb3").apply{app.assets.open("turbobuild/packager/example.sb3").use{input->outputStream().use{input.copyTo(it)}}}
        val file=JSONObject().put("uuid",fileId).put("kind","projects").put("name","example.sb3").put("path","files/$fileId.sb3")
            .put("bytes",project.length()).put("sha256",ContentPackageInstaller.hash(project)).put("names",JSONObject().put("fa","پروژهٔ آزمایش"))
        val manifest=JSONObject().put("format",1).put("item_uuid",id).put("files",JSONArray().put(file))
        val zip=File(root,"package.zip")
        ZipOutputStream(zip.outputStream()).use{z->
            z.putNextEntry(ZipEntry("manifest.json"));z.write(manifest.toString().toByteArray());z.closeEntry()
            z.putNextEntry(ZipEntry(file.getString("path")));project.inputStream().use{it.copyTo(z)};z.closeEntry()
        }
        val ticket=JSONObject().put("item_uuid",id).put("download_uuid",UUID.randomUUID().toString()).put("bytes",zip.length())
            .put("sha256",ContentPackageInstaller.hash(zip)).put("manifest",manifest)
        try{
            try{installer.install(zip,ticket,"test","owner",{source,title,origin->repository.importStoreProject(source,title,origin);error("Interrupted")}){true};fail("Must interrupt")}
            catch(expected:IllegalStateException){assertEquals("Interrupted",expected.message)}
            assertFalse(installer.installed(id));assertEquals(1,repository.listProjects().size)
            val editable=repository.listProjects().single().projectFile
            editable.appendText("user edit");val editedHash=ContentPackageInstaller.hash(editable)
            val importer:(File,String,String)->Unit={source,title,origin->repository.importStoreProject(source,title,origin);Unit}
            installer.install(zip,ticket,"test","owner",importer){true}
            installer.install(zip,ticket,"test","owner",importer){true}
            assertTrue(installer.installed(id));assertEquals(1,repository.listProjects().size)
            assertEquals(editedHash,ContentPackageInstaller.hash(editable))
            installer.remove(id)
            assertFalse(installer.installed(id));assertTrue(editable.isFile)
            assertEquals(editedHash,ContentPackageInstaller.hash(editable))
            assertNotNull(repository.openProjectForWeb(editable.name))
        }finally{root.deleteRecursively()}
    }
}
