package io.github.hxfuxyy.zetacard.metadata

import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionMetadataTest {
    private val moduleDir = locateModuleDir()

    @Test
    fun versionPropertiesAreValid() {
        val properties = Properties().apply { File(moduleDir.parentFile, "version.properties").reader().use { load(it) } }
        val name = properties.getProperty("versionName")
        val code = properties.getProperty("versionCode")
        assertTrue("versionName '$name'", name != null && Regex("""\d+\.\d+(\.\d+)?(-[0-9A-Za-z.]+)?""").matches(name))
        assertTrue("versionCode '$code'", code != null && Regex("""[1-9]\d*""").matches(code))
    }

    @Test
    fun modulePropDoesNotDuplicateVersion() {
        val prop = xposedFile("module.prop").readLines().filter { it.isNotBlank() }
        assertFalse("module.prop must not hard-code a version", prop.any { it.startsWith("version") })
        assertTrue(prop.contains("minApiVersion=102"))
        assertTrue(prop.contains("targetApiVersion=102"))
    }

    @Test
    fun entryPointAndScopeMatchSources() {
        val entry = xposedFile("java_init.list").readText().trim()
        val source = File(moduleDir, "src/main/java/" + entry.replace('.', '/') + ".java")
        assertTrue("entry class $entry not found", source.isFile)
        assertEquals("com.google.android.apps.walletnfcrel", xposedFile("scope.list").readText().trim())
        assertTrue(File(moduleDir, "build.gradle").readText().contains("applicationId 'io.github.hxfuxyy.zetacard'"))
    }

    @Test
    fun xposedRepositoryMetadataMatchesTheModule() {
        val root = moduleDir.parentFile
        val scope = File(root, "xposed/SCOPE").readText().trim()
        val packages = Regex(""""([^"]+)"""").findAll(scope).map { it.groupValues[1] }.toList()
        assertEquals(xposedFile("scope.list").readLines().map { it.trim() }.filter { it.isNotEmpty() }, packages)
        val summary = File(root, "xposed/SUMMARY").readText().trim()
        assertTrue("SUMMARY must be one short line", summary.isNotEmpty() && summary.length <= 200 && !summary.contains('\n'))
        assertEquals("https://github.com/hxfuxyy/ZetaCard", File(root, "xposed/SOURCE_URL").readText().trim())
        assertTrue(File(root, "xposed/README.md").readText().contains("ZetaCard"))
    }

    private fun xposedFile(name: String) = File(moduleDir, "src/main/resources/META-INF/xposed/$name")

    private fun locateModuleDir(): File {
        val working = File(System.getProperty("user.dir")).absoluteFile
        return listOf(working, File(working, "app")).first { File(it, "src/main/resources/META-INF/xposed").isDirectory }
    }
}
