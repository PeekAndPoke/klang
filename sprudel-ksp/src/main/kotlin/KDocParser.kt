package io.peekandpoke.klang.sprudel.ksp

/**
 * Parsed KDoc information from a function.
 */
data class ParsedKDoc(
    /** Main description (everything before the first tag) */
    val description: String,
    /** @param tags: parameter name -> description */
    val params: Map<String, String>,
    /** @return tag description */
    val returnDoc: String,
    /** Code examples extracted from ```KlangScript...``` fenced blocks */
    val samples: List<String>,
    /** @category tag (custom) */
    val category: String?,
    /** @tags tag (custom, comma-separated) */
    val tags: List<String>,
    /** @alias tag (custom, comma-separated alternative function names) */
    val aliases: List<String>,
    /** @param-tool tags: parameter name -> list of UI tool names */
    val paramTools: Map<String, List<String>>,
    /** @param-sub tags: parameter name -> (sub-field name -> description) */
    val paramSubs: Map<String, Map<String, String>>,
)

/**
 * Parses KDoc string into structured information.
 */
object KDocParser {

    fun parse(kdoc: String?): ParsedKDoc {
        if (kdoc == null) {
            return ParsedKDoc(
                description = "",
                params = emptyMap(),
                returnDoc = "",
                samples = emptyList(),
                category = null,
                tags = emptyList(),
                aliases = emptyList(),
                paramTools = emptyMap(),
                paramSubs = emptyMap(),
            )
        }

        // Phase 1: extract ```KlangScript...``` fenced code blocks as samples.
        // Build a cleaned line list that excludes the fenced blocks entirely.
        // Raw lines are preserved for non-code content so that description indentation
        // and empty lines are retained (they matter for markdown rendering).
        val samples = mutableListOf<String>()
        val cleanedLines = mutableListOf<String>()
        var inCodeBlock = false
        val currentBlock = StringBuilder()

        for (rawLine in kdoc.lines()) {
            val trimmed = rawLine.trim()
            when {
                !inCodeBlock && trimmed.startsWith("```KlangScript") -> {
                    inCodeBlock = true
                    currentBlock.clear()
                }

                inCodeBlock && trimmed.startsWith("```") -> {
                    inCodeBlock = false
                    val s = currentBlock.toString().trimEnd().trimIndent()
                    if (s.isNotEmpty()) samples.add(s)
                    currentBlock.clear()
                }

                inCodeBlock -> {
                    if (currentBlock.isNotEmpty()) currentBlock.append("\n")
                    currentBlock.append(rawLine)  // preserve original indentation
                }

                else -> cleanedLines.add(rawLine)  // preserve raw lines for description fidelity
            }
        }

        // Phase 2: parse cleaned lines (description + @param/@return/@category/@tags/@alias).
        // Tag detection uses trimmed comparison so indented @-tags are handled correctly.
        val descriptionLines = mutableListOf<String>()
        val tagLines = mutableListOf<String>()
        var inTags = false

        for (line in cleanedLines) {
            when {
                line.trim().startsWith("@") -> {
                    inTags = true
                    tagLines.add(line)
                }

                inTags -> tagLines.add(line)
                else -> descriptionLines.add(line)
            }
        }

        val description = descriptionLines
            .dropWhile { it.trim().isEmpty() }
            .dropLastWhile { it.trim().isEmpty() }
            .joinToString("\n")

        val params = mutableMapOf<String, String>()
        var returnDoc = ""
        var category: String? = null
        val tags = mutableListOf<String>()
        val aliases = mutableListOf<String>()
        val paramTools = mutableMapOf<String, MutableList<String>>()
        val paramSubs = mutableMapOf<String, MutableMap<String, String>>()

        var currentTag: String? = null
        var currentContent = StringBuilder()

        fun saveCurrentTag() {
            val tag = currentTag ?: return
            val content = currentContent.toString()
                .trim()
                .replace(Regex("\\s+"), " ")
            if (content.isEmpty()) return

            when {
                tag.startsWith("param:") -> {
                    val paramName = tag.removePrefix("param:")
                    params[paramName] = content
                }

                tag == "return" -> returnDoc = content
                tag == "category" -> category = content
                tag == "tags" -> {
                    content.split(",").forEach { t ->
                        val trimmed = t.trim()
                        if (trimmed.isNotEmpty()) tags.add(trimmed)
                    }
                }

                tag == "alias" -> {
                    content.split(",").forEach { a ->
                        val trimmed = a.trim()
                        if (trimmed.isNotEmpty()) aliases.add(trimmed)
                    }
                }

                tag.startsWith("param-tool:") -> {
                    val paramName = tag.removePrefix("param-tool:")
                    content.split(",").forEach { t ->
                        val trimmed = t.trim()
                        if (trimmed.isNotEmpty()) paramTools.getOrPut(paramName) { mutableListOf() }.add(trimmed)
                    }
                }

                tag.startsWith("param-sub:") -> {
                    val parts = tag.removePrefix("param-sub:").split(":", limit = 2)
                    if (parts.size == 2 && content.isNotEmpty()) {
                        paramSubs.getOrPut(parts[0]) { mutableMapOf() }[parts[1]] = content
                    }
                }
            }
        }

        for (line in tagLines) {
            val t = line.trim()
            when {
                t.startsWith("@param-sub") -> {
                    saveCurrentTag()
                    val match = Regex("@param-sub\\s+(\\w+)\\s+(\\w+)\\s+(.*)").matchEntire(t)
                    if (match != null) {
                        currentTag = "param-sub:${match.groupValues[1]}:${match.groupValues[2]}"
                        currentContent = StringBuilder(match.groupValues[3])
                    }
                }

                t.startsWith("@param-tool") -> {
                    saveCurrentTag()
                    val match = Regex("@param-tool\\s+(\\w+)\\s*(.*)").matchEntire(t)
                    if (match != null) {
                        currentTag = "param-tool:${match.groupValues[1]}"
                        currentContent = StringBuilder(match.groupValues[2])
                    }
                }

                t.startsWith("@param") -> {
                    saveCurrentTag()
                    val match = Regex("@param\\s+(\\w+)\\s*(.*)").matchEntire(t)
                    if (match != null) {
                        currentTag = "param:${match.groupValues[1]}"
                        currentContent = StringBuilder(match.groupValues[2])
                    }
                }

                t.startsWith("@return") -> {
                    saveCurrentTag()
                    currentTag = "return"
                    currentContent = StringBuilder(t.removePrefix("@return").trim())
                }

                t.startsWith("@category") -> {
                    saveCurrentTag()
                    currentTag = "category"
                    currentContent = StringBuilder(t.removePrefix("@category").trim())
                }

                t.startsWith("@tags") -> {
                    saveCurrentTag()
                    currentTag = "tags"
                    currentContent = StringBuilder(t.removePrefix("@tags").trim())
                }

                t.startsWith("@alias") -> {
                    saveCurrentTag()
                    currentTag = "alias"
                    currentContent = StringBuilder(t.removePrefix("@alias").trim())
                }

                else -> {
                    if (currentContent.isNotEmpty()) currentContent.append(" ")
                    currentContent.append(t)
                }
            }
        }

        saveCurrentTag()

        return ParsedKDoc(
            description = description,
            params = params,
            returnDoc = returnDoc,
            samples = samples,
            category = category,
            tags = tags,
            aliases = aliases,
            paramTools = paramTools,
            paramSubs = paramSubs,
        )
    }
}
