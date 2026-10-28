/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.klang.ui.comp.KlangYesNoModal
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import io.peekandpoke.kraft.routing.JoinedPageTitle
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.span
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.DevStoragePage() = comp {
    DevStoragePage(it)
}

/**
 * The Dev Tools' storage page: what this browser keeps for Klang, part by part ([StorageKeys.parts]), each to reset on
 * its own, and all of it at once, as on a first visit (the maintainer, 2026-10-28: "a debug tool somewhere in the ui,
 * where i can reset everything in the local storage or individual settings"). Keys no part knows (left by an older
 * version) are listed apart. A reset takes effect on the next load: what the app holds in memory stays until then.
 */
class DevStoragePage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    /** Counts the resets, so the page reads the storage anew. */
    private var resets: Int by value(0)

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    /** Every key in the browser's storage for this page; none when it cannot be read. */
    private fun allKeys(): List<String> = try {
        (0 until localStorage.length).mapNotNull { localStorage.key(it) }.sorted()
    } catch (e: Throwable) {
        console.warn("DevStoragePage: could not read the storage", e)
        emptyList()
    }

    private fun valueOf(key: String): String = try {
        localStorage.getItem(key).orEmpty()
    } catch (e: Throwable) {
        ""
    }

    private fun remove(keys: List<String>) {
        for (key in keys) {
            try {
                localStorage.removeItem(key)
            } catch (e: Throwable) {
                console.warn("DevStoragePage: could not remove $key", e)
            }
        }

        resets++
    }

    /** Resets [part]'s keys; a precious part asks first. */
    private fun reset(part: StorageKeys.Part, keys: List<String>) {
        if (!part.precious) {
            remove(keys)

            return
        }

        modals.show<Boolean> { handle ->
            KlangYesNoModal(
                handle = handle,
                headline = "Reset ${part.name.lowercase()}?",
                content = { +"${part.about}: ${keys.size} kept in this browser, gone for good." },
                yesText = "Reset",
                noText = "Keep",
                onResult = { yes ->
                    if (yes) {
                        remove(keys)
                    }
                },
            )
        }
    }

    /** Clears everything and loads the app again, as on a first visit. */
    private fun resetEverything() {
        modals.show<Boolean> { handle ->
            KlangYesNoModal(
                handle = handle,
                headline = "Reset everything?",
                content = {
                    +"Everything this browser keeps for Klang goes: your songs, your own looks, your keys and every "
                    +"setting. Klang then starts as on a first visit."
                },
                yesText = "Reset everything",
                noText = "Keep it",
                onResult = { yes ->
                    if (yes) {
                        remove(allKeys())
                        window.location.reload()
                    }
                },
            )
        }
    }

    //  RENDER  /////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        // Read anew on every render: a reset counts up [resets], which renders again
        val keys = allKeys()
        val unknown = keys.filter { key -> StorageKeys.parts.none { it.covers(key) } }

        JoinedPageTitle { listOf("Storage", "Dev Tools") }

        div {
            key = "dev-storage-page"
            css {
                put("box-sizing", "border-box")
                put("height", "100%")
                put("overflow", "auto")
                put("padding", "28px 32px")
                put("color", "var(--kl-ink-text)")
            }

            h1(classes = KlangControlStyles.heading.toString()) {
                css {
                    put("margin", "0 0 10px")
                    put("font-size", "13px")
                }
                +"Storage"
            }

            div {
                css {
                    put("display", "flex")
                    put("flex-wrap", "wrap")
                    put("align-items", "center")
                    put("gap", "14px")
                    put("margin-bottom", "18px")
                }

                span {
                    css {
                        put("font-size", "13px")
                        put("color", "var(--kl-ink-label)")
                        put("margin-right", "auto")
                    }
                    +"What this browser keeps for Klang. A reset takes effect when the app loads again."
                }

                KlangButton(onClick = { window.location.reload() }) { +"Load again" }
                KlangButton(onClick = { resetEverything() }, surface = KlangButton.Surface.Domed) { +"Reset everything" }
            }

            // The parts in their sections: the settings, then the random seeds (the maintainer, 2026-10-28)
            StorageKeys.parts.groupBy { it.section }.entries.forEachIndexed { index, (section, parts) ->
                div(classes = KlangControlStyles.heading.toString()) {
                    key = "section-$section"
                    css { put("margin", if (index == 0) "0 0 10px" else "26px 0 10px") }
                    +section
                }

                div {
                    key = "parts-$section"
                    css {
                        put("display", "flex")
                        put("flex-direction", "column")
                        put("gap", "8px")
                    }

                    for (part in parts) {
                        renderPart(part, keys.filter(part.covers))
                    }
                }
            }

            if (unknown.isNotEmpty()) {
                div(classes = KlangControlStyles.heading.toString()) {
                    css { put("margin", "26px 0 10px") }
                    +"Not known to this version"
                }

                div {
                    css {
                        put("display", "flex")
                        put("flex-direction", "column")
                        put("gap", "8px")
                    }

                    for (key in unknown) {
                        row(
                            name = key,
                            about = "Left by an older version, or by something else on this site",
                            kept = listOf(key),
                            onReset = { remove(listOf(key)) },
                        )
                    }
                }
            }
        }
    }

    private fun FlowContent.renderPart(part: StorageKeys.Part, kept: List<String>) {
        row(name = part.name, about = part.about, kept = kept, onReset = { reset(part, kept) })
    }

    /** One part: its name and what it holds, what is kept (one value shortened, or a count), its size, its reset. */
    private fun FlowContent.row(name: String, about: String, kept: List<String>, onReset: () -> Unit) {
        val bytes = kept.sumOf { (it.length + valueOf(it).length) * 2 }

        div {
            key = "row-$name"
            css {
                put("display", "grid")
                put("grid-template-columns", "minmax(160px, 1fr) minmax(0, 2fr) 90px auto")
                put("align-items", "center")
                put("gap", "14px")
                put("padding", "10px 14px")
                put("background", "var(--kl-ground-raised)")
                put("border", "1px solid var(--kl-edge)")
                put("border-radius", "6px")
            }

            div {
                div {
                    css {
                        put("font-size", "14px")
                        put("color", "var(--kl-ink-strong)")
                        put("overflow-wrap", "anywhere")
                    }
                    +name
                }

                div {
                    css {
                        put("font-size", "12px")
                        put("color", "var(--kl-ink-label)")
                    }
                    +about
                }
            }

            div {
                css {
                    put("font-family", "monospace")
                    put("font-size", "12px")
                    put("color", if (kept.isEmpty()) "var(--kl-ink-label)" else "var(--kl-ink-text)")
                    put("white-space", "nowrap")
                    put("overflow", "hidden")
                    put("text-overflow", "ellipsis")
                }

                when (kept.size) {
                    0 -> +"nothing kept"
                    1 -> {
                        val value = valueOf(kept.single())
                        title = value
                        +value.take(PREVIEW_CHARS)
                    }
                    else -> +"${kept.size} kept"
                }
            }

            div {
                css {
                    put("font-family", "monospace")
                    put("font-size", "12px")
                    put("color", "var(--kl-ink-label)")
                    put("text-align", "right")
                }
                +if (kept.isEmpty()) "" else sizeText(bytes)
            }

            KlangButton(
                onClick = onReset,
                availability = if (kept.isEmpty()) KlangButton.Availability.Disabled else KlangButton.Availability.Enabled,
            ) { +"Reset" }
        }
    }

    private fun sizeText(bytes: Int): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }

    companion object {
        /** How much of a value the row shows; the whole is its hint. */
        private const val PREVIEW_CHARS = 80
    }
}
