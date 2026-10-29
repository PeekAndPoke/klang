/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.KlangGuiDance
import io.peekandpoke.klang.ui.GuiDance
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.klang.ui.comp.KlangDialog
import io.peekandpoke.klang.ui.comp.KlangLamp
import io.peekandpoke.klang.workshop.keys.openKeyLayer
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager
import io.peekandpoke.kraft.semanticui.modals.FadingModal
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.px
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.title
import org.w3c.dom.HTMLElement
import kotlin.js.Date

@Suppress("FunctionName")
fun Tag.WalkthroughsWindow(
    handle: ModalsManager.Handle<Boolean>,
    dance: GuiDance = KlangGuiDance.dance,
    mute: (forMs: Double) -> Unit = { KlangGuiDance.mute(it) },
    unmute: () -> Unit = { KlangGuiDance.unmute() },
) = comp(
    WalkthroughsWindow.Props(handle = handle, dance = dance, mute = mute, unmute = unmute)
) {
    WalkthroughsWindow(it)
}

/**
 * The walkthroughs (KLANG-00350; the maintainer, 2026-10-26: "a model showing all the "tours" already completed and how many
 * are still to come, just the number", and "a chance to "mute" the tour for some time"): the Gui-dance's arcs done, by
 * name, the number still to come, and keys to mute them for an hour or a day, or to unmute. A finished one, pressed,
 * is marked to be taken again (its lamp off). Nothing changes until Confirm; Cancel leaves all as it was. Opened by
 * the walkthroughs key at the workbench strip's foot. Called walkthroughs, not tours (the maintainer, 2026-10-27).
 */
class WalkthroughsWindow(ctx: Ctx<Props>) : FadingModal<WalkthroughsWindow.Props, Boolean>(ctx) {

    data class Props(
        override val handle: ModalsManager.Handle<Boolean>,
        /** The walkthroughs: the app's ([KlangGuiDance]), or a test's own. */
        val dance: GuiDance,
        /** Mutes them for a while, with the timer that ends the mute on time ([KlangGuiDance.mute]). */
        val mute: (forMs: Double) -> Unit,
        val unmute: () -> Unit,
    ) : FadingModal.Props<Boolean>()

    /** The walkthroughs' lit step: the window redraws when it changes, so a mute that runs out while it is open shows. */
    @Suppress("unused")
    private val lit by subscribingTo(props.dance.lit)

    private companion object {
        const val HOUR_MS = 60.0 * 60.0 * 1000.0
        const val DAY_MS = 24.0 * HOUR_MS
    }

    init {
        // Its keys over the page's, for the components below it too; closed when it unmounts
        openKeyLayer()

        lifecycle {
            onMount {
                // The last key, the main one, takes the keyboard
                val keys = dom?.querySelectorAll(".${KlangControlStyles.dialogKeys} button")

                (keys?.let { it.item(it.length - 1) } as? HTMLElement)?.focus()
            }
        }
    }

    /** What the window will change on Confirm: nothing until then (the maintainer, 2026-10-27). */
    private enum class MuteChoice { Keep, Hour, Day, Unmute }

    /** The finished walkthroughs marked to be taken again, their lamps off. */
    /**
     * The walkthroughs whose state Confirm turns over: a finished one taken again, one to come marked finished. Clicked
     * again, the mark goes.
     */
    private var toTurn: Set<String> by value(emptySet())

    /** Whether [arc] shows as finished: as it is, or turned over by a mark. */
    private fun shownFinished(arc: GuiDance.Arc): Boolean = (arc in props.dance.doneArcs()) != (arc.id in toTurn)

    private var muteChoice: MuteChoice by value(MuteChoice.Keep)

