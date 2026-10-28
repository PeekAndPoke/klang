/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.comp.Motor
import io.peekandpoke.klang.comp.Oscilloscope
import io.peekandpoke.klang.comp.Spectrumeter
import io.peekandpoke.klang.storage.LookStorage
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.klang.ui.comp.KlangGauge
import io.peekandpoke.klang.ui.comp.KlangScreen
import io.peekandpoke.klang.ui.comp.KlangTextField
import io.peekandpoke.klang.ui.comp.KlangValueKnob
import io.peekandpoke.klang.ui.comp.KlangYesNoModal
import io.peekandpoke.klang.ui.feel.KlangLoadout
import io.peekandpoke.klang.ui.feel.KlangLookPreset
import io.peekandpoke.klang.ui.feel.KlangLookShelf
import io.peekandpoke.klang.ui.feel.KlangLookText
import io.peekandpoke.klang.ui.feel.KlangLooks
import io.peekandpoke.klang.ui.feel.KlangRole
import io.peekandpoke.klang.ui.feel.KlangRoleGroup
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import io.peekandpoke.kraft.routing.JoinedPageTitle
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.html.onInput
import kotlinx.browser.window
import kotlinx.css.Color
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.input
import kotlinx.html.span
import kotlinx.html.textArea
import kotlinx.html.title
import org.khronos.webgl.Float32Array
import org.khronos.webgl.set
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

@Suppress("FunctionName")
fun Tag.LooksPage(slot: Int?) = comp(
    LooksPage.Props(slot = slot)
) {
    LooksPage(it)
}

/**
 * The look editor (KLANG-00382): the equip grid and the bench side by side, and below them the look clicked last, its
 * roles in tiles by [KlangRoleGroup].
 *
 * **Equipping** is pick and place, as games equip (the maintainer, 2026-10-28: "Like equipping in a video game"): a
 * click takes a look in hand, a click on a slot puts it there ([KlangLoadout.place]), the bench takes it off. It is
 * the mouse's, the keyboard's and the touch screen's way at once; the drag waits for Kraft's dnd (ULTRA-00265).
 *
 * **Live, then back:** while the page is open the app wears the look being edited, its draft included
 * ([KlangTheme.preview]); leaving, it goes back to the look worn. **Apply** keeps the draft in the look, **Wear it**
 * makes it the look worn, **Reset** drops the draft. A built-in look is read only: "Wear it" and "Duplicate to edit".
 * A draft not applied is kept when the page is left ([Drafts]): coming back shows it, still to apply or reset.
 */
class LooksPage(ctx: Ctx<Props>) : Component<LooksPage.Props>(ctx) {

