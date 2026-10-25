package io.peekandpoke.klang.workshop.keys

import io.peekandpoke.ultra.common.TypedKey
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
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
 *   disabled) the one before answers again: a dialog's Esc over the page's. A handler names its action, never a key,
 *   so a key the user changes reaches it without registering again.
 * - **Hints:** [hints] is true while the [hintTrigger] (by default `Mod`: Ctrl on a PC, Cmd on a Mac) is held alone for
 *   [hintDelayMs], and false again on its release, on any other key, a click or the wheel (Ctrl+click, Ctrl+wheel), or
 *   when the window loses focus. Moving the mouse while the delay runs stops it (Ctrl+hover in the code editor). AltGr
 *   never shows them. Components show their key while it is true, in their own look ([chordsOf], [labelOf]).
 *
 * One listener on [target] (the document) answers every key, after the page's own handlers: a key a component already
 * handled (`defaultPrevented`: the code editor's own keys while it has the focus) is left alone, and so is a held key's
 * repeat. A key without Ctrl, Alt or Meta is left alone while the user types in a text field. When a key triggers
 * several actions (a clash), the first defined one with an enabled handler answers. The hints follow the keys before
 * the page's handlers (the capture phase), so a handler that stops a key cannot leave them showing.
 */
class KeysManager(
    val platform: KeyPlatform = KeyPlatform.detect(),
    private val store: KeymapStore = KeymapStore.InMemory(),
    /** The modifiers whose holding shows the hints, as a [KeyChord]'s modifiers (`"Mod"`, `"Ctrl-Shift"`). */
    hintTrigger: String = "Mod",
    /** How long the [hintTrigger] is held before the hints show, in milliseconds. */
    val hintDelayMs: Int = 300,
    private val target: EventTarget = document,
) {
    companion object {
        /** The key under which a Kraft app keeps its manager (`KraftApp.Builder.keys`). */
        val key = TypedKey<KeysManager>("keys")

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

    private inner class Handler(
        val actionId: String,
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

    private var userKeys: Map<String, List<String>> = store.load()

    private val _keymap = StreamSource(Keymap(platform = platform, actions = emptyList(), userKeys = userKeys))

    /** Which keys trigger which action, now; a new one on every change. */
    val keymap: Stream<Keymap> = _keymap.readonly

    private val trigger = KeyChord.parse("$hintTrigger-X", platform)

    private val _hints = StreamSource(false)

    /** True while the key hints show. */
    val hints: Stream<Boolean> = _hints.readonly

    private var hintTimer: Int? = null

    /** Where the pointer was last seen, and where it was when the hint delay started (null: not seen yet). */
    private var lastPointer: Pair<Int, Int>? = null
    private var delayStartedAt: Pair<Int, Int>? = null

    private val onKeyDown: (Event) -> Unit = { event -> (event as? KeyboardEvent)?.let { keyDown(it) } }
    private val onHintKeyDown: (Event) -> Unit = { event -> (event as? KeyboardEvent)?.let { hintKeyDown(it) } }
    private val onHintKeyUp: (Event) -> Unit = { event -> (event as? KeyboardEvent)?.let { hintKeyUp(it) } }
    private val onPointer: (Event) -> Unit = { hideHints() }
    private val onPointerMove: (Event) -> Unit = { event -> (event as? MouseEvent)?.let { pointerMove(it) } }
    private val onBlur: (Event) -> Unit = { hideHints() }

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

    /** The chords that trigger [action] now, and on every change. */
    fun chordsOf(action: KeyAction): Stream<List<KeyChord>> = keymap.map { it.chordsOf(action.id) }.distinct()

    /** The first key of [action] as the user reads it (`Ctrl+Shift+Enter`), null when it has none. */
    fun labelOf(action: KeyAction): String? = _keymap().chordsOf(action.id).firstOrNull()?.label(platform)

    /** True when [event] triggers [action], in the keymap as it is now. */
    fun matches(action: KeyAction, event: KeyboardEvent): Boolean = _keymap().matches(action.id, event)

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

    /**
     * Answers [action] with [handler] while [enabled] says so, over the handlers registered before. The returned
     * registration removes it, or swaps what it does without losing its place.
     */
    fun handle(
        action: KeyAction,
        enabled: () -> Boolean = { true },
        handler: (KeyboardEvent) -> Unit,
    ): HandlerRegistration {
        // A forgotten define: no key, no hint, and nothing says why; say it once
        if (_keymap().action(action.id) == null && warnedUndefined.add(action.id)) {
            console.warn("KeysManager: \"${action.id}\" is handled but no bag defines it, so it has no key yet")
        }

        val registered = Handler(actionId = action.id, enabled = enabled, handler = handler)
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

        _keymap().actionsFor(event).forEach { action ->
            // While the user types, only keys with Ctrl, Alt or Meta act: an Esc or a letter belongs to the field
            if (typing && _keymap().chordsOf(action.id).none { it.matches(event = event, platform = platform) && it.hasCommandModifier }) {
                return@forEach
            }

            val answering = handlers.lastOrNull { it.actionId == action.id && it.enabled() } ?: return@forEach

            event.preventDefault()
            answering.handler(event)

            return
        }
    }

    private fun isTextField(eventTarget: EventTarget?): Boolean {
        val element = eventTarget as? Element ?: return false
        val tag = element.tagName.lowercase()

        return tag == "input" || tag == "textarea" || tag == "select" || (element as? HTMLElement)?.isContentEditable == true
    }

    private fun hintKeyDown(event: KeyboardEvent) {
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

    private fun hintKeyUp(event: KeyboardEvent) {
        if (event.key !in KeyChord.MODIFIER_KEY_NAMES) {
            return
        }

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
