package io.peekandpoke.klang.workshop.keys

import io.peekandpoke.kraft.components.Component
import org.w3c.dom.events.KeyboardEvent

/**
 * What a key hint shows: the key that triggers the action now, and whether the action can act.
 */
data class KeyHintState(
    val action: KeyAction,
    /** The chords that trigger the action, the first one being the one to show. */
    val chords: List<KeyChord>,
    /** The first chord as the user reads it on this platform (`Ctrl+Shift+Enter`, `⇧⌘↩`). */
    val label: String,
    /** False while the action would not act (a disabled button). */
    val enabled: Boolean,
    /** The platform the keys are shown for. */
    val platform: KeyPlatform,
) {
    /** The first chord's keys one by one, to draw each as a key cap; see [KeyChord.keyParts]. */
    fun keyParts(compact: Boolean = true): List<String> =
        chords.firstOrNull()?.keyParts(platform = platform, compact = compact) ?: emptyList()
}

/**
 * Creates an [OnKeyController] for this component: it answers [action] with [onKey] while the component is mounted and
 * [enabled] says so, and offers the [OnKeyController.hint] to draw while the hints show.
 */
fun Component<*>.onKeyController(
    action: KeyAction,
    enabled: () -> Boolean = { true },
    onKey: (KeyboardEvent) -> Unit,
): OnKeyController = OnKeyController(component = this, action = action, enabled = enabled, onKey = onKey)

/**
 * The machinery behind a key on a component, as Kraft's `FormController` is behind a form: the component keeps the
 * look, the controller does the rest.
 *
 * - It registers [action] with the app's [KeysManager] when the [component] mounts and removes it when it unmounts.
 * - [enabled] and [onKey] are read at the moment of the key, so a lambda over the component's latest props answers;
 *   a new [action] registers again at once.
 * - It follows the hints and the keymap and redraws the component when either changes, so [hint] is always current:
 *   null while the hints are hidden or the action has no key, else what to show. A key the user changes reaches it
 *   without registering again.
 *
 * A component that only answers a key (a dialog's Esc) ignores [hint]; one that draws the key in its own way (in its
 * label, right-aligned in a menu row) reads it in its render. [OnKey] is the slim default: this controller and a
 * [KeyHintRenderer].
 */
class OnKeyController(
    private val component: Component<*>,
    action: KeyAction,
    /** Whether the action acts now; read at the moment of the key. */
    var enabled: () -> Boolean = { true },
    /** What the key does; read at the moment of the key. */
    var onKey: (KeyboardEvent) -> Unit,
) {
    private val manager = component.keys

    private val hintsShown by component.subscribingTo(manager.hints)

    private val keymap by component.subscribingTo(manager.keymap)

    private var registration: KeysManager.HandlerRegistration? = null

    /** The action this controller answers; a new one is registered at once, in place of the old one. */
    var action: KeyAction = action
        set(value) {
            val changed = value.id != field.id
            field = value

            // Mounted: the old action is no longer answered here, and the host draws the new key
            if (changed && registration != null) {
                register()
                component.triggerRedraw()
            }
        }

    /** What to show while the hints show; null while they are hidden or the action has no key. */
    val hint: KeyHintState?
        get() {
            if (!hintsShown) {
                return null
            }

            val chords = keymap.chordsOf(action.id)
            val first = chords.firstOrNull() ?: return null

            return KeyHintState(
                action = action,
                chords = chords,
                label = first.label(manager.platform),
                enabled = enabled(),
                platform = manager.platform,
            )
        }

    init {
        component.lifecycle {
            onMount {
                register()
            }

            onUnmount {
                registration?.remove()
                registration = null
            }
        }
    }

    private fun register() {
        registration?.remove()
        registration = manager.handle(action = action, enabled = { enabled() }) { event -> onKey(event) }
    }
}
