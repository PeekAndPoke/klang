/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.PipelineDsl
import io.peekandpoke.klang.audio_bridge.StageDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * Config methods on the VCA stage (`Stage.vca()`). Each returns a new `StageDsl.Vca`, so chain
 * them right after `Stage.vca()` and before adding the next stage.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(StageDsl.Vca::class)
object KlangScriptVcaStageExtensions {

    /** Exponential-curve steepness (default 3.0). Larger = steeper exp attack/decay/release. */
    @KlangScript.Method
    fun expK(self: StageDsl.Vca, k: Double): StageDsl.Vca = self.copy(expK = k)

    /** Gain de-click time constant in seconds (default 0.001). Rounds ADSR segment-join clicks. */
    @KlangScript.Method
    fun declick(self: StageDsl.Vca, seconds: Double): StageDsl.Vca = self.copy(declickSeconds = seconds)
}

/**
 * Config methods on the filter stage (`Stage.filter()`). All values are scaled by the note's
 * `analog` param; each returns a new `StageDsl.Filter` for chaining.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(StageDsl.Filter::class)
object KlangScriptFilterStageExtensions {

    // Defaults quoted below are FILTER_* in audio_bridge/constants/FilterHumanizationDefaults.kt —
    // the single declaration StageDsl.Filter reads. Re-check these strings when retuning: they are
    // harvested into the live-editor tooltips, and they were stale before 2026-08-11 —
    // cutoffOffset by 15x (0.003 vs 0.0002), drift by 20x (5.0 vs 0.25), drive by 2x.

    /** Per-voice cutoff-offset scale per unit analog (default 0.0002 ≈ ±0.35 cents at analog=1). */
    @KlangScript.Method
    fun cutoffOffset(self: StageDsl.Filter, perAnalog: Double): StageDsl.Filter =
        self.copy(cutoffOffsetPerAnalog = perAnalog)

    /** SVF drive / saturation scale per unit analog (default 0.25; more = more OB-X "bite"). */
    @KlangScript.Method
    fun drive(self: StageDsl.Filter, perAnalog: Double): StageDsl.Filter =
        self.copy(drivePerAnalog = perAnalog)

    /**
     * Filter cutoff drift magnitude relative to oscillator pitch drift (default 0.25 — the filter
     * currently wanders 4x LESS than pitch). Oscillator drift is 1.0 cent per unit analog, so this
     * is directly the filter-to-pitch drift ratio: 1.0 would make them equal.
     */
    @KlangScript.Method
    fun drift(self: StageDsl.Filter, relToOsc: Double): StageDsl.Filter =
        self.copy(driftRelToOsc = relToOsc)
}

/**
 * Convenience tweaks on a whole [PipelineDsl] — forward to its (single) VCA stage so a preset can
 * be nudged in one call: `Pipeline.modern().expK(2.5).declick(0.0008)`.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(PipelineDsl::class)
object KlangScriptPipelineExtensions {

    private fun PipelineDsl.tweakVca(f: (StageDsl.Vca) -> StageDsl.Vca): PipelineDsl =
        copy(stages = stages.map { if (it is StageDsl.Vca) f(it) else it })

    /** Sugar for the engine's VCA `expK`. */
    @KlangScript.Method
    fun expK(self: PipelineDsl, k: Double): PipelineDsl = self.tweakVca { it.copy(expK = k) }

    /** Sugar for the engine's VCA `declick` seconds. */
    @KlangScript.Method
    fun declick(self: PipelineDsl, seconds: Double): PipelineDsl = self.tweakVca { it.copy(declickSeconds = seconds) }
}
