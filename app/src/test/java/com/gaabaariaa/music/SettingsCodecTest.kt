package com.gaabaariaa.music

import com.gaabaariaa.music.domain.model.HomeSection
import com.gaabaariaa.music.domain.model.HomeSectionConfig
import com.gaabaariaa.music.domain.model.LibraryTab
import com.gaabaariaa.music.domain.model.decodeHomeSections
import com.gaabaariaa.music.domain.model.decodeLibraryTabs
import com.gaabaariaa.music.domain.model.defaultHomeSections
import com.gaabaariaa.music.domain.model.encodeHomeSections
import com.gaabaariaa.music.domain.model.encodeLibraryTabs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {
    @Test
    fun homeSectionsRoundTrip() {
        val custom = defaultHomeSections().reversed().map { it.copy(enabled = it.section == HomeSection.FAVORITES) }
        assertEquals(custom, decodeHomeSections(encodeHomeSections(custom)))
    }

    @Test
    fun missingOrUnknownSectionsAreRepaired() {
        val decoded = decodeHomeSections("FAVORITES:1,NOT_A_SECTION:1,FAVORITES:0")
        assertEquals(HomeSectionConfig(HomeSection.FAVORITES, true), decoded.first())
        assertEquals(HomeSection.entries.size, decoded.size)
        assertEquals(defaultHomeSections(), decodeHomeSections(null))
        assertEquals(defaultHomeSections(), decodeHomeSections(""))
    }

    @Test
    fun libraryTabsKeepOrderAndAddNewOnes() {
        val tabs = listOf(LibraryTab.ALBUMS, LibraryTab.SONGS)
        val decoded = decodeLibraryTabs(encodeLibraryTabs(tabs))
        assertEquals(LibraryTab.ALBUMS, decoded[0])
        assertEquals(LibraryTab.SONGS, decoded[1])
        assertEquals(LibraryTab.entries.size, decoded.size)
        assertTrue(decodeLibraryTabs("garbage").containsAll(LibraryTab.entries))
    }
}

class NumberFormatTest {
    @Test
    fun formatsNumbersWithoutGrouping() {
        assertEquals("1.25", com.gaabaariaa.music.core.util.formatNumber(1.25, 2, java.util.Locale.US))
        assertEquals("2", com.gaabaariaa.music.core.util.formatNumber(2.0, 2, java.util.Locale.US))
        assertEquals("1500", com.gaabaariaa.music.core.util.formatNumber(1500, java.util.Locale.US))
    }
}
