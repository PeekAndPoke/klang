/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.KlangGuiDance
import io.peekandpoke.klang.comp.HelpWindow
import io.peekandpoke.klang.comp.KlangCodeEditorComp
import io.peekandpoke.klang.comp.KlangCodePlaybackCtrl
import io.peekandpoke.klang.comp.LcdDisplay
import io.peekandpoke.klang.comp.QuickHelpPanel
import io.peekandpoke.klang.comp.SongColumn
import io.peekandpoke.klang.comp.SongSettings
import io.peekandpoke.klang.layouts.WorkbenchSlots
import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import io.peekandpoke.klang.storage.EditorSettings
import io.peekandpoke.klang.storage.SongStorage
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.ui.codemirror.BookmarkOptions
import io.peekandpoke.klang.ui.codemirror.Bookmarks
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangFinish
import io.peekandpoke.klang.ui.comp.KlangScreen
import io.peekandpoke.klang.ui.comp.KlangSpinner
import io.peekandpoke.klang.ui.comp.KlangValueKnob
import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.klang.ui.comp.KlangYesNoModal
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

    private val state by subscribingTo(ctrl.state)

    /** The Gui-dance's lit step: Play wears its beacon when it is Play's (KLANG-00350). */
    private val guiDanceLit by subscribingTo(KlangGuiDance.dance.lit)
    private val currentModals by subscribingTo(modals)

    private val codeEditorRef = ComponentRef.Tracker<KlangCodeEditorComp>()

    /** The highlights a sound event draws, from the Settings tool window (KLANG-00349, once "EVT" in the header). */
    private val highlightPerEvent by subscribingTo(EditorSettings.highlightsPerEvent) { newValue ->
        codeEditorRef { it.setMaxHighlightsPerEvent(newValue) }
        ctrl.reemitVoiceSignals()
    }

    /** The column's content this page put into the workbench; null while it put none. */
    private var slotColumn: (DIV.() -> Unit)? = null

    /** This page's own key for its parts in the workbench: two pages never share one, even on one song id ("new"). */
    private val pageKey = ++pagesDrawn

    /** The bottom panel's Quick help and Lexikon (KLANG-00349, KLANG-00365), shared with the tutorials. */
    private val helpPanel = QuickHelpPanel(
        pageKey = "song-$pageKey",
        // The Sprudel tab's tool works through the editor, which knows the code and the playback (KLANG-00374)
        toolContext = { site -> codeEditorRef.getOrNull()?.liveToolContext(site) },
    )

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
        KlangGuiDance.dance.used(KlangGuiDance.SONG_PLAY)

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
                SongColumn(ctrl = ctrl, builtIn = builtIn, onReset = { askToReset() })
            }
        }

        slotColumn = column
        WorkbenchSlots.column(column)
        WorkbenchSlots.tools(
            listOf(
                KlangWorkbench.Tool(id = "settings", title = "Settings", icon = { sliders_horizontal }) { SongSettings() },
            )
        )
        // Under the editor, wide enough for signatures and parameters: the quick help, and the Lexikon beside it
        // (the maintainer, 2026-10-27: the tabs in the panel's header, a tab's own options in its content)
        WorkbenchSlots.bottom(helpPanel.bottom())
    }

    /** Puts the cursor in the song's code: a use clicked in the Quick help's "Used on" list. */
    private fun jumpTo(pos: Int) {
        codeEditorRef { it.jumpTo(pos) }
    }

    /** Empties the workbench's slots, unless another page has filled them since. */
    private fun emptyWorkbench() {
        if (slotColumn != null && WorkbenchSlots.column() === slotColumn) {
            WorkbenchSlots.clear()
        }

        slotColumn = null
    }

    /** Back to the built-in song's code, title and rpm; a playing song goes on, updated to the original (the maintainer). */
    private fun resetToOriginal() {
        builtIn?.let { b ->
            ctrl.setCode(b.code)
            ctrl.setRpm(b.rpm)
            ctrl.setTitle(b.title)
            songStorage.code(b.code)
            codeEditorRef { it.setCode(b.code) }

            // As the Update key does: the playback takes the original's code and keeps going
            if (ctrl.state().isPlaying) {
                ctrl.play()
            }
        }
    }

    /** Asks before the song goes back to the original, which throws away the user's changes (the maintainer). */
    private fun askToReset() {
        val original = builtIn ?: return

        modals.show<Boolean> { handle ->
            KlangYesNoModal(
                handle = handle,
                headline = "Back to the original?",
                content = {
                    +"Your changes to \"${original.title}\" are lost: the code, the title and the tempo go back to the "
                    +"original. A playing song keeps playing, with the original."
                },
                yesText = "Back to the original",
                noText = "Keep my changes",
                onResult = { yes ->
                    if (yes) {
                        resetToOriginal()
                    }
                },
            )
        }
    }

    /** Asks whether to move a bookmark, in Klang's yes or no modal, which answers exactly once. */
    private fun askToMoveBookmark(move: BookmarkOptions.Move, answer: (yes: Boolean) -> Unit) {
        modals.show { handle ->
            KlangYesNoModal(
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
                    // minHeight 0 keeps the form from growing past the page's
                    // height: the header stays pinned and only the editor
                    // wrapper scrolls internally.
                    minHeight = 0.px
                    // One panel: the transport bar and the bezel round the editor's screen
                    put("background", "var(--kl-ground-deep)")
                }

                renderTransportBar()

                // The editor as a screen set into the machine, the panel's ground a bezel round it, as the LCD and the
                // fields are wells in the panel (the maintainer, 2026-10-26: a line across the screen "cut through the
                // entire picture", and without it "it looks too much like a code editor"; its cut corners "obvious but
                // not too obvious", then smaller, no rivets). None of it scrolls; the editor scrolls itself.
                div {
                    key = "dashboard-form-code"
                    css {
                        flex = Flex(1.0, 1.0, FlexBasis.auto)
                        minHeight = 0.px
                        display = Display.flex
                        flexDirection = FlexDirection.column
                        put("margin", "0 10px 10px")
                    }

                    KlangScreen(size = KlangScreen.Size.Large, surface = "#000000") {
                        // Inner CLIP: clips the editor content to the screen. The editor
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
                            }

                            renderEditor()
                        }
                    }
                }
            }
        }
    }

    /**
     * The transport bar over the editor (KLANG-00349, the canvas's board A): play or update, stop, the cycle counter,
     * the RPM on a knob. The song's title and its actions live in the column now, fullscreen in the workbench strip.
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
                // The column's and the strip's ground, one panel with the bezel round the editor's screen
                put("background", "var(--kl-ground-deep)")
            }

            renderPlayButton()

            KlangButton(
                onClick = { ctrl.stop() },
                finish = KlangFinish.Silver,
                surface = KlangButton.Surface.Flat,
                // A pill with its word, as Play (the maintainer)
                shape = KlangButton.Shape.Pill,
                availability = if (state.isPlaying) KlangButton.Availability.Enabled else KlangButton.Availability.Disabled,
            ) {
                OnKey(KlangKeyBindings.Stop, enabled = state.isPlaying, showHint = state.isPlaying) { ctrl.stop() }
                title = "Stop playback"
                icon.stop()
                +"Stop"
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
        }
    }

    /** Play, or update a playing song once its code changed; retry when the engine did not start. */
    private fun DIV.renderPlayButton() {
        val updating = state.isPlaying
        val enabled = !updating || state.isCodeModified

        KlangButton(
            onClick = { onPlay() },
            // Silver and flat, Play and Stop (the maintainer)
            finish = KlangFinish.Silver,
            surface = KlangButton.Surface.Flat,
            shape = KlangButton.Shape.Pill,
            availability = if (enabled) KlangButton.Availability.Enabled else KlangButton.Availability.Disabled,
            beacon = guiDanceLit == KlangGuiDance.SONG_PLAY,
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
            // Alt+Q, and a hover: the name's help in the Help panel, never a popup, the focus staying in the code
            onQuickDocumentation = { symbol, declarationCode, usages ->
                helpPanel.showHelp(entry = HelpWindow.Entry(symbol, declarationCode, usages), jumpTo = ::jumpTo)
            },
            onHoverDocumentation = { symbol, declarationCode, usages ->
                helpPanel.showHelp(entry = HelpWindow.Entry(symbol, declarationCode, usages), jumpTo = ::jumpTo)
            },
            // The call the cursor is in: its tool in the Sprudel tab
            onToolCall = { site -> helpPanel.showToolCall(site) },
        ).track(codeEditorRef)
    }
}
