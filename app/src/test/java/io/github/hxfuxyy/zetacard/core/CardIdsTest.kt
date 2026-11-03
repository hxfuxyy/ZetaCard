package io.github.hxfuxyy.zetacard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardIdsTest {
    @Test
    fun hashesSourceWithSha256() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", CardIds.fromSource("abc"))
    }

    @Test
    fun generatedIdsAreValid() {
        assertTrue(CardIds.isValid(CardIds.fromSource("https://lh3.googleusercontent.com/card-art")))
    }

    @Test
    fun rejectsMalformedIds() {
        assertFalse(CardIds.isValid(null))
        assertFalse(CardIds.isValid(""))
        assertFalse(CardIds.isValid("A".repeat(64)))
        assertFalse(CardIds.isValid("a".repeat(63)))
        assertFalse(CardIds.isValid("../" + "a".repeat(61)))
        assertFalse(CardIds.isValid("a".repeat(64) + "\n"))
    }

    @Test
    fun shortensIdsForLogs() {
        assertEquals("ba7816bf", CardIds.shortId(CardIds.fromSource("abc")))
        assertEquals("abc", CardIds.shortId("abc"))
        assertEquals("unknown", CardIds.shortId(null))
    }
}
