/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.audio_bridge.constants.BODY_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.BODY_WET
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RATIO
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_THRESHOLD_DB
import io.peekandpoke.klang.audio_bridge.constants.DELAY_CAP
import io.peekandpoke.klang.audio_bridge.constants.DELAY_FEEDBACK
import io.peekandpoke.klang.audio_bridge.constants.DELAY_TIME_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DELAY_WET
import io.peekandpoke.klang.audio_bridge.constants.DUCK_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DUCK_DEPTH
import io.peekandpoke.klang.audio_bridge.constants.PHASER_CENTER_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.PHASER_RATE_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_SWEEP_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_WET
import io.peekandpoke.klang.audio_bridge.constants.REVERB_SIZE
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_WET
import io.peekandpoke.klang.audio_bridge.constants.REVERB_WET

/**
 * Declarative, data-driven **orbit chain**: the effects one orbit (one cylinder) runs on its own
 * summed voices, before the playback's master bus.
 *
 * A Katalyst is an ordered list of [KatalystStageDsl] stages. The list order IS the signal order,
 * so where a stage sits in the list is what it means: an `eq` before `reverb` shapes the dry mix,
 * the same `eq` after it shapes dry and tail together.
 *
 * Mirrors [MasterDsl] (per playback), [PipelineDsl] (per voice) and [IgnitorDsl] (per voice
 * exciter): a `@WireFormat` root, registered by name, referenced from `VoiceData.katalyst`. One
 * concept, one word, four hosts: exciter / voice pipeline / orbit chain / master bus.
 *
 * Like the master, a Katalyst rides *events*: `katalyst(...)` stamps the reference onto a pattern
 * event, so an orbit's chain can change over musical time. Unlike the master, `.katalyst(...)`
 * APPENDS to the chain the pattern already carries (see `KatalystDsl.plus`), because the pattern
 * text is the stage order.
 *
 * Step 1 of the Katalyst work (2026-09-17) is the wire model and the plumbing only: the chain
 * travels and is registered, and the cylinder does not read it yet. Nothing sounds different until
 * step 2.
 */
