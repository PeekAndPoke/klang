package io.peekandpoke.klang.sprudel.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.semanticui.ui
import de.peekandpoke.ultra.streams.Stream
import de.peekandpoke.ultra.streams.ops.filterIsInstance
import de.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.sprudel.lang.editor.MnNodeOps
import io.peekandpoke.klang.sprudel.lang.parser.MnNode
import io.peekandpoke.klang.sprudel.lang.parser.MnPattern
import io.peekandpoke.klang.sprudel.lang.parser.MnRenderer
import io.peekandpoke.klang.sprudel.lang.parser.parseMiniNotationMnPattern
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.codetools.KlangToolAutoUpdate
import io.peekandpoke.klang.ui.feel.KlangLookAndFeel
import io.peekandpoke.klang.ui.feel.KlangTheme
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.css.*
import kotlinx.html.FlowContent
import kotlinx.html.div
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlin.js.Date

/**
 * Abstract base component containing all shared state and logic for mini-notation editors.
 *
 * Tree operations use [MnNode.replaceById] / [MnNode.removeById] directly.
 */
abstract class MnPatternEditorBase<P : MnPatternEditorBase.BaseProps>(ctx: Ctx<P>) : Component<P>(ctx) {

    interface BaseProps {
        val toolCtx: KlangUiToolContext
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    protected val laf: KlangLookAndFeel by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)

    // ── State ─────────────────────────────────────────────────────────────────

    protected var text by value(initialText())
    protected var cursorOffset by value(0)
    private var lastCommittedText by value(initialText())

    /** Last atom the cursor was over — retained so panels survive button clicks. */
    protected var lastAtom: MnNode.Atom? = null

    /** Last rest selected in the staff — cleared when an atom is selected. */
    protected var lastRest: MnNode.Rest? = null

    /** Incremented on every reset — used to force embedded atom tools to remount. */
    protected var resetVersion by value(0)

    /** Synthetic atom used when the pattern has no real atoms, so the atom editor panel is always visible. */
    private val syntheticAtom = MnNode.Atom(value = "")

    /**
     * Resolves the current atom to display in the editor panel.
     *
     * Priority: selectedAtom (from cursor) > lastAtom > first atom in pattern > syntheticAtom.
     * This ensures the atom editor panel is always visible when the tool opens.
     */
    protected fun resolveCurrentAtom(): MnNode.Atom {
        val selected = selectedAtom
        if (selected != null) {
            lastAtom = selected
            lastRest = null
            return selected
        }
        lastAtom?.let { return it }
        // Auto-select the first atom in the pattern
        val first = pattern?.let { collectAtoms(it).firstOrNull() }
        if (first != null) {
            lastAtom = first
            cursorOffset = first.sourceRange?.first ?: 0
            return first
        }
        return syntheticAtom
    }

    /** Whether the given atom is the synthetic placeholder (no real atom in the pattern). */
    protected fun isSyntheticAtom(atom: MnNode.Atom): Boolean = atom === syntheticAtom

    // ── Undo / redo ───────────────────────────────────────────────────────────

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()

    /** Push current [text] onto the undo stack before a programmatic edit. */
    protected fun pushUndo() {
        undoStack.addLast(text)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(text)
        text = undoStack.removeLast()
        cursorOffset = text.length
        lastAtom = null
        triggerRedraw()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(text)
        text = redoStack.removeLast()
        cursorOffset = text.length
        lastAtom = null
        triggerRedraw()
    }

    private val keydownListener: (Event) -> Unit = listener@{ event ->
        val ke = event as? KeyboardEvent ?: return@listener
        when {
            KlangKeyBindings.isUndo(ke) -> {
                ke.preventDefault(); undo()
            }

            KlangKeyBindings.isRedo(ke) -> {
                ke.preventDefault(); redo()
            }
        }
    }

    // ── Playback highlight ────────────────────────────────────────────────────

    /**
     * Voice stream mapped to resolved highlights (timing + IntRange within the MN string).
     */
    protected val resolvedHighlightStream: Stream<List<ResolvedVoiceHighlight>>? by lazy {
        val stream = props.toolCtx.attrs[KlangUiToolContext.PlaybackVoiceEvents] ?: return@lazy null
        val base = props.toolCtx.attrs[KlangUiToolContext.BaseSourceLocation] ?: return@lazy null
        stream
            .filterIsInstance<KlangPlaybackSignal.VoicesScheduled, KlangPlaybackSignal>()
            .map { signal ->
                val voices = signal?.voices ?: return@map emptyList()
                voices.mapNotNull { voice ->
                    val range = voiceToSourceRange(voice, base, text) ?: return@mapNotNull null
                    ResolvedVoiceHighlight(voice.startTime, voice.endTime, range)
                }
            }
    }