    data class Props(
        /** The drawer's empty slot the user came from, to be filled by the next look picked; null for none. */
        val slot: Int?,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val shelf by subscribingTo(KlangLookShelf)

    /** The resolved look, draft included: the scope's and the spectrum's canvases take their colours from it. */
    private val look by subscribingTo(KlangTheme.look)

    /** The look clicked last, shown below for editing. */
    private var selectedId: String by value(KlangTheme.worn.id)

    /** The look taken in hand, to be placed on a slot or the bench; null for none. */
    private var inHand: String? by value(null)

    /** An empty slot waiting to be filled by the next look picked (from the drawer's "+"). */
    private var target: Int? by value(props.slot?.takeIf { it in 0 until KlangLoadout.SIZE })

    /** The selected look's roles as edited, not applied yet. */
    private var draft: Map<KlangRole, String> by value(Drafts.valuesOf(KlangTheme.worn))

    /** The selected look's name as edited. */
    private var draftName: String by value(Drafts.nameOf(KlangTheme.worn))

    /** The paste box: open, its text, and why the last paste was not a look. */
    private var pasteOpen: Boolean by value(false)
    private var pasteText: String by value("")
    private var pasteError: Boolean by value(false)

    private var copied: Boolean by value(false)

    /** The knob card's preview turns, so its arc shows. */
    private var knobPreview: Double by value(0.6)

    init {
        lifecycle {
            onMount { KlangTheme.preview(draft) }
            onUnmount { KlangTheme.endPreview() }
        }
    }

    //  ACTIONS  ////////////////////////////////////////////////////////////////////////////////////////////////

    private val selected: KlangLookPreset get() = shelf.byId(selectedId) ?: KlangLooks.Default

    private val dirty: Boolean get() = selected.own && (draft != selected.values || draftName != selected.name)

    private fun select(look: KlangLookPreset) {
        selectedId = look.id
        draft = Drafts.valuesOf(look)
        draftName = Drafts.nameOf(look)
        copied = false
        KlangTheme.preview(draft)
    }

    private fun edit(role: KlangRole, value: String?) {
        draft = when {
            value == null -> draft - role
            role.accepts(value) -> draft + (role to value)
            else -> return
        }

        Drafts.keep(selected, name = draftName, values = draft)
        KlangTheme.preview(draft)
    }

    private fun rename(name: String) {
        draftName = name.take(KlangLookPreset.NAME_MAX)
        Drafts.keep(selected, name = draftName, values = draft)
    }

    /** A click on a look, in a slot ([slot]) or on the bench (null). */
    private fun pick(look: KlangLookPreset, slot: Int?) {
        val hand = inHand
        val waiting = target

        when {
            hand == look.id -> inHand = null

            hand != null && slot != null -> {
                KlangLookShelf.place(hand, slot)
                inHand = null
            }

            waiting != null && slot == null -> {
                KlangLookShelf.place(look.id, waiting)
                target = null
                select(look)
            }

            else -> {
                inHand = look.id
                select(look)
            }
        }
    }

    /** A click on an empty slot: the look in hand goes there, else the slot waits for the next look picked. */
    private fun pickEmpty(slot: Int) {
        val hand = inHand

        if (hand != null) {
            KlangLookShelf.place(hand, slot)
            inHand = null
        } else {
            target = if (target == slot) null else slot
        }
    }

    /** The look in hand off its slot, to the bench. */
    private fun toBench() {
        val hand = inHand ?: return

        shelf.loadout.slotOf(hand)?.let { KlangLookShelf.unequip(it) }
        inHand = null
    }

    private fun apply(): KlangLookPreset {
        val kept = selected.copy(name = KlangLookText.cleanName(draftName), values = draft)

        KlangLookShelf.keep(kept)
        Drafts.forget(kept.id)
        draftName = kept.name

        if (KlangTheme.worn.id == kept.id) {
            KlangTheme.choose(kept)
        }

        return kept
    }

    private fun wear() {
        val look = if (dirty) apply() else selected

        KlangTheme.choose(look)
        LookStorage.save(look)
        KlangTheme.preview(draft)
    }

    private fun reset() {
        Drafts.forget(selectedId)
        select(selected)
    }

    /** A look of the user's own from [values], kept, selected, and on the waiting slot if there is one. */
    private fun create(name: String, values: Map<KlangRole, String>, author: String = "") {
        val look = KlangLookPreset(
            id = KlangLookShelf.newId(),
            name = freeName(KlangLookText.cleanName(name)),
            values = values,
            author = author,
            own = true,
        )

        KlangLookShelf.keep(look)

        target?.let {
            KlangLookShelf.place(look.id, it)
            target = null
        }

        select(look)
    }

    private fun duplicate() {
        create(name = selected.name, values = if (selected.own) draft else selected.values)
    }

    /** [name], or with the first number that makes it a name no look has: "Sun", "Sun 2", "Sun 3"; cut to fit. */
    private fun freeName(name: String): String {
        val taken = shelf.all.map { it.name }.toSet()

        return generateSequence(1) { it + 1 }
            .map { n -> if (n == 1) name else name.take(KlangLookPreset.NAME_MAX - " $n".length).trim() + " $n" }
            .first { it !in taken }
    }

    private fun askToDelete() {
        val look = selected

        modals.show<Boolean> { handle ->
            KlangYesNoModal(
                handle = handle,
                headline = "Delete \"${look.name}\"?",
                content = { +"The look is gone for good, and its slot is empty. A copy you shared stays a copy." },
                yesText = "Delete",
                noText = "Keep it",
                onResult = { yes ->
                    if (yes) {
                        KlangLookShelf.delete(look.id)
                        Drafts.forget(look.id)

                        if (KlangTheme.worn.id == look.id) {
                            KlangTheme.choose(KlangLooks.Default)
                            LookStorage.save(KlangLooks.Default)
                        }

                        select(KlangTheme.worn)
                    }
                },
            )
        }
    }

    /** Copies the look as text; "Copied" only once it is. Outside a secure context there is no clipboard. */
    private fun copyAsText() {
        val clipboard = window.navigator.asDynamic().clipboard ?: return
        val look = selected.copy(name = KlangLookText.cleanName(draftName), values = draft)

        clipboard.writeText(KlangLookText.encode(look)).then(
            { _: dynamic -> copied = true },
            { e: dynamic -> console.warn("LooksPage: could not copy the look", e) },
        )
    }

    private fun paste() {
        val look = KlangLookText.decode(pasteText, id = KlangLookShelf.newId())

        if (look == null) {
            pasteError = true

            return
        }

        create(name = look.name, values = look.values, author = look.author)
        pasteOpen = false
        pasteText = ""
        pasteError = false
    }

    /** What [role] shows: the draft's value, else the role it follows (as drafted), else its default. */
    private fun effective(role: KlangRole): String =
        draft[role] ?: role.follows?.let { effective(it) } ?: role.default

    //  RENDER  /////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        JoinedPageTitle { listOf("Looks") }

        div {
            key = "looks-page"
            css {
                put("box-sizing", "border-box")
                put("height", "100%")
                put("overflow", "auto")
                put("padding", "28px 32px")
                put("color", "var(--kl-ink-text)")
            }

            h1(classes = KlangControlStyles.heading.toString()) {
                css {
                    put("margin", "0 0 18px")
                    put("font-size", "13px")
                }

                +"Looks"
            }

            div {
                css {
                    put("display", "grid")
                    put("grid-template-columns", "minmax(0, 1fr) minmax(0, 1fr)")
                    put("gap", "18px")
                    put("align-items", "start")
                }

                renderEquipped()
                renderBench()
            }

            renderHint()
            renderEditor()
        }
    }