@WireFormat
data class KatalystDsl(val stages: List<KatalystStageDsl>) {
    companion object {
        /**
         * The historical chain, in the historical order: body, vowel, delay, reverb, phaser,
         * compressor, and the duck the cylinder runs after every orbit. This is what every
         * cylinder has always run, written down.
         *
         * Every knob is a **slot** ([IgnitorDsl.Param]) named `<stage>.<knob>`, which is the
         * vocabulary the sprudel doors write into once the cylinder reads the chain.
         *
         * **A slot's default IS its value when nobody writes it, so the classic chain has to encode
         * the engine's "off unless touched" state, slot for slot, not the shared constants.** The
         * reference is `VoiceFactory`'s untouched branch, which is what every song that never wrote
         * a bus door gets today:
         *
         * ```
         * Voice.Delay(amount = 0.0, time = 0.0, feedback = 0.0, cap = DELAY_CAP)
         * Voice.Reverb(amount = 0.0, size = 0.0)
         * ```
         *
         * Note WHERE the engine's gate sits: `KatalystDelayEffect.configure` engages on
         * `time >= MIN_ACTIVE_DELAY_SECONDS` and `KatalystReverbEffect.configure` on
         * `size >= MIN_ACTIVE_SIZE`, neither of them on `wet`. So zeroing `wet` alone would NOT be
         * off, and `delay.time` / `delay.feedback` / `reverb.size` are zero here as well.
         * `delay.cap` keeps [DELAY_CAP] because the untouched voice does too: it is the soft-cap
         * shape of a line that is not running, not an amount.
         * `KatalystClassicMatchesUntouchedVoiceSpec` compares the two, through the real factory.
         *
         * The families, then:
         *
         *  - zero, because the engine's untouched value is zero: `body.wet`, `vowel.wet`,
         *    `delay.wet`, `delay.time`, `delay.feedback`, `reverb.wet`, `reverb.size`,
         *    `phaser.wet`, `duck.depth`;
         *  - [SLOT_UNSET], the wire's non-finite "never set", where the off state is an absence
         *    rather than a number: all FIVE compressor slots, `duck.orbit`, `reverb.lowpass`;
         *  - the shared constant, for a knob that is inert while its gate is off and must be right
         *    the moment the gate opens: `delay.cap`, `body.floor`, `vowel.floor`, `phaser.rate`,
         *    `phaser.center`, `phaser.sweep`, `phaser.floor`, `duck.attack`.
         *
         * The compressor is the one stage whose gate is not a single knob. `Voice.Compressor
         * .fromParams` reads "ANY of the five set = on, and each unset one takes its constant", so
         * four constants and an unset threshold would be a compressor that is ALREADY ON with a
         * threshold to be derived. All five are unset here, and a step-2 resolver that reproduces
         * `fromParams` verbatim (any finite = on, each non-finite = its constant) then behaves
         * exactly as the voice path does today.
         *
         * **What makes a door still feel familiar is the door, not this chain**, and the two doors
         * do it differently:
         *
         *  - a sprudel `reverb(...)` / `delay(...)` call fills every companion slot it leaves unset
         *    at write time (the rule of 2026-09-16, `constants/SendEffectDefaults.kt`), so
         *    `.reverb(wet = 0.3)` writes `size` too and sounds like it always has. A raw
         *    `katp("reverb.wet", 0.3)` writes exactly ONE slot, so on this chain it stays silent
         *    until `reverb.size` is written as well.
         *  - `compressor(...)` does NOT fill, and deliberately: a tail-only call such as
         *    `compressor(ratio = 8)` leaves `threshold` untouched (see the door's own `if`), and
         *    the voice path still compresses, at [COMPRESSOR_THRESHOLD_DB], because an unset knob
         *    takes its constant. So a raw `katp("compressor.ratio", 8)` on this chain switches the
         *    stage ON with the constant threshold, which is exactly what the voice does today. The
         *    asymmetry between the two doors is real and pre-dates this chain; it is recorded here
         *    rather than papered over.
         *
         * That is the honest consequence of slots, and it is why the doors do the filling they do.
         *
         * `material` and `vowel` are names, not numbers, so they have no slot and stay unset here.
         */
        val classic: KatalystDsl = KatalystDsl(
            listOf(
                KatalystStageDsl.Body(
                    wet = IgnitorDsl.Param(name = "body.wet", default = 0.0),
                    floor = IgnitorDsl.Param(name = "body.floor", default = BODY_FLOOR),
                ),
                KatalystStageDsl.Vowel(
                    wet = IgnitorDsl.Param(name = "vowel.wet", default = 0.0),
                    floor = IgnitorDsl.Param(name = "vowel.floor", default = VOWEL_FLOOR),
                ),
                KatalystStageDsl.Delay(
                    wet = IgnitorDsl.Param(name = "delay.wet", default = 0.0),
                    time = IgnitorDsl.Param(name = "delay.time", default = 0.0),
                    feedback = IgnitorDsl.Param(name = "delay.feedback", default = 0.0),
                    cap = IgnitorDsl.Param(name = "delay.cap", default = DELAY_CAP),
                ),
                KatalystStageDsl.Reverb(
                    wet = IgnitorDsl.Param(name = "reverb.wet", default = 0.0),
                    size = IgnitorDsl.Param(name = "reverb.size", default = 0.0),
                    lowpass = IgnitorDsl.Param(name = "reverb.lowpass", default = SLOT_UNSET),
                ),
                KatalystStageDsl.Phaser(
                    rate = IgnitorDsl.Param(name = "phaser.rate", default = PHASER_RATE_HZ),
                    wet = IgnitorDsl.Param(name = "phaser.wet", default = 0.0),
                    center = IgnitorDsl.Param(name = "phaser.center", default = PHASER_CENTER_HZ),
                    sweep = IgnitorDsl.Param(name = "phaser.sweep", default = PHASER_SWEEP_HZ),
                    floor = IgnitorDsl.Param(name = "phaser.floor", default = PHASER_FLOOR),
                ),
                // All five unset, not four constants and an unset threshold: the engine's gate is
                // "any of the five set", so four finite constants would read as a compressor that
                // is already on. See the classic KDoc.
                KatalystStageDsl.Compressor(
                    threshold = IgnitorDsl.Param(name = "compressor.threshold", default = SLOT_UNSET),
                    ratio = IgnitorDsl.Param(name = "compressor.ratio", default = SLOT_UNSET),
                    knee = IgnitorDsl.Param(name = "compressor.knee", default = SLOT_UNSET),
                    attack = IgnitorDsl.Param(name = "compressor.attack", default = SLOT_UNSET),
                    release = IgnitorDsl.Param(name = "compressor.release", default = SLOT_UNSET),
                ),
                KatalystStageDsl.Duck(
                    orbit = IgnitorDsl.Param(name = "duck.orbit", default = SLOT_UNSET),
                    depth = IgnitorDsl.Param(name = "duck.depth", default = 0.0),
                    attack = IgnitorDsl.Param(name = "duck.attack", default = DUCK_ATTACK_SECONDS),
                ),
            )
        )

        /** Builds an orbit chain from an ordered list of stages. Kotlin, engine-level; the script door is `Katalyst(k => ...)`. */
        fun of(vararg stages: KatalystStageDsl): KatalystDsl = KatalystDsl(stages.toList())
    }
}

