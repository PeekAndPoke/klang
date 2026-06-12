package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.EngineDsl
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

    /** Per-voice cutoff-offset scale per unit analog (default 0.003 ≈ ±5 cents at analog=1). */
    @KlangScript.Method
    fun cutoffOffset(self: StageDsl.Filter, perAnalog: Double): StageDsl.Filter =
        self.copy(cutoffOffsetPerAnalog = perAnalog)

    /** SVF drive / saturation scale per unit analog (default 0.5; more = more OB-X "bite"). */
    @KlangScript.Method
    fun drive(self: StageDsl.Filter, perAnalog: Double): StageDsl.Filter =
        self.copy(drivePerAnalog = perAnalog)

    /** Filter cutoff drift magnitude relative to oscillator drift (default 5.0). */
    @KlangScript.Method
    fun drift(self: StageDsl.Filter, relToOsc: Double): StageDsl.Filter =
        self.copy(driftRelToOsc = relToOsc)
}

/**
 * Convenience tweaks on a whole [EngineDsl] — forward to its (single) VCA stage so a preset can
 * be nudged in one call: `Engine.modern().expK(2.5).declick(0.0008)`.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(EngineDsl::class)
object KlangScriptEngineExtensions {

    private fun EngineDsl.tweakVca(f: (StageDsl.Vca) -> StageDsl.Vca): EngineDsl =
        copy(stages = stages.map { if (it is StageDsl.Vca) f(it) else it })

    /** Sugar for the engine's VCA `expK`. */
    @KlangScript.Method
    fun expK(self: EngineDsl, k: Double): EngineDsl = self.tweakVca { it.copy(expK = k) }

    /** Sugar for the engine's VCA `declick` seconds. */
    @KlangScript.Method
    fun declick(self: EngineDsl, seconds: Double): EngineDsl = self.tweakVca { it.copy(declickSeconds = seconds) }
}
