/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.common.toFixed
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.PlayerWarehouseStats() = comp {
    PlayerWarehouseStats(it)
}

/**
 * The resource warehouse's stats, the way the backend keeps them: what it holds and what happened
 * to it (KLANG-00177 (warehouse stats feed)). Shown in the workbench's Motor tool window
 * (KLANG-00341).
 *
 * The snapshot rides every `Diagnostics`; the backend rebuilds it only when a part changed, so
 * this component only reads the latest one.
 */
class PlayerWarehouseStats(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The current player's latest warehouse stats; none while there is no player. Redraws only when they changed: no
     * ticker needed.
     */
    private val stats: KlangCommLink.Feedback.Diagnostics.WarehouseStats? by subscribingTo(
        Player.diagnostics.stream.map { it?.warehouse }.distinct()
    )

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val w = stats

        if (w == null) {
            div {
                css {
                    put("font-size", "12px")
                    put("color", "var(--kl-ink-label)")
                }
                +"No diagnostics yet"
            }

            return
        }

        // A tile per part of the warehouse, its main figure large and its counters written out under it, the tile's
        // hint on hover (the maintainer, 2026-10-28: "The warehouse still needs some "love"")
        div {
            css {
                put("display", "grid")
                put("grid-template-columns", "repeat(auto-fill, minmax(150px, 1fr))")
                put("gap", "8px")
            }

            tile(
                name = "Delay rings",
                hint = "The shelf of class-sized stereo buffers orbit and master delays rent",
                figure = mb(w.ringIdleBytes),
                unit = "idle",
                details = listOf(
                    "${w.ringIdleCount} idle, ${w.ringDirtyCount} to zero",
                    "${w.ringAllocations} built, ${w.ringHits} reused",
                    "${w.ringDropped} freed, ${w.ringSyncCleans} zeroed late",
                ),
                warn = w.ringFailures > 0,
                warning = "${w.ringFailures} failed for lack of memory",
            )
            tile(
                name = "Reverbs",
                hint = "Reverb networks: one size each, built on the first reverb()",
                figure = w.reverbIdleCount.toString(),
                unit = "idle",
                details = listOf(
                    "${w.reverbDirtyCount} to zero",
                    "${w.reverbAllocations} built, ${w.reverbHits} reused",
                    "${w.reverbDropped} dropped",
                ),
                warn = w.reverbFailures > 0,
                warning = "${w.reverbFailures} failed for lack of memory",
            )
            tile(
                name = "Cylinders",
                hint = "Whole orbits, built by the warmup, returned when a playback ends",
                figure = w.cylinderIdleCount.toString(),
                unit = "idle",
                details = listOf(
                    "${w.cylinderAllocations} built, ${w.cylinderHits} reused",
                    "${w.cylinderDropped} dropped",
                ),
            )
            tile(
                name = "Scratch",
                hint = "The one shared block-sized pool every ignitor renders through: the deepest nesting served, of " +
                    "the capacity it was sized for",
                figure = "${w.scratchHighWater}",
                unit = "of ${w.scratchCapacity}",
                details = listOf("deepest nesting"),
                warn = w.scratchLateAllocations > 0 || w.scratchUnbalancedReleases > 0,
                warning = "${w.scratchLateAllocations} late, ${w.scratchUnbalancedReleases} unbalanced",
            )
            tile(
                name = "Samples",
                hint = "Sample PCM resident in the backend's store",
                figure = mb(w.sampleBytes),
                unit = "PCM",
                details = listOf("${w.sampleCount} ${if (w.sampleCount == 1) "sample" else "samples"}"),
                warn = w.sampleAllocationFailures > 0,
                warning = "${w.sampleAllocationFailures} silent, no memory",
            )
            tile(
                name = "Late voices",
                hint = "Voices dropped at admission because their start was already behind the render clock",
                figure = w.droppedVoices.toString(),
                unit = "dropped",
                details = listOf("since the start"),
                warn = w.droppedVoices > 0,
            )
            tile(
                name = "Playing dry",
                hint = "Delay or reverb rents the warehouse refused for lack of memory: that orbit plays dry",
                figure = w.deniedRents.toString(),
                unit = "refused",
                details = listOf("since the start"),
                warn = w.deniedRents > 0,
            )
        }
    }

    /** A part of the warehouse: its [figure] large, its [details] under it; [warn] lights the tile and adds [warning]. */
    private fun FlowContent.tile(
        name: String,
        hint: String,
        figure: String,
        unit: String,
        details: List<String>,
        warn: Boolean = false,
        warning: String? = null,
    ) {
        div {
            title = hint
            css {
                put("padding", "8px 10px")
                put("background", "var(--kl-ground-sunken)")
                put("border", "1px solid " + if (warn) "var(--kl-lamp-warning)" else "var(--kl-edge)")
                put("border-radius", "var(--kl-radius)")
            }

            div {
                css {
                    put("font-size", "11px")
                    put("color", "var(--kl-ink-label)")
                }
                +name
            }

            div {
                css {
                    put("margin", "2px 0 4px")
                    put("font-family", "monospace")
                    put("font-size", "18px")
                    put("color", if (warn) "var(--kl-lamp-warning)" else "var(--kl-lcd-ink)")
                }
                +figure

                span {
                    css {
                        put("margin-left", "5px")
                        put("font-family", "inherit")
                        put("font-size", "11px")
                        put("color", "var(--kl-ink-label)")
                    }
                    +unit
                }
            }

            for (line in details) {
                div {
                    css {
                        put("font-size", "11px")
                        put("line-height", "15px")
                        put("color", "var(--kl-ink-text)")
                    }
                    +line
                }
            }

            if (warn && warning != null) {
                div {
                    css {
                        put("font-size", "11px")
                        put("line-height", "15px")
                        put("color", "var(--kl-lamp-warning)")
                    }
                    +warning
                }
            }
        }
    }

    private fun mb(bytes: Double): String = "${(bytes / (1024.0 * 1024.0)).toFixed(1)} MB"
}