/**
 * One stage in a [KatalystDsl] chain, applied to the orbit in list order.
 *
 * Stages are thin declarations. Nothing reads them yet: step 1 of the Katalyst work registers a
 * chain and the cylinder ignores it, so the paragraphs below are the CONTRACT a stage carries, and
 * what step 2 will build from it, not a description of code that runs today.
 *
 * **The contract: a stage is a shell over the shared DSP classes in `audio_be/`** (the same
 * `Compressor` / `Reverb` / `DelayLine` the master stages use). No stage may introduce its own DSP
 * implementation, which is what keeps one effect one sound on every bus.
 *
 * **Every knob is an [IgnitorDsl], restricted to block-constant nodes** ([IgnitorDsl.Constant] and
 * [IgnitorDsl.Param]): one value vocabulary for the chain, the same one [IgnitorDsl.EqSection]
 * already uses. A knob that somehow carries a signal-rate node is to be COERCED, never rejected:
 * the step-2 resolver reads it once per block and falls back to the knob's default when it cannot.
 * The Motor stays raw, so a knob is never clamped beyond what the underlying DSP already does.
 *
 * **Every knob keeps the name and the scale of its sprudel door**, so a number means the same
 * thing whether it is written on a pattern or in a chain. A BARE stage means "I reached for this
 * effect", so its knob defaults are the shared touched constants from
 * `constants/SendEffectDefaults.kt` and `constants/BusEffectDefaults.kt`, never a literal here.
 *
 * Two stages are deliberately NOT audible bare, matching their sprudel doors: [Phaser] stays off
 * until `wet` is written (the engine gates on `Phaser.MIN_ACTIVE_DEPTH`, and `PHASER_WET` is 0.0),
 * and [Duck] stays off until `orbit` names a source (its default is [SLOT_UNSET]). Everywhere else
 * a bare stage is a working effect.
 *
 * [KatalystDsl.classic] is the other case entirely and reads differently: nobody reached for
 * anything, so every slot carries the engine's untouched value.
 *
 * All values are tune-by-ear starting points; the engine is intentionally raw, so nothing here is
 * clamped for "safety" beyond what the underlying DSP already does.
 */
@WireFormat
sealed interface KatalystStageDsl {

    /**
     * Resonant body: a bank of narrow modes over a broadband floor, the box a sound sits in.
     *
     * @param material material name (`"wood"`, `"glass"`, `"tube"`, ...); null = the chain does not
     *   name one and the orbit keeps whatever it plays. Orbit twin: `body("wood")`.
     * @param wet how much of the orbit runs through the body, 0 to 1. Orbit twin: `body(wet = ...)`.
     * @param floor minimum dry share kept in the mix, 0 to 1. Lower = the modes sit over less dry
     *   and the body is more audible. Orbit twin: `body(floor = ...)`.
     */
    @WireName("body")
    data class Body(
        val material: String? = null,
        val wet: IgnitorDsl = IgnitorDsl.Constant(BODY_WET),
        val floor: IgnitorDsl = IgnitorDsl.Constant(BODY_FLOOR),
    ) : KatalystStageDsl

    /**
     * Formant bank: the vowel a sound sings, as a set of resonances over a low broadband floor.
     *
     * @param vowel vowel name, optionally `voice:vowel` (`"a"`, `"soprano:o"`); null = the chain
     *   does not name one. Orbit twin: `vowel("a")`.
     * @param wet how much of the orbit runs through the formant bank, 0 to 1. Orbit twin:
     *   `vowel(wet = ...)`.
     * @param floor minimum dry share kept between the formants, 0 to 1. Much lower than the body's:
     *   a vowel is a source strongly shaped by its formants. Orbit twin: `vowel(floor = ...)`.
     */
    @WireName("vowel")
    data class Vowel(
        val vowel: String? = null,
        val wet: IgnitorDsl = IgnitorDsl.Constant(VOWEL_WET),
        val floor: IgnitorDsl = IgnitorDsl.Constant(VOWEL_FLOOR),
    ) : KatalystStageDsl

