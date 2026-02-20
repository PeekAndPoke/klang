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
    /** @sample tags (code examples) */
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

        val lines = kdoc.lines().map { it.trim() }

        // Find description (everything before first @ tag)
        val descriptionLines = mutableListOf<String>()
        val tagLines = mutableListOf<String>()
        var inTags = false

        for (line in lines) {
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
            .replace(Regex("\\s+"), " ") // Normalize whitespace

        // Parse tags
        val params = mutableMapOf<String, String>()
        val samples = mutableListOf<String>()
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
                .replace(Regex("\\s+"), " ") // Normalize whitespace
            if (content.isEmpty()) return

            when {
                tag.startsWith("param:") -> {
                    val paramName = tag.removePrefix("param:")
                    params[paramName] = content
                }

                tag == "return" -> returnDoc = content
                tag == "sample" -> samples.add(content)
                tag == "category" -> category = content
                tag == "tags" -> {
                    // Parse comma-separated tags
                    content.split(",").forEach { t ->
                        val trimmed = t.trim()
                        if (trimmed.isNotEmpty()) {
                            tags.add(trimmed)
                        }
                    }
                }

                tag == "alias" -> {
                    // Parse comma-separated alias names
                    content.split(",").forEach { a ->
                        val trimmed = a.trim()
                        if (trimmed.isNotEmpty()) {
                            aliases.add(trimmed)
                        }
                    }
                }
            }
        }

        for (line in tagLines) {
            when {
                line.startsWith("@param") -> {
                    saveCurrentTag()

                    // Parse @param name description
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

                line.startsWith("@sample") -> {
                    saveCurrentTag()
                    currentTag = "sample"
                    currentContent = StringBuilder(line.removePrefix("@sample").trim())
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
                    // Continuation of current tag
                    if (currentContent.isNotEmpty()) {
                        currentContent.append(" ")
                    }
                    currentContent.append(line.trim())
                }
            }
        }

        // Save last tag
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
