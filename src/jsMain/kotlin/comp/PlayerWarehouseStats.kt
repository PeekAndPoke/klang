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
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.streams.ops.ticker
import kotlinx.css.Color
import kotlinx.css.Display
import kotlinx.css.FontWeight
import kotlinx.css.JustifyContent
import kotlinx.css.TextAlign
import kotlinx.css.WhiteSpace
import kotlinx.css.backgroundColor
import kotlinx.css.borderRadius
import kotlinx.css.color
import kotlinx.css.display
import kotlinx.css.fontFamily
import kotlinx.css.fontSize
import kotlinx.css.fontWeight
import kotlinx.css.height
import kotlinx.css.justifyContent
import kotlinx.css.lineHeight
import kotlinx.css.marginRight
import kotlinx.css.opacity
import kotlinx.css.Padding
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
import kotlin.time.Duration.Companion.milliseconds

@Suppress("FunctionName")
fun Tag.PlayerWarehouseStats() = comp {
    PlayerWarehouseStats(it)
}

/**
 * The resource warehouse's stats, the way the backend keeps them: what it holds and what happened
 * to it (`docs/tasks/future/warehouse-stats-feed.md`). Rendered in the Motor's title slot when the
 * title is clicked, in place of the word, at exactly the slot's height so nothing else moves.
 *
 * The snapshot rides every `Diagnostics`; the backend rebuilds it only when a part changed, so
 * this component only reads the latest one.
 */
class PlayerWarehouseStats(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private var playerDiagnosticsSubscription: (() -> Unit)? = null
    private var stats: KlangCommLink.Feedback.Diagnostics.WarehouseStats? = null

    @Suppress("unused")
    private val ticker by subscribingTo(ticker(250.milliseconds))

    @Suppress("unused")
    private val currentPlayer by subscribingTo(Player.player) { p ->
        if (p != null) {
            playerDiagnosticsSubscription?.invoke()
            playerDiagnosticsSubscription = p.signals.subscribeToStream { signal ->
                if (signal is KlangPlaybackSignal.Diagnostics) {
                    stats = signal.diagnostics.warehouse
                }
            }
        }
    }

    init {
        lifecycle {
            onUnmount {
                playerDiagnosticsSubscription?.invoke()
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val w = stats

        div {
            css {
                height = Motor.TITLE_HEIGHT
                display = Display.flex
                justifyContent = JustifyContent.center
                // A solid ground at 80 % over the spectrum, or the numbers cannot be read.
                backgroundColor = Color("${KlangTheme.Hex.panelBackground}CC")
                borderRadius = 0.3.rem
                padding = Padding(vertical = 0.2.rem, horizontal = 0.5.rem)
                fontFamily = "monospace"
                fontSize = 0.62.rem
                lineHeight = LineHeight("0.9rem")
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

            // Two columns of four lines each — the height of the three gauges.
            div {
                css { marginRight = 0.9.rem }
                title = "Delay rings on the shelf: idle bytes (count, not yet zeroed) · allocated / shelf hits / failed / dropped / zeroed on rent"
                line("rings", "${mb(w.ringIdleBytes)} ${w.ringIdleCount}i ${w.ringDirtyCount}d")
                line("", "${w.ringAllocations}a ${w.ringHits}h ${w.ringFailures}f ${w.ringDropped}x ${w.ringSyncCleans}z")
                line("reverb", "${w.reverbIdleCount}i ${w.reverbDirtyCount}d ${w.reverbAllocations}a ${w.reverbHits}h")
                line("cyl", "${w.cylinderIdleCount}i ${w.cylinderAllocations}a ${w.cylinderHits}h ${w.cylinderDropped}x")
            }
            div {
                title = "Scratch: high water / capacity, late allocations, unbalanced releases · sample PCM bytes (count), allocation failures · voices dropped as late · delay/room rents refused"
                line("scratch", "${w.scratchHighWater}/${w.scratchCapacity} ${w.scratchLateAllocations}l ${w.scratchUnbalancedReleases}u")
                line("samples", "${mb(w.sampleBytes)} (${w.sampleCount}) ${w.sampleAllocationFailures}f")
                line("late", "${w.droppedVoices}", warn = w.droppedVoices > 0)
                line("dry", "${w.deniedRents}", warn = w.deniedRents > 0)
            }
        }
    }

    private fun FlowContent.line(label: String, value: String, warn: Boolean = false) {
        div {
            span {
                css {
                    display = Display.inlineBlock
                    width = 3.4.rem
                    color = Color.white
                    fontWeight = FontWeight.bold
                }
                +label
            }
            span {
                if (warn) {
                    css { color = KlangTheme.warning }
                }
                +value
            }
        }
    }

    private fun mb(bytes: Double): String = "${(bytes / (1024.0 * 1024.0)).toFixed(1)}M"
}
