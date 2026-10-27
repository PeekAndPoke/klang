/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

import io.peekandpoke.klang.ui.comp.KlangAdsr
import io.peekandpoke.klang.ui.comp.KlangAdsrCurve
import io.peekandpoke.klang.ui.comp.KlangAdsrSignal
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.streams.Stream
import kotlinx.html.Tag

@Suppress("FunctionName")
fun Tag.AdsrVisual(
    visual: Block.Visual.Adsr,
    example: String?,
    signals: Stream<KlangAdsrSignal?>,
) = comp(
    AdsrVisual.Props(visual = visual, example = example, signals = signals)
) {
    AdsrVisual(it)
}

/**
 * A tutorial's ADSR picture ([Block.Visual.Adsr]): the curve of its "a:d:s:r" values, or of what [Props.example] (the
 * code right after it) plays (drift is lint-enforced in TutorialCurriculumSpec), following the notes [Props.signals] brings
 * (KLANG-00371). The drawing is klangui's [KlangAdsrCurve]: each stage in the curve it plays (the engine's
 * exponential unless the example writes `.adsrCurves(...)`), the widths schematic.
 */
class AdsrVisual(ctx: Ctx<Props>) : Component<AdsrVisual.Props>(ctx) {

    data class Props(
        val visual: Block.Visual.Adsr,
        /** The code right after the picture, the example it follows. */
        val example: String?,
        val signals: Stream<KlangAdsrSignal?>,
    )

    override fun VDom.render() {
        // Malformed values render nothing: the curriculum lint catches them at build time
        val (attack, decay, sustain, release) = props.visual.numbers(example = props.example) ?: return
        val (attackCurve, decayCurve, releaseCurve) = props.visual.curves(example = props.example)

        KlangAdsrCurve(
            adsr = KlangAdsr(
                attack = attack,
                decay = decay,
                sustain = sustain,
                release = release,
                attackCurve = attackCurve,
                decayCurve = decayCurve,
                releaseCurve = releaseCurve,
            ),
            signals = props.signals,
            label = props.visual.envelope.label,
        )
    }
}