    private fun FlowContent.panel(title: String, content: FlowContent.() -> Unit) {
        div {
            css {
                put("background", "var(--kl-ground-raised)")
                put("border", "1px solid var(--kl-edge)")
                put("border-radius", "6px")
                put("padding", "16px")
                put("box-shadow", "inset 0 1px 0 rgba(255,255,255,0.05)")
            }

            div(classes = KlangControlStyles.heading.toString()) {
                css { put("margin-bottom", "12px") }
                +title
            }

            content()
        }
    }

    private fun FlowContent.renderEquipped() {
        panel("Equipped, as the drawer shows them") {
            div {
                css {
                    put("display", "grid")
                    put("grid-template-columns", "repeat(4, minmax(0, 1fr))")
                    put("gap", "8px")
                }

                shelf.equipped.forEachIndexed { slot, look ->
                    if (look != null) {
                        renderLook(look, slot)
                    } else {
                        renderEmptySlot(slot)
                    }
                }
            }
        }
    }

    private fun FlowContent.renderBench() {
        panel("The bench") {
            div {
                css {
                    put("display", "grid")
                    put("grid-template-columns", "repeat(auto-fill, minmax(96px, 1fr))")
                    put("gap", "8px")
                }

                for (look in shelf.bench) {
                    renderLook(look, slot = null)
                }

                // The bench takes a look in hand off its slot
                val hand = inHand

                if (hand != null && shelf.loadout.slotOf(hand) != null) {
                    dashedKey(label = "To the bench", action = { toBench() })
                }

                dashedKey(label = "+ New look", action = { create(name = "Mine", values = KlangTheme.worn.values) })
            }
        }
    }

