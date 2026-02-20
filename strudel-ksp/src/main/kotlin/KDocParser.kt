package io.peekandpoke.klang.strudel.ksp

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
            )
        }

        // Phase 1: extract ```KlangScript...``` fenced code blocks as samples.
        // Build a cleaned line list that excludes the fenced blocks entirely.
        // Note: raw (untrimmed) lines are used inside code blocks to preserve indentation;
        // only non-code lines are trimmed before being passed to phase 2.
        val samples = mutableListOf<String>()
        val cleanedLines = mutableListOf<String>()
        var inCodeBlock = false
        val currentBlock = StringBuilder()

        for (rawLine in kdoc.lines()) {
            val line = rawLine.trim()
            when {
                !inCodeBlock && line.startsWith("```KlangScript") -> {
                    inCodeBlock = true
                    currentBlock.clear()
                }

                inCodeBlock && line.startsWith("```") -> {
                    inCodeBlock = false
                    val s = currentBlock.toString().trimEnd()
                    if (s.isNotEmpty()) samples.add(s)
                    currentBlock.clear()
                }

                inCodeBlock -> {
                    if (currentBlock.isNotEmpty()) currentBlock.append("\n")
                    currentBlock.append(rawLine)  // preserve original indentation
                }

                else -> cleanedLines.add(line)
            }
        }

        // Phase 2: parse cleaned lines (description + @param/@return/@category/@tags/@alias).
        val descriptionLines = mutableListOf<String>()
        val tagLines = mutableListOf<String>()
        var inTags = false

        for (line in cleanedLines) {
            when {
                line.startsWith("@") -> {
                    inTags = true
                    tagLines.add(line)
                }

                inTags -> tagLines.add(line)
                else -> descriptionLines.add(line)
            }
        }

        val description = descriptionLines
            .joinToString(" ")
            .trim()
            .replace(Regex("\\s+"), " ")

        val params = mutableMapOf<String, String>()
        var returnDoc = ""
        var category: String? = null
        val tags = mutableListOf<String>()
        val aliases = mutableListOf<String>()

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
            }
        }

        for (line in tagLines) {
            when {
                line.startsWith("@param") -> {
                    saveCurrentTag()
                    val match = Regex("@param\\s+(\\w+)\\s*(.*)").matchEntire(line)
                    if (match != null) {
                        currentTag = "param:${match.groupValues[1]}"
                        currentContent = StringBuilder(match.groupValues[2])
                    }
                }

                line.startsWith("@return") -> {
                    saveCurrentTag()
                    currentTag = "return"
                    currentContent = StringBuilder(line.removePrefix("@return").trim())
                }

                line.startsWith("@category") -> {
                    saveCurrentTag()
                    currentTag = "category"
                    currentContent = StringBuilder(line.removePrefix("@category").trim())
                }

                line.startsWith("@tags") -> {
                    saveCurrentTag()
                    currentTag = "tags"
                    currentContent = StringBuilder(line.removePrefix("@tags").trim())
                }

                line.startsWith("@alias") -> {
                    saveCurrentTag()
                    currentTag = "alias"
                    currentContent = StringBuilder(line.removePrefix("@alias").trim())
                }

                else -> {
                    if (currentContent.isNotEmpty()) currentContent.append(" ")
                    currentContent.append(line.trim())
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
        )
    }
}
