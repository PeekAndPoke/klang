package io.peekandpoke.klang.pages

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
import io.peekandpoke.klang.script.docs.DslDocsRegistry
import io.peekandpoke.klang.script.docs.DslType
import io.peekandpoke.klang.script.docs.FunctionDoc
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
    fun matches(func: FunctionDoc, terms: List<String>): Boolean =
        terms.all { term -> matchesTerm(func, term) }

    private fun matchesTerm(func: FunctionDoc, term: String): Boolean {
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
    private val registry = DslDocsRegistry().apply {
        io.peekandpoke.klang.strudel.lang.docs.registerStrudelDocs(this)
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private var searchQuery: String by urlParam(name = PARAM_SEARCH, default = "")

    //  DERIVED DATA  ///////////////////////////////////////////////////////////////////////////////////////////

    private val filteredFunctions: List<FunctionDoc>
        get() {
            val terms = DocSearch.parseTerms(searchQuery)
            val all = registry.functions.values.sortedBy { it.name }

            return when {
                // Smart search (supports prefixed terms + logical AND)
                terms.isNotEmpty() -> all.filter { DocSearch.matches(it, terms) }
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
                renderFunctionDoc(func)
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

    private fun DIV.renderFunctionDoc(func: FunctionDoc) {
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
            func.variants.forEach { variant ->
                ui.attached.segment {
                    renderVariant(variant)
                }
            }
        }
    }

    private fun DIV.renderVariant(variant: io.peekandpoke.klang.script.docs.VariantDoc) {
        div {
            css {
                marginTop = 1.rem
                marginBottom = 1.rem
            }

            // Variant type badge
            ui.label {
                +when (variant.type) {
                    DslType.TOP_LEVEL -> "Top Level Function"
                    DslType.EXTENSION_METHOD -> "Extension Function"
                    DslType.PROPERTY -> "Property"
                    DslType.OBJECT -> "Object"
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
                    +variant.signature
                }
            }

            // Description
            p {
                css {
                    marginTop = 0.5.rem
                    marginBottom = 0.5.rem
                }
                +variant.description
            }

            // Parameters
            if (variant.params.isNotEmpty()) {
                ui.header H4 {
                    css {
                        marginTop = 0.75.rem
                    }
                    +"Parameters"
                }
                ui.list {
                    variant.params.forEach { param ->
                        noui.item {
                            key = param.name
                            b { +param.name }
                            +" (${param.type}): ${param.description}"
                        }
                    }
                }
            }

            // Return value
            if (variant.returnDoc.isNotEmpty()) {
                ui.header H4 {
                    css {
                        marginTop = 0.75.rem
                    }
                    +"Returns"
                }
                p {
                    +variant.returnDoc
                }
            }

            // Examples
            if (variant.samples.isNotEmpty()) {
                ui.header H4 {
                    css {
                        marginTop = 0.75.rem
                    }
                    +"Examples"
                }
                variant.samples.forEach { sample ->
                    key = sample
                    InViewport {
                        PlayableCodeExample(code = sample)
                    }
                }
            }
        }
    }
}
