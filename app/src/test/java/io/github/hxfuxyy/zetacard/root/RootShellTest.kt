package io.github.hxfuxyy.zetacard.root

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class RootShellTest {
    private val shell = arrayOf("/bin/sh", "-c")

    @Before
    fun requireShell() {
        assumeTrue(File("/bin/sh").canExecute())
    }

    @Test
    fun returnsOutputOfSuccessfulCommands() {
        assertArrayEquals("done".toByteArray(), RootShell.exec(shell, "printf done", 5_000))
    }

    @Test
    fun failedCommandsReturnNull() {
        assertNull(RootShell.exec(shell, "printf partial; exit 3", 5_000))
    }

    @Test
    fun timeoutCoversBlockedOutput() {
        val started = System.nanoTime()
        val result = RootShell.exec(shell, "sleep 3; printf late", 200)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertNull(result)
        assertTrue("timed out after $elapsedMs ms", elapsedMs < 1_500)
    }

    @Test
    fun timeoutCoversChildrenHoldingStdout() {
        val started = System.nanoTime()
        val result = RootShell.exec(shell, "(sleep 3; printf late) & wait", 200)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertNull(result)
        assertTrue("timed out after $elapsedMs ms", elapsedMs < 1_500)
    }
}
