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
import io.peekandpoke.klang.comp.MarkdownDisplay
import io.peekandpoke.klang.comp.PlayableCodeExample
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangDecl
import io.peekandpoke.klang.script.types.KlangFun
import io.peekandpoke.klang.script.types.KlangObject
import io.peekandpoke.klang.strudel.lang.docs.registerStrudelDocs
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

    /** Returns true if [func] matches ALL given search terms (logical AND). */
    fun matches(func: KlangFun, terms: List<String>): Boolean =
        terms.all { term -> matchesTerm(func, term) }

    /**
     * Returns a relevance score for [func] against all [terms].
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
    fun score(func: KlangFun, terms: List<String>): Int =
        terms.sumOf { term -> scoreTerm(func, term) }

    private fun scoreTerm(func: KlangFun, term: String): Int {
        val lower = term.lowercase()
        return when {
            lower.startsWith(PREFIX_CATEGORY) -> {
                val value = lower.removePrefix(PREFIX_CATEGORY)
                if (func.category.lowercase().contains(value)) 3 else 0
            }

            lower.startsWith(PREFIX_TAG) -> {
                val value = lower.removePrefix(PREFIX_TAG)
                func.tags.maxOfOrNull { tag ->
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
                val name = func.name.lowercase()
                when {
                    name == value -> 1000
                    name.startsWith(value) -> 500
                    name.contains(value) -> 100
                    else -> 0
                }
            }

            else -> {
                // Name score
                val name = func.name.lowercase()
                val nameScore = when {
                    name == lower -> 1000
                    name.startsWith(lower) -> 500
                    name.contains(lower) -> 100
                    else -> 0
                }
                // Alias score
                val aliasScore = func.aliases.maxOfOrNull { alias ->
                    val a = alias.lowercase()
                    when {
                        a == lower -> 80
                        a.startsWith(lower) -> 40
                        a.contains(lower) -> 20
                        else -> 0
                    }
                } ?: 0
                // Tag score
                val tagScore = func.tags.maxOfOrNull { tag ->
                    val t = tag.lowercase()
                    when {
                        t == lower -> 10
                        t.contains(lower) -> 5
                        else -> 0
                    }
                } ?: 0
                // Category / library score
                val catScore = when {
                    func.category.lowercase().contains(lower) -> 3
                    func.library.lowercase().contains(lower) -> 3
                    else -> 0
                }
                nameScore + aliasScore + tagScore + catScore
            }
        }
    }

    private fun matchesTerm(func: KlangFun, term: String): Boolean {
        val lower = term.lowercase()
        return when {
            lower.startsWith(PREFIX_CATEGORY) -> {
                val value = lower.removePrefix(PREFIX_CATEGORY)
                func.category.lowercase().contains(value)
            }

            lower.startsWith(PREFIX_TAG) -> {
                val value = lower.removePrefix(PREFIX_TAG)
                func.tags.any { it.lowercase().contains(value) }
            }

            lower.startsWith(PREFIX_FUNCTION) -> {
                val value = lower.removePrefix(PREFIX_FUNCTION)
                func.name.lowercase().contains(value)
            }

            else -> {
                // Plain term: search across name, aliases, tags, category, library
                func.name.lowercase().contains(lower) ||
                        func.aliases.any { it.lowercase().contains(lower) } ||
                        func.tags.any { it.lowercase().contains(lower) } ||
                        func.category.lowercase().contains(lower) ||
                        func.library.lowercase().contains(lower)
            }
        }
    }
}

@Suppress("FunctionName")
fun Tag.StrudelDocsPage() = comp {
    StrudelDocsPage(it)
}

class StrudelDocsPage(ctx: NoProps) : PureComponent(ctx) {

    companion object {
        const val PARAM_SEARCH = "search"
    }

    //  REGISTRY  ///////////////////////////////////////////////////////////////////////////////////////////////////

    // Create isolated registry for Strudel docs only
    private val registry = KlangDocsRegistry().apply {
        registerStrudelDocs(this)
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private var searchQuery: String by urlParam(name = PARAM_SEARCH, default = "")

    //  DERIVED DATA  ///////////////////////////////////////////////////////////////////////////////////////////

    private val filteredFunctions: List<KlangFun>
        get() {
            val terms = DocSearch.parseTerms(searchQuery)
            val all = registry.functions.values.sortedBy { it.name }

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
                    div {
                        UiInputField(value = searchQuery, onChange = { searchQuery = it }) {
                            placeholder(
                                "Search: plain text, category:structural, tag:timing, function:seq"
                            )

                            rightClearingIcon()

                            leftLabel {
                                ui.basic.label { icon.search(); +"Search" }
                            }
                        }
                    }
                }
            }

            // Results count
            ui.message {
                css { marginTop = 1.rem }
                +"Found ${filteredFunctions.size} entries"
            }

            // Function list
            filteredFunctions.forEach { func ->
                renderKlangFun(func)
            }

            // Empty state
            if (filteredFunctions.isEmpty()) {
                ui.message {
                    ui.header { +"No functions found" }
                    p { +"Try adjusting your search or filters" }
                }
            }
        }
    }

    private fun DIV.renderKlangFun(func: KlangFun) {
        ui.segments {
            key = func.name
            css {
                marginBottom = 2.rem
            }

            // Function name and category
            ui.segment {
                ui.relaxed.horizontal.list {
                    noui.item {
                        ui.large.header {
                            +func.name
                        }
                    }

                    noui.item {
                        // Category label — click to search by category
                        ui.label {
                            css { cursor = Cursor.pointer }
                            onClick { searchQuery = DocSearch.categoryQuery(func.category) }
                            icon.tag()
                            +func.category
                        }
                        // Tag labels — click to search by tag
                        func.tags.forEach { tag ->
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
                    if (func.aliases.isNotEmpty()) {
                        noui.item {
                            func.aliases.forEach { alias ->
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
            func.variants.forEach { decl ->
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
                    is KlangCallable -> if (decl.receiver == null) "Top Level Function"
                    else "${decl.receiver} Extension Function"

                    is KlangObject -> "Object"
                }
            }

            // Signature (code block)
            pre {
                css {
                    backgroundColor = Color("#f4f4f4")
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
