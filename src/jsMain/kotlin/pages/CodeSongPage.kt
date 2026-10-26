/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.comp.FullscreenToggleButton
import io.peekandpoke.klang.comp.KlangCodeEditorComp
import io.peekandpoke.klang.comp.KlangCodePlaybackCtrl
import io.peekandpoke.klang.comp.LcdDisplay
import io.peekandpoke.klang.fs
import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import io.peekandpoke.klang.storage.SongStorage
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.ui.codemirror.BookmarkOptions
import io.peekandpoke.klang.ui.codemirror.Bookmarks
import io.peekandpoke.klang.ui.comp.YesNoModal
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.klang.version
import io.peekandpoke.klang.workshop.keys.OnKey
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import io.peekandpoke.kraft.semanticui.forms.UiInputField
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
import kotlinx.browser.window
import kotlinx.css.Align
import kotlinx.css.Color
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.Flex
import kotlinx.css.FlexBasis
import kotlinx.css.FlexDirection
import kotlinx.css.JustifyContent
import kotlinx.css.Overflow
import kotlinx.css.Padding
import kotlinx.css.alignItems
import kotlinx.css.color
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.css.flex
import kotlinx.css.flexDirection
import kotlinx.css.flexShrink
import kotlinx.css.height
import kotlinx.css.justifyContent
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

    /** Shows the check mark on the copy button for a moment after a successful copy. */
    private var justCopied by value(false)

    private var highlightPerEvent by value(15) { newValue ->
        codeEditorRef { it.setMaxHighlightsPerEvent(newValue) }
        ctrl.reemitVoiceSignals()
    }

    val isBuiltInModified get() = builtIn != null && builtIn.code != state.code

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
            }
            onUnmount {
                ctrl.stop()
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun onPlay() {
        // Persist the current code before starting (matches original strategy of persist-on-play).
        songStorage.code(ctrl.state().code)
        ctrl.play()
    }

    private fun copyCode() {
        window.asDynamic().navigator.clipboard.writeText(ctrl.state().code)
        justCopied = true
        window.setTimeout({ justCopied = false }, 2000)
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
                ui.basic.segment {
                    key = "dashboard-form-segment"

                    css {
                        flexShrink = 0.0
                        // Balanced vertical padding — Fomantic's segment default is
                        // 1em top with our old 0 bottom, which read lopsided
                        put("padding", "13px 14px")
                        // Fomantic gives segments a 1rem bottom margin — that was
                        // the black gap between header and editor
                        put("margin", "0")
                    }

                    ui.horizontal.list {
                        key = "dashboard-form-fields"

                        css {
                            // Centered flex row — also vertically centers the
                            // mixed-height items (buttons, LCD, inputs, icons)
                            display = Display.flex
                            justifyContent = JustifyContent.center
                            alignItems = Align.center
                            put("flex-wrap", "wrap")
                        }

                        // Play / Update / Stop controls
                        noui.item {
                            if (!state.isPlaying) {
                                ui.circular.white.button {
                                    OnKey(KlangKeyBindings.PlayOrUpdate) { onPlay() }
                                    onClick { onPlay() }
                                    if (state.isPlayerFailed) {
                                        // The engine did not start. Clicking retries: Player.ensure()
                                        // drops its memoized deferred on failure.
                                        icon.black.exclamation_triangle()
                                        +"Retry"
                                    } else if (state.isPlayerLoading) {
                                        icon.black.loading.spinner()
                                        +"Loading"
                                    } else {
                                        icon.play {
                                            css {
                                                put("--icon-glow-color", laf.critical)
                                                put("animation", "iconGlow 2.5s ease-in-out infinite")
                                            }
                                        }
                                        +"Play"
                                    }
                                }
                            } else {
                                ui.circular.white
                                    .givenNot(state.isCodeModified) { disabled }.button {
                                        OnKey(
                                            action = KlangKeyBindings.PlayOrUpdate,
                                            enabled = state.isCodeModified,
                                            showHint = state.isCodeModified,
                                        ) { onPlay() }
                                        onClick { onPlay() }
                                        if (state.isCodeModified) {
                                            icon.redo_alternate {
                                                css {
                                                    put("--icon-glow-color", laf.critical)
                                                    put("animation", "iconGlow 2.5s ease-in-out infinite")
                                                }
                                            }
                                        } else {
                                            icon.black.redo_alternate()
                                        }
                                        +"Update"
                                    }
                            }

                            ui.circular.white
                                .givenNot(state.isPlaying) { disabled }
                                .given(state.isPlaying) { white }.icon.button {
                                    OnKey(KlangKeyBindings.Stop, enabled = state.isPlaying, showHint = state.isPlaying) { ctrl.stop() }
                                    onClick { ctrl.stop() }
                                    title = "Stop playback"
                                    icon.black.stop()
                                }
                        }

                        noui.top.aligned.item {
                            LcdDisplay(value = state.currentCycle, digits = 4, dim = !state.isPlaying)
                        }

                        if (isBuiltInModified) {
                            noui.item {
                                ui.circular.white.icon.button {
                                    onClick { resetToOriginal() }
                                    title = "Reset to original code"
                                    icon.black.undo()
                                }
                            }
                        }

                        // RPM field
                        noui.item {
                            css { width = 140.px }
                            UiInputField(state.rpm, { ctrl.setRpm(it) }) {
                                step(0.5)
                                appear { large }
                                wrapFieldWith { fluid }
                                leftLabel {
                                    ui.grey.label {
                                        title = "Revolutions per minute"
                                        +"RPM"
                                    }
                                }
                            }
                        }

                        // Highlight-per-event field
                        noui.item {
                            css { width = 140.px }
                            UiInputField(highlightPerEvent, { highlightPerEvent = it }) {
                                step(1)
                                appear { large }
                                wrapFieldWith { fluid }
                                leftLabel {
                                    ui.grey.label {
                                        title = "Max highlights per audio event"
                                        +"EVT"
                                    }
                                }
                            }
                        }

                        // Title field
                        noui.item {
                            css { width = 300.px }
                            UiInputField(state.title ?: "", { ctrl.setTitle(it) }) {
                                placeholder("Song title")
                                appear { large }
                                wrapFieldWith { fluid }
                                leftLabel {
                                    title = "Song title"
                                    ui.grey.label { +"Title" }
                                }
                            }
                        }


                        // Copy the whole editor content to the clipboard
                        noui.item {
                            div {
                                css {
                                    cursor = Cursor.pointer
                                    display = Display.inlineBlock

                                    if (justCopied) {
                                        color = Color(laf.good)
                                    }
                                }
                                onClick { copyCode() }
                                title = if (justCopied) "Copied to clipboard" else "Copy the code to the clipboard"

                                if (justCopied) {
                                    icon.circular.check()
                                } else {
                                    icon.circular.copy()
                                }
                            }
                        }

                        // Fullscreen toggle
                        noui.item {
                            FullscreenToggleButton(fs = fs)
                        }
                    }
                }

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

    private fun DIV.renderEditor() {
        KlangCodeEditorComp(
            ctrl = ctrl,
            availableLibraries = listOf(stdlibLib, sprudelLib),
            maxHighlightsPerEvent = highlightPerEvent,
            pauseHighlightsWhen = { currentModals.isNotEmpty() },
            showMinimap = true,
            bookmarks = bookmarks,
            showKeysOverview = true,
        ).track(codeEditorRef)
    }
}
