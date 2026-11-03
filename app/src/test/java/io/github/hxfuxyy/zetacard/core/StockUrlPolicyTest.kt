package io.github.hxfuxyy.zetacard.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockUrlPolicyTest {
    @Test
    fun allowsGoogleArtworkHosts() {
        assertTrue(StockUrlPolicy.isAllowed("https://lh3.googleusercontent.com/abc=w700"))
        assertTrue(StockUrlPolicy.isAllowed("https://googleusercontent.com/abc"))
        assertTrue(StockUrlPolicy.isAllowed("https://LH3.GoogleUserContent.com:443/abc"))
    }

    @Test
    fun rejectsEverythingElse() {
        assertFalse(StockUrlPolicy.isAllowed(null))
        assertFalse(StockUrlPolicy.isAllowed(""))
        assertFalse(StockUrlPolicy.isAllowed("http://lh3.googleusercontent.com/abc"))
        assertFalse(StockUrlPolicy.isAllowed("https://googleusercontent.com.example.com/abc"))
        assertFalse(StockUrlPolicy.isAllowed("https://evilgoogleusercontent.com/abc"))
        assertFalse(StockUrlPolicy.isAllowed("https://lh3.googleusercontent.com@example.com/abc"))
        assertFalse(StockUrlPolicy.isAllowed("https://lh3.googleusercontent.com:8443/abc"))
        assertFalse(StockUrlPolicy.isAllowed("file:///data/local/tmp/a.png"))
        assertFalse(StockUrlPolicy.isAllowed("https://lh3.googleusercontent.com/" + "a".repeat(5000)))
        assertFalse(StockUrlPolicy.isAllowed("not a url"))
    }
}
