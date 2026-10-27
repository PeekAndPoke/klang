/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.script.intel.LocalUsage
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangDecl
import io.peekandpoke.klang.script.types.KlangParam
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.klang.ui.comp.KlangScreen
import io.peekandpoke.klang.ui.comp.MarkdownDisplay
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.html.onKeyDown
import kotlinx.browser.window
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.QuickHelpView(
    symbol: KlangSymbol,
    declarationCode: String?,
    onNavigate: (KlangSymbol, dynamic) -> Unit,
    usages: List<LocalUsage> = emptyList(),
    onJump: (pos: Int) -> Unit = {},
) = comp(
    QuickHelpView.Props(
        symbol = symbol,
        declarationCode = declarationCode,
        onNavigate = onNavigate,
        usages = usages,
        onJump = onJump,
    )
) {
    QuickHelpView(it)
}

/**
 * A name's help in the Quick help panel (KLANG-00349; the maintainer, 2026-10-27: the old popup's view in the wide
 * panel was "pretty much a mess", "wasting a lot of space", its signatures "extremely ugly"). Laid out for a wide,
 * low panel:
 *
 * - one line on top: the name, where it takes effect and where it comes from, and the key to its docs page;
 * - a name with several kinds (`lowpass` on a pattern, on a string, in an Ignitor, on the reverb): small keys pick
 *   one, a pattern's first; each kind shows only its own facts;
 * - two columns: on the left the call as one writes it (`.lowpass(freq, q, …)`, the optional parameters quieter, what
 *   it gives back after it), its description and its example; on the right its parameters, each name with its type,
 *   its default and what it does.
 *
 * A local name (the song's own) shows its declaration as the song writes it, and where the song uses it: each line
 * number with that line's code, a key that puts the cursor there (the maintainer, 2026-10-27: "all the usage sites
 * (line number and code) with the option to jump there").
 */
class QuickHelpView(ctx: Ctx<Props>) : Component<QuickHelpView.Props>(ctx) {

    data class Props(
        val symbol: KlangSymbol,
        /** A local's declaration as the song writes it; null for a library name. */
        val declarationCode: String?,
        val onNavigate: (KlangSymbol, dynamic) -> Unit,
        /** A local's uses in the code; none for a library name. */
        val usages: List<LocalUsage>,
        /** Puts the cursor in the code at an offset. */
        val onJump: (pos: Int) -> Unit,
    )

    /**
     * The kind chosen, by its place in [kinds]. A new symbol starts over: the same name comes with other kinds where
     * its receiver is known (`.lowpass` on a pattern has one, on an unknown receiver several).
     */
    private var chosen: Int? by value(null)

    /** "Copied" on the example's key for a moment. */
    private var copied: Boolean by value(false)

    /** The Copied key's way back to Copy; cleared when the view goes. */
    private var copiedTimer: Int? = null

    /** Gone from the page: a copy that finishes after it starts no timer. */
    private var unmounted = false

    init {
        lifecycle {
            onNextProps { next, previous ->
                if (next.symbol != previous.symbol) {
                    chosen = null
                }
            }

            onUnmount {
                unmounted = true
                copiedTimer?.let { window.clearTimeout(it) }
                copiedTimer = null
            }
        }
    }

    /** The name's kinds, a pattern's first, then the others as the library lists them. */
    private fun kinds(): List<KlangDecl> {
        val all = props.symbol.variants

        return all.filter { receiverOf(it)?.contains("Pattern") == true } + all.filterNot { receiverOf(it)?.contains("Pattern") == true }
    }

    private fun receiverOf(decl: KlangDecl): String? = when (decl) {
        is KlangCallable -> decl.receiver?.simpleName
        is KlangProperty -> decl.owner?.simpleName
    }

    /** A kind's key: what it is on, as short as it can be said. */
    private fun kindLabel(decl: KlangDecl): String {
        val on = receiverOf(decl) ?: return if (decl is KlangCallable) "function" else "value"

        return on.removePrefix("KlangScript").removePrefix("Sprudel").removeSuffix("Slots").removeSuffix("Dsl")
            .ifBlank { on }
    }

