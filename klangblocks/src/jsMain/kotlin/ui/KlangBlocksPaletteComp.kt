package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.html.onInput
import de.peekandpoke.ultra.html.onMouseDown
import io.peekandpoke.klang.blocks.model.KBImportStmt
import io.peekandpoke.klang.script.KlangScriptLibrary
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangSymbol
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.KlangBlocksPaletteComp(
    availableLibraries: List<KlangScriptLibrary>,
    importedLibraries: Set<KBImportStmt>,
    onImportLibrary: (String) -> Unit,
    onDragStart: (funcName: String, x: Double, y: Double) -> Unit,
) = comp(
    KlangBlocksPaletteComp.Props(
        availableLibraries = availableLibraries,
        importedLibraries = importedLibraries,
        onImportLibrary = onImportLibrary,
        onDragStart = onDragStart,
    )
) {
    KlangBlocksPaletteComp(it)
}

class KlangBlocksPaletteComp(ctx: Ctx<Props>) : Component<KlangBlocksPaletteComp.Props>(ctx) {

    data class Props(
        val availableLibraries: List<KlangScriptLibrary>,
        val importedLibraries: Set<KBImportStmt>,
        val onImportLibrary: (String) -> Unit,
        val onDragStart: (funcName: String, x: Double, y: Double) -> Unit,
    )

    private val importedLibraryNames: Set<String> get() = props.importedLibraries.map { it.libraryName }.toSet()
    private var searchQuery: String by value("")

    override fun VDom.render() {
        val query = searchQuery.trim().lowercase()
        val importedLibraries = props.availableLibraries.filter { it.name in importedLibraryNames }
        val notImportedLibraries = props.availableLibraries.filter { it.name !in importedLibraryNames }

        div {
            css {
                width = 200.px
                flexShrink = 0.0
                backgroundColor = Color("#252535")
                put("border-right", "1px solid #333")
                display = Display.flex
                flexDirection = FlexDirection.column
            }

            // Available-but-not-imported libraries
            if (notImportedLibraries.isNotEmpty()) {
                div {
                    css {
                        padding = Padding(8.px)
                        flexShrink = 0.0
                        put("border-bottom", "1px solid #333")
                    }
                    div {
                        css {
                            color = Color("#888")
                            fontSize = 10.px
                            fontWeight = FontWeight.bold
                            textTransform = TextTransform.uppercase
                            letterSpacing = LinearDimension("0.08em")
                            marginBottom = 6.px
                        }
                        +"Import library"
                    }
                    notImportedLibraries.forEach { library ->
                        div {
                            css {
                                display = Display.inlineFlex
                                alignItems = Align.center
                                put("gap", "4px")
                                backgroundColor = Color("#333")
                                color = Color("#aaa")
                                borderRadius = 4.px
                                padding = Padding(vertical = 3.px, horizontal = 8.px)
                                marginBottom = 3.px
                                fontSize = 12.px
                                fontFamily = "monospace"
                                cursor = Cursor.pointer
                                userSelect = UserSelect.none
                            }
                            onClick { props.onImportLibrary(library.name) }
                            span { css { color = Color("#666"); marginRight = 4.px }; +"+" }
                            +library.name
                        }
                    }
                }
            }

            // Search input
            div {
                css {
                    padding = Padding(8.px)
                    flexShrink = 0.0
                    put("border-bottom", "1px solid #333")
                }
                input {
                    placeholder = "Search blocks…"
                    value = searchQuery
                    onInput { event ->
                        searchQuery = event.asDynamic().target.value as String
                    }
                    css {
                        width = 100.pct
                        backgroundColor = Color("#1e1e2e")
                        color = Color("#ccc")
                        border = Border(1.px, BorderStyle.solid, Color("#444"))
                        borderRadius = 4.px
                        padding = Padding(vertical = 4.px, horizontal = 6.px)
                        fontSize = 12.px
                        outline = Outline.none
                        put("box-sizing", "border-box")
                    }
                }
            }

            // Block list — only from imported libraries
            div {
                css {
                    flex = Flex(1.0, 1.0, FlexBasis.auto)
                    overflowY = Overflow.auto
                    padding = Padding(8.px)
                }

                if (importedLibraries.isEmpty()) {
                    div {
                        css {
                            color = Color("#555")
                            fontSize = 12.px
                            padding = Padding(8.px)
                            textAlign = TextAlign.center
                        }
                        +"Import a library to see blocks"
                    }
                } else {
                    importedLibraries.forEach { library ->
                        val symbolsByCategory = library.docs.symbols.values
                            .filter { hasVisibleBlock(it) }
                            .filter { query.isEmpty() || it.name.lowercase().contains(query) }
                            .groupBy { it.category }
                            .entries
                            .sortedBy { it.key }

                        symbolsByCategory.forEach { entry ->
                            val category = entry.key
                            val funcs = entry.value
                            // Category header
                            div {
                                css {
                                    color = Color(categoryColour(category))
                                    fontSize = 10.px
                                    fontWeight = FontWeight.bold
                                    textTransform = TextTransform.uppercase
                                    letterSpacing = LinearDimension("0.08em")
                                    padding = Padding(vertical = 6.px, horizontal = 4.px)
                                    marginTop = 4.px
                                }
                                +category
                            }

                            funcs.sortedBy { it.name }.forEach { doc ->
                                div {
                                    css {
                                        display = Display.block
                                        backgroundColor = Color(categoryColour(category))
                                        color = Color.white
                                        borderRadius = 6.px
                                        padding = Padding(vertical = 4.px, horizontal = 8.px)
                                        marginBottom = 3.px
                                        fontSize = 12.px
                                        fontFamily = "monospace"
                                        cursor = Cursor.grab
                                        userSelect = UserSelect.none
                                        whiteSpace = WhiteSpace.nowrap
                                        overflow = Overflow.hidden
                                        textOverflow = TextOverflow.ellipsis
                                    }
                                    onMouseDown { event ->
                                        event.preventDefault()
                                        props.onDragStart(
                                            doc.name,
                                            event.clientX.toDouble(),
                                            event.clientY.toDouble(),
                                        )
                                    }
                                    +doc.name
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun hasVisibleBlock(doc: KlangSymbol): Boolean =
    doc.variants.any { it is KlangCallable }
