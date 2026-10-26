package io.peekandpoke.klang.workshop.keys

import io.peekandpoke.ultra.common.TypedKey
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.MouseEvent
import kotlin.math.abs

/**
 * The one place keys are handled (KLANG-00325): which key triggers which action ([keymap]), who answers an action
 * ([handle]), and whether the key hints show ([hints]).
 *
 * - **Actions** come in bags ([define], a [KeyBindings]); their keys are the defaults for [platform] with the user's keys
 *   over them ([setUserKeys], kept in the [KeymapStore]). Every change publishes a new [Keymap].
 * - **Handlers** answer an action. They stack per action: the newest enabled one answers, and when it goes (or is
 *   disabled) the one before answers again. A handler names its action, never a key, so a key the user changes reaches
 *   it without registering again.
 * - **Layers:** every handler answers in a layer, the page's in [rootLayer], which is always there. A modal opens one
 *   over the rest ([openLayer]); only the top layer's handlers answer and only their hints show, the keys below wait,
 *   as the mouse does under a dimmer. Layers close in any order: the top open one always wins, a closed one never
 *   comes back, and the root cannot be closed at all.
 * - **Keys in a row** ([KeySequence]), in the top layer as any key:
 *   - taps, `"Shift Shift"`: a modifier tapped on its own (down and up within [tapMaxMs], no other key, click or wheel
 *     between) twice within [tapPairMs]; also while the user types, since a tap types nothing. A capital (Shift, a
 *     letter) is no tap.
 *   - chords, `"Mod-K Mod-S"`: the first chord acts as a single key would (not on a plain key in a text field) and
 *     starts a wait of [sequenceWaitMs] for the next ([waiting] says which chords are in, for the app to show). While
 *     it waits every key belongs to it, before the page's own handlers: the next chord of a sequence goes on or
 *     triggers it, Esc or any other key ends the wait (and does nothing else), as do a click and the time running out.
 *     A chord that starts a sequence waits even when it is also an action's own key ([Keymap.Problem.PrefixClash]).
 * - **Hints:** [hints] is true while the [hintTrigger] (by default `Mod`: Ctrl on a PC, Cmd on a Mac) is held alone for
 *   [hintDelayMs], and false again on its release, on any other key, a click or the wheel (Ctrl+click, Ctrl+wheel), or
 *   when the window loses focus. Moving the mouse while the delay runs stops it (Ctrl+hover in the code editor). AltGr
 *   never shows them. Components show their key while it is true, in their own look ([labelOf], [keymap]).
 *
 * One listener on [target] (the document) answers every key, after the page's own handlers: a key a component already
 * handled (`defaultPrevented`: the code editor's own keys while it has the focus) is left alone, and so is a held key's
 * repeat. A key without Ctrl, Alt or Meta is left alone while the user types in a text field. When a key triggers
 * several actions (a clash), the first defined one with an enabled handler in the top layer answers. The hints follow the keys before
 * the page's handlers (the capture phase), so a handler that stops a key cannot leave them showing.
 */