    override fun VDom.render() {
        val symbol = props.symbol
        val kinds = kinds()
        val decl = kinds.getOrNull(chosen ?: 0)

        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "12px")
                put("color", "var(--kl-ink-text)")
            }

            renderHeadline(symbol)

            if (kinds.size > 1) {
                renderKinds(kinds)
            }

            div {
                css {
                    put("display", "grid")
                    put("grid-template-columns", "repeat(auto-fit, minmax(320px, 1fr))")
                    put("gap", "14px 32px")
                    put("align-items", "start")
                }

                div {
                    css {
                        put("display", "flex")
                        put("flex-direction", "column")
                        put("gap", "10px")
                        put("min-width", "0")
                    }

                    val declaration = props.declarationCode

                    if (declaration != null) {
                        renderCode(declaration)
                    } else if (decl != null) {
                        renderCall(symbol.name, decl)
                    }

                    decl?.description?.takeIf { it.isNotBlank() }?.let { description ->
                        div {
                            css {
                                put("font-size", "13px")
                                put("line-height", "1.5")
                                put("max-width", "72ch")
                            }

                            MarkdownDisplay(description.trim(), linksInNewTab = true)
                        }
                    }

                    decl?.samples?.firstOrNull { it.code.isNotBlank() }?.let { sample -> renderExample(sample.code) }
                }

                (decl as? KlangCallable)?.params?.takeIf { it.isNotEmpty() }?.let { params -> renderParams(params) }

                if (props.declarationCode != null) {
                    renderUsages()
                }
            }
        }
    }

    /** The name, where it takes effect and where it comes from; the docs key at the end. */
    private fun FlowContent.renderHeadline(symbol: KlangSymbol) {
        div {
            css {
                put("display", "flex")
                put("align-items", "center")
                put("flex-wrap", "wrap")
                put("gap", "8px")
            }

            span {
                css {
                    put("font-family", "monospace")
                    put("font-size", "15px")
                    put("font-weight", "700")
                    put("color", "var(--kl-ink-strong)")
                    put("margin-right", "4px")
                }

                +symbol.name
            }

            symbol.scope?.let { scope -> engraved(text = scope.label, hint = scope.hint()) }

            when (val origin = symbol.origin) {
                null -> engraved(text = "built in")
                is KlangSymbol.Origin.Library -> engraved(text = origin.name.ifBlank { "built in" })
                is KlangSymbol.Origin.Local -> engraved(text = origin.kind.display)
            }

            if (symbol.aliases.isNotEmpty()) {
                span {
                    css {
                        put("font-size", "12px")
                        put("color", "var(--kl-ink-label)")
                    }

                    +"also ${symbol.aliases.joinToString(", ")}"
                }
            }

            div { css { put("flex", "1") } }

            // A library name has a docs page; a local name has its declaration instead
            if (symbol.origin !is KlangSymbol.Origin.Local) {
                KlangButton(onClick = { props.onNavigate(symbol, window.asDynamic().event) }) {
                    title = "Open its page in the docs; with Shift, in a new tab"
                    +"Docs"
                }
            }
        }
    }

    /** A small label engraved in the panel: what kind of thing it says. */
    private fun FlowContent.engraved(text: String, hint: String? = null) {
        span {
            css {
                put("font-size", "10px")
                put("letter-spacing", "0.08em")
                put("text-transform", "uppercase")
                put("padding", "2px 7px")
                put("border-radius", "var(--kl-radius)")
                put("border", "1px solid var(--kl-edge)")
                put("background", "var(--kl-ground-sunken)")
                put("color", "var(--kl-ink-label)")
            }

            hint?.let { title = it }
            +text
        }
    }

    /** The kinds of the name as small keys, the chosen one pressed. */
    private fun FlowContent.renderKinds(kinds: List<KlangDecl>) {
        div {
            css {
                put("display", "flex")
                put("flex-wrap", "wrap")
                put("gap", "6px")
            }

            kinds.forEachIndexed { index, decl ->
                val pressed = index == (chosen ?: 0)

                KlangButton(onClick = { chosen = index }, pressed = pressed) {
                    title = decl.signature
                    +kindLabel(decl)
                }
            }
        }
    }

    /** The call as one writes it: the optional parameters quieter, what it gives back after it. */
    private fun FlowContent.renderCall(name: String, decl: KlangDecl) {
        div {
            css {
                put("font-family", "monospace")
                put("font-size", "14px")
                put("line-height", "1.6")
                put("color", "var(--kl-ink-strong)")
                put("overflow-wrap", "anywhere")
            }

            when (decl) {
                is KlangCallable -> {
                    +"${if (decl.receiver != null) "." else ""}$name("

                    decl.params.forEachIndexed { index, param ->
                        if (index > 0) {
                            +", "
                        }

                        span {
                            if (param.isOptional) {
                                css { put("color", "var(--kl-ink-label)") }
                            }

                            +"${if (param.isVararg) "..." else ""}${param.name}"
                        }
                    }

                    +")"

                    decl.returnType?.let { returns -> quietly(" gives ${returns.render()}") }
                }

                is KlangProperty -> {
                    +"${if (decl.owner != null) "." else ""}$name"
                    quietly(" is ${decl.type.render()}")
                }
            }
        }
    }

    private fun FlowContent.quietly(text: String) {
        span {
            css {
                put("font-family", "inherit")
                put("font-size", "12px")
                put("color", "var(--kl-ink-label)")
            }

            +text
        }
    }

    /** The parameters: each name with its type and default, and under it what it does. */
    private fun FlowContent.renderParams(params: List<KlangParam>) {
        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "8px")
                put("min-width", "0")
            }

            div(classes = KlangControlStyles.heading.toString()) { +"Parameters" }

            for (param in params) {
                div {
                    key = param.name

                    div {
                        css {
                            put("display", "flex")
                            put("flex-wrap", "wrap")
                            put("align-items", "baseline")
                            put("gap", "8px")
                        }

                        span {
                            css {
                                put("font-family", "monospace")
                                put("font-size", "13px")
                                put("font-weight", "700")
                                put("color", "var(--kl-ink-strong)")
                            }

                            +param.name
                        }

                        span {
                            css {
                                put("font-family", "monospace")
                                put("font-size", "12px")
                                put("color", "var(--kl-ink-label)")
                            }

                            +param.type.render()
                            param.defaultDoc?.let { +" = $it" }

                            if (param.isOptional && param.defaultDoc == null) {
                                +", optional"
                            }
                        }
                    }

                    if (param.description.isNotBlank()) {
                        div {
                            css {
                                put("font-size", "12px")
                                put("line-height", "1.45")
                                put("margin-top", "2px")
                            }

                            MarkdownDisplay(param.description.trim(), linksInNewTab = true)
                        }
                    }
                }
            }
        }
    }

    /** Where the song uses a local: each line number with that line's code, a click puts the cursor there. */
    private fun FlowContent.renderUsages() {
        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "4px")
                put("min-width", "0")
            }

            div(classes = KlangControlStyles.heading.toString()) { +"Used on" }

            if (props.usages.isEmpty()) {
                div {
                    css {
                        put("font-size", "12px")
                        put("color", "var(--kl-ink-label)")
                    }

                    +"Nowhere yet."
                }
            }

            for (usage in props.usages) {
                div {
                    key = "use-${usage.start}"
                    attributes["role"] = "button"
                    attributes["tabindex"] = "0"
                    attributes["data-use"] = usage.start.toString()
                    title = "Put the cursor there"
                    css {
                        put("display", "flex")
                        put("gap", "12px")
                        put("align-items", "baseline")
                        put("padding", "3px 8px")
                        put("border-radius", "var(--kl-radius)")
                        put("cursor", "pointer")
                        put("font-family", "monospace")
                        put("font-size", "12px")
                    }

                    onClick { props.onJump(usage.start) }
                    onKeyDown { e ->
                        if (e.key == "Enter" || e.key == " ") {
                            e.preventDefault()
                            props.onJump(usage.start)
                        }
                    }

                    span {
                        css {
                            put("min-width", "4ch")
                            put("text-align", "right")
                            put("color", "var(--kl-ink-label)")
                        }

                        +usage.line.toString()
                    }

                    span {
                        css {
                            put("white-space", "nowrap")
                            put("overflow", "hidden")
                            put("text-overflow", "ellipsis")
                            put("color", "var(--kl-ink-text)")
                        }

                        +usage.code
                    }
                }
            }
        }
    }

    /** The example in a small screen, with a key that copies it. */
    private fun FlowContent.renderExample(code: String) {
        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "6px")
            }

            div {
                css {
                    put("display", "flex")
                    put("align-items", "center")
                    put("justify-content", "space-between")
                }

                div(classes = KlangControlStyles.heading.toString()) { +"Example" }

                KlangButton(onClick = { copy(code) }) { +if (copied) "Copied" else "Copy" }
            }

            renderCode(code)
        }
    }

    private fun FlowContent.renderCode(code: String) {
        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
            }

            KlangScreen(size = KlangScreen.Size.Small, bezel = "var(--kl-ground-panel)") {
                css {
                    put("padding", "8px 12px")
                    put("font-family", "monospace")
                    put("font-size", "13px")
                    put("white-space", "pre-wrap")
                    put("color", "var(--kl-ink-text)")
                }

                +code
            }
        }
    }

    /** Copies the example; "Copied" only once it is. Outside a secure context (a dev URL over http) there is no clipboard. */
    private fun copy(code: String) {
        val clipboard = window.navigator.asDynamic().clipboard ?: return

        clipboard.writeText(code).then(
            {
                if (!unmounted) {
                    copied = true
                    copiedTimer?.let { window.clearTimeout(it) }
                    copiedTimer = window.setTimeout({
                        copiedTimer = null
                        copied = false
                    }, 2000)
                }
            },
            { e: dynamic -> console.warn("QuickHelpView: could not copy the example", e) },
        )
    }
}
