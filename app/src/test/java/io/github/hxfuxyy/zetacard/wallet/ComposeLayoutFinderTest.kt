package io.github.hxfuxyy.zetacard.wallet

import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeLayoutFinderTest {
    private val finder = ComposeLayoutFinder(FakeWallet.Pixels::class.java)
    private val walletClasses: List<Class<*>> = listOf(
        FakeWallet.Pixels::class.java,
        FakeWallet.ImageModel::class.java,
        FakeWallet.BitmapModel::class.java,
        FakeWallet.UrlModel::class.java,
        FakeWallet.CardState::class.java,
        FakeWallet.KeyedState::class.java,
        FakeWallet.Renderer::class.java,
        FakeWallet.WeakRenderer::class.java,
        FakeWallet.Unrelated::class.java,
    )

    @Test
    fun findsTheCardLayout() {
        val result = finder.find(walletClasses, 0)
        assertEquals(ComposeLayoutFinder.Status.FOUND, result.status)
        val layout = result.layout!!
        assertEquals(FakeWallet.UrlModel::class.java, layout.urlModel)
        assertEquals("art", layout.cardArt.name)
        assertEquals("url", layout.url.name)
        assertEquals(FakeWallet.BitmapModel::class.java, layout.bitmapConstructor.declaringClass)
        assertEquals(FakeWallet.KeyedState::class.java, layout.cardConstructor.declaringClass)
        assertEquals(FakeWallet.Renderer::class.java, layout.renderer.declaringClass)
        assertEquals(FakeWallet.ImageModel::class.java, layout.imageModel())
    }

    @Test
    fun savedLayoutCanBeRestored() {
        val layout = finder.find(walletClasses, 0).layout!!
        val restored = finder.restore(javaClass.classLoader, layout.encode())
        assertEquals(layout.encode(), restored.encode())
        assertEquals(layout.renderer, restored.renderer)
    }

    @Test(expected = IllegalArgumentException::class)
    fun corruptedCacheIsRejected() {
        finder.restore(javaClass.classLoader, "a|b|c")
    }

    @Test
    fun cacheRestoresTheExactOverload() {
        val classes = walletClasses.filter { it != FakeWallet.Renderer::class.java } + FakeWallet.OverloadedRenderer::class.java
        val result = finder.find(classes, 0)
        assertEquals(ComposeLayoutFinder.Status.FOUND, result.status)
        val restored = finder.restoreAll(javaClass.classLoader, result.encode())
        assertEquals(result.layouts.map { it.renderer }, restored.map { it.renderer })
        assertEquals(5, result.layout!!.renderer.parameterTypes.indexOf(java.lang.Float.TYPE))
    }

    @Test
    fun equalCardStateWrappersAreAllKept() {
        val result = finder.find(walletClasses + FakeWallet.KeyedStateCopy::class.java, 0)
        assertEquals(ComposeLayoutFinder.Status.FOUND, result.status)
        assertEquals(
            setOf(FakeWallet.KeyedState::class.java, FakeWallet.KeyedStateCopy::class.java),
            result.layouts.map { it.cardConstructor.declaringClass }.toSet(),
        )
        assertEquals(2, finder.restoreAll(javaClass.classLoader, result.encode()).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cacheFromOlderVersionsIsRejected() {
        val current = finder.find(walletClasses, 0).layout!!.encode()
        finder.restore(javaClass.classLoader, current.removePrefix("v2|").substringBeforeLast('|') + "|11")
    }

    @Test
    fun equallyGoodRenderersAreTreatedAsAmbiguous() {
        val result = finder.find(walletClasses + FakeWallet.OtherRenderer::class.java, 0)
        assertEquals(ComposeLayoutFinder.Status.AMBIGUOUS, result.status)
        assertNull(result.layout)
    }

    @Test
    fun missingStructureIsReported() {
        val result = finder.find(listOf(FakeWallet.Unrelated::class.java, String::class.java), 0)
        assertEquals(ComposeLayoutFinder.Status.NOT_FOUND, result.status)
        assertNull(result.layout)
    }

    @Test
    fun rendererShapeRequiresStaticVoidWithMixedParameters() {
        val render = FakeWallet.Renderer::class.java.declaredMethods.single { it.name == "render" }
        val unrelated = FakeWallet.Unrelated::class.java.declaredMethods.single { it.name == "notARenderer" }
        assertTrue(ComposeLayoutFinder.isRendererShape(render))
        assertFalse(ComposeLayoutFinder.isRendererShape(unrelated))
    }

    @Test
    fun scanStaysFastWithThousandsOfUnrelatedClasses() {
        val decoys = platformClasses(6000)
        assertTrue("expected a large decoy set, got ${decoys.size}", decoys.size > 2000)
        val started = System.nanoTime()
        val result = finder.find(decoys + walletClasses, 0)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(ComposeLayoutFinder.Status.FOUND, result.status)
        assertTrue("scan took $elapsedMs ms", elapsedMs < 15_000)
    }

    @Test
    fun scanStopsWhenTheBudgetIsExhausted() {
        val result = finder.find(platformClasses(6000) + walletClasses, 1)
        assertEquals(ComposeLayoutFinder.Status.TIMED_OUT, result.status)
        assertNull(result.layout)
    }

    private fun platformClasses(limit: Int): List<Class<*>> {
        val fs = FileSystems.newFileSystem(URI.create("jrt:/"), emptyMap<String, Any>())
        val root = fs.getPath("/modules/java.base")
        val result = ArrayList<Class<*>>()
        Files.walk(root).use { paths ->
            for (path in paths) {
                if (result.size >= limit) break
                val name = path.toString()
                if (!name.endsWith(".class") || name.endsWith("module-info.class")) continue
                val className = root.relativize(path).toString().removeSuffix(".class").replace('/', '.')
                try {
                    result += Class.forName(className, false, ClassLoader.getSystemClassLoader())
                } catch (_: Throwable) {
                }
            }
        }
        return result
    }
}