    override fun FlowContent.renderContent() {
        val dance = props.dance
        val done = dance.doneArcs()
        val toCome = dance.arcs.count { !shownFinished(it) }
        val mutedUntil = dance.mutedUntil()

        div {
            css {
                // In the middle of the dimmer, which covers the window; the dimmer's centred, unselectable text undone
                put("position", "absolute")
                put("top", "50%")
                put("left", "50%")
                put("transform", "translate(-50%, -50%)")
                // Wide enough for the walkthroughs' cards two to a row (the maintainer, 2026-10-28; wider for the cards,
                // 2026-10-29)
                put("width", "780px")
                put("max-width", "calc(100vw - 32px)")
                put("outline", "none")
                put("text-align", "left")
                put("line-height", "normal")
                put("user-select", "text")
            }
            // Focusable, so a click on the text keeps the keyboard in the modal
            attributes["tabindex"] = "-1"

            KlangDialog(title = "Walkthrough guidance", actions = actions()) {
                div {
                    +"Let's dance through the gui."
                }

                ui.divider()

                // A card per walkthrough: its lamp, lit when finished, its title, and two links under it, to turn it
                // over (finished, taken again; to come, marked finished) and to start it now (the maintainer, 2026-10-29:
                // "a small card for each tour. Lamp left. Title and below two "links" to click: 1. "Complete / Reopen"
                // and 2. "Start"")
                twoToARow {
                    for (arc in dance.arcs) {
                        renderCard(arc)
                    }
                }

                div {
                    css { put("margin-top", "14px") }

                    +when (toCome) {
                        0 -> "That was every walkthrough, for now."
                        1 -> "1 more to come."
                        else -> "$toCome more to come."
                    }
                }

                renderMute(mutedUntil = mutedUntil, toCome = toCome)
            }
        }
    }

    /**
     * The mute: while muted, its end and a key to unmute; else keys to mute for a while, there always, so the window
     * keeps its height, and disabled while no walkthrough is to come (the maintainer, 2026-10-27).
     */
    private fun FlowContent.renderMute(mutedUntil: Double?, toCome: Int) {
        div {
            css {
                put("margin-top", "14px")
                put("display", "flex")
                put("flex-wrap", "wrap")
                put("align-items", "center")
                put("gap", "8px")
            }

            if (mutedUntil != null) {
                div {
                    css { put("color", "var(--kl-ink-label)") }

                    +"Muted until ${whenText(mutedUntil)}."
                }

                muteKey(label = "Unmute", choice = MuteChoice.Unmute, enabled = true)
            } else {
                muteKey(label = "Mute for an hour", choice = MuteChoice.Hour, enabled = toCome > 0)
                muteKey(label = "Mute for a day", choice = MuteChoice.Day, enabled = toCome > 0)
            }
        }
    }

    /**
     * A mute choice as a toggle: pressed, it is the one Confirm applies; pressed again, nothing changes. Disabled, it
     * cannot be chosen, and a choice made before does not count.
     */
    private fun FlowContent.muteKey(label: String, choice: MuteChoice, enabled: Boolean) {
        val chosen = enabled && muteChoice == choice

        KlangButton(
            onClick = { muteChoice = if (chosen) MuteChoice.Keep else choice },
            pressed = chosen,
            lamp = if (chosen) KlangLamp.State.On else KlangLamp.State.Off,
            availability = if (enabled) KlangButton.Availability.Enabled else KlangButton.Availability.Disabled,
        ) {
            +label
        }
    }

