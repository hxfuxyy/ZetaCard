package io.github.hxfuxyy.zetacard.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import io.github.hxfuxyy.zetacard.core.BackupArchive
import io.github.hxfuxyy.zetacard.core.BackupCard
import io.github.hxfuxyy.zetacard.core.BackupManifest
import io.github.hxfuxyy.zetacard.core.CardLabels
import io.github.hxfuxyy.zetacard.core.CardRegistry
import io.github.hxfuxyy.zetacard.core.ImageLimits
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class BackupResult(val cards: Int, val designs: Int, val applied: Boolean = true)

object DesignBackup {
    @Synchronized
    fun export(context: Context, uri: Uri): BackupResult {
        val ids = ArtStore.ids(context)
        val cards = ids.map { BackupCard(it, ArtStore.name(context, it), ArtStore.isCaptionEnabled(context, it)) }
        var designs = 0
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Could not open the selected file")
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupManifest.MANIFEST_ENTRY))
            zip.write(BackupManifest.format(cards).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for (id in ids) {
                val art = ArtStore.artFile(context, id)
                if (!art.isFile) continue
                zip.putNextEntry(ZipEntry(BackupManifest.artEntry(id)))
                art.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                designs++
            }
        }
        return BackupResult(cards.size, designs)
    }

    @Synchronized
    fun restore(context: Context, uri: Uri): BackupResult {
        val staging = File(context.cacheDir, "backup-import-${System.nanoTime()}").apply {
            if (!mkdirs()) throw IOException("Could not prepare the import")
        }
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Could not open the selected file")
            val backup = input.use { BackupArchive.stage(it, staging, ::isImage) }
            val known = backup.cards.associateBy { it.id }
            val all = backup.cards + backup.art.keys.filterNot { known.containsKey(it) }.map { BackupCard(it, CardLabels.DEFAULT, false) }
            ArtStore.artDirectory(context).mkdirs()
            for ((id, file) in backup.art) {
                val destination = ArtStore.artFile(context, id)
                if (!file.renameTo(destination)) file.copyTo(destination, overwrite = true)
                destination.setLastModified(System.currentTimeMillis())
            }
            ArtStore.addCards(context, all.map { CardRegistry.Entry(it.id, it.name, "") })
            for (card in all) {
                ArtStore.setNameFromBackup(context, card.id, card.name)
                ArtStore.setCaptionEnabled(context, card.id, card.caption)
            }
            return BackupResult(all.size, backup.art.size)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun isImage(file: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        return ImageLimits.isDecodable(bounds.outWidth, bounds.outHeight)
    }
}
