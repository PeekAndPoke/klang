/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.codemirror.ext.Command
import io.peekandpoke.klang.codemirror.ext.EditorState
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.codemirror.ext.KeyBinding
import io.peekandpoke.klang.codemirror.ext.Prec
import io.peekandpoke.klang.codemirror.ext.acceptCompletion
import io.peekandpoke.klang.codemirror.ext.addCursorAbove
import io.peekandpoke.klang.codemirror.ext.addCursorBelow
import io.peekandpoke.klang.codemirror.ext.autocompletion
import io.peekandpoke.klang.codemirror.ext.bracketMatching
import io.peekandpoke.klang.codemirror.ext.closeBrackets
import io.peekandpoke.klang.codemirror.ext.closeBracketsKeymap
import io.peekandpoke.klang.codemirror.ext.closeCompletion
import io.peekandpoke.klang.codemirror.ext.closeSearchPanel
import io.peekandpoke.klang.codemirror.ext.copyLineDown
import io.peekandpoke.klang.codemirror.ext.crosshairCursor
import io.peekandpoke.klang.codemirror.ext.cursorMatchingBracket
import io.peekandpoke.klang.codemirror.ext.defaultHighlightStyle
import io.peekandpoke.klang.codemirror.ext.drawSelection
import io.peekandpoke.klang.codemirror.ext.dropCursor
import io.peekandpoke.klang.codemirror.ext.findNext
import io.peekandpoke.klang.codemirror.ext.findPrevious
import io.peekandpoke.klang.codemirror.ext.foldGutter
import io.peekandpoke.klang.codemirror.ext.gotoLine
import io.peekandpoke.klang.codemirror.ext.highlightActiveLine
import io.peekandpoke.klang.codemirror.ext.highlightActiveLineGutter
import io.peekandpoke.klang.codemirror.ext.highlightSelectionMatches
import io.peekandpoke.klang.codemirror.ext.highlightSpecialChars
import io.peekandpoke.klang.codemirror.ext.history
import io.peekandpoke.klang.codemirror.ext.indentLess
import io.peekandpoke.klang.codemirror.ext.indentMore
import io.peekandpoke.klang.codemirror.ext.indentOnInput
import io.peekandpoke.klang.codemirror.ext.insertBlankLine
import io.peekandpoke.klang.codemirror.ext.keymap
import io.peekandpoke.klang.codemirror.ext.lineNumbers
import io.peekandpoke.klang.codemirror.ext.moveCompletionSelection
import io.peekandpoke.klang.codemirror.ext.moveLineDown
import io.peekandpoke.klang.codemirror.ext.moveLineUp
import io.peekandpoke.klang.codemirror.ext.nextDiagnostic
import io.peekandpoke.klang.codemirror.ext.openSearchPanel
import io.peekandpoke.klang.codemirror.ext.previousDiagnostic
import io.peekandpoke.klang.codemirror.ext.rectangularSelection
import io.peekandpoke.klang.codemirror.ext.redo
import io.peekandpoke.klang.codemirror.ext.selectLine
import io.peekandpoke.klang.codemirror.ext.selectNextOccurrence
import io.peekandpoke.klang.codemirror.ext.selectParentSyntax
import io.peekandpoke.klang.codemirror.ext.selectSelectionMatches
import io.peekandpoke.klang.codemirror.ext.simplifySelection
import io.peekandpoke.klang.codemirror.ext.splitLine
import io.peekandpoke.klang.codemirror.ext.standardKeymap
import io.peekandpoke.klang.codemirror.ext.startCompletion
import io.peekandpoke.klang.codemirror.ext.syntaxHighlighting
import io.peekandpoke.klang.codemirror.ext.toggleComment
import io.peekandpoke.klang.codemirror.ext.toggleTabFocusMode
import io.peekandpoke.klang.codemirror.ext.undo
import io.peekandpoke.klang.workshop.keys.Keymap
import io.peekandpoke.kraft.utils.jsObject
import org.w3c.dom.events.KeyboardEvent

