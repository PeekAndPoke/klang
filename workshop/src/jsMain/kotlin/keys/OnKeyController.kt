package io.peekandpoke.klang.workshop.keys

import io.peekandpoke.kraft.components.Component
import org.w3c.dom.events.KeyboardEvent

/**
 * What a key hint shows: the key that triggers the action now, and whether the action can act.
 */
data class KeyHintState(
    val action: KeyAction,
    /** The single chords that trigger the action (keys in a row are in [bindings]). */
    val chords: List<KeyChord>,
    /** The first chord as the user reads it on this platform (`Ctrl+Shift+Enter`, `⇧⌘↩`). */
    val label: String,
    /** False while the action would not act (a disabled button). */
    val enabled: Boolean,
    /** The platform the keys are shown for. */
    val platform: KeyPlatform,
    /** Everything that triggers the action, chords and keys in a row, the first one being the one to show. */
    val bindings: List<KeyBinding> = chords,
) {
    /** The first binding as groups of key caps, one group per step (`Shift` `Shift` is two); see [KeyBinding.keyGroups]. */
    fun keyGroups(compact: Boolean = true): List<List<String>> =
        bindings.firstOrNull()?.keyGroups(platform = platform, compact = compact) ?: emptyList()

    /** The first binding's keys one by one, to draw each as a key cap; the steps of keys in a row follow each other. */
    fun keyParts(compact: Boolean = true): List<String> = keyGroups(compact = compact).flatten()
}

/** When a component's key hint shows. */
enum class KeyHintMode {
    /** While the user holds the hints' key (Ctrl): the default, for a hint drawn over the page. */
    WhileHeld,

    /** Always, where there is room for it: a pill in a header that names its key ("Close: Esc"). */
    Always,
}

/**
 * Creates an [OnKeyController] for this component: it answers [action] with [onKey] while the component is mounted and
 * [enabled] says so, and offers the [OnKeyController.hint] to draw while the hints show, or always ([hintMode]).
 */
fun Component<*>.onKeyController(
    action: KeyAction,
    enabled: () -> Boolean = { true },
    hintMode: KeyHintMode = KeyHintMode.WhileHeld,
    onKey: (KeyboardEvent) -> Unit,
): OnKeyController = OnKeyController(component = this, action = action, enabled = enabled, hintMode = hintMode, onKey = onKey)

/**
 * The machinery behind a key on a component, as Kraft's `FormController` is behind a form: the component keeps the
 * look, the controller does the rest.
 *
 * - It registers [action] with the app's [KeysManager] when the [component] mounts and removes it when it unmounts.
 * - [enabled] and [onKey] are read at the moment of the key, so a lambda over the component's latest props answers;
 *   a new [action] registers again at once.
 * - It answers in the component's layer ([keyLayer]: the nearest one opened above it, else the page's), and only while
 *   that layer is on top: under a modal the page's keys wait. Inside an inert part of the page (the page a router
 *   fades out after a navigation) it answers nothing and shows no hint.
 * - It follows the hints, the keymap and the top layer and redraws the component when one changes, so [hint] is
 *   always current: null while the hints are hidden (unless [hintMode] is [KeyHintMode.Always]), the layer is not on
 *   top or the action has no key, else what to show. A key the user changes reaches it without registering again.
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
    hintMode: KeyHintMode = KeyHintMode.WhileHeld,
    /** What the key does; read at the moment of the key. */
    var onKey: (KeyboardEvent) -> Unit,
) {
    private val manager = component.keys

    private val hintsShown by component.subscribingTo(manager.hints)

    /** The layer its key answers in: the nearest opened one above the component, else the page's. */
    private val layer = component.keyLayer

    /** The layer on top: a hint shows only while its own layer is on top (the page's hints wait under a modal). */
    private val topLayer by component.subscribingTo(manager.topLayer)

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

    /** When [hint] shows: while the hints are held, or always (a component with room for it); a change redraws. */
    var hintMode: KeyHintMode = hintMode
        set(value) {
            if (value != field) {
                field = value
                component.triggerRedraw()
            }
        }

    /**
     * What to show: while the hints show, or always ([hintMode]); null while another layer is on top or the action has
     * no key.
     */
    val hint: KeyHintState?
        get() {
            val held = hintsShown || hintMode == KeyHintMode.Always

            if (!held || topLayer !== layer || isInert()) {
                return null
            }

            val bindings = keymap.bindingsOf(action.id)
            val first = bindings.firstOrNull() ?: return null

            return KeyHintState(
                action = action,
                chords = bindings.filterIsInstance<KeyChord>(),
                label = first.label(manager.platform),
                enabled = enabled(),
                platform = manager.platform,
                bindings = bindings,
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
        registration = manager.handle(action = action, enabled = { enabled() && !isInert() }, layer = layer) { event -> onKey(event) }
    }

    /** True inside an inert part of the page (a page fading out after a navigation): it answers no keys, shows no hint. */
    private fun isInert(): Boolean = component.dom?.closest("[inert]") != null
}
