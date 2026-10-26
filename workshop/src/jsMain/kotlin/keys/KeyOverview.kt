package io.peekandpoke.klang.workshop.keys

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div

/**
 * Actions that belong together and show as one row of an overview: "Go to bookmark" `Ctrl` `1…9`. Each action keeps
 * its own key (the user can change one, the clash check sees each); only the overview joins them.
 */
data class KeyActionGroup(
    val title: String,
    val actions: List<KeyAction>,
)

/** One row of a [KeyBindings]' overview: one action, or a [KeyActionGroup]. */
sealed interface KeyOverviewRow {
    val title: String

    data class Single(val action: KeyAction) : KeyOverviewRow {
        override val title: String get() = action.title
    }

    data class Group(val group: KeyActionGroup) : KeyOverviewRow {
        override val title: String get() = group.title
    }
}

/**
 * What an overview shows: its [title] and its rows, each with the keys that are set now. A row's [Entry.keys] holds one
 * or more ways to press it, each as groups of key cap parts, one group per step ([KeyBinding.keyGroups]: a chord is one
 * group, `Shift Shift` two); a group whose keys share their modifiers and differ in one digit or letter is one way
 * (`Ctrl` `1…9`).
 */
data class KeyOverviewState(
    val title: String?,
    val entries: List<Entry>,
) {
    data class Entry(val title: String, val keys: List<List<List<String>>>)

    companion object {
        /** The overview of [rows] in [keymap], the key caps compact or not. */
        fun of(title: String?, rows: List<KeyOverviewRow>, keymap: Keymap, compact: Boolean = true): KeyOverviewState =
            KeyOverviewState(
                title = title,
                // A row without any key (the user took them all off) says nothing: it is left out
                entries = rows
                    .map { row -> Entry(title = row.title, keys = keysOf(row = row, keymap = keymap, compact = compact)) }
                    .filter { it.keys.isNotEmpty() },
            )

        private fun keysOf(row: KeyOverviewRow, keymap: Keymap, compact: Boolean): List<List<List<String>>> = when (row) {
            is KeyOverviewRow.Single ->
                keymap.bindingsOf(row.action.id).take(1).map { it.keyGroups(platform = keymap.platform, compact = compact) }

            is KeyOverviewRow.Group -> groupKeys(group = row.group, keymap = keymap, compact = compact)
        }

        private fun groupKeys(group: KeyActionGroup, keymap: Keymap, compact: Boolean): List<List<List<String>>> {
            val firsts = group.actions.mapNotNull { keymap.bindingsOf(it.id).firstOrNull() }
            val chords = firsts.filterIsInstance<KeyChord>()

            if (firsts.isEmpty()) {
                return emptyList()
            }

            // Keys in a row among them (a user's own key): each shown as it is, nothing hidden
            if (chords.size < firsts.size) {
                return firsts.map { it.keyGroups(platform = keymap.platform, compact = compact) }
            }

            val first = chords.first()
            val sameModifiers = chords.all { it.copy(key = first.key) == first }
            val singleKeys = chords.all { it.key.length == 1 && (it.key[0].isDigit() || it.key[0] in 'a'..'z') }

            // A key changed on its own, or keys that are not one digit or letter: each shown as it is
            if (!sameModifiers || !singleKeys) {
                return chords.map { it.keyGroups(platform = keymap.platform, compact = compact) }
            }

            val modifiers = first.keyParts(platform = keymap.platform, compact = compact).dropLast(1)
            val keys = chords.map { it.key.uppercase() }
            val running = keys.zipWithNext().all { (a, b) -> b[0] == a[0] + 1 }

            val joined = when {
                keys.size == 1 -> keys.single()
                running -> "${keys.first()}…${keys.last()}"
                else -> keys.joinToString(", ")
            }

            return listOf(listOf(modifiers + joined))
        }
    }
}

/** Draws an overview of keys, in the app's own look; [KeyHints.overview] is the plain default. */
typealias KeyOverviewRenderer = FlowContent.(KeyOverviewState) -> Unit

/**
 * An overview of a bag's keys while the hints show: its [KeyBindings.overview] rows with the keys set now (a key the
 * user changes shows at once), drawn by [renderer]. Nothing while the hints are hidden or another layer (a modal) is
 * on top of its own. It places nothing: put it where
 * it belongs (`position: absolute` in the corner of the area it is about).
 */
@Suppress("FunctionName")
fun Tag.KeyBindingsOverlay(
    bindings: KeyBindings,
    title: String? = null,
    renderer: KeyOverviewRenderer = KeyHints.overview,
) = comp(
    KeyBindingsOverlay.Props(bindings = bindings, title = title, renderer = renderer)
) {
    KeyBindingsOverlay(it)
}

/** See [Tag.KeyBindingsOverlay]. */
class KeyBindingsOverlay(ctx: Ctx<Props>) : Component<KeyBindingsOverlay.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val bindings: KeyBindings,
        val title: String?,
        val renderer: KeyOverviewRenderer,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val hintsShown by subscribingTo(keys.hints)

    private val keymap by subscribingTo(keys.keymap)

    /** The layer this overview belongs to: under a modal, the page's overview waits with the page's keys. */
    private val layer = keyLayer

    private val topLayer by subscribingTo(keys.topLayer)

    init {
        lifecycle {
            // The first draw cannot see whether it sits in an inert part of the page (no element yet): look again
            onMount {
                triggerRedraw()
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div(classes = CLASS) {
            if (hintsShown && topLayer === layer && dom?.closest("[inert]") == null) {
                props.renderer(this, KeyOverviewState.of(title = props.title, rows = props.bindings.overview, keymap = keymap))
            }
        }
    }

    companion object {
        /** The CSS class of the overlay's element, which the app places. */
        const val CLASS = "key-bindings-overlay"
    }
}
