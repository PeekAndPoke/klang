package io.peekandpoke.klang.pages.docs

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.routing.urlParam
import de.peekandpoke.kraft.semanticui.forms.UiInputField
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.key
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.noui
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.comp.InViewport
import io.peekandpoke.klang.comp.PlayableCodeExample
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.types.*
import io.peekandpoke.klang.sprudel.lang.docs.registerSprudelDocs
import io.peekandpoke.klang.ui.comp.MarkdownDisplay
import io.peekandpoke.klang.ui.feel.KlangTheme
import kotlinx.css.*
import kotlinx.html.*

/**
 * Search syntax constants and matching logic for the docs page.
 *
 * Supported prefixed terms (may be combined with whitespace for logical AND):
 *   category:XYZ  — matches functions whose category contains XYZ
 *   tag:XYZ       — matches functions with a tag containing XYZ
 *   function:XYZ  — matches functions whose name contains XYZ
 *   XYZ           — plain term: matches name, aliases, tags, category, or library
 */
private object DocSearch {
    const val PREFIX_CATEGORY = "category:"
    const val PREFIX_TAG = "tag:"
    const val PREFIX_FUNCTION = "function:"

    fun categoryQuery(name: String) = "$PREFIX_CATEGORY$name"
    fun tagQuery(name: String) = "$PREFIX_TAG$name"
    fun functionQuery(name: String) = "$PREFIX_FUNCTION$name"

    /** Splits a raw query string into individual search terms (whitespace-separated). */
    fun parseTerms(query: String): List<String> =
        query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** Returns true if [symbol] matches ALL given search terms (logical AND). */
    fun matches(symbol: KlangSymbol, terms: List<String>): Boolean =
        terms.all { term -> matchesTerm(symbol, term) }

    /**
     * Returns a relevance score for [symbol] against all [terms].
     * Higher score = better match. Scores per term are summed.
     *
     * Score tiers (per term):
     *   1000 — exact name match
     *    500 — name starts with term
     *    100 — name contains term
     *     80 — exact alias match
     *     40 — alias starts with term
     *     20 — alias contains term
     *     10 — tag exact match
     *      5 — tag contains term
     *      3 — category / library contains term
     */
    fun score(symbol: KlangSymbol, terms: List<String>): Int =
        terms.sumOf { term -> scoreTerm(symbol, term) }

    private fun scoreTerm(symbol: KlangSymbol, term: String): Int {
        val lower = term.lowercase()
        return when {
            lower.startsWith(PREFIX_CATEGORY) -> {
                val value = lower.removePrefix(PREFIX_CATEGORY)
                if (symbol.category.lowercase().contains(value)) 3 else 0
            }

            lower.startsWith(PREFIX_TAG) -> {
                val value = lower.removePrefix(PREFIX_TAG)
                symbol.tags.maxOfOrNull { tag ->
                    val t = tag.lowercase()
                    when {
                        t == value -> 10
                        t.contains(value) -> 5
                        else -> 0
                    }
                } ?: 0
            }

            lower.startsWith(PREFIX_FUNCTION) -> {
                val value = lower.removePrefix(PREFIX_FUNCTION)
                val name = symbol.name.lowercase()
                when {
                    name == value -> 1000
                    name.startsWith(value) -> 500
                    name.contains(value) -> 100
                    else -> 0
                }
            }

            else -> {
                // Name score
                val name = symbol.name.lowercase()
                val nameScore = when {
                    name == lower -> 1000
                    name.startsWith(lower) -> 500
                    name.contains(lower) -> 100
                    else -> 0
                }
                // Alias score
                val aliasScore = symbol.aliases.maxOfOrNull { alias ->
                    val a = alias.lowercase()
                    when {
                        a == lower -> 80
                        a.startsWith(lower) -> 40
                        a.contains(lower) -> 20
                        else -> 0
                    }
                } ?: 0
                // Tag score
                val tagScore = symbol.tags.maxOfOrNull { tag ->
                    val t = tag.lowercase()
                    when {
                        t == lower -> 10
                        t.contains(lower) -> 5
                        else -> 0
                    }
                } ?: 0
                // Category / library score
                val catScore = when {
                    symbol.category.lowercase().contains(lower) -> 3
                    symbol.library.lowercase().contains(lower) -> 3
                    else -> 0
                }
                nameScore + aliasScore + tagScore + catScore
            }
        }
    }

