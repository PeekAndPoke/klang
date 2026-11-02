/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.common.logging.LogBuffer
import io.peekandpoke.klang.common.logging.LogLevel
import io.peekandpoke.klang.common.logging.LogRecord
import io.peekandpoke.klang.common.logging.Logger
import io.peekandpoke.klang.spine.ConsoleLog
import io.peekandpoke.klang.ui.comp.KlangLamp
import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onChange
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.html.onMouseEnter
import io.peekandpoke.ultra.html.onScroll
import io.peekandpoke.ultra.semanticui.icon
import kotlinx.browser.window
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.option
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.title
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLSelectElement
import kotlin.js.Date
import kotlin.js.Promise

private val log = Logger("Console")

@Suppress("FunctionName")
fun Tag.ConsoleTool() = comp {
    ConsoleTool(it)
}

/**
 * The Console (KLANG-00407): the app's log, a tab of the bottom panel on every page, as the MIDI tool is. The records
 * newest last, each with its time, its level's lamp, its source and its message; filters by level and by source; Clear
 * and Copy (what is shown, as text, for a report). It follows the newest unless the user scrolled up. Shown, it marks the
 * errors seen, which the panel's key counts until then.
 */
class ConsoleTool(ctx: NoProps) : PureComponent(ctx) {

    companion object {
        /** The tool as a tab of a bottom panel, with this id beside the page's own tabs. */
        const val TAB_ID = "console"

        /** The tool as a tab of a bottom panel; its title counts the errors not seen yet. */
        fun tab(unseenErrors: Int) = KlangWorkbench.Tab(
            id = TAB_ID,
            title = when {
                unseenErrors > 99 -> "Console (99+)"
                unseenErrors > 0 -> "Console ($unseenErrors)"
                else -> "Console"
            },
        ) { ConsoleTool() }

        /** The bar's buttons: Fomantic's small basic look on a real button. */
        private const val BUTTON = "ui small compact basic button"

        /** The list's own class, to find it for the follow. */
        private const val LIST_CLASS = "kl-console-list"

        /** Within this of the end, the list counts as at the end: it follows. */
        private const val FOLLOW_SLACK_PX = 8

        /** The lamp of each level. */
        fun lampOf(level: LogLevel): KlangLamp.State = when (level) {
            LogLevel.Debug -> KlangLamp.State.Off
            LogLevel.Info -> KlangLamp.State.On
            LogLevel.Warn -> KlangLamp.State.Warning
            LogLevel.Error -> KlangLamp.State.Critical
        }

        /** [timeMs] as the wall clock's time, `14:03:07.215`. */
        fun timeOf(timeMs: Double): String {
            val date = Date(timeMs)

            fun two(n: Int) = n.toString().padStart(2, '0')

            return "${two(date.getHours())}:${two(date.getMinutes())}:${two(date.getSeconds())}." +
                    date.getMilliseconds().toString().padStart(3, '0')
        }

        /**
         * What [record]'s error adds to its message, `: the error`; nothing where the message says it already (an
         * uncaught error's message is its error's).
         */
        fun errorSuffixOf(record: LogRecord): String {
            val error = record.error ?: return ""
            val text = error.message ?: error.toString()

            return if (text in record.message) "" else ": $text"
        }

        /** [record] as one line of text, for Copy: `14:03:07.215 WARN [MidiRig] message: the error`. */
        fun lineOf(record: LogRecord): String =
            "${timeOf(record.timeMs)} ${record.level.name.uppercase()} [${record.source}] ${record.message}${errorSuffixOf(record)}"
    }

    private val console by subscribingTo(ConsoleLog.state)

    /** The levels shown; all at first. */
    private var levels: Set<LogLevel> by value(LogLevel.entries.toSet())

    /** The source shown; null for all. */
    private var source: String? by value(null)

    /** Whether the list keeps to its end as records come: until the user scrolls up, and again at the end. */
    private var follow = true

    /** Copy just done: its key says so for a moment. */
    private var copied: Boolean by value(false)

    /** The timer that takes "Copied" back; null while none runs. */
    private var copiedTimer: Int? = null

    /** Whether the tool is on the page: a copy that ends after it left changes nothing. */
    private var mounted = false

    init {
        lifecycle {
            // Shown, the errors are seen, and those that come as they come (after the render, never in it: it changes
            // the state this renders)
            onMount {
                mounted = true
                ConsoleLog.showing()
                keepToEnd()
            }

            onUpdate {
                keepToEnd()
            }

            onUnmount {
                mounted = false
                ConsoleLog.notShowing()
                copiedTimer?.let { window.clearTimeout(it) }
                copiedTimer = null
            }
        }
    }

    private fun list(): HTMLElement? = dom?.querySelector(".$LIST_CLASS") as? HTMLElement

    private fun keepToEnd() {
        if (follow) {
            list()?.let { it.scrollTop = it.scrollHeight.toDouble() }
        }
    }

    private fun shown(): List<LogBuffer.Entry> = console.buffer.entries.filter { (_, record) ->
        record.level in levels && (source == null || record.source == source)
    }

    override fun VDom.render() {
        val shown = shown()

        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("height", "100%")
                put("min-height", "0")
            }

