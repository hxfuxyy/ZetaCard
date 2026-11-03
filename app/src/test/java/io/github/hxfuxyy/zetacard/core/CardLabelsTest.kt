package io.github.hxfuxyy.zetacard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardLabelsTest {
    @Test
    fun importedLabelsFallBackWhenUnsafe() {
        assertEquals(CardLabels.DEFAULT, CardLabels.forImport(""))
        assertEquals(CardLabels.DEFAULT, CardLabels.forImport("   "))
        assertEquals(CardLabels.DEFAULT, CardLabels.forImport("Card 4111111111111111"))
        assertEquals(CardLabels.DEFAULT, CardLabels.forImport("x".repeat(61)))
        assertEquals("Visa •• 1234", CardLabels.forImport(" Visa •• 1234 "))
    }

    @Test
    fun controlCharactersAreReplaced() {
        assertEquals("My card", CardLabels.forImport("My\t\ncard"))
        assertEquals("", CardLabels.stripControl(null))
    }

    @Test
    fun displayCandidatesExcludeIdentifiers() {
        assertTrue(CardLabels.isDisplayCandidate("Monobank Black"))
        assertFalse(CardLabels.isDisplayCandidate("ab"))
        assertFalse(CardLabels.isDisplayCandidate("path/to/file"))
        assertFalse(CardLabels.isDisplayCandidate("12345678"))
        assertFalse(CardLabels.isDisplayCandidate("0123456789abcdef0123456789abcdef"))
        assertFalse(CardLabels.isDisplayCandidate(null))
    }

    @Test
    fun userInputIsTrimmedAndBounded() {
        assertEquals("Travel", CardLabels.forUserInput("  Travel \n"))
        assertEquals(CardLabels.MAX_USER_LENGTH, CardLabels.forUserInput("y".repeat(100)).length)
    }
}
