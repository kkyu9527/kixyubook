import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class TextResource(val kind: String, val values: Map<String, String>)

// Keep this host-only: missing translations and broken printf arguments fail before Android
// compilation, without an emulator, a translation service, or additional dependencies.
val verifyTranslations = tasks.register("verifyTranslations") {
    group = "verification"
    description = "Checks all four UI locales, plural forms, and format argument parity."
    val resourceFiles = fileTree(rootDir) {
        include("app/src/main/res/values*/*.xml", "core/**/src/main/res/values*/*.xml", "feature/**/src/main/res/values*/*.xml")
        exclude("**/build/**")
    }
    inputs.files(resourceFiles)
    doLast {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val violations = mutableListOf<String>()
        val formats = Regex("""%([1-9][0-9]*)\$[-#+ 0,(]*[0-9]*(?:\.[0-9]+)?[sdfoxegbcSDFOXEGBC]""")
        fun signature(value: String): List<String> = formats.findAll(value).map { it.value }.sorted().toList()
        fun read(files: List<File>): Map<String, TextResource> = buildMap {
            files.sorted().forEach { file ->
                val nodes = factory.newDocumentBuilder().parse(file).documentElement.childNodes
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i) as? Element ?: continue
                    if (node.tagName !in setOf("string", "plurals") || node.getAttribute("translatable") == "false") continue
                    val key = node.getAttribute("name")
                    val values = if (node.tagName == "string") mapOf("other" to node.textContent) else buildMap {
                        val items = node.getElementsByTagName("item")
                        for (j in 0 until items.length) {
                            val item = items.item(j) as Element
                            val quantity = item.getAttribute("quantity")
                            if (put(quantity, item.textContent) != null) violations += "$file: duplicate plural $key/$quantity"
                        }
                    }
                    if (put(key, TextResource(node.tagName, values)) != null) violations += "$file: duplicate resource $key"
                    values.forEach { (quantity, text) ->
                        if (text.isBlank()) violations += "$file: empty $key/$quantity"
                        val unformatted = formats.replace(text.replace("%%", ""), "")
                        if (Regex("%[sdfoxegbcSDFOXEGBC]").containsMatchIn(unformatted)) {
                            violations += "$file: use positional format arguments in $key"
                        }
                    }
                }
            }
        }
        var keys = 0
        resourceFiles.files.groupBy { it.parentFile.parentFile }.forEach { (res, files) ->
            val byLocale = files.groupBy { it.parentFile.name }
            val base = read(byLocale["values"].orEmpty())
            if (base.isEmpty()) return@forEach
            keys += base.size
            base.filterValues { it.kind == "plurals" }.forEach { (key, value) ->
                if (!value.values.keys.containsAll(listOf("one", "other"))) violations += "$res: English plural $key requires one and other"
            }
            listOf("values-b+zh+Hans", "values-b+zh+Hant", "values-ja").forEach { locale ->
                val translated = read(byLocale[locale].orEmpty())
                (base.keys - translated.keys).forEach { violations += "$res/$locale: missing $it" }
                (translated.keys - base.keys).forEach { violations += "$res/$locale: unknown $it" }
                base.forEach { (key, source) ->
                    val target = translated[key] ?: return@forEach
                    if (source.kind != target.kind) violations += "$res/$locale: resource type mismatch for $key"
                    if ("other" !in target.values) violations += "$res/$locale: missing other form for $key"
                    val expected = signature(source.values.getValue("other"))
                    (source.values.values + target.values.values).forEach { value ->
                        if (signature(value) != expected) violations += "$res/$locale: format argument mismatch for $key: $value"
                    }
                }
            }
        }
        check(violations.isEmpty()) { "Translation contract violated:\n${violations.joinToString("\n")}" }
        logger.lifecycle("Verified $keys module resource keys across English, Simplified Chinese, Traditional Chinese, and Japanese.")
    }
}

tasks.named("verifyHostTests") { dependsOn(verifyTranslations) }
subprojects {
    tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(verifyTranslations) }
}
