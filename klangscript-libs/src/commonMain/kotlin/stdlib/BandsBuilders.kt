/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:KlangScript.Library(KlangScriptLibraries.STDLIB)

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorBandsBuilder
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystBandsBuilder
import io.peekandpoke.klang.audio_bridge.KatalystBuilder
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

// The script door of `bands`' builders (typed-doors rollout S6): the builders, their typed members and the
// Linkwitz-Riley crossover live once in `audio_bridge` (`BandsBuilders.kt`, with the crossover's reasons). These are
// the `internal`, renamed variants (the typed-door pattern, `/dsl-design` §3 point 3) that carry the user-facing text.
// The `band` variant wraps the script's processor in its stage check, the band and the processor numbers known when
// it wraps, so the typed split only invokes it; `cut` is a plain number on both doors and passes through.

// ── Ignitor ──────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Adds [processor] to the band being written. Two `band()` calls on one band SUM their processors, as `parallel` does;
 * a band with no `band()` passes untouched.
 *
 * @param processor a function from the band's signal to a signal.
 */
@KlangScript.Function(name = "band")
internal fun IgnitorBandsBuilder.scriptBand(processor: (IgnitorDsl) -> IgnitorDsl): IgnitorBandsBuilder = this.band(
    processor = checkedStage(
        door = "band ${processors.size} of Ignitor bands",
        noun = "processor",
        index = processors.last().size,
        stage = processor,
        returns = "signal",
        example = "x => x.distort(0.5)",
        isResult = { it is IgnitorDsl },
    ),
)

/**
 * Closes the band being written at [freq] and starts the next one up. A cut below the one before it is moved up to it.
 * A plain number: the crossover does not move.
 *
 * @param freq the crossover frequency in Hz.
 */
@KlangScript.Function(name = "cut")
internal fun IgnitorBandsBuilder.scriptCut(freq: Double): IgnitorBandsBuilder = this.cut(freq = freq)

// ── Katalyst ─────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Adds [processor] to the band being written: a function of stages that receives an empty builder, the band at this
 * point, as a `parallel` branch does. Two `band()` calls on one band SUM their processors; a band with no `band()`
 * passes untouched.
 *
 * @param processor a function from a builder to a builder.
 */
@KlangScript.Function(name = "band")
internal fun KatalystBandsBuilder.scriptBand(processor: (KatalystBuilder) -> KatalystBuilder): KatalystBandsBuilder = this.band(
    processor = checkedStage(
        door = "band ${processors.size} of Katalyst bands",
        noun = "processor",
        index = processors.last().size,
        stage = processor,
        returns = "builder",
        example = "b => b.distort(0.5)",
        isResult = { it is KatalystBuilder },
    ),
)

/**
 * Closes the band being written at [freq] and starts the next one up. A cut below the one before it is moved up to it.
 * A plain number: the crossover does not move.
 *
 * @param freq the crossover frequency in Hz.
 */
@KlangScript.Function(name = "cut")
internal fun KatalystBandsBuilder.scriptCut(freq: Double): KatalystBandsBuilder = this.cut(freq = freq)