    private fun FlowContent.renderLook(look: KlangLookPreset, slot: Int?) {
        val hand = inHand == look.id

        button(type = ButtonType.button) {
            key = "look-${look.id}"
            title = look.name
            attributes["aria-pressed"] = hand.toString()
            attributes["aria-label"] = look.name + (if (hand) ", in hand" else "")
            onClick { pick(look, slot) }

            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("align-items", "center")
                put("justify-content", "center")
                put("gap", "4px")
                put("height", "56px")
                put("min-width", "0")
                put("padding", "4px 6px")
                put("border-radius", "var(--kl-radius-domed)")
                put("border", "1px solid var(--kl-edge)")
                put("background", "radial-gradient(circle at 50% 0%, var(--kl-cap-light), var(--kl-cap-dark) 70%)")
                put("color", "var(--kl-ink-strong)")
                put("font-family", "inherit")
                put("font-size", "12px")
                put("cursor", "pointer")
                put("box-shadow", "inset 0 1px 0 rgba(255,255,255,0.12), 0 2px 4px rgba(0,0,0,0.5)")

                if (look.id == selectedId) {
                    put("outline", "2px solid var(--kl-lamp-on)")
                    put("outline-offset", "1px")
                }

                // In hand: a dashed ring in the focus blue, never the trail's gold
                if (hand) {
                    put("outline", "2px dashed var(--kl-focus)")
                    put("outline-offset", "3px")
                }
            }

            span {
                attributes["aria-hidden"] = "true"
                css {
                    put("width", "14px")
                    put("height", "14px")
                    put("border-radius", "50%")
                    put("border", "1px solid var(--kl-edge)")
                    put("background", "radial-gradient(circle at 40% 30%, ${look.swatch.joinToString(", ")})")
                }
            }

            span {
                css {
                    put("max-width", "100%")
                    put("overflow", "hidden")
                    put("text-overflow", "ellipsis")
                    put("white-space", "nowrap")
                }
                +look.name
            }
        }
    }

    private fun FlowContent.renderEmptySlot(slot: Int) {
        val waiting = target == slot

        button(type = ButtonType.button) {
            key = "slot-$slot"
            attributes["aria-label"] = if (waiting) "Empty slot, waiting for a look" else "Empty slot"
            onClick { pickEmpty(slot) }

            css {
                put("height", "56px")
                put("border-radius", "var(--kl-radius-domed)")
                put("border", "1px dashed " + if (waiting) "var(--kl-focus)" else "var(--kl-cap-light)")
                put("background", "transparent")
                put("color", "var(--kl-ink-label)")
                put("font-size", "16px")
                put("cursor", "pointer")
            }

            +"+"
        }
    }

    private fun FlowContent.dashedKey(label: String, action: () -> Unit) {
        button(type = ButtonType.button) {
            key = "key-$label"
            onClick { action() }

            css {
                put("height", "56px")
                put("padding", "4px 8px")
                put("border-radius", "var(--kl-radius-domed)")
                put("border", "1px dashed var(--kl-cap-light)")
                put("background", "transparent")
                put("color", "var(--kl-ink-label)")
                put("font-family", "inherit")
                put("font-size", "12px")
                put("cursor", "pointer")
            }

            +label
        }
    }

    private fun FlowContent.renderHint() {
        val hand = inHand?.let { shelf.byId(it) }
        val waiting = target

        div {
            css {
                put("min-height", "20px")
                put("margin", "10px 2px 0")
                put("font-size", "12px")
                put("color", "var(--kl-ink-label)")
            }

            when {
                hand != null -> +"\"${hand.name}\" in hand: click a slot to equip it, the bench to take it off, the look again to put it down."
                waiting != null -> +"Slot ${waiting + 1} waits: click a look on the bench, or make a new one, to fill it."
                else -> +"Click a look to take it in hand, then a slot to equip it. The drawer shows the eight equipped."
            }
        }
    }

    private fun FlowContent.renderEditor() {
        val look = selected
        val own = look.own

        div {
            css { put("margin-top", "18px") }

            panel(if (own) "Your look" else "Built-in look, read only") {
                renderNameRow(look)

                if (pasteOpen) {
                    renderPasteBox()
                }

                renderKeys(look)
            }
        }

        for (group in KlangRoleGroup.entries) {
            renderGroup(group, editable = own)
        }
    }

    /** The look's swatch, its name (a field for one's own), and how it stands: changed, saved, worn. */
    private fun FlowContent.renderNameRow(look: KlangLookPreset) {
        div {
            css {
                put("display", "flex")
                put("align-items", "center")
                put("gap", "12px")
            }

            span {
                attributes["aria-hidden"] = "true"
                css {
                    put("flex", "none")
                    put("width", "26px")
                    put("height", "26px")
                    put("border-radius", "50%")
                    put("border", "1px solid var(--kl-edge)")
                    put("background", "radial-gradient(circle at 40% 30%, ${look.copy(values = draft).swatch.joinToString(", ")})")
                }
            }

            if (look.own) {
                div {
                    css { put("width", "200px") }
                    KlangTextField(
                        label = "The look's name",
                        value = draftName,
                        onInput = { rename(it) },
                        suffix = "${draftName.length}/${KlangLookPreset.NAME_MAX}",
                    )
                }
            } else {
                span {
                    css {
                        put("font-size", "18px")
                        put("color", "var(--kl-ink-strong)")
                    }
                    +look.name
                }
            }

            span {
                css {
                    put("margin-left", "auto")
                    put("font-size", "12px")
                    put("color", if (dirty) "var(--kl-lamp-warning)" else "var(--kl-ink-label)")
                }
                +standing(look)
            }
        }
    }

    private fun standing(look: KlangLookPreset): String {
        val changes = KlangRole.entries.count { draft[it] != look.values[it] } + (if (draftName != look.name) 1 else 0)
        val worn = KlangTheme.worn.id == look.id

        return when {
            look.own && changes > 0 -> "$changes ${if (changes == 1) "change" else "changes"}, not applied yet"
            worn -> "Worn. The app wears it while you are here"
            look.own -> "Saved. The app wears it while you are here"
            else -> "Duplicate it to make it your own. The app wears it while you are here"
        }
    }

    /** The keys, in a sunken strip along the panel's foot, as a device's key row: editing left, sharing right. */
    private fun FlowContent.renderKeys(look: KlangLookPreset) {
        val own = look.own

        div {
            css {
                put("display", "flex")
                put("flex-wrap", "wrap")
                put("align-items", "center")
                put("gap", "8px")
                put("margin", "16px -16px -16px")
                put("padding", "12px 16px")
                put("background", "color-mix(in srgb, var(--kl-ground-sunken) 70%, transparent)")
                put("border-top", "1px solid var(--kl-edge)")
                // The panel's inner corner: its 6 px radius less its 1 px border
                put("border-radius", "0 0 5px 5px")
            }

            if (own) {
                KlangButton(onClick = { apply() }, availability = enabledIf(dirty)) { +"Apply" }
                KlangButton(onClick = { reset() }, availability = enabledIf(dirty)) { +"Reset" }
            }

            KlangButton(onClick = { wear() }, surface = KlangButton.Surface.Domed, pressed = KlangTheme.worn.id == look.id && !dirty) {
                +"Wear it"
            }

            KlangButton(onClick = { duplicate() }) { +if (own) "Duplicate" else "Duplicate to edit" }

            if (own) {
                KlangButton(onClick = { askToDelete() }) { +"Delete" }
            }

            span { css { put("flex", "1") } }

            KlangButton(onClick = { copyAsText() }) { +if (copied) "Copied" else "Copy as text" }
            KlangButton(onClick = { pasteOpen = !pasteOpen }, pressed = pasteOpen) { +"Paste a look" }
        }
    }

    private fun FlowContent.renderGroup(group: KlangRoleGroup, editable: Boolean) {
        div(classes = KlangControlStyles.heading.toString()) {
            key = "group-${group.name}"
            css { put("margin", "22px 0 10px") }
            +group.title
        }

        div {
            key = "cards-${group.name}"
            css {
                put("display", "grid")
                // Four cards to a row (the maintainer, 2026-10-28)
                put("grid-template-columns", "repeat(4, minmax(0, 1fr))")
                put("gap", "14px")
            }

            for (element in group.elements) {
                renderElement(element, editable = editable)
            }
        }
    }

    private fun enabledIf(on: Boolean) = if (on) KlangButton.Availability.Enabled else KlangButton.Availability.Disabled

    private fun FlowContent.renderPasteBox() {
        div {
            css {
                put("margin-top", "14px")
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "8px")
                put("max-width", "560px")
            }

            textArea {
                attributes["aria-label"] = "A look as text"
                attributes["placeholder"] = "Paste a look's text here"
                attributes["rows"] = "4"
                css {
                    put("background", "var(--kl-ground-sunken)")
                    put("color", "var(--kl-ink-text)")
                    put("border", "1px solid var(--kl-edge)")
                    put("border-radius", "var(--kl-radius)")
                    put("padding", "8px")
                    put("font-family", "monospace")
                    put("font-size", "12px")
                }

                onInput { e ->
                    (e.target as? HTMLTextAreaElement)?.let {
                        pasteText = it.value
                        pasteError = false
                    }
                }

                +pasteText
            }

            div {
                css {
                    put("display", "flex")
                    put("align-items", "center")
                    put("gap", "10px")
                }

                KlangButton(onClick = { paste() }, availability = enabledIf(pasteText.isNotBlank())) { +"Add the look" }

                if (pasteError) {
                    span {
                        css { put("color", "var(--kl-lamp-warning)") }
                        +"That is not a look's text."
                    }
                }
            }
        }
    }

    private fun FlowContent.renderElement(element: KlangRoleGroup.Element, editable: Boolean) {
        div {
            key = "element-${element.id}"
            css {
                if (element.id == "spectrum") {
                    put("grid-column", "span 2")
                }

                put("background", "var(--kl-ground-raised)")
                put("border", "1px solid var(--kl-edge)")
                put("border-radius", "6px")
                put("overflow", "hidden")
            }

            // The element's preview, centred on the panel's ground: a step up from the page, a step below the card's
            // lower part (the maintainer, 2026-10-28: the page's ground melted into it, the raised ground was too far)
            div {
                css {
                    put("display", "flex")
                    put("align-items", "center")
                    put("justify-content", "center")
                    put("gap", "14px")
                    // The spectrum as tall as the Motor's, so its cells are the Motor's
                    put("height", if (element.id == "spectrum") "${Motor.SPECTRUM_HEIGHT_REM}rem" else "96px")
                    put("background", "var(--kl-ground-panel)")
                    put("border-bottom", "1px solid var(--kl-edge)")
                }

                renderPreview(element.id)
            }

            div {
                css { put("padding", "12px 14px") }

                div {
                    css {
                        put("margin-bottom", "6px")
                        put("font-size", "13px")
                        put("font-weight", "700")
                        put("color", "var(--kl-ink-strong)")
                    }
                    +element.title
                }

                for ((role, label) in element.roles) {
                    renderRole(role, label, editable)
                }
            }
        }
    }

    private fun FlowContent.renderRole(role: KlangRole, label: String, editable: Boolean) {
        val value = effective(role)
        val following = role.follows?.takeIf { role !in draft }
        val saved = selected.values[role]

        div {
            key = "role-${role.name}"
            css {
                put("display", "flex")
                put("align-items", "center")
                put("gap", "8px")
                put("min-height", "30px")
                if (following != null) {
                    put("opacity", "0.6")
                }
            }

            span {
                css {
                    put("flex", "1")
                    put("display", "flex")
                    put("align-items", "center")
                    put("gap", "6px")
                    put("min-width", "0")
                    put("font-size", "13px")
                }
                +label

                if (following != null) {
                    span {
                        css {
                            put("font-size", "11px")
                            put("color", "var(--kl-ink-label)")
                        }
                        +"follows ${roleLabel(following).lowercase()}"
                    }
                }

                // A changed role's own undo, beside its label, so the controls never move (the maintainer, 2026-10-28)
                if (editable && draft[role] != saved) {
                    button(type = ButtonType.button) {
                        title = "Undo this change"
                        attributes["aria-label"] = "Undo the change to ${roleLabel(role)}"
                        onClick { edit(role, saved) }

                        css {
                            put("display", "inline-flex")
                            put("align-items", "center")
                            put("justify-content", "center")
                            put("flex", "none")
                            put("width", "18px")
                            put("height", "18px")
                            put("padding", "0")
                            put("border", "1px solid var(--kl-ink-label)")
                            put("border-radius", "50%")
                            put("background", "transparent")
                            put("color", "var(--kl-ink-label)")
                            put("font-size", "11px")
                            put("line-height", "1")
                            put("cursor", "pointer")
                        }

                        +"\u21B6"
                    }
                }
            }

            when (role.kind) {
                KlangRole.Kind.Color -> renderColor(role, value, editable)
                KlangRole.Kind.Length -> renderAmount(role, value, editable, range = 0.0..24.0, step = 1.0, unit = "px")
                KlangRole.Kind.Amount -> renderAmount(role, value, editable, range = 0.0..1.0, step = 0.01, unit = "")
            }
        }
    }

    private fun FlowContent.renderColor(role: KlangRole, value: String, editable: Boolean) {
        input(type = InputType.color) {
            attributes["aria-label"] = roleLabel(role)
            this.value = sixDigits(value)
            disabled = !editable
            css {
                put("width", "32px")
                put("height", "24px")
                put("padding", "0")
                put("border", "1px solid var(--kl-edge)")
                put("border-radius", "var(--kl-radius)")
                put("background", "transparent")
                put("cursor", if (editable) "pointer" else "default")
            }

            onInput { e -> (e.target as? HTMLInputElement)?.let { edit(role, it.value) } }
        }

        input(type = InputType.text) {
            attributes["aria-label"] = "${roleLabel(role)}, hex"
            attributes["spellcheck"] = "false"
            this.value = value
            readonly = !editable
            css {
                put("width", "80px")
                put("padding", "3px 6px")
                put("background", "var(--kl-ground-sunken)")
                put("color", "var(--kl-ink-text)")
                put("border", "1px solid var(--kl-edge)")
                put("border-radius", "var(--kl-radius)")
                put("font-family", "monospace")
                put("font-size", "12px")
            }

            // Only a value the role takes reaches the look; a half-typed one waits in the field
            onInput { e -> (e.target as? HTMLInputElement)?.let { edit(role, it.value.trim()) } }
        }
    }

    private fun FlowContent.renderAmount(
        role: KlangRole,
        value: String,
        editable: Boolean,
        range: ClosedFloatingPointRange<Double>,
        step: Double,
        unit: String,
    ) {
        val number = value.removeSuffix("px").toDoubleOrNull() ?: 0.0

        if (!editable) {
            span {
                css {
                    put("font-family", "monospace")
                    put("font-size", "12px")
                }
                +value
            }

            return
        }

        KlangValueKnob(
            name = roleLabel(role),
            value = number,
            range = range,
            step = step,
            onChange = { v ->
                val text = if (unit == "px") "${v.roundToInt()}px" else ((v * 1000).roundToInt() / 1000.0).toString()
                edit(role, text)
            },
            unit = unit,
            decimals = if (unit == "px") 0 else 2,
        )
    }

    private fun FlowContent.renderPreview(element: String) {
        when (element) {
            "grounds" -> div {
                css {
                    put("display", "flex")
                    put("width", "200px")
                    put("height", "64px")
                    put("border", "1px solid var(--kl-edge)")
                }

                div {
                    css {
                        put("width", "24px")
                        put("background", "var(--kl-ground-deep)")
                    }
                }

                div {
                    css {
                        put("flex", "1")
                        put("display", "flex")
                        put("align-items", "center")
                        put("justify-content", "center")
                        put("gap", "8px")
                        put("background", "var(--kl-ground-panel)")
                    }

                    swatchBox("var(--kl-ground-raised)", 40, 36)
                    swatchBox("var(--kl-ground-sunken)", 40, 20)
                }
            }

            "ink" -> div {
                css {
                    put("padding", "8px 12px")
                    put("background", "var(--kl-ground-panel)")
                    put("border", "1px solid var(--kl-edge)")
                    put("font-size", "12px")
                    put("line-height", "1.4")
                }

                div {
                    css {
                        put("color", "var(--kl-ink-strong)")
                        put("font-weight", "700")
                    }
                    +"Headline"
                }

                div {
                    css { put("color", "var(--kl-ink-text)") }
                    +"Body text"
                }

                div(classes = KlangControlStyles.heading.toString()) { +"Label" }
            }

            "corners" -> {
                KlangButton(onClick = {}) { +"Flat" }
                KlangButton(onClick = {}, surface = KlangButton.Surface.Domed) { +"Domed" }
            }

            "texture" -> {
                KlangButton(onClick = {}, surface = KlangButton.Surface.Domed, shape = KlangButton.Shape.Tile) { +"Grain" }

                div(classes = KlangControlStyles.workbenchColumn.toString()) {
                    css {
                        put("flex", "none")
                        put("width", "64px")
                        put("height", "56px")
                        put("border", "1px solid var(--kl-edge)")
                    }
                }
            }

            "metal" -> span {
                css {
                    put("width", "48px")
                    put("height", "48px")
                    put("border-radius", "50%")
                    put("border", "1px solid var(--kl-edge)")
                    put("background", "radial-gradient(circle at 40% 30%, var(--kl-metal-light), var(--kl-metal-mid), var(--kl-metal-dark))")
                    put("color", "var(--kl-metal-ink)")
                    put("display", "flex")
                    put("align-items", "center")
                    put("justify-content", "center")
                    put("font-weight", "700")
                }
                +"A"
            }

            "caps" -> {
                KlangButton(onClick = {}, surface = KlangButton.Surface.Domed) { +"Key" }
                KlangButton(onClick = {}, surface = KlangButton.Surface.Domed, pressed = true) { +"Pressed" }
            }

            "lamps" -> for (lamp in listOf("off", "on", "try", "warning", "critical")) {
                span {
                    css {
                        put("width", "14px")
                        put("height", "14px")
                        put("border-radius", "50%")
                        put("background", "var(--kl-lamp-$lamp)")
                        put("box-shadow", if (lamp == "off") "inset 0 1px 2px rgba(0,0,0,0.6)" else "0 0 8px var(--kl-lamp-$lamp)")
                    }
                }
            }

            "accent" -> div {
                css {
                    put("width", "180px")
                    put("font-size", "12px")
                }

                div {
                    css {
                        put("padding", "4px 8px")
                        put("color", "var(--kl-accent)")
                    }
                    +"A link"
                }

                div {
                    css {
                        put("padding", "4px 8px")
                        put("background", "color-mix(in srgb, var(--kl-accent-muted) 35%, transparent)")
                        put("border-left", "2px solid var(--kl-accent-muted)")
                        put("color", "var(--kl-ink-strong)")
                    }
                    +"A selected row"
                }
            }

            "scrollbar" -> div {
                css {
                    put("width", "12px")
                    put("height", "72px")
                    put("padding", "2px")
                    put("box-sizing", "border-box")
                    put("background", "var(--kl-ground-sunken)")
                    put("border-radius", "6px")
                }

                // As the page shows it, at 65 %
                div {
                    css {
                        put("height", "40px")
                        put("margin-top", "14px")
                        put("border-radius", "4px")
                        put("background", "color-mix(in srgb, var(--kl-scroll-thumb-hover) 65%, transparent)")
                    }
                }
            }

            "focus" -> KlangButton(onClick = {}) {
                css {
                    put("outline", "2px solid var(--kl-focus)")
                    put("outline-offset", "2px")
                }
                +"Focused"
            }

            "gauge" -> KlangGauge(name = "Preview", value = 0.62)

            "knob" -> KlangValueKnob(
                name = "Preview",
                value = knobPreview,
                range = 0.0..1.0,
                step = 0.01,
                onChange = { knobPreview = it },
                unit = "",
                decimals = 2,
            )

            "scope" -> div {
                css {
                    put("width", "220px")
                    put("height", "${Motor.OSCILLOSCOPE_HEIGHT_REM}rem")
                }

                Oscilloscope(player = Player.player, strokeColor = Color(look[KlangRole.ScopeTrace]), preview = SCOPE_FRAME)
            }

            "screen" -> div {
                css {
                    put("display", "flex")
                    put("width", "200px")
                    put("height", "72px")
                }

                KlangScreen(size = KlangScreen.Size.Small, surface = "#000000", tube = true) {
                    // The editor's own class, so the glow reaches it as it reaches the code
                    div(classes = "cm-content") {
                        css {
                            put("padding", "8px 10px")
                            put("font-family", "monospace")
                            put("font-size", "12px")
                            put("line-height", "1.5")
                        }

                        div {
                            token(color = "#c678dd", text = "let ")
                            token(color = "#e5c07b", text = "feel")
                            token(color = "#abb2bf", text = " = 5")
                        }

                        div {
                            css { put("color", "#7f848e") }
                            +"// the tube glows"
                        }
                    }
                }
            }

            "lcd" -> span {
                css {
                    put("padding", "4px 10px")
                    put("background", "var(--kl-ground-sunken)")
                    put("color", "var(--kl-lcd-ink)")
                    put("font-family", "monospace")
                    put("font-size", "18px")
                    put("border", "1px solid var(--kl-edge)")
                    put("border-radius", "var(--kl-radius)")
                }
                +"128"
            }

            "spectrum" -> div {
                css {
                    put("width", "100%")
                    put("align-self", "stretch")
                }

                Spectrumeter(
                    colors = Motor.SPECTRUM_ROLES.map { Color(look[it]) },
                    numBoxesInStack = Motor.SPECTRUM_BOXES,
                    colorStep = Motor.SPECTRUM_COLOR_STEP,
                    numBuckets = Motor.SPECTRUM_COLUMNS,
                    preview = SPECTRUM_LEVELS,
                ) { null }
            }
        }
    }

    private fun FlowContent.token(color: String, text: String) {
        span {
            css { put("color", color) }
            +text
        }
    }

    private fun FlowContent.swatchBox(background: String, width: Int, height: Int) {
        span {
            css {
                put("width", "${width}px")
                put("height", "${height}px")
                put("background", background)
                put("border", "1px solid var(--kl-edge)")
                put("border-radius", "var(--kl-radius)")
            }
        }
    }

    companion object {
        /** The scope card's still frame: a tone with an overtone, as a song might show it. */
        private val SCOPE_FRAME = Float32Array(2048).also { frame ->
            for (i in 0 until frame.length) {
                val t = i / frame.length.toDouble()
                frame[i] = (0.5 * sin(2 * PI * 3 * t) + 0.15 * sin(2 * PI * 9 * t)).toFloat()
            }
        }

        /** The spectrum card's still levels: loud in the bass, falling to the top, the first columns into the peak band. */
        private val SPECTRUM_LEVELS = List(Motor.SPECTRUM_COLUMNS) { i ->
            0.85 * exp(-i / 11.0) + 0.06 * sin(i * 1.7) + 0.04
        }

        /** "GroundSunken" as "Ground sunken". */
        private fun roleLabel(role: KlangRole): String =
            role.name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase().replaceFirstChar { it.uppercase() }

        /** The colour input takes `#rrggbb` only: a short one doubled, an alpha cut (shown whole in the hex field). */
        private fun sixDigits(hex: String): String {
            val digits = hex.removePrefix("#")

            return when (digits.length) {
                3, 4 -> "#" + digits.take(3).map { "$it$it" }.joinToString("")
                6, 8 -> "#" + digits.take(6)
                else -> "#000000"
            }.lowercase()
        }
    }
}

