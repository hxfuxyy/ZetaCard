package io.github.hxfuxyy.zetacard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRegistryTest {
    private val first = CardIds.fromSource("https://lh3.googleusercontent.com/one")
    private val second = CardIds.fromSource("https://lh3.googleusercontent.com/two")

    @Test
    fun formatAndParseRoundTrip() {
        val entries = listOf(
            CardRegistry.Entry(first, "Payment card", "https://lh3.googleusercontent.com/one"),
            CardRegistry.Entry(second, "Travel", ""),
        )
        val parsed = CardRegistry.parse(CardRegistry.format(entries))
        assertEquals(2, parsed.size)
        assertEquals(first, parsed[0].id)
        assertEquals("https://lh3.googleusercontent.com/one", parsed[0].stockUrl)
        assertEquals("Travel", parsed[1].label)
        assertEquals("", parsed[1].stockUrl)
    }

    @Test
    fun formattingCannotInjectExtraRowsOrColumns() {
        val text = CardRegistry.format(listOf(CardRegistry.Entry(first, "Evil\n$second\tInjected", "u\trl")))
        val parsed = CardRegistry.parse(text)
        assertEquals(1, parsed.size)
        assertEquals(first, parsed[0].id)
        assertEquals(1, text.count { it == '\n' })
        assertEquals(2, text.count { it == '\t' })
    }

    @Test
    fun parseSkipsInvalidRowsAndFillsDefaults() {
        val text = "not-an-id\tName\turl\n\n$first\n${second.uppercase()}\tUpper\n"
        val parsed = CardRegistry.parse(text)
        assertEquals(1, parsed.size)
        assertEquals(CardLabels.DEFAULT, parsed[0].label)
        assertEquals("", parsed[0].stockUrl)
    }

    @Test
    fun emptyInputProducesNoEntries() {
        assertTrue(CardRegistry.parse(null).isEmpty())
        assertTrue(CardRegistry.parse("").isEmpty())
    }
}