    /** A walkthrough's card; its lamp and its first link show the state Confirm will leave it in. */
    private fun FlowContent.renderCard(arc: GuiDance.Arc) {
        val finished = shownFinished(arc)

        div {
            key = "arc-${arc.id}"
            attributes["data-arc"] = arc.id
            css {
                put("display", "flex")
                put("align-items", "flex-start")
                put("gap", "10px")
                put("padding", "10px 12px")
                put("background", "var(--kl-ground-raised)")
                put("border", "1px solid var(--kl-edge)")
                put("border-radius", "var(--kl-radius)")
            }

            div {
                css { put("padding-top", "3px") }
                KlangLamp(state = if (finished) KlangLamp.State.On else KlangLamp.State.Off, size = 10.px)
            }

            div {
                css {
                    put("flex", "1")
                    put("min-width", "0")
                }

                div {
                    css {
                        put("font-size", "13px")
                        put("color", "var(--kl-ink-strong)")
                    }
                    +arc.title
                }

                div {
                    css {
                        put("display", "flex")
                        put("gap", "16px")
                        put("margin-top", "6px")
                    }

                    link(
                        label = if (finished) "Reopen" else "Complete",
                        hint = if (finished) "Take this walkthrough again" else "Mark this walkthrough as finished",
                    ) {
                        toTurn = if (arc.id in toTurn) toTurn - arc.id else toTurn + arc.id
                    }

                    link(label = "Start", hint = "Start this walkthrough now") { start(arc) }
                }
            }
        }
    }

    /** A link-like key on a card. */
    private fun FlowContent.link(label: String, hint: String, onClick: () -> Unit) {
        button(type = ButtonType.button) {
            title = hint
            onClick { onClick() }
            css {
                put("padding", "0")
                put("border", "none")
                put("background", "none")
                put("color", "var(--kl-accent)")
                put("font-size", "12px")
                put("text-decoration", "underline")
                put("text-underline-offset", "2px")
                put("cursor", "pointer")
            }
            +label
        }
    }

    private fun FlowContent.twoToARow(content: FlowContent.() -> Unit) {
        div {
            css {
                put("display", "grid")
                put("grid-template-columns", "repeat(2, minmax(0, 1fr))")
                put("gap", "6px")
            }

            content()
        }
    }

    /** A moment as the user reads it: the weekday and the time. */
    private fun whenText(ms: Double): String {
        val options: dynamic = js("({})")

        options.weekday = "short"
        options.hour = "2-digit"
        options.minute = "2-digit"

        return Date(ms).asDynamic().toLocaleString(js("[]"), options) as String
    }

    /**
     * Confirm: the walkthroughs marked are taken again, the mute chosen is set; then the window closes. A walkthrough
     * taken again while muted, the mute left as it was, unmutes: else "take it again" would seem to do nothing until
     * the mute ends.
     */
    private fun confirm() {
        val dance = props.dance
        val wasMuted = dance.mutedUntil() != null

        val reopened = dance.doneArcs().filter { it.id in toTurn }

        for (arc in dance.arcs.filter { it.id in toTurn }) {
            if (arc in reopened) {
                dance.reopen(arc.id)
            } else {
                dance.complete(arc.id)
            }
        }

        // A mute chosen while walkthroughs were to come, then left with none to come, does not count
        val toCome = dance.remaining()

        when (muteChoice) {
            MuteChoice.Keep -> if (wasMuted && reopened.isNotEmpty()) props.unmute()
            MuteChoice.Hour -> if (toCome > 0) props.mute(HOUR_MS)
            MuteChoice.Day -> if (toCome > 0) props.mute(DAY_MS)
            MuteChoice.Unmute -> props.unmute()
        }

        close(result = true)
    }

    /**
     * Starts [arc] now, from its first step, and closes the window so its lamp shows at once; a mute ends, else the
     * walkthrough started would seem to do nothing. The window's other choices are left as they were.
     */
    private fun start(arc: GuiDance.Arc) {
        props.dance.start(arc.id)

        if (props.dance.mutedUntil() != null) {
            props.unmute()
        }

        close(result = true)
    }

    /** Cancel and Confirm (the maintainer, 2026-10-27): Esc cancels, Mod+Enter confirms. */
    private fun actions(): List<KlangDialog.Action> = listOf(
        KlangDialog.Action(label = "Cancel", key = KlangKeyBindings.DialogClose, onClick = { close(result = false) }),
        KlangDialog.Action(label = "Confirm", key = KlangKeyBindings.DialogConfirm, primary = true, onClick = { confirm() }),
    )
}
