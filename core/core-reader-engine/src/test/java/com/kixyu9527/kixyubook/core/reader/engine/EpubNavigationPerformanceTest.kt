package com.kixyu9527.kixyubook.core.reader.engine

import org.junit.Assert.*
import org.junit.Test

class EpubNavigationPerformanceTest {
    @Test fun thousandsOfCompleteChapterEntriesRequireZeroBodyProbes() {
        val owners = List(3000) { NavigationEntry("Chapter $it", null, null) }
        val paths = owners.indices.map { "s$it.xhtml" }
        val pkg = PackageDocument("", "", emptyList(), emptyList(), null,
            paths.associateWith { ManifestItem(it, "application/xhtml+xml", emptySet()) }, paths,
            navigation = paths.zip(owners).toMap(), spineNavigation = owners)
        var probes = 0
        assertEquals(emptyMap<Int, Int>(), epubTitlePageRedirects(pkg) { probes++; error("Must not open chapter bodies") })
        assertEquals(0, probes)
    }

    @Test fun onlyOwnedSplitSectionsAreProbedAndConsecutiveTitleAliasesStillResolve() {
        val a = NavigationEntry("First", null, null)
        val b = NavigationEntry("Second", null, null)
        val paths = (0..4).map { "s$it.xhtml" }
        val pkg = PackageDocument("", "", emptyList(), emptyList(), null,
            paths.associateWith { ManifestItem(it, "application/xhtml+xml", emptySet()) }, paths,
            navigation = mapOf(paths[0] to a, paths[3] to b), spineNavigation = listOf(a,a,a,b,b))
        val probes = mutableListOf<Int>()
        assertEquals(mapOf(0 to 2, 1 to 2), epubTitlePageRedirects(pkg) { probes += it; it < 2 })
        assertEquals(listOf(0,1,2,3), probes)
    }
}
