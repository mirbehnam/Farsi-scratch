package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.MissionActions
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MissionActionsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun storeLinksFollowFlavorAndDoNotInventAWebsiteMarket() {
        assertEquals("https://cafebazaar.ir/app/ir.example", MissionActions.storeUrl("bazaar", "ir.example"))
        assertEquals("https://myket.ir/app/ir.example", MissionActions.storeUrl("myket", "ir.example"))
        assertNull(MissionActions.storeUrl("website", "ir.example"))
    }

    @Test fun projectEvidenceRequiresPrivateProjectArchiveNotJustAThumbnailOrFilename() {
        val filesDir = temporary.newFolder("files")
        assertFalse(MissionActions.hasSavedProject(filesDir))
        val projects = File(filesDir, "scratch_projects").apply { mkdir() }
        File(projects, "preview.png").writeText("image")
        File(projects, "broken.sb3").writeText("not a project")
        assertFalse(MissionActions.hasSavedProject(filesDir))
        ZipOutputStream(File(projects, "saved.sb3").outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("project.json")); zip.write("{}".toByteArray()); zip.closeEntry()
        }
        assertTrue(MissionActions.hasSavedProject(filesDir))
    }
}
