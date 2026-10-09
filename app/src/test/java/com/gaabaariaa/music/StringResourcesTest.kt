package com.gaabaariaa.music

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Keeps English and Persian in step and catches resource mistakes before they reach a build. */
class StringResourcesTest {
    private fun load(dir: String): Pair<Map<String, String>, Map<String, List<String>>> {
        val file = File("src/main/res/$dir/strings.xml")
        assertTrue("missing ${file.absolutePath}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = LinkedHashMap<String, String>()
        val plurals = LinkedHashMap<String, List<String>>()
        val root = doc.documentElement
        for (i in 0 until root.childNodes.length) {
            val node = root.childNodes.item(i) as? Element ?: continue
            when (node.tagName) {
                "string" -> strings[node.getAttribute("name")] = node.textContent
                "plurals" -> plurals[node.getAttribute("name")] =
                    (0 until node.getElementsByTagName("item").length).map { node.getElementsByTagName("item").item(it).textContent }
            }
        }
        return strings to plurals
    }

    private val placeholder = Regex("%(\\d+\\$)?[sd]")

    private fun placeholders(text: String): List<String> =
        placeholder.findAll(text.replace("%%", "")).map { it.value }.sorted().toList()

    @Test
    fun englishAndPersianHaveTheSameKeys() {
        val (en, enPlurals) = load("values")
        val (fa, faPlurals) = load("values-fa")
        assertEquals("only in English: ${en.keys - fa.keys}", emptySet<String>(), en.keys - fa.keys)
        assertEquals("only in Persian: ${fa.keys - en.keys}", emptySet<String>(), fa.keys - en.keys)
        assertEquals(enPlurals.keys, faPlurals.keys)
    }

    @Test
    fun placeholdersMatchBetweenLanguages() {
        val (en, enPlurals) = load("values")
        val (fa, faPlurals) = load("values-fa")
        for ((name, text) in en) {
            assertEquals("placeholders differ in '$name'", placeholders(text), placeholders(fa.getValue(name)))
        }
        for ((name, items) in enPlurals) {
            assertEquals("placeholders differ in plurals '$name'", placeholders(items.first()), placeholders(faPlurals.getValue(name).first()))
        }
    }

    @Test
    fun noUnescapedApostrophesOrEmptyValues() {
        for (dir in listOf("values", "values-fa")) {
            val (strings, plurals) = load(dir)
            val all = strings + plurals.mapValues { it.value.joinToString(" ") }
            for ((name, text) in all) {
                assertTrue("empty string '$name' in $dir", text.isNotBlank())
                // A raw apostrophe breaks aapt unless it is escaped or the whole text is quoted.
                val unescaped = Regex("(?<!\\\\)'").containsMatchIn(text) && !(text.startsWith("\"") && text.endsWith("\""))
                assertTrue("unescaped apostrophe in '$name' ($dir)", !unescaped)
            }
        }
    }

    @Test
    fun persianTextIsActuallyTranslated() {
        val (en, _) = load("values")
        val (fa, _) = load("values-fa")
        // Names, symbols and technical terms may stay the same; ordinary sentences must not.
        val allowedSame = setOf("app_name", "language_en", "language_fa", "speed_value", "settings_value_dp", "tags_failed_dummy")
        val untranslated = en.filter { (name, text) ->
            name !in allowedSame && text.length > 12 && text == fa.getValue(name)
        }.keys
        assertTrue("same text in both languages: $untranslated", untranslated.isEmpty())
    }
}