    /** Source ranges currently highlighted — used for the text input overlay and staff. */
    protected val highlightedRanges = mutableSetOf<IntRange>()

    private fun subscribeToHighlights() {
        resolvedHighlightStream?.subscribe { highlights ->
            if (highlights.isEmpty()) return@subscribe
            val now = Date.now()
            for (h in highlights) {
                val startDelay = maxOf(1, (h.startTime * 1000.0 - now).toInt())
                val endDelay = maxOf(1, (h.endTime * 1000.0 - now).toInt())
                window.setTimeout({ if (highlightedRanges.add(h.sourceRange)) triggerRedraw() }, startDelay)
                window.setTimeout({ if (highlightedRanges.remove(h.sourceRange)) triggerRedraw() }, endDelay)
            }
        }
    }

    init {
        lifecycle {
            onMount {
                document.addEventListener("keydown", keydownListener)
                subscribeToHighlights()
            }
            onUnmount {
                document.removeEventListener("keydown", keydownListener)
                highlightedRanges.clear()
            }
        }
    }

    // ── Parse cache ───────────────────────────────────────────────────────────

    private val patternCache = mutableMapOf<String, MnPattern?>()

    protected val pattern: MnPattern?
        get() = patternCache.getOrPut(text) {
            try {
                parseMiniNotationMnPattern(text)
            } catch (_: Exception) {
                null
            }
        }

    protected val parseError: Boolean get() = pattern == null && text.isNotBlank()
    protected val selectedAtom: MnNode.Atom? get() = pattern?.let { MnNodeOps.findAtomAtOffset(it, text, cursorOffset) }
    protected val isModified: Boolean get() = text != initialText()
    protected val hasUncommittedChanges: Boolean get() = text != lastCommittedText

    // ── Initial value ─────────────────────────────────────────────────────────

    private fun initialText(): String {
        val raw = props.toolCtx.currentValue?.trim() ?: return ""
        return raw.removeSurrounding("\"").removeSurrounding("`")
    }

    // ── Node queries ──────────────────────────────────────────────────────────

    protected fun collectAtoms(p: MnPattern): List<MnNode.Atom> = MnNodeOps.collectAtoms(p)

    protected fun findAtomById(p: MnPattern, id: Int): MnNode.Atom? = MnNodeOps.findAtomById(p, id)

    // ── Core edit operation ───────────────────────────────────────────────────

    /** Replaces the node with [targetId] in the tree, re-renders, and updates cursor/selection. */
    protected fun replaceNode(targetId: Int, replacement: MnNode) {
        val p = pattern ?: return
        pushUndo()
        val newRoot = p.replaceById(targetId, replacement) as? MnPattern ?: return
        text = MnRenderer.render(newRoot)
        // Try to re-find the atom near the cursor position
        lastAtom = lastAtom?.let { _ ->
            pattern?.let { MnNodeOps.findAtomAtOffset(it, text, cursorOffset) }
        }
    }

    /** Removes the node with [targetId] from the tree, normalizing wrapper groups afterward. */
    protected fun removeNode(targetId: Int) {
        val p = pattern ?: return
        pushUndo()
        val newRoot = p.removeById(targetId) as? MnPattern ?: return
        text = MnRenderer.render(MnNodeOps.normalizeGroups(newRoot))
        cursorOffset = cursorOffset.coerceAtMost(text.length)
        lastAtom = null
    }