/**
 * The editor on the key map (KLANG-00325 step 3): one keydown handler matches the editor's actions
 * ([KlangEditorKeyBindings]) with the key manager's own matcher and runs their commands, so the editor answers the keys
 * the table sets, the user's own included, and no hidden CodeMirror key besides.
 *
 * CodeMirror checks its tab focus mode (Esc, then Tab) and an input method's composition before any handler runs, so
 * both keep working. Of the actions a key triggers, the higher priority goes first, then the table's order; each runs
 * its commands until one acts. A key no command acted on goes on: to CodeMirror's text keys, then to the page (an Esc
 * the editor did not use closes the page's panel).
 */
object EditorKeyTable {

    /**
     * Does nothing and keeps the key: a key the table owns in the editor, also where nothing answers it. Before
     * [commands], which uses it: an object's properties start in order.
     */
    private val nothing: Command = { true }

    /** The commands of the table's actions that CodeMirror brings, by action id; an extension adds its own. */
    val commands: Map<String, List<Command>> = with(KlangEditorKeyBindings) {
        mapOf(
            MoveLineUp to listOf(moveLineUp),
            MoveLineDown to listOf(moveLineDown),
            DuplicateLine to listOf(copyLineDown),
            StartNewLine to listOf(insertBlankLine),
            SplitLine to listOf(splitLine),
            Indent to listOf(indentMore),
            Unindent to listOf(indentLess),
            LineComment to listOf(toggleComment),
            BlockComment to listOf(EditorCommands.blockComment),
            Reformat to listOf(EditorCommands.reformat),
            SelectNextOccurrence to listOf(selectNextOccurrence),
            UnselectOccurrence to listOf(EditorCommands.unselectOccurrence),
            SelectAllOccurrences to listOf(selectSelectionMatches),
            ExpandSelection to listOf(consumed(selectParentSyntax)),
            AddCursorAbove to listOf(addCursorAbove),
            AddCursorBelow to listOf(addCursorBelow),
            SelectLine to listOf(selectLine),
            MatchingBracket to listOf(consumed(cursorMatchingBracket)),
            // As CodeMirror's own keymaps did: several cursors become one first, then the search panel closes
            BackToOneCursor to listOf(simplifySelection, closeSearchPanel),
            Find to listOf(openSearchPanel),
            Replace to listOf(EditorCommands.replace),
            FindNext to listOf(consumed(findNext)),
            FindPrevious to listOf(consumed(findPrevious)),
            GoToLine to listOf(gotoLine),
            Undo to listOf(undo),
            Redo to listOf(redo),
            Completion to listOf(startCompletion),
            AcceptCompletion to listOf(acceptCompletion),
            CloseCompletion to listOf(closeCompletion),
            NextProblem to listOf(nextDiagnostic),
            PreviousProblem to listOf(previousDiagnostic),
            TabMovesFocus to listOf(toggleTabFocusMode),
            // The DSL extension's commands take these over where it knows the names; elsewhere (the REPL, the MIDI
            // playground) the keys still never reach the browser (Firefox's bookmarks on Ctrl+B)
            GoToDefinition to listOf(nothing),
            QuickDocumentation to listOf(nothing),
        ).mapKeys { (action, _) -> action.id }
    }

    /**
     * The keys of the search panel's fields (the scope `search-panel`), which sit outside the code and so never reach
     * [extension]'s handler: find again, replace, next and previous match, go to line, and Esc closes the panel and puts the
     * focus back in the code. Only these: Tab moves between the panel's fields, and Esc there leaves the code's selection alone.
     */
    val searchPanelCommands: Map<String, List<Command>> = with(KlangEditorKeyBindings) {
        mapOf(
            Find to listOf(openSearchPanel),
            Replace to listOf(EditorCommands.replace),
            FindNext to listOf(consumed(findNext)),
            FindPrevious to listOf(consumed(findPrevious)),
            BackToOneCursor to listOf(closeSearchPanel),
            // Ctrl+G on a PC: Chrome would take it as its own find next
            GoToLine to listOf(gotoLine),
        ).mapKeys { (action, _) -> action.id }
    }

