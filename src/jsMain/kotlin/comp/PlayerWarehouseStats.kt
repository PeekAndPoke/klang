/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.common.toFixed
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.streams.ops.filterIsInstance
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.switchMapNotNull
import kotlinx.css.Color
import kotlinx.css.Display
import kotlinx.css.FontWeight
import kotlinx.css.JustifyContent
import kotlinx.css.Padding
import kotlinx.css.TextAlign
import kotlinx.css.WhiteSpace
import kotlinx.css.backgroundColor
import kotlinx.css.borderRadius
import kotlinx.css.color
import kotlinx.css.display
import kotlinx.css.fontFamily
import kotlinx.css.fontSize
import kotlinx.css.fontWeight
import kotlinx.css.justifyContent
import kotlinx.css.lineHeight
import kotlinx.css.marginLeft
import kotlinx.css.marginRight
import kotlinx.css.opacity
import kotlinx.css.padding
import kotlinx.css.properties.LineHeight
import kotlinx.css.rem
import kotlinx.css.textAlign
import kotlinx.css.whiteSpace
import kotlinx.css.width
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
     * The current player's latest warehouse stats; kept while the player is gone, until the next one has its own.
     * Redraws only when they changed: no ticker needed.
     */
    private val stats: KlangCommLink.Feedback.Diagnostics.WarehouseStats? by subscribingTo(
        Player.player.switchMapNotNull { it.signals }
            .filterIsInstance<KlangPlaybackSignal.Diagnostics, _>()
            .map { it?.diagnostics?.warehouse }
            .distinct()
    )

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val w = stats

        div {
            css {
                display = Display.flex
                justifyContent = JustifyContent.center
                // A solid ground at 80 %, so the numbers read on any look.
                backgroundColor = Color("${KlangTheme.Hex.panelBackground}CC")
                borderRadius = 0.3.rem
                padding = Padding(vertical = 0.2.rem, horizontal = 0.5.rem)
                fontFamily = "monospace"
                fontSize = 0.7.rem
                lineHeight = LineHeight("0.85rem")
                whiteSpace = WhiteSpace.nowrap
                textAlign = TextAlign.left
                color = Color(KlangTheme.Hex.textSecondary)
                put("text-shadow", "0 0 4px #000")
            }

            title = "click: back to KLANGMOTOR"

            if (w == null) {
                div {
                    css { opacity = 0.6 }
                    +"warehouse: no diagnostics yet"
                }
                return@div
            }

            // Two columns of four lines each. Every number carries
            // its own hover hint; the labels say what the row is about.
            div {
                css { marginRight = 0.9.rem }
                line(
                    "rings", "Delay rings: the shelf of class-sized stereo buffers orbit and master delays rent",
                    mb(w.ringIdleBytes) to "bytes idle on the shelf",
                    "${w.ringIdleCount}i" to "rings idle on the shelf",
                    "${w.ringDirtyCount}d" to "idle rings not yet zeroed (housekeeping zeroes a slice per block)",
                )
                line(
                    "", "",
                    "${w.ringAllocations}a" to "rings allocated since start",
                    "${w.ringHits}h" to "rents served from the shelf without allocating",
                    "${w.ringFailures}f" to "allocations that failed (out of memory)",
                    "${w.ringDropped}x" to "idle rings freed because the shelf was over budget",
                    "${w.ringSyncCleans}z" to "rents that had to zero a dirty ring on the spot",
                )
                line(
                    "reverb", "Reverb networks: one size each, lazy on the first reverb()",
                    "${w.reverbIdleCount}i" to "networks idle on the shelf",
                    "${w.reverbDirtyCount}d" to "idle networks not yet zeroed",
                    "${w.reverbAllocations}a" to "networks allocated since start",
                    "${w.reverbHits}h" to "rents served from the shelf",
                    "${w.reverbFailures}f" to "allocations that failed (out of memory)",
                    "${w.reverbDropped}x" to "returned networks dropped because the shelf was full",
                )
                line(
                    "cyl", "Cylinders: whole orbits, built by the warmup, returned when a playback ends",
                    "${w.cylinderIdleCount}i" to "cylinders idle on the shelf",
                    "${w.cylinderAllocations}a" to "cylinders built since start",
                    "${w.cylinderHits}h" to "orbits served from the shelf",
                    "${w.cylinderDropped}x" to "idle cylinders dropped because the shelf was full",
                )
            }
            div {
                line(
                    "scratch", "Scratch buffers: the one shared block-sized pool every ignitor renders through",
                    "${w.scratchHighWater}/${w.scratchCapacity}" to "deepest nesting ever served / pre-sized capacity",
                    "${w.scratchLateAllocations}l" to "allocations that happened inside render (should stay 0)",
                    "${w.scratchUnbalancedReleases}u" to "releases without an acquire (should stay 0)",
                )
                line(
                    "samples", "Sample PCM resident in the backend's store",
                    mb(w.sampleBytes) to "PCM bytes resident",
                    "(${w.sampleCount})" to "samples resident (complete or still arriving)",
                    "${w.sampleAllocationFailures}f" to "uploads whose PCM could not be allocated (silent samples)",
                )
                line(
                    "late", "Voices dropped at admission because their start was already behind the render clock",
                    "${w.droppedVoices}" to "voices dropped as late since start (across all playbacks)",
                    warn = w.droppedVoices > 0,
                )
                line(
                    "dry", "Delay or reverb rents the warehouse refused for lack of memory: that orbit plays dry",
                    "${w.deniedRents}" to "refused rents since start (across all live orbits)",
                    warn = w.deniedRents > 0,
                )
            }
        }
    }

    private fun FlowContent.line(label: String, labelHint: String, vararg values: Pair<String, String>, warn: Boolean = false) {
        div {
            span {
                css {
                    display = Display.inlineBlock
                    width = 3.4.rem
                    color = Color.white
                    fontWeight = FontWeight.bold
                }
                if (labelHint.isNotEmpty()) {
                    title = labelHint
                }
                +label
            }
            for ((index, entry) in values.withIndex()) {
                val (value, hint) = entry
                span {
                    css {
                        if (warn) {
                            color = KlangTheme.warning
                        }
                        if (index > 0) {
                            marginLeft = 0.35.rem
                        }
                    }
                    title = hint
                    +value
                }
            }
        }
    }

    private fun mb(bytes: Double): String = "${(bytes / (1024.0 * 1024.0)).toFixed(1)}M"
}
