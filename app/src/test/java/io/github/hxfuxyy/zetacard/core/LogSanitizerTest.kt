package io.github.hxfuxyy.zetacard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {
    @Test
    fun removesLineBreaksAndTabs() {
        assertEquals("one two three", LogSanitizer.clean("one\ntwo\r\n\tthree"))
    }

    @Test
    fun redactsSensitiveValues() {
        val cleaned = LogSanitizer.clean(
            "Failed https://lh3.googleusercontent.com/secret?token=1 for me@example.com " +
                "card 4111 1111 1111 1111 at /data/user/0/com.google.android.apps.walletnfcrel/files/x",
        )
        assertFalse(cleaned.contains("googleusercontent"))
        assertFalse(cleaned.contains("example.com"))
        assertFalse(cleaned.contains("4111"))
        assertFalse(cleaned.contains("walletnfcrel/files"))
        assertTrue(cleaned.contains("<url>"))
        assertTrue(cleaned.contains("<email>"))
        assertTrue(cleaned.contains("<number>"))
        assertTrue(cleaned.contains("<path>"))
    }

    @Test
    fun keepsUsefulDiagnostics() {
        assertEquals("Wallet 933985015 scanned in 412 ms", LogSanitizer.clean("Wallet 933985015 scanned in 412 ms"))
        assertEquals("Artwork synced: ba7816bf", LogSanitizer.clean("Artwork synced: ba7816bf"))
    }

    @Test
    fun truncatesLongMessages() {
        assertEquals(LogSanitizer.MAX_MESSAGE_LENGTH, LogSanitizer.clean("x".repeat(1000)).length)
        assertEquals("", LogSanitizer.clean(null))
    }

    @Test
    fun describesErrorsWithoutLeakingMessages() {
        assertEquals("IllegalStateException: <url>", LogSanitizer.describe(IllegalStateException("https://example.com/a")))
        assertEquals("RuntimeException", LogSanitizer.describe(RuntimeException()))
        assertEquals("", LogSanitizer.describe(null))
    }

    @Test
    fun trimsOnLineBoundaries() {
        val entries = (1..100).joinToString("") { "line $it\n" }
        val trimmed = LogSanitizer.trimToLastLines(entries, 60)
        assertTrue(trimmed.length <= 60)
        assertTrue(trimmed.startsWith("line "))
        assertTrue(trimmed.endsWith("line 100\n"))
        assertEquals("short\n", LogSanitizer.trimToLastLines("short\n", 60))
    }
}