    private fun matchesTerm(symbol: KlangSymbol, term: String): Boolean {
        val lower = term.lowercase()
        return when {
            lower.startsWith(PREFIX_CATEGORY) -> {
                val value = lower.removePrefix(PREFIX_CATEGORY)
                symbol.category.lowercase().contains(value)
            }

            lower.startsWith(PREFIX_TAG) -> {
                val value = lower.removePrefix(PREFIX_TAG)
                symbol.tags.any { it.lowercase().contains(value) }
            }

            lower.startsWith(PREFIX_FUNCTION) -> {
                val value = lower.removePrefix(PREFIX_FUNCTION)
                symbol.name.lowercase().contains(value)
            }

            else -> {
                // Plain term: search across name, aliases, tags, category, library
                symbol.name.lowercase().contains(lower) ||
                        symbol.aliases.any { it.lowercase().contains(lower) } ||
                        symbol.tags.any { it.lowercase().contains(lower) } ||
                        symbol.category.lowercase().contains(lower) ||
                        symbol.library.lowercase().contains(lower)
            }
        }
    }
}

@Suppress("FunctionName")
fun Tag.SprudelDocsPage() = comp {
    SprudelDocsPage(it)
}

class SprudelDocsPage(ctx: NoProps) : PureComponent(ctx) {

    companion object {
        const val PARAM_SEARCH = "search"
    }

    //  REGISTRY  ///////////////////////////////////////////////////////////////////////////////////////////////////