    /**
     * Orbit delay, the shared `DelayLine` (audio_be). Same names, scales and defaults as the
     * master's `MasterStageDsl.Delay` and the sprudel `delay(...)` door.
     *
     * @param wet how much of the orbit goes into the delay (0.0 = off). Orbit twin: `delay(wet = x)`.
     * @param time delay time in seconds. Orbit twin: `delay(time = ...)`.
     * @param feedback feedback amount; at or above 1.0 it recirculates without loss and
     *   self-oscillates, allowed (raw engine), with [cap] deciding how loud. Orbit twin:
     *   `delay(feedback = ...)`.
     * @param cap level the recirculating signal saturates toward. Orbit twin: `delay(cap = ...)`.
     */
    @WireName("delay")
    data class Delay(
        val wet: IgnitorDsl = IgnitorDsl.Constant(DELAY_WET),
        val time: IgnitorDsl = IgnitorDsl.Constant(DELAY_TIME_SECONDS),
        val feedback: IgnitorDsl = IgnitorDsl.Constant(DELAY_FEEDBACK),
        val cap: IgnitorDsl = IgnitorDsl.Constant(DELAY_CAP),
    ) : KatalystStageDsl

    /**
     * Orbit reverb, the shared Freeverb `Reverb` (audio_be). Same names, scales and defaults as the
     * master's `MasterStageDsl.Reverb` and the sprudel `reverb(...)` door.
     *
     * @param wet how much of the orbit goes into the reverb (0.0 = off). Orbit twin: `reverb(wet = x)`.
     * @param size tail length on the authored 0 to 10 scale (the backend divides by 10, see
     *   `Reverb.normalizeSize`). 3 is about a 1 s tail, 5 about 1.4 s, 10 about 12.5 s; the
     *   shortest reachable is about 0.7 s, and above 10 is bounded at 10. Orbit twin:
     *   `reverb(size = ...)`.
     * @param lowpass high-frequency damping of the tail as a cutoff in Hz; lower is darker. Null,
     *   or a non-finite value such as [SLOT_UNSET], = the engine's fixed default damping. That
     *   default is a sample-rate relation rather than a number, which is why this knob alone has no
     *   wire constant: [KatalystDsl.classic] carries it as a slot whose default is "unset".
     *   Orbit twin: `reverb(lowpass = ...)`.
     */
    @WireName("reverb")
    data class Reverb(
        val wet: IgnitorDsl = IgnitorDsl.Constant(REVERB_WET),
        val size: IgnitorDsl = IgnitorDsl.Constant(REVERB_SIZE),
        val lowpass: IgnitorDsl? = null,
    ) : KatalystStageDsl

    /**
     * Orbit phaser: a sweeping all-pass notch comb, the drifting one the chain owns (a mic's comb
     * deliberately stands still).
     *
     * @param rate sweep rate in Hz. 0 = the sweep stands still. Orbit twin: `phaser(rate = ...)`.
     * @param wet wet amount, 0 to 1 (the wire spelling on the voice is `phaserDepth`). Orbit twin:
     *   `phaser(wet = ...)`.
     * @param center center frequency of the sweep in Hz. Orbit twin: `phaser(center = ...)`.
     * @param sweep width of the sweep around [center], in Hz. Orbit twin: `phaser(sweep = ...)`.
     * @param floor minimum dry coefficient of the wet/dry law; 1.0 = purely additive. Orbit twin:
     *   `phaser(floor = ...)`.
     */
    @WireName("phaser")
    data class Phaser(
        val rate: IgnitorDsl = IgnitorDsl.Constant(PHASER_RATE_HZ),
        val wet: IgnitorDsl = IgnitorDsl.Constant(PHASER_WET),
        val center: IgnitorDsl = IgnitorDsl.Constant(PHASER_CENTER_HZ),
        val sweep: IgnitorDsl = IgnitorDsl.Constant(PHASER_SWEEP_HZ),
        val floor: IgnitorDsl = IgnitorDsl.Constant(PHASER_FLOOR),
    ) : KatalystStageDsl

