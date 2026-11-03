package io.github.hxfuxyy.zetacard.root

import io.github.hxfuxyy.zetacard.core.CardIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyListingTest {
    private val first = CardIds.fromSource("one")
    private val second = CardIds.fromSource("two")

    @Test
    fun keepsOnlyValidArtworkFiles() {
        val listing = "$first.png\n$second.png  notes.txt ../evil.png ${first.uppercase()}.png $second.tmp\n$first.png"
        assertEquals(listOf(first, second), LegacyMigration.parseListing(listing))
    }

    @Test
    fun emptyListingMeansNoDesigns() {
        assertTrue(LegacyMigration.parseListing("").isEmpty())
    }
}
