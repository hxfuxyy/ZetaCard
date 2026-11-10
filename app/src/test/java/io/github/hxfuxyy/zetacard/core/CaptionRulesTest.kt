package io.github.hxfuxyy.zetacard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptionRulesTest {
    @Test
    fun cleansAndLimitsCaptions() {
        assertEquals("Travel", CaptionRules.text("  Travel\n"))
        assertEquals(CaptionRules.MAX_LENGTH, CaptionRules.text("x".repeat(80))!!.length)
        assertNull(CaptionRules.text("   "))
        assertNull(CaptionRules.text(null))
    }
}
