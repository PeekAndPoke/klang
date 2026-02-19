package io.peekandpoke.klang.pages

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.semanticui.forms.UiInputField
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.key
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.noui
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.comp.PlayableCodeExample
import io.peekandpoke.klang.script.docs.DslDocsRegistry
import io.peekandpoke.klang.script.docs.DslType
import io.peekandpoke.klang.script.docs.FunctionDoc
import kotlinx.css.*
import kotlinx.html.*

@Suppress("FunctionName")
fun Tag.StrudelDocsPage() = comp {
    StrudelDocsPage(it)
}

class StrudelDocsPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private var searchQuery: String by value("")
    private var selectedCategory: String? by value(null)

    //  DERIVED DATA  ///////////////////////////////////////////////////////////////////////////////////////////

    private val allCategories: List<String>
        get() = DslDocsRegistry.categories

    private val filteredFunctions: List<FunctionDoc>
        get() {
            val query = searchQuery.trim()
            val category = selectedCategory

            return when {
                // Search has priority
                query.isNotEmpty() -> DslDocsRegistry.search(query)
                // Then category filter
                category != null -> DslDocsRegistry.getFunctionsByCategory(category)
                // Show all by default
                else -> DslDocsRegistry.functions.values.sortedBy { it.name }
            }
        }

    //  RENDERING  //////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        ui.fluid.container {
            css {
                padding = Padding(2.rem)
            }

            // Header
            ui.header H1 {
                css {
                    marginBottom = 2.rem
                }
                +"Strudel DSL Reference"
            }

            // Search and filters
            ui.segment {
                ui.form {
                    // Search box
                    div {
                        UiInputField(value = searchQuery, onChange = { searchQuery = it }) {

                            placeholder("Search functions, categories, or tags")

                            leftLabel {
                                ui.basic.label { icon.search(); +"Search" }
                            }
                        }
                    }

                    // Category filter buttons
                    ui.buttons {
                        css {
                            marginTop = 1.rem
                        }

                        // "All" button
                        button(classes = "ui ${if (selectedCategory == null) "primary" else ""} button") {
                            onClick {
                                selectedCategory = null
                            }
                            +"All"
                        }

                        // Category buttons
                        allCategories.forEach { category ->
                            key = category

                            button(classes = "ui ${if (selectedCategory == category) "primary" else ""} button") {
                                onClick {
                                    selectedCategory = category
                                }
                                +category.replaceFirstChar { it.uppercase() }
                            }
                        }
                    }
                }
            }

            // Results count
            ui.message {
                css {
                    marginTop = 1.rem
                }
                +"Showing ${filteredFunctions.size} function(s)"
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
        ui.segment {
            key = func.name
            css {
                marginBottom = 2.rem
            }

            // Function name and category
            ui.header H2 {
                +func.name

                // Aliases
                if (func.aliases.isNotEmpty()) {
                    noui.sub.header {
                        css {
                            display = Display.inlineBlock
                            marginLeft = 1.rem
                            color = Color("#666")
                        }
                        +"(aliases: ${func.aliases.joinToString(", ")})"
                    }
                }

                noui.sub.header {
                    css {
                        display = Display.inlineBlock
                        marginLeft = 1.rem
                    }
                    ui.label {
                        icon.tag()
                        +func.category
                    }
                    // Tags
                    func.tags.forEach { tag ->
                        ui.label {
                            key = tag
                            icon.hashtag()
                            +tag
                        }
                    }
                }
            }

            // Variants
            func.variants.forEach { variant ->
                renderVariant(variant)
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
            ui.mini.label {
                +when (variant.type) {
                    DslType.TOP_LEVEL -> "Function"
                    DslType.EXTENSION_METHOD -> "Extension"
                    DslType.PROPERTY -> "Property"
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
                    PlayableCodeExample(code = sample)
                }
            }
        }
    }
}
