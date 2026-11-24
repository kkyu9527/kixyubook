package com.kixyu9527.kixyubook.core.reader.engine

/** Structural filtering happens before any XHTML I/O. Most novels need zero title-body probes. */
internal fun epubTitlePageRedirects(pkg: PackageDocument, titleOnly: (Int) -> Boolean): Map<Int, Int> {
    val paths = pkg.spine.map { pkg.manifest[it]?.path?.normalizedArchivePath() }
    val positions = linkedMapOf<String?, Int>().apply { paths.forEachIndexed { i, path -> putIfAbsent(path, i) } }
    val entries = pkg.navigation.entries.toList()
    val anchoredPaths = entries.filter { '#' in it.key }.mapTo(hashSetOf()) { it.key.normalizedArchivePath() }
    return buildMap {
        entries.forEachIndexed { position, (target, entry) ->
            if ('#' in target || entries.getOrNull(position + 1)?.value?.depth?.let { it > entry.depth } == true) return@forEachIndexed
            if (target.normalizedArchivePath() in anchoredPaths) return@forEachIndexed
            val source = positions[target.normalizedArchivePath()] ?: return@forEachIndexed
            if (source + 1 >= paths.size || pkg.spineNavigation[source + 1] !== pkg.spineNavigation[source]) return@forEachIndexed
            if (!titleOnly(source)) return@forEachIndexed
            var next = source + 1
            while (next < paths.size && pkg.spineNavigation[next] === pkg.spineNavigation[source] && titleOnly(next)) next++
            if (next < paths.size && pkg.spineNavigation[next] === pkg.spineNavigation[source]) {
                (source until next).forEach { put(it, next) }
            }
        }
    }
}
