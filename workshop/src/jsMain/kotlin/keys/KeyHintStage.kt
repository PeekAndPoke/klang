package io.peekandpoke.klang.workshop.keys

import io.peekandpoke.kraft.KraftApp
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.common.TypedKey
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.style
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import kotlin.js.Promise
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The layer every key hint is drawn on (KLANG-00325), as Kraft draws its popups and modals: one fixed layer at the top
 * of the app, so no ancestor of a button (a disabled button's fade, `overflow: hidden`, its own stacking context) can
 * fade, clip or cover a hint, and hints that would overlap are pushed apart ([KeyHintLayout]).
 *
 * An [OnKey] adds itself here with its anchor (the element it sits in), what to show and how; the stage draws the hints
 * while the [KeysManager]'s hints show, measures each anchor and each hint, and places them. It is mounted by Kraft on
 * its own (`KraftApp.Builder.keys` sets it as an app attribute).
 */
class KeyHintStage(
    val manager: KeysManager,
    /** The app's look of a hint, for every [OnKey] that brings none of its own. */
    val renderer: KeyHintRenderer,
    /** Where hints go, for every [OnKey] that says nothing else. */
    val placement: KeyHintPlacement,
    /** The app's look of the chip that shows chords in a row waiting for their next key. */
    val waitingRenderer: KeyWaitingRenderer = KeyHints.waiting,
) : KraftApp.AutoMountedUi {

    companion object {
        /** The key under which a Kraft app keeps its stage. */
        val key = TypedKey<KeyHintStage>("key-hint-stage")
    }

    /** One hint the stage draws while the hints show; its lambdas are read at the moment of drawing. */
    class Entry internal constructor(
        val id: Int,
        /** The element the hint belongs to; null while it has none. */
        val anchor: () -> Element?,
        /** What to show; null while nothing is (no key). */
        val state: () -> KeyHintState?,
        /** Where to show it; null: the stage's [KeyHintStage.placement]. */
        val placement: () -> KeyHintPlacement?,
        /** How it looks; null: the stage's [KeyHintStage.renderer]. */
        val renderer: () -> KeyHintRenderer?,
    )

    /** An [Entry] on the stage, removed with [remove]. */
    interface Registration {
        fun remove()
    }

    private var nextId = 0

    private val _entries = StreamSource<List<Entry>>(emptyList())

    /** Every hint that may show. */
    val entries: Stream<List<Entry>> = _entries.readonly

    private val _changes = StreamSource(0)

    /** Counts the changes to what the hints show, while they show: each one places them again. */
    val changes: Stream<Int> = _changes.readonly

    override val autoMountPriority: Int = 900

    override fun autoMount(element: FlowContent) {
        with(element) {
            comp(KeyHintStageView.Props(stage = this@KeyHintStage)) { KeyHintStageView(it) }
        }
    }

    /** Adds a hint; see [Entry]. */
    fun add(
        anchor: () -> Element?,
        state: () -> KeyHintState?,
        placement: () -> KeyHintPlacement? = { null },
        renderer: () -> KeyHintRenderer? = { null },
    ): Registration {
        val entry = Entry(id = nextId++, anchor = anchor, state = state, placement = placement, renderer = renderer)
        _entries { it + entry }

        return object : Registration {
            override fun remove() {
                _entries { list -> list.filterNot { it.id == entry.id } }
            }
        }
    }

    private var changePending = false

    /**
     * What a hint shows changed (enabled, a new action): placed again while the hints show, else nothing to do. Many
     * changes in one go (every [OnKey] redraws when the hints appear) place them once, still before the next paint.
     */
    fun changed() {
        if (!manager.hints() || changePending) {
            return
        }

        changePending = true

        Promise.resolve(Unit).then {
            changePending = false
            _changes { it + 1 }
        }
    }
}

/**
 * Draws a [KeyHintStage]: nothing while the hints are hidden; else every hint, first unseen to measure it, then placed.
 */
class KeyHintStageView(ctx: Ctx<Props>) : Component<KeyHintStageView.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(val stage: KeyHintStage)

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val stage get() = props.stage

    private val shown by subscribingTo(stage.manager.hints) { placed = null }

    /** The chords of keys in a row pressed so far, while the manager waits for the next one. */
    private val waiting by subscribingTo(stage.manager.waiting)

    private val entries by subscribingTo(stage.entries) { placed = null }

    @Suppress("unused")
    private val changes by subscribingTo(stage.changes) { placed = null }

    /** Where each hint goes, by entry id; null: to be measured and placed after the next render. */
    private var placed: Map<Int, KeyHintLayout.Box>? = null

    private val onScrollOrResize: (Event) -> Unit = {
        if (shown) {
            placed = null
            triggerRedraw()
        }
    }

    init {
        lifecycle {
            onMount {
                window.addEventListener("scroll", onScrollOrResize, true)
                window.addEventListener("resize", onScrollOrResize)
            }

            onUpdate {
                if (shown && placed == null) {
                    placed = measureAndPlace()
                    triggerRedraw()
                }
            }

            onUnmount {
                window.removeEventListener("scroll", onScrollOrResize, true)
                window.removeEventListener("resize", onScrollOrResize)
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun measureAndPlace(): Map<Int, KeyHintLayout.Box> {
        val hints = (dom?.querySelectorAll("[$HINT_ATTRIBUTE]")?.asList() ?: emptyList())
            .mapNotNull { it as? HTMLElement }
            .associateBy { it.getAttribute(HINT_ATTRIBUTE)?.toIntOrNull() }

        val items = entries.mapNotNull { entry ->
            val hint = hints[entry.id] ?: return@mapNotNull null
            val anchor = entry.anchor()?.let { visibleBox(it) } ?: return@mapNotNull null

            KeyHintLayout.Item(
                id = entry.id,
                anchor = anchor,
                width = hint.offsetWidth.toDouble(),
                height = hint.offsetHeight.toDouble(),
                placement = entry.placement() ?: stage.placement,
            )
        }

        // The window without its scroll bars: a hint at the edge must not hide under one
        val viewport = document.documentElement

        return KeyHintLayout.place(
            items = items,
            viewportWidth = (viewport?.clientWidth ?: window.innerWidth).toDouble(),
            viewportHeight = (viewport?.clientHeight ?: window.innerHeight).toDouble(),
        )
    }

    /**
     * The box of [anchor] when some of it can be seen, cut by every scrolling or clipping ancestor up to the body; null
     * when nothing is left (a row scrolled out of its panel), so its hint does not float over whatever is drawn there.
     * The box returned is the whole anchor's: the hint belongs to the whole element.
     */
    private fun visibleBox(anchor: Element): KeyHintLayout.Box? {
        val rect = anchor.getBoundingClientRect()
        var left = rect.left
        var top = rect.top
        var right = rect.right
        var bottom = rect.bottom
        var ancestor = anchor.parentElement

        // Up to the body, not further: the overflow of body and html belongs to the window (Fomantic sets
        // `overflow-x: hidden` on the body), and the layout keeps every hint inside the window already
        val page = setOf<Element?>(document.body, document.documentElement)

        while (ancestor != null && ancestor !in page && right > left && bottom > top) {
            val style = window.getComputedStyle(ancestor)

            if (style.overflowX != "visible" || style.overflowY != "visible") {
                val clip = ancestor.getBoundingClientRect()
                left = max(left, clip.left)
                top = max(top, clip.top)
                right = min(right, clip.right)
                bottom = min(bottom, clip.bottom)
            }

            ancestor = ancestor.parentElement
        }

        if (right <= left || bottom <= top) {
            return null
        }

        // The anchor as it is, when it can be seen at all: the hint belongs to the whole element
        return KeyHintLayout.Box(left = rect.left, top = rect.top, width = rect.width, height = rect.height)
    }

    override fun VDom.render() {
        div(classes = STAGE_CLASS) {
            style = "position: fixed; left: 0; top: 0; width: 0; height: 0; pointer-events: none; z-index: 10000"
            // Chords in a row waiting for their second key: a chip at the foot of the window, hints or not. Said aloud
            // too: it is the only sign that the next key goes to the wait (a live region, there before the chip comes)
            div {
                key = "key-waiting"
                attributes["role"] = "status"
                attributes["aria-live"] = "polite"
                style = "position: fixed; left: 50%; bottom: 24px; transform: translateX(-50%)"

                if (waiting.isNotEmpty()) {
                    stage.waitingRenderer(this, KeyWaitingState(pressed = waiting, platform = stage.manager.platform))
                }
            }

            if (!shown) {
                return@div
            }

            val positions = placed

            entries.forEach { entry ->
                val state = entry.state() ?: return@forEach
                val renderer = entry.renderer() ?: stage.renderer
                val box = positions?.get(entry.id)

                div {
                    key = "key-hint-${entry.id}"
                    attributes[HINT_ATTRIBUTE] = entry.id.toString()
                    // Each hint repeats a control's key next to it; the key is part of the control's own description
                    attributes["aria-hidden"] = "true"
                    // Unplaced: drawn unseen, to measure it; placed: at its spot
                    style = when {
                        box == null -> "position: fixed; left: 0; top: 0; visibility: hidden"
                        // Whole pixels: text and lines placed between two pixels are drawn soft
                        else -> "position: fixed; left: ${box.left.roundToInt()}px; top: ${box.top.roundToInt()}px"
                    }

                    renderer(state)
                }
            }
        }
    }

    private companion object {
        const val STAGE_CLASS = "key-hint-stage"
        const val HINT_ATTRIBUTE = "data-key-hint"
    }
}

/** What the chip of chords in a row shows while the [KeysManager] waits for their next key. */
data class KeyWaitingState(
    /** The chords pressed so far, in order. */
    val pressed: List<KeyChord>,
    /** The platform the keys are shown for. */
    val platform: KeyPlatform,
) {
    /** The chords pressed so far as groups of key caps, one group per chord; see [KeyChord.keyParts]. */
    fun keyGroups(compact: Boolean = true): List<List<String>> =
        pressed.map { it.keyParts(platform = platform, compact = compact) }
}

/** Draws the chip of chords in a row waiting for their next key, in the app's own look ([KeyHints.waiting] by default). */
typealias KeyWaitingRenderer = FlowContent.(KeyWaitingState) -> Unit
