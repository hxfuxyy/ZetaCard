package io.github.hxfuxyy.zetacard.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

class InvalidBackupException(message: String) : IOException(message)

data class StagedBackup(val cards: List<BackupCard>, val art: Map<String, File>)

object BackupArchive {
    fun stage(input: InputStream, directory: File, isImage: (File) -> Boolean): StagedBackup {
        var manifest: List<BackupCard>? = null
        val staged = LinkedHashMap<String, File>()
        var total = 0L
        var entries = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > BackupManifest.MAX_ENTRIES) throw InvalidBackupException("Too many entries")
                val id = BackupManifest.artId(entry.name)
                total += when {
                    entry.name == BackupManifest.MANIFEST_ENTRY -> {
                        val bytes = ByteArrayOutputStream()
                        val size = copyLimited(zip, bytes, BackupManifest.MAX_MANIFEST_BYTES)
                        manifest = try {
                            BackupManifest.parse(bytes.toString(Charsets.UTF_8.name()))
                        } catch (error: IllegalArgumentException) {
                            throw InvalidBackupException("Not a ZetaCard backup")
                        }
                        size
                    }
                    id != null -> {
                        val target = File(directory, "$id.png")
                        val size = target.outputStream().use { copyLimited(zip, it, BackupManifest.MAX_ART_BYTES) }
                        if (!isImage(target)) throw InvalidBackupException("Backup contains an invalid image")
                        staged[id] = target
                        size
                    }
                    else -> copyLimited(zip, DiscardOutput, BackupManifest.MAX_ART_BYTES)
                }
                if (total > BackupManifest.MAX_TOTAL_BYTES) throw InvalidBackupException("Backup is too large")
                zip.closeEntry()
            }
        }
        val cards = manifest ?: throw InvalidBackupException("Not a ZetaCard backup")
        return StagedBackup(cards, staged)
    }

    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(8192)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            copied += read
            if (copied > limit) throw InvalidBackupException("Backup entry is too large")
            output.write(buffer, 0, read)
        }
        return copied
    }

    private object DiscardOutput : OutputStream() {
        override fun write(b: Int) = Unit

        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }
}