    /** Replaces [old] with [new] in the pattern tree (legacy convenience, uses id-based replace). */
    protected fun updateNode(old: MnNode, new: MnNode) {
        if (old is MnNode.Atom && isSyntheticAtom(old)) {
            // Synthetic atom: no tree node to replace — set text directly from the new value
            val newValue = (new as? MnNode.Atom)?.value ?: return
            pushUndo()
            text = newValue
            cursorOffset = newValue.length
            lastAtom = pattern?.let { MnNodeOps.findAtomAtOffset(it, text, cursorOffset) }
            liveUpdate()
            return
        }

        val p = pattern ?: return
        pushUndo()
        val newRoot = p.replaceById(old.id, new) as? MnPattern ?: return
        text = MnRenderer.render(newRoot)
        if (new is MnNode.Atom) {
            val newAtom = pattern?.let { MnNodeOps.findAtomAtOffset(it, text, cursorOffset) }
            cursorOffset = newAtom?.sourceRange?.first ?: cursorOffset
            lastAtom = newAtom ?: lastAtom
        } else {
            lastAtom = null
        }
        liveUpdate()
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    protected fun liveUpdate() {
        if (autoUpdate) {
            lastCommittedText = text
            props.toolCtx.onCommit(text.quoteForCommit())
        }
    }

    protected fun onCancel() {
        if (autoUpdate && isModified) {
            val initial = initialText()
            props.toolCtx.onCommit(initial.quoteForCommit())
        }
        props.toolCtx.onCancel()
    }

    protected fun onReset() {
        pushUndo()
        text = initialText()
        cursorOffset = 0
        lastAtom = null
        resetVersion++
    }

    protected fun onCommit() {
        lastCommittedText = text
        props.toolCtx.onCommit(text.quoteForCommit())
    }

    // ── Bottom bar ────────────────────────────────────────────────────────────

    protected fun FlowContent.renderBottomBar() {
        ToolButtonBar(
            isInitialModified = isModified,
            isCurrentModified = hasUncommittedChanges,
            onCancel = ::onCancel,
            onReset = ::onReset,
            onCommit = ::onCommit,
        )
    }
}

// ── Resolved voice highlight ─────────────────────────────────────────────────

/** A voice highlight with timing and a resolved source range within the MN string. */
data class ResolvedVoiceHighlight(
    val startTime: Double,
    val endTime: Double,
    val sourceRange: IntRange,
)

// ── Voice → source-range matching ────────────────────────────────────────────

/**
 * Converts a [KlangPlaybackSignal.VoicesScheduled.VoiceEvent]'s innermost [SourceLocation] to an [IntRange] within the
 * mini-notation string, using the [base] source location (position of the opening quote).
 *
 * This reverses the formula in `MnPatternToStrudelPattern.toLocation()`:
 * - For line 1:  `absoluteCol = base.startColumn + (sourceRange.first + 1)`
 * - For line N:  `absoluteCol = sourceColumn` (column within that line of the MN string)
 *
 * @param mnText The mini-notation string — needed for multi-line offset calculation.
 * Returns `null` if the voice has no source location or the location doesn't match.
 */
internal fun voiceToSourceRange(voice: KlangPlaybackSignal.VoicesScheduled.VoiceEvent, base: SourceLocation, mnText: String): IntRange? {
    val chain = voice.sourceLocations ?: return null
    // Check all locations in the chain, not just innermost — more robust
    for (loc in chain.locations.asReversed()) {
        val range = locationToSourceRange(loc, base, mnText) ?: continue
        return range
    }
    return null
}

/**
 * Converts a single [SourceLocation] to a mini-notation string [IntRange]
 * given the [base] location of the opening quote.
 *
 * @param mnText The mini-notation string — used for multi-line line-start offsets.
 */
private fun locationToSourceRange(loc: SourceLocation, base: SourceLocation, mnText: String): IntRange? {
    val mnLine = loc.startLine - base.startLine + 1
    if (mnLine < 1) return null

    return if (mnLine == 1) {
        // Single-line: pure arithmetic, no string scanning
        val from = loc.startColumn - base.startColumn - 1
        val to = loc.endColumn - base.startColumn - 2
        if (from >= 0 && to >= from) from..to else null
    } else {
        // Multi-line: find the character offset of the start of mnLine in the MN string,
        // then add the 1-based column offset.
        val lineStartOffset = mnText.nthLineOffset(mnLine) ?: return null
        val from = lineStartOffset + loc.startColumn - 1
        val to = lineStartOffset + loc.endColumn - 2
        if (from >= 0 && to >= from && to < mnText.length) from..to else null
    }
}

/** Returns the 0-based character offset of the start of the [n]-th line (1-based). */
private fun String.nthLineOffset(n: Int): Int? {
    if (n == 1) return 0
    var line = 1
    for (i in indices) {
        if (this[i] == '\n') {
            line++
            if (line == n) return i + 1
        }
    }
    return null
}

// ── Note staff editor base ────────────────────────────────────────────────────

/**
 * Abstract base for note-staff editors.
 *
 * Subclasses provide mappings between atom string values and staff positions:
 * - [atomToStaffPosition]: "c4" → 0, "d4" → 1, …
 * - [staffPositionToAtomValue]: 0 → "c4", 1 → "d4", …
 */
abstract class MnEditorBase<P : MnPatternEditorBase.BaseProps>(ctx: Ctx<P>) : MnPatternEditorBase<P>(ctx) {

    protected abstract fun atomToStaffPosition(value: String): Int?
    protected abstract fun staffPositionToAtomValue(pos: Int): String
    protected open fun keySignatureScaleName(): String? = null
    protected open fun FlowContent.renderExtraControls() {}

    // ── Template render ───────────────────────────────────────────────────────

