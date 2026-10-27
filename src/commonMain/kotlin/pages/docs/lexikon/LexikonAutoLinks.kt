/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.lexikon

/** Where an entry's page lives, as the docs link to it; [lexikonAutoLinks] writes its links so. */
const val LEXIKON_PATH = "/manuals/lexikon/"

/**
 * Links the Lexikon's terms in a markdown text to their entries (KLANG-00369; the maintainer, 2026-10-27: "auto-apply
 * links to the lexikon in the help prose [...] less manual links to manage"): each entry's [LexikonEntry.linkWords],
 * the first time the text names it, as a whole word in any case, the longest word first ("orbit bus" before "orbit").
 *
 * Never inside code (inline or fenced), a link or its text, an autolink or a URL; an entry a hand-written link in the
 * text already points to is not linked again, so a written link and an auto-link never meet; a KDoc reference
 * (`[orbit]`) stays as it is, as it names a function, not the concept. [self] is the entry the text belongs to (its
 * own detail), which never links to itself.
 */
fun lexikonAutoLinks(
    markdown: String,
    entries: List<LexikonEntry> = allLexikonEntries,
    self: String? = null,
): String {
    val linker = if (entries === allLexikonEntries && self == null) defaultLinker else LexikonLinker(entries, self)

    return linker.link(markdown)
}

/** The real Lexikon's words, built once: the help and the docs pages link many texts with them (KLANG-00369). */
private val defaultLinker by lazy { LexikonLinker(entries = allLexikonEntries, self = null) }

/** The words of [entries] (without [self]'s) and the pattern that finds them. */
private class LexikonLinker(entries: List<LexikonEntry>, self: String?) {

    private val slugByWord = LinkedHashMap<String, String>()

    init {
        for (entry in entries) {
            if (entry.slug == self) {
                continue
            }

            for (word in entry.linkWords) {
                slugByWord.getOrPut(word.lowercase()) { entry.slug }
            }
        }
    }

    /**
     * A word not glued to a letter, digit, `_` or `-` on either side; the side before is matched (group 1), not looked
     * behind, which older Safari cannot.
     */
    private val wordPattern: Regex? = slugByWord.keys
        .takeIf { it.isNotEmpty() }
        ?.sortedByDescending { it.length }
        ?.joinToString("|") { Regex.escape(it) }
        ?.let { words -> Regex("""(^|[^\p{L}\p{N}_-])($words)(?![\p{L}\p{N}_-])""", RegexOption.IGNORE_CASE) }

    fun link(markdown: String): String {
        val pattern = wordPattern ?: return markdown

        // The entries the text links by hand: those it does not get a second time
        val linked = lexikonLinkInText.findAll(markdown).map { it.groupValues[1] }.toMutableSet()

        val out = StringBuilder()
        var free = 0

        fun linkFree(until: Int) {
            val part = markdown.substring(free, until)

            out.append(
                pattern.replace(part) { match ->
                    val before = match.groupValues[1]
                    val word = match.groupValues[2]
                    val slug = slugByWord[word.lowercase()]

                    if (slug == null || slug in linked) {
                        match.value
                    } else {
                        linked += slug
                        "$before[$word]($LEXIKON_PATH$slug)"
                    }
                }
            )
        }

        for (kept in untouchable.findAll(markdown)) {
            linkFree(until = kept.range.first)
            out.append(kept.value)
            free = kept.range.last + 1
        }

        linkFree(until = markdown.length)

        return out.toString()
    }
}

/** A link to an entry, in a markdown link's target or a plain URL: its slug in group 1. */
private val lexikonLinkInText = Regex("""/manuals/lexikon/([a-z0-9-]+)""")

/**
 * What the auto-links never touch: fenced code, inline code, a link or an image with its text and target, a reference
 * link, an autolink or an HTML tag, a bare URL.
 */
private val untouchable = Regex(
    listOf(
        """```[\s\S]*?(?:```|$)""",
        """`[^`]*`""",
        // Unrolled (no alternation repeated): on the JVM a repeated alternation recurses per character and overflows
        """!?\[[^\[\]]*(?:\[[^\]]*\][^\[\]]*)*\]\([^)]*\)""",
        """\[[^\]]*\]\[[^\]]*\]""",
        // A KDoc reference or a shortcut reference link (`[orbit]`): it names a function or a type, not the concept
        """\[[^\[\]\n]*\]""",
        """<[^>\n]+>""",
        """https?://\S+""",
    ).joinToString("|")
)
