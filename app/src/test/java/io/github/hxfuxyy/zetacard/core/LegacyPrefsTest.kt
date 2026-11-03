package io.github.hxfuxyy.zetacard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPrefsTest {
    private val first = CardIds.fromSource("https://lh3.googleusercontent.com/one")
    private val second = CardIds.fromSource("https://lh3.googleusercontent.com/two")
    private val third = CardIds.fromSource("https://lh3.googleusercontent.com/three")

    @Test
    fun readsIdsAndNamesFromSharedPreferencesXml() {
        val xml = """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <map>
                <boolean name="welcomed" value="true" />
                <string name="name.$first">Tom &amp; Jerry &#8226;&#x2022; 1234</string>
                <set name="ids">
                    <string>$first</string>
                    <string>$second</string>
                    <string>not-a-card</string>
                </set>
                <string name="name.$third">Orphan</string>
            </map>
        """.trimIndent()
        val entries = LegacyPrefs.parse(xml)
        assertEquals(listOf(first, second, third), entries.map { it.id })
        assertEquals("Tom & Jerry •• 1234", entries[0].label)
        assertEquals(CardLabels.DEFAULT, entries[1].label)
        assertEquals("Orphan", entries[2].label)
    }

    @Test
    fun malformedEntitiesDoNotAbortMigration() {
        val xml = "<map><set name=\"ids\"><string>$first</string></set>" +
            "<string name=\"name.$first\">A&#99999999999;B&#x110000;C</string></map>"
        val entries = LegacyPrefs.parse(xml)
        assertEquals(1, entries.size)
        assertEquals("ABC", entries[0].label)
    }

    @Test
    fun emptyOrBrokenInputIsIgnored() {
        assertTrue(LegacyPrefs.parse(null).isEmpty())
        assertTrue(LegacyPrefs.parse("").isEmpty())
        assertTrue(LegacyPrefs.parse("<map><set name=\"ids\"><string>../../etc</string></set></map>").isEmpty())
    }
}