    override fun VDom.render() {
        val atom = resolveCurrentAtom()
        val isSynthetic = isSyntheticAtom(atom)

        val rest = if (!isSynthetic) null else lastRest

        ui.segment {
            css {
                minWidth = 60.vw
                display = Display.flex
                flexDirection = FlexDirection.column
            }

            mnPatternTextInput(laf, text, if (isSynthetic) null else atom, parseError, highlightedRanges) { newText, cursor ->
                text = newText
                cursorOffset = cursor
                lastAtom = lastAtom?.let { a -> pattern?.let { p -> findAtomById(p, a.id) } }
                liveUpdate()
            }

            renderExtraControls()

            ui.divider {}

            renderStaves()

            ui.divider {}
            if (!isSynthetic) {
                val parentRepeat = pattern?.let { MnNodeOps.findParentRepeat(it, atom.id) }
                mnModifierPanel(
                    laf,
                    atom,
                    onToggleRest = { updateNode(atom, MnNode.Rest(atom.sourceRange)) },
                    repeatCount = parentRepeat?.count,
                    onRepeatChange = { newCount ->
                        if (newCount != null && parentRepeat != null) {
                            // Update existing repeat count
                            replaceNode(parentRepeat.id, parentRepeat.copy(count = newCount))
                        } else if (newCount != null && parentRepeat == null) {
                            // Wrap atom in a new Repeat
                            replaceNode(atom.id, MnNode.Repeat(node = atom, count = newCount))
                        } else if (newCount == null && parentRepeat != null) {
                            // Remove repeat — unwrap to just the inner node
                            replaceNode(parentRepeat.id, parentRepeat.node)
                        }
                    },
                ) { updated -> updateNode(atom, updated) }
            } else if (rest != null) {
                mnModifierPanel(laf, rest, onToggleNote = {
                    updateNode(rest, MnNode.Atom(value = staffPositionToAtomValue(6)))
                    lastRest = null
                }) { updated ->
                    updateNode(rest, updated)
                    lastRest = pattern?.let { p ->
                        MnNodeOps.collectStaffItems(p).filterIsInstance<MnNode.Rest>()
                            .find { it.sourceRange?.first == rest.sourceRange?.first }
                    }
                }
            } else {
                mnModifierPanelDisabled(laf)
            }

            div { css { flexGrow = 1.0 } }

            ui.divider {}
            renderBottomBar()
        }
    }

    // ── Staff rendering ───────────────────────────────────────────────────────

    private fun FlowContent.renderStaves() {
        val p = pattern ?: return

        val selection: MnSelection? =
            selectedAtom?.let { MnSelection.Atom(it) }
                ?: lastRest?.let { MnSelection.Rest(it) }

        noteStaffSheet(
            pattern = p,
            text = text,
            atomToPos = ::atomToStaffPosition,
            posToValue = ::staffPositionToAtomValue,
            scaleName = keySignatureScaleName(),
            selection = selection,
            resolvedHighlightStream = resolvedHighlightStream,
            onAction = { action -> handleStaffAction(action) },
        )
    }

    private fun handleStaffAction(action: NoteStaffEditor.Action) {
        when (action) {
            is NoteStaffEditor.Action.Select -> when (val sel = action.selection) {
                is MnSelection.Atom -> {
                    cursorOffset = sel.node.sourceRange?.first ?: cursorOffset
                    lastAtom = sel.node; lastRest = null
                }

                is MnSelection.Rest -> {
                    cursorOffset = sel.node.sourceRange?.first ?: cursorOffset
                    lastRest = sel.node; lastAtom = null
                }
            }

            is NoteStaffEditor.Action.Remove -> removeNode(action.nodeId)

            is NoteStaffEditor.Action.InsertChild -> {
                val p = pattern ?: return
                val parent = p.findById(action.parentId) ?: return
                val newAtom = MnNode.Atom(value = staffPositionToAtomValue(action.staffPos))
                val newParent = when (parent) {
                    is MnPattern -> parent.insertAt(action.index, newAtom)
                    is MnNode.Group -> MnNodeOps.groupInsertAt(parent, action.index, newAtom)
                    is MnNode.Alternation -> parent.insertAt(action.index, newAtom)
                    else -> return
                }
                replaceNode(parent.id, newParent)
                cursorOffset = text.length
            }

            is NoteStaffEditor.Action.StackOnto -> {
                val p = pattern ?: return
                val node = p.findById(action.nodeId) ?: return
                val newValue = staffPositionToAtomValue(action.staffPos)
                when (node) {
                    is MnNode.Atom -> {
                        // Wrap in a stack: c4 → [c4,e4]
                        val stack = MnNode.Group(
                            items = listOf(MnNode.Stack(layers = listOf(listOf(node), listOf(MnNode.Atom(value = newValue)))))
                        )
                        replaceNode(node.id, stack)
                    }

                    is MnNode.Stack -> {
                        // Add layer to existing stack: [c4,e4] → [c4,e4,g4]
                        replaceNode(node.id, node.addLayer(MnNode.Atom(value = newValue)))
                    }

                    is MnNode.Rest -> {
                        // Replace rest with the new note
                        replaceNode(node.id, MnNode.Atom(value = newValue))
                    }

                    else -> return
                }
                cursorOffset = text.length
            }

            is NoteStaffEditor.Action.Replace -> updateNode(action.old, action.new)
        }
    }
}
