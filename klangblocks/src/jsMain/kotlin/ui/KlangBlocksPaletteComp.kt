package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.html.onInput
import de.peekandpoke.ultra.html.onMouseDown
import de.peekandpoke.ultra.semanticui.icon
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
    ctx: KlangBlocksCtx,
) = comp(
    KlangBlocksPaletteComp.Props(
        availableLibraries = availableLibraries,
        ctx = ctx,
    )
) {
    KlangBlocksPaletteComp(it)
}

class KlangBlocksPaletteComp(ctx: Ctx<Props>) : Component<KlangBlocksPaletteComp.Props>(ctx) {

    data class Props(
        val availableLibraries: List<KlangScriptLibrary>,
        val ctx: KlangBlocksCtx,
    )

    private var searchQuery: String by value("")

    override fun VDom.render() {
        val query = searchQuery.trim().lowercase()
        val importedLibraryNames = props.ctx.editing.program.statements
            .filterIsInstance<KBImportStmt>().map { it.libraryName }.toSet()
        val importedLibraries = props.availableLibraries.filter { it.name in importedLibraryNames }
        val notImportedLibraries = props.availableLibraries.filter { it.name !in importedLibraryNames }

        val theme = props.ctx.theme
        val styles = theme.styles
        div(classes = styles.paletteContainer()) {

            // Undo / Redo toolbar
            val canUndo = props.ctx.editing.canUndo
            val canRedo = props.ctx.editing.canRedo
            div(classes = styles.paletteToolbar()) {
                // Undo
                div(classes = styles.paletteToolbarBtn()) {
                    css {
                        cursor = if (canUndo) Cursor.pointer else Cursor.default
                        opacity = if (canUndo) 1.0 else 0.3
                    }
                    onClick { if (canUndo) props.ctx.editing.undo() }
                    if (canUndo) icon.white.undo() else icon.grey.undo()
                }
                // Redo
                div(classes = styles.paletteToolbarBtn()) {
                    css {
                        cursor = if (canRedo) Cursor.pointer else Cursor.default
                        opacity = if (canRedo) 1.0 else 0.3
                    }
                    onClick { if (canRedo) props.ctx.editing.redo() }
                    if (canRedo) icon.white.redo() else icon.grey.redo()
                }
            }

            // Available-but-not-imported libraries
            if (notImportedLibraries.isNotEmpty()) {
                div(classes = styles.paletteSection()) {
                    div(classes = styles.paletteImportLabel()) { +"Import library" }
                    notImportedLibraries.forEach { library ->
                        div(classes = styles.paletteImportBtn()) {
                            onClick { props.ctx.editing.commitImportLibrary(library.name) }
                            span { css { color = Color(theme.textSubdued); marginRight = 4.px }; +"+" }
                            +library.name
                        }
                    }
                }
            }

            // Search input
            div(classes = styles.paletteSection()) {
                input(classes = styles.paletteSearchInput()) {
                    placeholder = "Search blocks…"
                    value = searchQuery
                    onInput { event ->
                        searchQuery = event.asDynamic().target.value as String
                    }
                }
            }

            // Block list — only from imported libraries
            div(classes = styles.paletteBlockList()) {

                if (importedLibraries.isEmpty()) {
                    div {
                        css {
                            color = Color(theme.textDisabled)
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
                            div(classes = styles.paletteCategoryHeader()) {
                                css { color = Color(theme.blockColor(category)) }
                                +category
                            }

                            funcs.sortedBy { it.name }.forEach { doc ->
                                div(classes = styles.paletteBlockItem()) {
                                    css { backgroundColor = Color(theme.blockColor(category)) }
                                    onMouseDown { event ->
                                        event.preventDefault()
                                        props.ctx.dnd.startPaletteDrag(
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
