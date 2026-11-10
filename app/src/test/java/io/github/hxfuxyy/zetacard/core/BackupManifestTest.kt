package io.github.hxfuxyy.zetacard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupManifestTest {
    private val first = CardIds.fromSource("one")
    private val second = CardIds.fromSource("two")

    @Test
    fun roundTripKeepsNamesAndCaptions() {
        val cards = listOf(BackupCard(first, "Travel", true), BackupCard(second, "Payment card", false))
        assertEquals(cards, BackupManifest.parse(BackupManifest.format(cards)))
    }

    @Test
    fun namesCannotBreakTheFormat() {
        val text = BackupManifest.format(listOf(BackupCard(first, "Evil\n$second\t1", true)))
        val parsed = BackupManifest.parse(text)
        assertEquals(1, parsed.size)
        assertEquals(first, parsed[0].id)
    }

    @Test
    fun invalidRowsAreSkippedAndDefaultsApplied() {
        val text = "${BackupManifest.FORMAT_LINE}\nnot-an-id\tName\t1\n$first\t\t\n$first\tDuplicate\t1\n"
        val parsed = BackupManifest.parse(text)
        assertEquals(listOf(BackupCard(first, CardLabels.DEFAULT, false)), parsed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun otherFilesAreRejected() {
        BackupManifest.parse("$first\tName\t1\n")
    }

    @Test
    fun artEntriesAreStrict() {
        assertEquals(first, BackupManifest.artId(BackupManifest.artEntry(first)))
        assertNull(BackupManifest.artId("art/../$first.png"))
        assertNull(BackupManifest.artId("../art/$first.png"))
        assertNull(BackupManifest.artId("art/$first.jpg"))
        assertNull(BackupManifest.artId("art/${first.uppercase()}.png"))
        assertNull(BackupManifest.artId("art/sub/$first.png"))
        assertNull(BackupManifest.artId("/art/$first.png"))
    }
}