            renderBar(shown)
            renderList(shown)
        }
    }

    /** The filters, Clear and Copy. */
    private fun FlowContent.renderBar(shown: List<LogBuffer.Entry>) {
        div {
            css {
                put("display", "flex")
                put("align-items", "center")
                put("gap", "8px")
                put("flex-wrap", "wrap")
                put("padding", "6px 12px")
            }

            LogLevel.entries.forEach { level ->
                val on = level in levels

                // Real buttons, so the keyboard reaches them (Fomantic's `ui.button` draws a div)
                button(type = ButtonType.button, classes = if (on) "$BUTTON active" else BUTTON) {
                    attributes["aria-pressed"] = on.toString()
                    onClick { levels = if (on) levels - level else levels + level }

                    css {
                        put("display", "inline-flex")
                        put("align-items", "center")
                        put("gap", "6px")
                    }

                    KlangLamp(state = if (on) lampOf(level) else KlangLamp.State.Unlit)
                    +level.name
                }
            }

            val sources = console.buffer.records.map { it.source }.distinct().sorted()

            select {
                attributes["aria-label"] = "Source"
                // The look's own ground and ink, not the browser's white field
                css {
                    put("height", "28px")
                    put("padding", "0 8px")
                    put("background", "var(--kl-ground-sunken)")
                    put("color", "var(--kl-ink-text)")
                    put("border", "1px solid var(--kl-edge)")
                    put("border-radius", "var(--kl-radius)")
                    put("font", "inherit")
                }
                onChange { event ->
                    source = (event.target as? HTMLSelectElement)?.value?.takeIf { it.isNotEmpty() }
                }

                option {
                    value = ""
                    selected = source == null
                    +"All sources"
                }

                // A source chosen before its records were cleared stays a choice
                (sources + listOfNotNull(source)).distinct().forEach { name ->
                    option {
                        value = name
                        selected = name == source
                        +name
                    }
                }
            }

            span { css { put("flex", "1") } }

            button(type = ButtonType.button, classes = BUTTON) {
                title = "Copy what is shown, as text"
                onClick { copy(shown.joinToString("\n") { lineOf(it.record) }) }

                if (copied) {
                    icon.check()
                    +"Copied"
                } else {
                    icon.copy_outline()
                    +"Copy"
                }
            }

            button(type = ButtonType.button, classes = BUTTON) {
                onClick {
                    follow = true
                    ConsoleLog.clear()
                }

                icon.trash_alternate_outline()
                +"Clear"
            }
        }
    }

    /**
     * [text] to the clipboard; "Copied" once it is there. Where the browser has no clipboard here (a page not served
     * over https) or refuses, a warning says so.
     */
    private fun copy(text: String) {
        val clipboard = window.navigator.asDynamic().clipboard

        if (clipboard == null) {
            log.warn("Could not copy: this browser offers no clipboard here")

            return
        }

        (clipboard.writeText(text) as Promise<Any?>).then(
            onFulfilled = {
                if (mounted) {
                    copied = true
                    // A copy again within the moment: its own moment, not what is left of the first one
                    copiedTimer?.let { window.clearTimeout(it) }
                    copiedTimer = window.setTimeout({
                        copiedTimer = null
                        copied = false
                    }, 1500)
                }
            },
            onRejected = { e -> log.warn("Could not copy", e) },
        )
    }

    /** The records shown, newest last. */
    private fun FlowContent.renderList(shown: List<LogBuffer.Entry>) {
        div(classes = LIST_CLASS) {
            attributes["role"] = "log"
            attributes["aria-label"] = "Console"

            css {
                put("flex", "1")
                put("min-height", "0")
                put("overflow", "auto")
                put("padding", "4px 12px 8px")
                put("font-family", "monospace")
                put("font-size", "12px")
                put("line-height", "1.5")
            }

            onScroll { event ->
                val list = event.currentTarget as? HTMLElement ?: return@onScroll

                follow = list.scrollHeight - list.scrollTop - list.clientHeight <= FOLLOW_SLACK_PX
            }

            if (shown.isEmpty()) {
                div {
                    css { put("color", "var(--kl-ink-label)") }

                    +if (console.buffer.records.isEmpty()) "Nothing logged yet" else "Nothing logged at these filters"
                }
            }

            shown.forEach { entry -> renderRecord(entry) }
        }
    }

    private fun FlowContent.renderRecord(entry: LogBuffer.Entry) {
        val record = entry.record

        div {
            // Its own number: a full buffer drops a row at the top for each that comes, the others stay as they are
            key = "r${entry.seq}"

            css {
                put("display", "flex")
                put("align-items", "baseline")
                put("gap", "8px")
            }

            span {
                css {
                    put("color", "var(--kl-ink-label)")
                    put("flex-shrink", "0")
                }

                +timeOf(record.timeMs)
            }

            span {
                css { put("flex-shrink", "0") }
                attributes["aria-label"] = record.level.name

                KlangLamp(state = lampOf(record.level))
            }

            span {
                css {
                    put("color", "var(--kl-ink-label)")
                    put("flex-shrink", "0")
                }

                +"[${record.source}]"
            }

            span {
                css {
                    put("color", "var(--kl-ink-text)")
                    put("white-space", "pre-wrap")
                    put("overflow-wrap", "anywhere")
                }

                +record.message

                record.error?.let { error ->
                    span {
                        // The whole stack on hover, built then, not for every row at every render
                        onMouseEnter { event -> (event.currentTarget as? HTMLElement)?.title = error.stackTraceToString() }
                        css { put("color", "var(--kl-ink-label)") }

                        +errorSuffixOf(record).ifEmpty { " (stack)" }
                    }
                }
            }
        }
    }
}
