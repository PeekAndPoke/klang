/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.comp.FullscreenToggleButton
import io.peekandpoke.klang.comp.HelpWindow
import io.peekandpoke.klang.comp.KlangCodeEditorComp
import io.peekandpoke.klang.comp.KlangCodePlaybackCtrl
import io.peekandpoke.klang.comp.LcdDisplay
import io.peekandpoke.klang.comp.SongColumn
import io.peekandpoke.klang.comp.SongSettings
import io.peekandpoke.klang.fs
import io.peekandpoke.klang.layouts.WorkbenchSlots
import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import io.peekandpoke.klang.storage.EditorSettings
import io.peekandpoke.klang.storage.SongStorage
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.ui.codemirror.BookmarkOptions
import io.peekandpoke.klang.ui.codemirror.Bookmarks
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangSpinner
import io.peekandpoke.klang.ui.comp.KlangValueKnob
import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.klang.ui.comp.YesNoModal
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.klang.version
import io.peekandpoke.klang.workshop.keys.OnKey
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
import kotlinx.browser.window
import kotlinx.css.Display
import kotlinx.css.Flex
import kotlinx.css.FlexBasis
import kotlinx.css.FlexDirection
import kotlinx.css.Overflow
import kotlinx.css.Padding
import kotlinx.css.color
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.css.flex
import kotlinx.css.flexDirection
import kotlinx.css.height
import kotlinx.css.minHeight
import kotlinx.css.overflowX
import kotlinx.css.overflowY
import kotlinx.css.padding
import kotlinx.css.paddingLeft
import kotlinx.css.pct
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.CodeSongPage(
    id: String?,
) = comp(
    CodeSongPage.Props(id = id)
) {
    CodeSongPage(it)
}

class CodeSongPage(ctx: Ctx<Props>) : Component<CodeSongPage.Props>(ctx) {

    private companion object {
        /** The Help tool window's id, which Alt+Q shows. */
        const val HELP_TOOL = "help"

        /** Counts the pages drawn, for [pageKey]. */
        var pagesDrawn = 0
    }

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val id: String?,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val v by subscribingTo(version.map { it.version })

    val defaultCode = """
        import * from "stdlib"
        import * from "sprudel"

        sound("bd hh sd oh")
    """.trimIndent()

    val songId get() = props.id ?: "new"

    val builtIn = BuiltInSongs.songs.firstOrNull { it.id == songId }