    // Create isolated registry for Sprudel docs only
    private val registry = KlangDocsRegistry().apply {
        registerSprudelDocs(this)
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val laf by subscribingTo(KlangTheme)
    private var searchQuery: String by urlParam(name = PARAM_SEARCH, default = "")

    //  DERIVED DATA  ///////////////////////////////////////////////////////////////////////////////////////////

    private val filteredSymbols: List<KlangSymbol>
        get() {
            val terms = DocSearch.parseTerms(searchQuery)
            val all = registry.symbols.values.sortedBy { it.name }

            return when {
                // Smart search: filter by match, then sort by score descending (exact matches first)
                terms.isNotEmpty() -> all
                    .filter { DocSearch.matches(it, terms) }
                    .sortedByDescending { DocSearch.score(it, terms) }
                // Show all by default
                else -> all
            }
        }

    //  RENDERING  //////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        ui.fluid.container {
            css {
                padding = Padding(2.rem)
            }

            // Search and filters
            div {
                ui.form {
                    // Search box
                    ui.two.column.stackable.grid {
                        noui.column {
                            UiInputField(value = searchQuery, onChange = { searchQuery = it }) {
                                placeholder(
                                    "Search: plain text, category:structural, tag:timing, function:seq"
                                )

                                rightClearingIcon()

                                leftLabel {
                                    ui.grey.label { icon.search(); +"Search" }
                                }
                            }
                        }
                        noui.column {
                            // Results count
                            ui.message {
                                css {
                                    paddingTop = 0.px
                                    paddingBottom = 0.px

                                    height = 100.pct
                                    display = Display.flex
                                    alignItems = Align.center
                                }
                                +"Found ${filteredSymbols.size} entries"
                            }
                        }
                    }
                }
            }

            // Symbol list
            filteredSymbols.forEach { symbol ->
                renderKlangSymbol(symbol)
            }

            // Empty state
            if (filteredSymbols.isEmpty()) {
                ui.message {
                    ui.header { +"No functions found" }
                    p { +"Try adjusting your search or filters" }
                }
            }
        }
    }

    private fun DIV.renderKlangSymbol(symbol: KlangSymbol) {
        ui.segments {
            key = symbol.name
            css {
                marginBottom = 2.rem
            }

            // Symbol name and category
            ui.segment {
                ui.relaxed.horizontal.list {
                    noui.item {
                        ui.large.header {
                            +symbol.name
                        }
                    }

                    noui.item {
                        // Category label — click to search by category
                        ui.label {
                            css { cursor = Cursor.pointer }
                            onClick { searchQuery = DocSearch.categoryQuery(symbol.category) }
                            icon.tag()
                            +symbol.category
                        }
                        // Tag labels — click to search by tag
                        symbol.tags.forEach { tag ->
                            ui.label {
                                key = tag
                                css { cursor = Cursor.pointer }
                                onClick { searchQuery = DocSearch.tagQuery(tag) }
                                icon.hashtag()
                                +tag
                            }
                        }
                    }

                    // Alias labels — click to search by function name
                    if (symbol.aliases.isNotEmpty()) {
                        noui.item {
                            symbol.aliases.forEach { alias ->
                                key = alias
                                ui.label {
                                    css { cursor = Cursor.pointer }
                                    onClick { searchQuery = DocSearch.functionQuery(alias) }
                                    icon.at()
                                    +alias
                                }
                            }
                        }
                    }
                }
            }

            // Variants
            symbol.variants.forEach { decl ->
                ui.attached.segment {
                    renderDecl(decl)
                }
            }
        }
    }

    private fun DIV.renderDecl(decl: KlangDecl) {
        div {
            css {
                marginTop = 1.rem
                marginBottom = 1.rem
            }

            // Variant type badge
            ui.label {
                +when (decl) {
                    is KlangCallable -> if (decl.receiver == null) {
                        "Top Level Function"
                    } else {
                        "${decl.receiver} Extension Function"
                    }

                    is KlangProperty -> when (decl.mutability) {
                        KlangMutability.READ_ONLY -> "Read-only Property"
                        KlangMutability.READ_WRITE -> "Read-write Property"
                        KlangMutability.WRITE_ONLY -> "Write-only Property"
                    }
                }
            }

            // Signature (code block)
            pre {
                css {
                    backgroundColor = Color(laf.cardBackground)
                    color = Color(laf.textPrimary)
                    padding = Padding(0.75.rem)
                    borderRadius = 4.px
                    overflow = Overflow.auto
                    marginTop = 0.5.rem
                    marginBottom = 0.5.rem
                }
                code {
                    +decl.signature
                }
            }

            // Description
            div {
                css {
                    marginTop = 0.5.rem
                    marginBottom = 0.5.rem
                }

                MarkdownDisplay(decl.description)
            }

            // Parameters
            val params = if (decl is KlangCallable) decl.params else emptyList()
            if (params.isNotEmpty()) {
                ui.header H4 {
                    css {
                        marginTop = 0.75.rem
                    }
                    +"Parameters"
                }
                ui.list {
                    params.forEach { param ->
                        noui.item {
                            key = param.name
                            b { +param.name }
                            +" (${param.type}): ${param.description}"
                        }
                    }
                }
            }

            // Return value
            if (decl.returnDoc.isNotEmpty()) {
                ui.header H4 {
                    css {
                        marginTop = 0.75.rem
                    }
                    +"Returns"
                }
                p {
                    +decl.returnDoc
                }
            }

            // Examples
            if (decl.samples.isNotEmpty()) {
                ui.header H4 {
                    css {
                        marginTop = 0.75.rem
                    }
                    +"Examples"
                }
                decl.samples.forEach { sample ->
                    key = sample
                    InViewport {
                        PlayableCodeExample(code = sample)
                    }
                }
            }
        }
    }
}