    /**
     * [command], but its key never reaches the browser while the editor has the focus, also when it did nothing: F3
     * without a further match would open the browser's find bar, Ctrl+I Firefox's page info, Ctrl+Shift+M the browser's
     * profile menu.
     */
    private fun consumed(command: Command): Command = { view ->
        command(view)
        true
    }

    /**
     * The key map the editor reads its keys from: the app's ([app], with the user's keys) when it knows the editor's
     * keys, else the editor's own presets ([own]: no key manager, or one that does not define them).
     */
    fun keymapOf(app: Keymap?, own: Keymap): Keymap = app?.takeIf { it.action(KlangEditorKeyBindings.Undo.id) != null } ?: own

    /**
     * The handler, before CodeMirror's keymaps, and the search panel's keys ([searchPanelCommands]). [currentKeymap] is
     * read on every key, so a key the user changed acts at once; [commands] are the table's plus those of the editor's
     * extensions (its bookmarks).
     */
    fun extension(currentKeymap: () -> Keymap, commands: Map<String, List<Command>>): Extension {
        val handlers = jsObject<dynamic> {
            this.keydown = { event: KeyboardEvent, view: EditorView ->
                run(view = view, event = event, keymap = currentKeymap(), commands = commands)
            }
        }
        val searchPanel = jsObject<KeyBinding> {
            this.scope = SEARCH_PANEL_SCOPE
            this.any = { view, event -> run(view = view, event = event, keymap = currentKeymap(), commands = searchPanelCommands) }
        }

        return arrayOf(
            Prec.high(EditorView.domEventHandlers(handlers)),
            keymap.of(arrayOf(searchPanel)),
        ).unsafeCast<Extension>()
    }

    /** CodeMirror's keymap scope of the search panel's fields. */
    private const val SEARCH_PANEL_SCOPE = "search-panel"

    /** Runs the commands [event] triggers in [keymap]; true when one acted (CodeMirror then prevents the key). */
    internal fun run(view: EditorView, event: KeyboardEvent, keymap: Keymap, commands: Map<String, List<Command>>): Boolean =
        keymap.actionsFor(event)
            .filter { it.scope == KlangEditorKeyBindings.SCOPE }
            .sortedByDescending { it.priority.value }
            .any { action -> commands[action.id].orEmpty().any { command -> command(view) } }

    /**
     * CodeMirror's `basicSetup` without its keymaps (CodeMirror's docs suggest copying it), so the editor answers the
     * table's keys only (maintainer, 2026-10-26: "Only our table"); Alt+Shift+click adds a cursor. What stays: the plain
     * text keys (arrows, Home, Backspace, Enter), the closing brackets' Backspace, and the arrows and pages inside an
     * open completion list, which pass the key on while no list is open.
     */
    fun setup(): Array<Extension> = arrayOf(
        lineNumbers(),
        highlightActiveLineGutter(),
        highlightSpecialChars(),
        history(undefined),
        foldGutter(),
        drawSelection(),
        dropCursor(),
        EditorState.allowMultipleSelections.of(true),
        indentOnInput(),
        syntaxHighlighting(defaultHighlightStyle, jsObject<dynamic> { this.fallback = true }),
        bracketMatching(),
        closeBrackets(),
        autocompletion(jsObject { this.defaultKeymap = false }),
        rectangularSelection(),
        crosshairCursor(),
        highlightActiveLine(),
        highlightSelectionMatches(),
        EditorCommands.addCursorByClick,
        Prec.highest(keymap.of(completionListKeys())),
        keymap.of(closeBracketsKeymap + standardKeymap),
    )

    private fun completionListKeys(): Array<KeyBinding> = arrayOf(
        binding(key = "ArrowDown", run = moveCompletionSelection(forward = true)),
        binding(key = "ArrowUp", run = moveCompletionSelection(forward = false)),
        binding(key = "PageDown", run = moveCompletionSelection(forward = true, by = "page")),
        binding(key = "PageUp", run = moveCompletionSelection(forward = false, by = "page")),
    )

    private fun binding(key: String, run: Command): KeyBinding = jsObject {
        this.key = key
        this.run = { view, _ -> run(view) }
    }
}