    /** What the browser keeps of this song: its code, title, rpm and bookmarks. */
    private val songStorage = SongStorage(
        version = v,
        songId = songId,
        defaultCode = builtIn?.code ?: defaultCode,
        defaultTitle = builtIn?.title ?: "New Song",
        defaultRpm = builtIn?.rpm ?: 30.0,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val ctrl = KlangCodePlaybackCtrl.builder()
        .code(songStorage.code())
        .rpm(songStorage.rpm())
        .title(songStorage.title())
        .build()

    /** The editor's bookmarks (KLANG-00323): it starts from the stored ones, and every change is stored. */
    private val bookmarks = BookmarkOptions(
        initial = songStorage.bookmarks().map { (digit, line) -> Bookmarks.Line(digit = digit, line = line) },
        onChanged = { lines -> songStorage.bookmarks(lines.associate { it.digit to it.line }) },
        askToMove = { move, answer -> askToMoveBookmark(move = move, answer = answer) },
    )

    @Suppress("unused")
    private val laf by subscribingTo(KlangTheme)
    private val state by subscribingTo(ctrl.state)
    private val currentModals by subscribingTo(modals)

    private val codeEditorRef = ComponentRef.Tracker<KlangCodeEditorComp>()

    /** The highlights a sound event draws, from the Settings tool window (KLANG-00349, once "EVT" in the header). */
    private val highlightPerEvent by subscribingTo(EditorSettings.highlightsPerEvent) { newValue ->
        codeEditorRef { it.setMaxHighlightsPerEvent(newValue) }
        ctrl.reemitVoiceSignals()
    }

    /** The name Alt+Q last asked about, for the Help tool window (KLANG-00349); null before the first. */
    private val help = StreamSource<HelpWindow.Entry?>(null)

    /** The column's content this page put into the workbench; null while it put none. */
    private var slotColumn: (DIV.() -> Unit)? = null

    /** This page's own key for its parts in the workbench: two pages never share one, even on one song id ("new"). */
    private val pageKey = ++pagesDrawn

    // On rpm changes: persist to localStorage AND cancel highlights to avoid stale timing across tempo shifts.
    @Suppress("unused")
    private val rpmChange by subscribingTo(ctrl.state.map { it.rpm }.distinct()) { newRpm ->
        songStorage.rpm(newRpm)
        codeEditorRef { editor -> editor.cancelHighlights() }
    }

    // When playback stops by any path (button, unmount, exclusive takeover), drop pending highlights.
    @Suppress("unused")
    private val playingChange by subscribingTo(ctrl.state.map { it.isPlaying }.distinct()) { isPlaying ->
        if (!isPlaying) {
            codeEditorRef { editor -> editor.cancelHighlights() }
        }
    }

    // Persist title changes back to the localStorage-backed stream.
    @Suppress("unused")
    private val titlePersist by subscribingTo(ctrl.state.map { it.title }.distinct()) { newTitle ->
        if (newTitle != null) {
            songStorage.title(newTitle)
        }
    }

    init {
        lifecycle {
            onMount {
                codeEditorRef { it.setMaxHighlightsPerEvent(highlightPerEvent) }
                fillWorkbench()
            }
            onUnmount {
                ctrl.stop()
                emptyWorkbench()
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun onPlay() {
        // Persist the current code before starting (matches original strategy of persist-on-play).
        songStorage.code(ctrl.state().code)
        ctrl.play()
    }

    /**
     * The song in the workbench's column, and the page's tool windows (KLANG-00349). A copy of the page that the
     * router draws fading out (inside its inert layer) leaves the workbench alone: the page just opened owns it.
     */
    private fun fillWorkbench() {
        if (dom?.closest("[inert]") != null) {
            return
        }

        // Keyed by the page: from one song straight to another (a history jump), the workbench draws them anew
        // rather than handing this page's parts to the next one, still listening to this page's streams
        val column: DIV.() -> Unit = {
            div {
                key = "song-column-$pageKey"
                SongColumn(ctrl = ctrl, builtIn = builtIn, onReset = { resetToOriginal() })
            }
        }

        slotColumn = column
        WorkbenchSlots.column(column)
        WorkbenchSlots.tools(
            listOf(
                KlangWorkbench.Tool(id = HELP_TOOL, title = "Help", icon = { question_circle }) {
                    div {
                        key = "song-help-$pageKey"
                        HelpWindow(entry = help)
                    }
                },
                KlangWorkbench.Tool(id = "settings", title = "Settings", icon = { sliders_horizontal }) { SongSettings() },
            )
        )
    }

    /** Empties the workbench's slots, unless another page has filled them since. */
    private fun emptyWorkbench() {
        if (slotColumn != null && WorkbenchSlots.column() === slotColumn) {
            WorkbenchSlots.clear()
        }

        slotColumn = null
    }

    private fun resetToOriginal() {
        builtIn?.let { b ->
            ctrl.stop()
            ctrl.setCode(b.code)
            ctrl.setRpm(b.rpm)
            ctrl.setTitle(b.title)
            songStorage.code(b.code)
            codeEditorRef { it.setCode(b.code) }
        }
    }

    /** Asks whether to move a bookmark, in Klang's yes or no modal, which answers exactly once. */
    private fun askToMoveBookmark(move: BookmarkOptions.Move, answer: (yes: Boolean) -> Unit) {
        modals.show { handle ->
            YesNoModal(
                handle = handle,
                headline = "Move bookmark ${move.digit}?",
                content = { +"Bookmark ${move.digit} is on line ${move.fromLine}. Move it to line ${move.toLine}?" },
                yesText = "Move",
                noText = "Keep",
                onResult = answer,
            )
        }
    }


    //  RENDER  /////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {

        ui.fluid.container.with("chrome-bg") {
            key = "make-song-page"
            css {
                display = Display.flex
                flexDirection = FlexDirection.column
                // The workbench's content height, which a bottom panel may share
                height = 100.pct
                padding = Padding(0.px)
            }

            // Esc anywhere on the page (from a field too, unless it uses its Esc) puts the focus back into the editor, at
            // its last cursor; an open code tool or panel answers Esc first (their priority is higher)
            OnKey(KlangKeyBindings.FocusEditor, showHint = false) { codeEditorRef { it.focus() } }

            ui.form {
                key = "dashboard-form"
                css {
                    display = Display.flex
                    flexDirection = FlexDirection.column
                    flex = Flex(1.0, 1.0, FlexBasis.auto)
                    // minHeight 0 (NOT overflow:hidden, which would clip the
                    // editor's glow) keeps the form from growing past the page's
                    // height: the header stays pinned and only the editor
                    // wrapper scrolls internally.
                    minHeight = 0.px
                }
                // Transparent — shows the page container's chrome-bg, so the
                // rounded editor corner reveals the same surface with no seam
                renderTransportBar()

                // Outer FRAME — carries the accent strips, corner radius, glow
                // and black surface. It does NOT scroll, so scrolled editor
                // content can never paint over the frame lines.
                div {
                    key = "dashboard-form-code"
                    css {
                        flex = Flex(1.0, 1.0, FlexBasis.auto)
                        minHeight = 0.px
                        display = Display.flex
                        flexDirection = FlexDirection.column
                        // Accent frame — drawn as 1px background gradient strips
                        // instead of real borders, so each line can fade
                        // independently AND follow the rounded corner:
                        //  · top line fades to 33% alpha over its last 20% of width
                        //  · left line fades to 33% alpha over its last third of height
                        // The 1px paddings keep the scroller off the strips.
                        put("border-top-left-radius", "3px")
                        put("padding-top", "1px")
                        put("padding-left", "1px")
                        put(
                            "background-image",
                            "linear-gradient(to right, ${laf.accentMuted} 0%, ${laf.accentMuted} 80%, ${laf.accentMuted}55 100%)," +
                                    " linear-gradient(to bottom, ${laf.accentMuted} 0%, ${laf.accentMuted} 66%, ${laf.accentMuted}55 100%)"
                        )
                        put("background-repeat", "no-repeat")
                        put("background-size", "100% 1px, 1px 100%")
                        // Black like the editor surface — otherwise any sub-pixel
                        // gap between the frame and the editor shows page chrome
                        put("background-color", "#000000")
                        // Soft accent light from the editor's top and left edges
                        put(
                            "box-shadow",
                            "0 -10px 22px ${laf.accentMuted}2e, -10px 0 22px ${laf.accentMuted}2e"
                        )
                    }

                    // Inner CLIP — clips the editor content just inside the
                    // frame; its small radius hugs the outer curve. The editor
                    // fills it and scrolls itself, which the minimap needs
                    // (`klang-editor-fill` in klang.css passes the height down).
                    div("klang-editor-fill") {
                        key = "dashboard-form-code-scroll"
                        css {
                            flex = Flex(1.0, 1.0, FlexBasis.auto)
                            minHeight = 0.px
                            overflowY = Overflow.hidden
                            overflowX = Overflow.hidden
                            display = Display.flex
                            flexDirection = FlexDirection.column
                            paddingLeft = 12.px
                            put("border-top-left-radius", "2px")
                        }

                        renderEditor()
                    }
                }
            }
        }
    }

    /**
     * The transport bar over the editor (KLANG-00349, the canvas's board A): play or update, stop, the cycle counter,
     * the RPM on a knob, and the fullscreen key. The song's title and its actions live in the column now.
     */
    private fun DIV.renderTransportBar() {
        div {
            key = "transport-bar"
            css {
                put("display", "flex")
                put("align-items", "center")
                put("gap", "12px")
                put("flex-shrink", "0")
                put("padding", "10px 14px")
            }

            renderPlayButton()

            KlangButton(
                onClick = { ctrl.stop() },
                surface = KlangButton.Surface.Domed,
                shape = KlangButton.Shape.Round,
                availability = if (state.isPlaying) KlangButton.Availability.Enabled else KlangButton.Availability.Disabled,
                ariaLabel = "Stop",
            ) {
                OnKey(KlangKeyBindings.Stop, enabled = state.isPlaying, showHint = state.isPlaying) { ctrl.stop() }
                title = "Stop playback"
                icon.stop()
            }

            LcdDisplay(value = state.currentCycle, digits = 4, dim = !state.isPlaying)

            KlangValueKnob(
                name = "Revolutions per minute",
                value = state.rpm,
                range = 10.0..120.0,
                step = 0.5,
                onChange = { ctrl.setRpm(it) },
                unit = "RPM",
                decimals = 2,
                perPixel = 0.05,
            )

            div { css { put("flex-grow", "1") } }

            FullscreenToggleButton(fs = fs)
        }
    }

    /** Play, or update a playing song once its code changed; retry when the engine did not start. */
    private fun DIV.renderPlayButton() {
        val updating = state.isPlaying
        val enabled = !updating || state.isCodeModified

        KlangButton(
            onClick = { onPlay() },
            surface = KlangButton.Surface.Domed,
            shape = KlangButton.Shape.Pill,
            availability = if (enabled) KlangButton.Availability.Enabled else KlangButton.Availability.Disabled,
        ) {
            OnKey(action = KlangKeyBindings.PlayOrUpdate, enabled = enabled, showHint = enabled) { onPlay() }

            when {
                updating -> {
                    icon.redo_alternate()
                    +"Update"
                }

                state.isPlayerFailed -> {
                    // The engine did not start: a click retries, Player.ensure() drops its memoized deferred on failure
                    icon.exclamation_triangle()
                    +"Retry"
                }

                state.isPlayerLoading -> {
                    KlangSpinner(label = "Loading the Motor", size = 14.px)
                    +"Loading"
                }

                else -> {
                    icon.play()
                    +"Play"
                }
            }
        }
    }

    private fun DIV.renderEditor() {
        KlangCodeEditorComp(
            ctrl = ctrl,
            availableLibraries = listOf(stdlibLib, sprudelLib),
            maxHighlightsPerEvent = highlightPerEvent,
            pauseHighlightsWhen = { currentModals.isNotEmpty() },
            showMinimap = true,
            bookmarks = bookmarks,
            showKeysOverview = true,
            // Alt+Q: the name's help in the Help window, the focus staying in the code
            onQuickDocumentation = { symbol, declarationCode ->
                help(HelpWindow.Entry(symbol = symbol, declarationCode = declarationCode))
                WorkbenchSlots.workbench { it.showTool(HELP_TOOL) }
            },
        ).track(codeEditorRef)
    }
}