class KeysManager(
    val platform: KeyPlatform = KeyPlatform.detect(),
    private val store: KeymapStore = KeymapStore.InMemory(),
    /** The modifiers whose holding shows the hints, as a [KeyChord]'s modifiers (`"Mod"`, `"Ctrl-Shift"`). */
    hintTrigger: String = "Mod",
    /** How long the [hintTrigger] is held before the hints show, in milliseconds. */
    val hintDelayMs: Int = 300,
    /** The longest a modifier may be held and still count as a tap in keys in a row ([KeySequence]), in milliseconds. */
    val tapMaxMs: Int = 300,
    /** The longest time from one tap's release to the next one's in keys in a row, in milliseconds. */
    val tapPairMs: Int = 400,
    /** How long the next chord of chords in a row is waited for, in milliseconds. */
    val sequenceWaitMs: Int = 1500,
    private val target: EventTarget = document,
) {
    companion object {
        /** The key under which a Kraft app keeps its manager (`KraftApp.Builder.keys`). */
        val key = TypedKey<KeysManager>("keys")

        /** The key under which a component keeps the layer it opened (`Component.openKeyLayer`), for those below it. */
        val layerKey = TypedKey<KeyLayer>("key-layer")

        /** How far the pointer may drift while the hint delay runs before it counts as pointing, in CSS pixels. */
        const val POINTER_SLACK_PX = 4
    }

    /** Something registered with the manager, removed with [remove]; removing twice does nothing. */
    interface Registration {
        fun remove()
    }

    /** A bag defined with [define]; [update] swaps its actions in place (after [remove] it changes nothing). */
    interface BagRegistration : Registration {
        fun update(bindings: KeyBindings)
    }

    /** A layer of keys: [rootLayer], or one opened with [openLayer]. */
    interface KeyLayer

    /** A layer opened with [openLayer]; [close] removes it, closing twice does nothing. */
    interface LayerHandle : KeyLayer {
        fun close()
    }

    /** A handler registered with [handle]; [update] swaps what it does, keeping its place in the stack. */
    interface HandlerRegistration : Registration {
        fun update(enabled: () -> Boolean = { true }, handler: (KeyboardEvent) -> Unit)
    }

    private inner class Bag(var bindings: KeyBindings) : BagRegistration {
        override fun update(bindings: KeyBindings) {
            val before = this.bindings
            this.bindings = bindings

            try {
                rebuild()
            } catch (e: IllegalArgumentException) {
                this.bindings = before
                throw e
            }
        }

        override fun remove() {
            if (bags.remove(this)) {
                rebuild()
            }
        }
    }

    private inner class Layer : LayerHandle {
        /** Once closed, it never opens again. */
        var closed = false

        override fun close() {
            closed = true

            if (layers.remove(this)) {
                _topLayer(layers.lastOrNull() ?: rootLayer)
            }
        }
    }

    private inner class Handler(
        val actionId: String,
        /** The layer it answers in. */
        val layer: KeyLayer,
        var enabled: () -> Boolean,
        var handler: (KeyboardEvent) -> Unit,
    ) : HandlerRegistration {
        override fun update(enabled: () -> Boolean, handler: (KeyboardEvent) -> Unit) {
            this.enabled = enabled
            this.handler = handler
        }

        override fun remove() {
            handlers.remove(this)
        }
    }

    private val bags = mutableListOf<Bag>()

    /** The action ids handled before any bag defined them, warned about once each. */
    private val warnedUndefined = mutableSetOf<String>()

    /** Every handler, oldest first; the newest enabled one of an action answers. */
    private val handlers = mutableListOf<Handler>()

    /** The page's layer, under every opened one; it cannot be closed. */
    val rootLayer: KeyLayer = object : KeyLayer {}

    /** The opened layers, oldest first; the last one is on top of the root. */
    private val layers = mutableListOf<Layer>()

    private val _topLayer = StreamSource(rootLayer)

    /** The layer on top now: the root while none is open. Hints redraw when it changes. */
    val topLayer: Stream<KeyLayer> = _topLayer.readonly

    private var userKeys: Map<String, List<String>> = store.load()

    private val _keymap = StreamSource(Keymap(platform = platform, actions = emptyList(), userKeys = userKeys))

    /** Which keys trigger which action, now; a new one on every change. */
    val keymap: Stream<Keymap> = _keymap.readonly

    private val trigger = KeyChord.parse("$hintTrigger-X", platform)

    private val _hints = StreamSource(false)

    /** True while the key hints show. */
    val hints: Stream<Boolean> = _hints.readonly

    private var hintTimer: Int? = null

    /** A modifier down on its own, which becomes a tap if it comes up soon with no other key between. */
    private var tapStart: Pair<TapModifier, Double>? = null

    /** The taps so far of keys in a row, with the time of the last one. */
    private var taps: List<TapModifier> = emptyList()
    private var lastTapAt = 0.0

    private val _waiting = StreamSource<List<KeyChord>>(emptyList())

    /** The chords of keys in a row pressed so far while the manager waits for the next one; empty while it does not. */
    val waiting: Stream<List<KeyChord>> = _waiting.readonly

    private var waitTimer: Int? = null

    /** Where the pointer was last seen, and where it was when the hint delay started (null: not seen yet). */
    private var lastPointer: Pair<Int, Int>? = null
    private var delayStartedAt: Pair<Int, Int>? = null

    private val onKeyDown: (Event) -> Unit = { event -> (event as? KeyboardEvent)?.let { keyDown(it) } }
    private val onHintKeyDown: (Event) -> Unit = { event -> (event as? KeyboardEvent)?.let { hintKeyDown(it) } }
    private val onHintKeyUp: (Event) -> Unit = { event -> (event as? KeyboardEvent)?.let { hintKeyUp(it) } }
    // A click or a wheel turn between a modifier's down and up makes it no tap: Shift+click extends a selection
    private val onPointer: (Event) -> Unit = {
        hideHints()
        breakTaps()
        stopWaiting()
    }
    private val onPointerMove: (Event) -> Unit = { event -> (event as? MouseEvent)?.let { pointerMove(it) } }
    private val onBlur: (Event) -> Unit = {
        hideHints()
        breakTaps()
        stopWaiting()
    }

    init {
        // The actions after the page's own handlers (bubbling), the hints before them (capturing)
        target.addEventListener("keydown", onKeyDown)
        target.addEventListener("keydown", onHintKeyDown, true)
        target.addEventListener("keyup", onHintKeyUp, true)
        target.addEventListener("pointerdown", onPointer, true)
        target.addEventListener("wheel", onPointer, true)
        target.addEventListener("pointermove", onPointerMove, true)
        window.addEventListener("blur", onBlur)
    }

    //  ACTIONS  ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Defines the actions of [bindings].
     *
     * @throws IllegalArgumentException when an action id is defined already, in this bag or another one
     */
    fun define(bindings: KeyBindings): BagRegistration {
        val bag = Bag(bindings)
        bags.add(bag)

        try {
            rebuild()
        } catch (e: IllegalArgumentException) {
            bags.remove(bag)
            throw e
        }

        return bag
    }

    /** The first key of [action] as the user reads it (`Ctrl+Shift+Enter`, `Shift Shift`), null when it has none. */
    fun labelOf(action: KeyAction): String? = _keymap().bindingsOf(action.id).firstOrNull()?.label(platform)

    private fun rebuild() {
        val actions = bags.flatMap { it.bindings.actions }

        actions.groupBy { it.id }.filterValues { it.size > 1 }.keys.firstOrNull()?.let { id ->
            throw IllegalArgumentException("The key action \"$id\" is defined twice")
        }

        _keymap(Keymap(platform = platform, actions = actions, userKeys = userKeys))
    }

    //  THE USER'S KEYS  ////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Gives the action with [actionId] the user's [keys] in place of its defaults (an empty list: no key at all), and
     * keeps them in the store. A clash this makes is in the new keymap's problems.
     */
    fun setUserKeys(actionId: String, keys: List<String>) {
        changeUserKeys(userKeys + (actionId to keys))
    }

    /** The action with [actionId] back to its default keys. */
    fun resetUserKeys(actionId: String) {
        changeUserKeys(userKeys - actionId)
    }

    /** Every action back to its default keys. */
    fun resetAllUserKeys() {
        changeUserKeys(emptyMap())
    }

    private fun changeUserKeys(next: Map<String, List<String>>) {
        userKeys = next
        store.save(next)
        rebuild()
    }

    //  HANDLERS  ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Opens a layer over everything open before it; see [LayerHandle]. */
    fun openLayer(): LayerHandle = newLayer().also { open(it) }

    /**
     * A layer not on the stack yet, for a component that hands it to the components below it before it mounts
     * (`Component.openKeyLayer`): it goes on top with [open], so one that never mounts never covers the page.
     */
    internal fun newLayer(): LayerHandle = Layer()

    /** Puts [layer] (from [newLayer]) on top; a layer already open or closed stays as it is. */
    internal fun open(layer: LayerHandle) {
        val opening = layer as? Layer ?: return

        if (opening.closed || opening in layers) {
            return
        }

        layers.add(opening)
        _topLayer(opening)
    }

    /** True while [layer] is the one whose keys answer. */
    fun isOnTop(layer: KeyLayer): Boolean = _topLayer() === layer

    /**
     * Answers [action] with [handler] while [enabled] says so and its [layer] is on top, over the handlers registered
     * before. The returned registration removes it, or swaps what it does without losing its place.
     */
    fun handle(
        action: KeyAction,
        enabled: () -> Boolean = { true },
        layer: KeyLayer = rootLayer,
        handler: (KeyboardEvent) -> Unit,
    ): HandlerRegistration {
        // A forgotten define: no key, no hint, and nothing says why; say it once
        if (_keymap().action(action.id) == null && warnedUndefined.add(action.id)) {
            console.warn("KeysManager: \"${action.id}\" is handled but no bag defines it, so it has no key yet")
        }

        val registered = Handler(actionId = action.id, layer = layer, enabled = enabled, handler = handler)
        handlers.add(registered)

        return registered
    }

    /** Stops listening; for tests and a page that goes for good. */
    fun dispose() {
        target.removeEventListener("keydown", onKeyDown)
        target.removeEventListener("keydown", onHintKeyDown, true)
        target.removeEventListener("keyup", onHintKeyUp, true)
        target.removeEventListener("pointerdown", onPointer, true)
        target.removeEventListener("wheel", onPointer, true)
        target.removeEventListener("pointermove", onPointerMove, true)
        window.removeEventListener("blur", onBlur)
        hideHints()
        stopWaiting()
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun keyDown(event: KeyboardEvent) {
        if (event.key in KeyChord.MODIFIER_KEY_NAMES || event.defaultPrevented || event.repeat) {
            return
        }

        dispatch(event)
    }

    private fun dispatch(event: KeyboardEvent) {
        val typing = isTextField(event.target)

        // The first chord of chords in a row: wait for the next, before any single key on the same chord
        val starting = _keymap().chordSequences.firstOrNull { (action, sequence) ->
            val first = sequence.chords.first()
            // While the user types, only a first chord with Ctrl, Alt or Meta starts it, as for a single key
            val allowed = !typing || first.hasCommandModifier

            first.matches(event = event, platform = platform) && allowed && answering(action) != null
        }

        if (starting != null) {
            event.preventDefault()
            startWaiting(starting.second.chords.take(1))

            return
        }

        _keymap().actionsFor(event).forEach { action ->
            // While the user types, only keys with Ctrl, Alt or Meta act: an Esc or a letter belongs to the field
            if (typing && _keymap().chordsOf(action.id).none { it.matches(event = event, platform = platform) && it.hasCommandModifier }) {
                return@forEach
            }

            val answering = answering(action) ?: return@forEach

            event.preventDefault()
            answering.handler(event)

            return
        }
    }

    /**
     * The handler that answers [action] now: the last registered enabled one in the top layer. The keys below the top
     * layer wait, the page's under a modal.
     */
    private fun answering(action: KeyAction): Handler? {
        val top = _topLayer()

        return handlers.lastOrNull { it.actionId == action.id && it.layer === top && it.enabled() }
    }

    private fun isTextField(eventTarget: EventTarget?): Boolean {
        val element = eventTarget as? Element ?: return false
        val tag = element.tagName.lowercase()

        return tag == "input" || tag == "textarea" || tag == "select" || (element as? HTMLElement)?.isContentEditable == true
    }

    private fun hintKeyDown(event: KeyboardEvent) {
        followTaps(event)

        // Waiting for the next chord of chords in a row: every key is the wait's, before the page's handlers see it
        if (_waiting().isNotEmpty() && event.key !in KeyChord.MODIFIER_KEY_NAMES) {
            event.preventDefault()
            event.stopPropagation()

            if (!event.repeat) {
                nextInRow(event)
            }

            return
        }

        // Any other key, and AltGr, which types characters (Windows sends a Control keydown before it)
        if (event.key !in KeyChord.MODIFIER_KEY_NAMES || event.key == "AltGraph" || event.getModifierState("AltGraph")) {
            hideHints()

            return
        }

        // Shown: another modifier joining keeps them (Ctrl, then Shift, for Ctrl+Shift+Enter). A held key's repeat
        // (Windows repeats a held Ctrl) never starts the delay again after a click or a move stopped it
        if (_hints() || event.repeat) {
            return
        }

        val heldAlone = event.ctrlKey == trigger.ctrl && event.altKey == trigger.alt &&
            event.shiftKey == trigger.shift && event.metaKey == trigger.meta

        when {
            !heldAlone -> hideHints()

            hintTimer == null -> {
                delayStartedAt = lastPointer
                hintTimer = window.setTimeout({
                    hintTimer = null
                    _hints(true)
                }, hintDelayMs)
            }
        }
    }

    private fun pointerMove(event: MouseEvent) {
        val here = event.clientX to event.clientY
        val from = delayStartedAt ?: here.also { delayStartedAt = it }
        lastPointer = here

        // Moving while the delay runs: the user is pointing (Ctrl+hover), not asking for the hints; a hand resting on
        // the mouse moves it by a pixel or two, which is no pointing
        val moved = abs(here.first - from.first) > POINTER_SLACK_PX || abs(here.second - from.second) > POINTER_SLACK_PX

        if (moved) {
            hintTimer?.let {
                window.clearTimeout(it)
                hintTimer = null
            }
        }

        // Shown, and the trigger let go where no keyup reached us (a native menu was open)
        if (_hints() && !triggerHeld(ctrl = event.ctrlKey, alt = event.altKey, shift = event.shiftKey, meta = event.metaKey)) {
            hideHints()
        }
    }

    private fun triggerHeld(ctrl: Boolean, alt: Boolean, shift: Boolean, meta: Boolean): Boolean =
        (!trigger.ctrl || ctrl) && (!trigger.alt || alt) && (!trigger.shift || shift) && (!trigger.meta || meta)

    private fun now(): Double = window.performance.now()

    /** Follows a key going down for keys in a row: a modifier alone may start a tap, anything else ends the row. */
    private fun followTaps(event: KeyboardEvent) {
        val modifier = TapModifier.ofKey(event.key)

        if (event.repeat) {
            return
        }

        // Alone: no other modifier held with it (Ctrl, then Shift, is no tap of either)
        val alone = modifier != null &&
            event.ctrlKey == (modifier == TapModifier.Ctrl) && event.altKey == (modifier == TapModifier.Alt) &&
            event.shiftKey == (modifier == TapModifier.Shift) && event.metaKey == (modifier == TapModifier.Meta)

        if (alone && !event.getModifierState("AltGraph")) {
            tapStart = modifier!! to now()
        } else {
            breakTaps()
        }
    }

    /** Ends keys in a row: no tap pending, no taps so far. */
    private fun breakTaps() {
        tapStart = null
        taps = emptyList()
    }

    /** A modifier came up: a tap when it went down alone not long ago; two in a row may trigger an action. */
    private fun tapEnded(event: KeyboardEvent) {
        val start = tapStart ?: return
        tapStart = null

        val at = now()

        if (TapModifier.ofKey(event.key) != start.first || at - start.second > tapMaxMs) {
            taps = emptyList()

            return
        }

        val row = if (taps.isNotEmpty() && at - lastTapAt <= tapPairMs) taps + start.first else listOf(start.first)
        // Only the last two count: a Ctrl brushed before a quick Shift Shift does not spoil it
        taps = row.takeLast(2)
        lastTapAt = at

        if (taps.size < 2) {
            return
        }

        _keymap().actionsFor(KeySequence(steps = taps.map { KeyStep.Tap(it) })).forEach { action ->
            val answering = answering(action) ?: return@forEach

            taps = emptyList()
            answering.handler(event)

            return
        }
    }

    /** A key while chords in a row wait: the next chord goes on or triggers, any other key (Esc too) ends the wait. */
    private fun nextInRow(event: KeyboardEvent) {
        val pressed = _waiting()
        stopWaiting()

        val fitting = _keymap().chordSequences.filter { (action, sequence) ->
            val chords = sequence.chords

            chords.size > pressed.size && chords.take(pressed.size) == pressed &&
                chords[pressed.size].matches(event = event, platform = platform) && answering(action) != null
        }

        // A sequence complete: the first defined one acts, as for a clash
        fitting.firstOrNull { (_, sequence) -> sequence.chords.size == pressed.size + 1 }?.let { (action, _) ->
            answering(action)?.handler(event)

            return
        }

        fitting.firstOrNull()?.let { (_, sequence) -> startWaiting(sequence.chords.take(pressed.size + 1)) }
    }

    private fun startWaiting(pressed: List<KeyChord>) {
        waitTimer?.let { window.clearTimeout(it) }
        _waiting(pressed)
        waitTimer = window.setTimeout({ stopWaiting() }, sequenceWaitMs)
    }

    private fun stopWaiting() {
        waitTimer?.let { window.clearTimeout(it) }
        waitTimer = null

        if (_waiting().isNotEmpty()) {
            _waiting(emptyList())
        }
    }

    private fun hintKeyUp(event: KeyboardEvent) {
        if (event.key !in KeyChord.MODIFIER_KEY_NAMES) {
            return
        }

        tapEnded(event)

        if (!triggerHeld(ctrl = event.ctrlKey, alt = event.altKey, shift = event.shiftKey, meta = event.metaKey)) {
            hideHints()
        }
    }

    private fun hideHints() {
        hintTimer?.let { window.clearTimeout(it) }
        hintTimer = null

        if (_hints()) {
            _hints(false)
        }
    }
}
