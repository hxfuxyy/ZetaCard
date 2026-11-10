package io.github.hxfuxyy.zetacard.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiveTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val id = CardIds.fromSource("card")
    private val manifest = BackupManifest.format(listOf(BackupCard(id, "Travel", true)))

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            for ((name, data) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(data)
                out.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }

    @Test
    fun stagesManifestAndArt() {
        val input = zip(BackupManifest.MANIFEST_ENTRY to manifest.toByteArray(), BackupManifest.artEntry(id) to byteArrayOf(1, 2, 3))
        val staged = BackupArchive.stage(input, folder.root) { true }
        assertEquals(listOf(BackupCard(id, "Travel", true)), staged.cards)
        assertEquals(setOf(id), staged.art.keys)
        assertEquals(3L, staged.art.getValue(id).length())
    }

    @Test
    fun smallUnknownEntriesAreIgnored() {
        val input = zip("readme.txt" to "hello".toByteArray(), BackupManifest.MANIFEST_ENTRY to manifest.toByteArray())
        assertTrue(BackupArchive.stage(input, folder.root) { true }.art.isEmpty())
    }

    @Test(expected = InvalidBackupException::class)
    fun oversizedUnknownEntriesAreRejected() {
        val bomb = ByteArray((BackupManifest.MAX_ART_BYTES + 1024 * 1024).toInt())
        BackupArchive.stage(zip("padding.bin" to bomb, BackupManifest.MANIFEST_ENTRY to manifest.toByteArray()), folder.root) { true }
    }

    @Test(expected = InvalidBackupException::class)
    fun oversizedArtIsRejected() {
        val big = ByteArray((BackupManifest.MAX_ART_BYTES + 1).toInt())
        BackupArchive.stage(zip(BackupManifest.MANIFEST_ENTRY to manifest.toByteArray(), BackupManifest.artEntry(id) to big), folder.root) {
            true
        }
    }

    @Test(expected = InvalidBackupException::class)
    fun invalidImagesAreRejected() {
        val input = zip(BackupManifest.MANIFEST_ENTRY to manifest.toByteArray(), BackupManifest.artEntry(id) to byteArrayOf(0))
        BackupArchive.stage(input, folder.root) { false }
    }

    @Test(expected = InvalidBackupException::class)
    fun archivesWithoutManifestAreRejected() {
        BackupArchive.stage(zip(BackupManifest.artEntry(id) to byteArrayOf(1)), folder.root) { true }
    }

    @Test(expected = InvalidBackupException::class)
    fun nonZipFilesAreRejected() {
        BackupArchive.stage(ByteArrayInputStream("not a zip".toByteArray()), folder.root) { true }
    }

    @Test(expected = InvalidBackupException::class)
    fun wrongManifestHeaderIsReportedAsInvalidBackup() {
        val input = zip(BackupManifest.MANIFEST_ENTRY to "something-else\t1\n$id\tTravel\t1\n".toByteArray())
        BackupArchive.stage(input, folder.root) { true }
    }
}
