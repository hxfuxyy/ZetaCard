package io.github.hxfuxyy.zetacard.resources

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class LocalizationTest {
    private val resDir = locateResources()
    private val placeholder = Regex("""%(\d+\$)?[sd]""")

    private data class Bundle(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>, val fixed: Set<String>)

    private fun load(folder: File): Bundle {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(folder, "strings.xml"))
        val strings = LinkedHashMap<String, String>()
        val fixed = HashSet<String>()
        val stringNodes = document.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i) as Element
            val name = node.getAttribute("name")
            if (node.getAttribute("translatable") == "false") fixed += name else strings[name] = node.textContent
        }
        val plurals = LinkedHashMap<String, Map<String, String>>()
        val pluralNodes = document.getElementsByTagName("plurals")
        for (i in 0 until pluralNodes.length) {
            val node = pluralNodes.item(i) as Element
            val items = node.getElementsByTagName("item")
            plurals[node.getAttribute("name")] = (0 until items.length).associate {
                val item = items.item(it) as Element
                item.getAttribute("quantity") to item.textContent
            }
        }
        return Bundle(strings, plurals, fixed)
    }

    private fun locales() = resDir.listFiles { file ->
        file.isDirectory && file.name.startsWith("values-") &&
            File(file, "strings.xml").isFile
    }!!.sortedBy { it.name }

    @Test
    fun supportsTheExpectedLanguages() {
        val expected =
            setOf("uk", "ru", "es", "pt-rBR", "fr", "de", "it", "pl", "tr", "vi", "in", "ja", "ko", "zh-rCN", "zh-rTW", "hi", "ar")
        assertEquals(expected, locales().map { it.name.removePrefix("values-") }.toSet())
    }

    @Test
    fun everyLanguageTranslatesEveryString() {
        val base = load(File(resDir, "values"))
        for (folder in locales()) {
            val bundle = load(folder)
            assertEquals("${folder.name} strings", base.strings.keys, bundle.strings.keys)
            assertEquals("${folder.name} plurals", base.plurals.keys, bundle.plurals.keys)
            assertTrue("${folder.name} must not redefine fixed strings", bundle.fixed.isEmpty())
        }
    }

    @Test
    fun placeholdersMatchTheDefaultLanguage() {
        val base = load(File(resDir, "values"))
        for (folder in locales()) {
            val bundle = load(folder)
            for ((key, value) in base.strings) {
                assertEquals("${folder.name}/$key", placeholders(value), placeholders(bundle.strings.getValue(key)))
            }
            for ((key, items) in bundle.plurals) {
                assertTrue("${folder.name}/$key needs other", items.containsKey("other"))
                assertEquals(
                    "${folder.name}/$key other",
                    placeholders(base.plurals.getValue(key).getValue("other")),
                    placeholders(items.getValue("other")),
                )
            }
        }
    }

    @Test
    fun apostrophesAreEscaped() {
        for (folder in locales() + File(resDir, "values")) {
            val raw = File(folder, "strings.xml").readText()
            val unescaped = Regex("""(?<!\\)'""").findAll(raw.substringAfter("<resources>")).count()
            assertEquals("${folder.name} has unescaped apostrophes", 0, unescaped)
        }
    }

    private fun placeholders(text: String) = placeholder.findAll(text).map { it.value }.sorted().toList()

    private fun locateResources(): File {
        val working = File(System.getProperty("user.dir")).absoluteFile
        return listOf(File(working, "src/main/res"), File(working, "app/src/main/res")).first { it.isDirectory }
    }
}
