package io.github.hxfuxyy.zetacard.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsTest {
    private val assets = listOf("src/main/assets", "app/src/main/assets").map {
        File(System.getProperty("user.dir"), it)
    }.first { it.isDirectory }

    @Test
    fun everyPresetHasAWebpAsset() {
        assertEquals(Presets.all.size, Presets.all.map { it.id }.toSet().size)
        for (preset in Presets.all) {
            val file = File(assets, preset.asset)
            assertTrue("${preset.asset} missing", file.isFile)
            val header = file.readBytes().copyOfRange(0, 12)
            assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
            assertEquals("WEBP", String(header, 8, 4, Charsets.US_ASCII))
        }
    }

    @Test
    fun noUnlistedPresetAssets() {
        val listed = Presets.all.map { File(it.asset).name }.toSet()
        val bundled = File(assets, "presets").listFiles()!!.map { it.name }.toSet()
        assertEquals(listed, bundled)
    }
}
