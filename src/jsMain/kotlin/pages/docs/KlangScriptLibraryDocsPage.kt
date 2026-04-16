package io.peekandpoke.klang.pages.docs

import io.peekandpoke.klang.comp.InViewport
import io.peekandpoke.klang.comp.KlangScriptReplComp
import io.peekandpoke.klang.comp.PlayableCodeExample
import io.peekandpoke.klang.script.KlangScriptLibrary
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.generated.generatedStdlibDocs
import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangCodeSampleType
import io.peekandpoke.klang.script.types.KlangDecl
import io.peekandpoke.klang.script.types.KlangMutability
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.sprudel.lang.docs.registerSprudelDocs
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import io.peekandpoke.klang.ui.comp.MarkdownDisplay
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.urlParam
import io.peekandpoke.kraft.semanticui.forms.UiInputField
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Align
import kotlinx.css.Color
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.Overflow
import kotlinx.css.Padding
import kotlinx.css.alignItems
import kotlinx.css.backgroundColor
import kotlinx.css.borderRadius
import kotlinx.css.color
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.css.height
import kotlinx.css.marginBottom
import kotlinx.css.marginTop
import kotlinx.css.overflow
import kotlinx.css.padding
import kotlinx.css.paddingBottom
import kotlinx.css.paddingTop
import kotlinx.css.pct
import kotlinx.css.px
import kotlinx.css.rem
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.b
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.pre

/**
 * Registry of known library docs providers.
 *
 * Maps library slug (used in URL) to a function that populates a [KlangDocsRegistry].
 */
private val libraryDocsProviders: Map<String, (KlangDocsRegistry) -> Unit> = mapOf(
    "sprudel" to { registry -> registerSprudelDocs(registry) },
    "stdlib" to { registry -> registry.registerAll(generatedStdlibDocs) },
)

/**
 * Maps library slug to the KlangScriptLibrary instances that should be auto-imported
 * when running code samples for that library.
 */
private val libraryAutoImports: Map<String, List<KlangScriptLibrary>> = mapOf(
    "sprudel" to listOf(stdlibLib, sprudelLib),
    "stdlib" to listOf(stdlibLib),
)

@Suppress("FunctionName")
fun Tag.KlangScriptLibraryDocsPage(library: String?) = comp(
    KlangScriptLibraryDocsPage.Props(library = library ?: "")
) {
    KlangScriptLibraryDocsPage(it)
}

class KlangScriptLibraryDocsPage(ctx: Ctx<Props>) : Component<KlangScriptLibraryDocsPage.Props>(ctx) {

    data class Props(
        val library: String,
    )