    /**
     * Orbit compressor: the group dynamics, after the EQ so the detector sees the corrected
     * spectrum and a low cut turns into headroom.
     *
     * The knobs follow sprudel (`threshold`, `knee`, `attack`), not the master limiter's
     * `thresholdDb` / `kneeDb`; the master limiter is the recorded odd one out.
     *
     * @param threshold ceiling in dBFS where gain reduction starts. Orbit twin:
     *   `compressor(threshold = ...)`.
     * @param ratio compression ratio above the threshold (4.0 = 4:1). Orbit twin:
     *   `compressor(ratio = ...)`.
     * @param knee soft-knee width in dB; a hard corner injects harmonics on every crossing. Orbit
     *   twin: `compressor(knee = ...)`.
     * @param attack how fast the gain closes, in seconds. Orbit twin: `compressor(attack = ...)`.
     * @param release how fast the gain opens again, in seconds. Orbit twin:
     *   `compressor(release = ...)`.
     */
    @WireName("compressor")
    data class Compressor(
        val threshold: IgnitorDsl = IgnitorDsl.Constant(COMPRESSOR_THRESHOLD_DB),
        val ratio: IgnitorDsl = IgnitorDsl.Constant(COMPRESSOR_RATIO),
        val knee: IgnitorDsl = IgnitorDsl.Constant(COMPRESSOR_KNEE_DB),
        val attack: IgnitorDsl = IgnitorDsl.Constant(COMPRESSOR_ATTACK_SECONDS),
        val release: IgnitorDsl = IgnitorDsl.Constant(COMPRESSOR_RELEASE_SECONDS),
    ) : KatalystStageDsl

    /**
     * Sidechain duck: this orbit is pulled down whenever the orbit it listens to sounds.
     *
     * **Declared in the list, but run outside it**, as it is today: ducking needs every orbit
     * processed first, so `Cylinders.processAndMix` runs it after the chains. Its position in the
     * list is therefore ignored, deliberately.
     *
     * When a chain declares more than one duck stage the LAST one wins, because the cylinder runs
     * exactly one ducking effect; the earlier ones are silently ignored rather than summed
     * (decided with the maintainer, 2026-09-17, recorded in `docs/tasks/katalyst-dsl.md`).
     *
     * @param orbit the orbit to listen to, as a number the runtime coerces to an Int. The default
     *   is [SLOT_UNSET]: no source named, no ducking. A consumer tests `isFinite()` and never
     *   compares, so a finite negative is an orbit REQUEST like any other, not an off switch.
     *   Orbit twin: `duck(orbit = ...)`.
     * @param depth how far this orbit is pulled down, 0 to 1. 0 = no ducking. Orbit twin:
     *   `duck(depth = ...)`.
     * @param attack how fast the duck closes, in seconds. Orbit twin: `duck(attack = ...)`.
     */
    @WireName("duck")
    data class Duck(
        val orbit: IgnitorDsl = IgnitorDsl.Constant(SLOT_UNSET),
        val depth: IgnitorDsl = IgnitorDsl.Constant(DUCK_DEPTH),
        val attack: IgnitorDsl = IgnitorDsl.Constant(DUCK_ATTACK_SECONDS),
    ) : KatalystStageDsl

    /**
     * The mix equalizer: one stage whose inside is a list of sections, applied left to right.
     *
     * Reuses [IgnitorDsl.EqSection] verbatim, so a section means the same thing on a voice and on
     * an orbit.
     *
     * There is no default position: a stage sits where it is written, and nothing reorders it. The
     * master puts its EQ after the reverb and before the dynamics, so the room is shaped with the
     * dry and the detector sees the result; write it there if you want the same. Note that
     * `k.classic().eq(...)` does NOT land there: it appends, so the EQ ends up after the compressor
     * (the duck runs outside the list regardless, see [Duck]). Write the stages out in order when
     * the position matters.
     *
     * @param sections the sections in written order; an empty list is a transparent stage.
     */
    @WireName("eq")
    data class Eq(
        val sections: List<IgnitorDsl.EqSection> = emptyList(),
    ) : KatalystStageDsl

    /**
     * Make-up gain on the orbit: the group fader, after the inserts.
     *
     * The structural fix for mixing an orbit deliberately low (to keep its compressor out of plop
     * territory) and bringing the level back up at the end of its chain.
     *
     * @param gain linear gain factor (1.0 = unity, 2.0 is about +6 dB). Unity is the identity
     *   element of the stage, not a tuned value, which is why it is written here rather than read
     *   from `constants/`; the master's `MasterStageDsl.Gain` does the same.
     */
    @WireName("gain")
    data class Gain(
        val gain: IgnitorDsl = IgnitorDsl.Constant(1.0),
    ) : KatalystStageDsl
}