/**
 * The drafts not applied yet, per look, kept while the app runs (KLANG-00382): leaving the editor keeps them, coming
 * back shows them. The ticket's first design asked before leaving; Kraft's router has no guard for leaving a page (its
 * middleware runs for the target and can only redirect), and a kept draft loses nothing without one. A reload or a
 * closed tab would lose them: the browser asks first while one is pending.
 */
private object Drafts {

    private val pending = mutableMapOf<String, Pair<String, Map<KlangRole, String>>>()

    /** The one listener, added with the first draft, for the app's life. */
    private var guarding = false

    fun nameOf(look: KlangLookPreset): String = pending[look.id]?.first ?: look.name

    fun valuesOf(look: KlangLookPreset): Map<KlangRole, String> = pending[look.id]?.second ?: look.values

    /** [name] and [values] as [look]'s draft; none when they are what it holds, or it is not one of the user's own. */
    fun keep(look: KlangLookPreset, name: String, values: Map<KlangRole, String>) {
        if (!look.own || (name == look.name && values == look.values)) {
            forget(look.id)

            return
        }

        pending[look.id] = name to values
        guard()
    }

    fun forget(id: String) {
        pending.remove(id)
    }

    private fun guard() {
        if (guarding) {
            return
        }

        guarding = true

        window.addEventListener("beforeunload", { e ->
            if (pending.isNotEmpty()) {
                e.preventDefault()
                e.asDynamic().returnValue = ""
            }
        })
    }
}