    companion object {
        const val PARAM_LIBRARY = "library"
        const val PARAM_SEARCH = "search"
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val laf by subscribingTo(KlangTheme)
    private var searchQuery: String by urlParam(name = PARAM_SEARCH, default = "")

    //  REGISTRY  ///////////////////////////////////////////////////////////////////////////////////////////////////

    // Cached per library name to avoid re-registering on every render
    private var cachedLibrary: String? = null
    private var cachedRegistry: KlangDocsRegistry? = null

    private val registry: KlangDocsRegistry
        get() {
            val lib = props.library
            if (lib != cachedLibrary || cachedRegistry == null) {
                cachedLibrary = lib
                cachedRegistry = KlangDocsRegistry().apply {
                    libraryDocsProviders[lib]?.invoke(this)
                }
            }
            return cachedRegistry!!
        }

    //  DERIVED DATA  ///////////////////////////////////////////////////////////////////////////////////////////

    private val filteredSymbols: List<KlangSymbol>
        get() {
            val terms = LibraryDocSearch.parseTerms(searchQuery)
            val all = registry.symbols.values.sortedBy { it.name }

            return when {
                terms.isNotEmpty() -> all
                    .filter { LibraryDocSearch.matches(it, terms) }
                    .sortedByDescending { LibraryDocSearch.score(it, terms) }

                else -> all
            }
        }

    //  RENDERING  //////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        ui.fluid.container {
            css {
                padding = Padding(2.rem)
            }

            // Header showing which library
            val library = props.library
            if (library.isNotEmpty()) {
                ui.segment {
                    ui.huge.header {
                        css { put("color", "${laf.textPrimary} !important") }
                        +"${library.replaceFirstChar { it.uppercase() }} Library Reference"
                    }
                }
            }

            // Search and filters
            div {
                ui.form {
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
                    ui.header {
                        if (libraryDocsProviders.containsKey(library)) {
                            +"No functions found"
                        } else {
                            +"Unknown library: $library"
                        }
                    }
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
                        ui.label {
                            css { cursor = Cursor.pointer }
                            onClick { searchQuery = LibraryDocSearch.categoryQuery(symbol.category) }
                            icon.tag()
                            +symbol.category
                        }
                        symbol.tags.forEach { tag ->
                            ui.label {
                                key = tag
                                css { cursor = Cursor.pointer }
                                onClick { searchQuery = LibraryDocSearch.tagQuery(tag) }
                                icon.hashtag()
                                +tag
                            }
                        }
                    }

                    if (symbol.aliases.isNotEmpty()) {
                        noui.item {
                            symbol.aliases.forEach { alias ->
                                key = alias
                                ui.label {
                                    css { cursor = Cursor.pointer }
                                    onClick { searchQuery = LibraryDocSearch.functionQuery(alias) }
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
                            val opt = if (param.isOptional && !param.isVararg) "?" else ""
                            val def = if (param.defaultDoc != null && !param.isVararg) " = ${param.defaultDoc}" else ""
                            +" (${param.type}$opt$def): ${param.description}"
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
                val autoImports = libraryAutoImports[props.library] ?: listOf(stdlibLib)
                decl.samples.forEach { sample ->
                    key = sample.code
                    InViewport {
                        when (sample.type) {
                            KlangCodeSampleType.PLAYABLE -> PlayableCodeExample(code = sample.code)
                            KlangCodeSampleType.EXECUTABLE -> KlangScriptReplComp(
                                initialCode = sample.code,
                                autoImportLibraries = autoImports,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Search syntax constants and matching logic for library docs pages.
 */
internal object LibraryDocSearch {
    const val PREFIX_CATEGORY = "category:"
    const val PREFIX_TAG = "tag:"
    const val PREFIX_FUNCTION = "function:"

    fun categoryQuery(name: String) = "$PREFIX_CATEGORY$name"
    fun tagQuery(name: String) = "$PREFIX_TAG$name"
    fun functionQuery(name: String) = "$PREFIX_FUNCTION$name"

    fun parseTerms(query: String): List<String> =
        query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    fun matches(symbol: KlangSymbol, terms: List<String>): Boolean =
        terms.all { term -> matchesTerm(symbol, term) }

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
                val name = symbol.name.lowercase()
                val nameScore = when {
                    name == lower -> 1000
                    name.startsWith(lower) -> 500
                    name.contains(lower) -> 100
                    else -> 0
                }
                val aliasScore = symbol.aliases.maxOfOrNull { alias ->
                    val a = alias.lowercase()
                    when {
                        a == lower -> 80
                        a.startsWith(lower) -> 40
                        a.contains(lower) -> 20
                        else -> 0
                    }
                } ?: 0
                val tagScore = symbol.tags.maxOfOrNull { tag ->
                    val t = tag.lowercase()
                    when {
                        t == lower -> 10
                        t.contains(lower) -> 5
                        else -> 0
                    }
                } ?: 0
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
                symbol.name.lowercase().contains(lower) ||
                        symbol.aliases.any { it.lowercase().contains(lower) } ||
                        symbol.tags.any { it.lowercase().contains(lower) } ||
                        symbol.category.lowercase().contains(lower) ||
                        symbol.library.lowercase().contains(lower)
            }
        }
    }
}
