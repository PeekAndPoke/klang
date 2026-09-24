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
 * event, so an orbit's chain can change over musical time. And like the master, `.katalyst(dsl)`
 * REPLACES the chain the pattern already carries (decided 2026-09-18): the chain is one instrument,
 * written in one place, and the way to build on the familiar one is `k.classic()` inside the
 * builder rather than a second door.
 *
 * The cylinder reads a declared chain and runs it (Katalyst step 2 onward), swapping it in with a
 * crossfade when the orbit is already sounding; an orbit that declares nothing keeps the historical
 * chain, [classic]. Every chain's SLOTS (its `Param` knobs) read the orbit's param state, the
 * born-with one included (step 5b-1), so a declaration changes which STAGES an orbit has and not
 * where a slot's value comes from; a knob declared as a fixed value stays that value.
 */
@WireFormat
data class KatalystDsl(val stages: List<KatalystStageDsl>) {
    companion object {
        /**
         * The historical chain, in the historical order: body, vowel, delay, reverb, phaser,
         * compressor, and the duck the cylinder runs after every orbit. This is what every
         * cylinder has always run, written down, plus the ONE stage that is new surface rather
         * than history: a [KatalystStageDsl.Gain] at unity, the group fader, last in the list
         * before the duck (signal-flow plan section 6, spot C, 2026-09-19). Unity is
         * bit-transparent in the stage, so the historical sound is untouched; what it buys is a
         * fader a pattern can reach with `katp("gain.gain", x)` on any orbit, declared chain or
         * not, since a gain stage is slot-driven on every chain.
         *
         * **What the fader covers.** The delay and the reverb are SEND buses whose returns are
         * mixed into the orbit's buffer by their own stages, which sit before this one, so the
         * fader scales dry and returns together, which is what a group fader should do. The duck
         * is the one thing after it: it runs outside the list, per orbit, in the cross-orbit pass
         * (see [KatalystStageDsl.Duck]), so a ducked orbit is ducked after its fader, and an orbit
         * that TRIGGERS a duck on another triggers it with its post-fader mix (lower A's fader and
         * A ducks B less hard, which is what a fader on a desk does). Both remain true when step
         * 5b makes the sends insert-style stages.
         *
         * Every knob is a **slot** ([IgnitorDsl.Param]) named `<stage>.<knob>`, which is the
         * vocabulary the sprudel doors write into once the cylinder reads the chain.
         *
         * **A slot's default IS its value when nobody writes it, so the classic chain has to encode
         * the engine's "off unless touched" state, slot for slot, not the shared constants.** For
         * the delay and the reverb that state is: wet, time and feedback 0.0 and cap [DELAY_CAP];
         * wet and size 0.0 (it was `VoiceFactory`'s untouched branch until the bus fields left the
         * wire in Katalyst step 5b-3).
         *
         * Note WHERE the engine's gate sits: `KatalystDelayEffect.configure` engages on
         * `time >= MIN_ACTIVE_DELAY_SECONDS` and `KatalystReverbEffect.configure` on
         * `size >= MIN_ACTIVE_SIZE`, neither of them on `wet`. So zeroing `wet` alone would NOT be
         * off, and `delay.time` / `delay.feedback` / `reverb.size` are zero here as well.
         * `delay.cap` keeps [DELAY_CAP] because the untouched voice did too: it is the soft-cap
         * shape of a line that is not running, not an amount.
         * `KatalystDefaultsSyncSpec` pins the delay, reverb, compressor and duck defaults; only the
         * phaser still has an untouched branch in `VoiceFactory`, and
         * `KatalystClassicMatchesUntouchedVoiceSpec` compares that one through the real factory.
         *
         * The families, then:
         *
         *  - zero, because the engine's untouched value is zero: `delay.wet`, `delay.time`,
         *    `delay.feedback`, `reverb.wet`, `reverb.size`, `phaser.wet`, `duck.depth`;
         *  - [SLOT_UNSET], the wire's non-finite "never set", where the off state is an absence
         *    rather than a number: all FIVE compressor slots, `duck.orbit`, `reverb.lowpass`,
         *    `body.material`, `vowel.vowel`, `body.wet` and `vowel.wet`;
         *  - the shared constant, for a knob that is inert while its gate is off and must be right
         *    the moment the gate opens: `delay.cap`, `body.floor`, `vowel.floor`, `phaser.rate`,
         *    `phaser.center`, `phaser.sweep`, `phaser.floor`, `duck.attack`;
         *  - unity, for the one knob whose untouched value is the IDENTITY of its stage rather
         *    than an off state or a tuned number: `gain.gain`. It is 1.0 written out here and in
         *    `MasterStageDsl.Gain` rather than read from `constants/`, for the reason that KDoc
         *    gives: an identity element is not a taste decision anybody could retune.
         *
         * The compressor is the one stage whose gate is not a single knob. `Voice.Compressor
         * .fromParams` reads "ANY of the five set = on, and each unset one takes its constant", so
         * four constants and an unset threshold would be a compressor that is ALREADY ON with a
         * threshold to be derived. All five are unset here, and a step-2 resolver that reproduces
         * `fromParams` verbatim (any finite = on, each non-finite = its constant) then behaves
         * exactly as the voice path does today.
         *
         * **`body.wet` and `vowel.wet` follow the compressor, not the sends** (round 1 of step
         * 5a-2's review, 2026-09-18). They are [SLOT_UNSET] rather than 0.0, because 0.0 is a SET
         * value: `KatalystSlots.bodyDef` substitutes [BODY_WET] for an unset mix exactly as
         * `fromParams` substitutes [COMPRESSOR_THRESHOLD_DB], and with a 0.0 default it never saw
         * "unset", so a material-only `body(material = "wood")` on a declared classic chain ran the bank at a
         * fully dry mix while the same call on an undeclared orbit played it at [BODY_WET]. Unset
         * is safe here and NOT on the sends, because these two stages are gated on their NAME:
         * `bodyDef` returns null whenever the bands are null, so an orbit that names no material
         * cannot be switched on by a wet, however large. The engine's substitution is what makes a
         * raw `katp("body.material", 1)` behave like the door, and it is the NaN rule for a raw
         * write, not a second fill.
         *
         * **What makes a door still feel familiar is the door, not this chain**: a sprudel door
         * fills the companions of the stage a call names (`/dsl-design` §4, the rule's one home),
         * so `.reverb(wet = 0.3)` and `.body(material = "wood")` sound as they always have on this chain.
         *
         * A raw `katp(...)` is the other half of that bargain: it writes exactly ONE slot, so
         * `katp("reverb.wet", 0.3)` on this chain stays silent until `reverb.size` is written as
         * well. That is the honest consequence of slots, and it is why the doors fill.
         *
         * `body.material` and `vowel.vowel` are slots too, and they carry a NUMBER: the INDEX of a
         * name in `BodyMaterials.names` respectively `VowelBands.names` (Katalyst step 5a-2,
         * 2026-09-18). Unset here, because an untouched orbit names no material and no vowel, and
         * unset is off. That is what lets `body(material = "wood", wet = 0.3)` on a pattern keep working when
         * its orbit declares a classic chain: the door writes the index, the same way it writes the
         * wet. And `body(material = "wood")` with no wet works because the door fills the wet and the floor
         * from the same two constants the engine would have substituted.
         */
        val classic: KatalystDsl = KatalystDsl(
            listOf(
                KatalystStageDsl.Body(
                    material = IgnitorDsl.Param(name = "body.material", default = SLOT_UNSET),
                    // Unset, not 0.0: the engine substitutes BODY_WET for an unset mix, and the
                    // stage is gated on the material, so seeding the amount switches nothing on.
                    wet = IgnitorDsl.Param(name = "body.wet", default = SLOT_UNSET),
                    floor = IgnitorDsl.Param(name = "body.floor", default = BODY_FLOOR),
                ),
                KatalystStageDsl.Vowel(
                    vowel = IgnitorDsl.Param(name = "vowel.vowel", default = SLOT_UNSET),
                    // Unset for the body's reason, see the classic KDoc.
                    wet = IgnitorDsl.Param(name = "vowel.wet", default = SLOT_UNSET),
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
                // The group fader, at unity: the LAST stage the orbit's mix runs through (the
                // duck below is declared last but runs outside the list, see [KatalystStageDsl.Duck]).
                // Unity is bit-transparent in `KatalystGainEffect`, which returns before it
                // multiplies, so this costs one virtual call per block and not one sample. It is
                // here so that a pattern can reach the fader with `katp("gain.gain", x)` without
                // declaring a chain at all; there is no sprudel door for it yet.
                KatalystStageDsl.Gain(
                    gain = IgnitorDsl.Param(name = "gain.gain", default = 1.0),
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
 * Stages are thin declarations: the paragraphs below are the CONTRACT a stage carries, and
 * `KatalystChainBuilder` is the one place that turns each one into the effect the engine runs.
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
 * Four stages are deliberately NOT audible bare, matching their sprudel doors. Three of them wait
 * for a NAME: [Body] and [Vowel] until `material` respectively `vowel` names an index, and [Duck]
 * until `orbit` names a source; all three carry [SLOT_UNSET] on that one knob. The duck waits for
 * a DEPTH as well, because its touched [DUCK_DEPTH] is 0, so naming the source alone is still
 * silent. [Phaser] is the fourth without a name knob at all: its `wet` carries its own touched
 * [PHASER_WET], which is 0.0 and therefore below the engine's `Phaser.MIN_ACTIVE_DEPTH` of 0.01.
 * Every other stage carries its touched constants throughout and a bare one is a working effect.
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
     * The material is a knob like every other one, and the number it carries is an INDEX into
     * `BodyMaterials.names` (Katalyst step 5a-2, 2026-09-18). That is what keeps a name off the
     * wire as a string and lets a pattern's `body(material = "wood")` reach a declared chain through
     * `katp("body.material", ...)`: both doors convert through `BodyMaterials.indexOf`, one place.
     * Index 0 is `none`, and so is an unset, negative or out-of-range index: the stage is off.
     *
     * @param material the material's index in `BodyMaterials.names`, the number
     *   `BodyMaterials.indexOf("wood")` gives. The default is [SLOT_UNSET]: the chain names no
     *   material and the stage is off. Write a name on either builder door
     *   (`k.body(material = "wood")`) and it is converted for you. Orbit twin: `body(material = "wood")`.
     * @param wet how much of the orbit runs through the body, 0 to 1. Orbit twin: `body(wet = ...)`.
     * @param floor minimum dry share kept in the mix, 0 to 1. Lower = the modes sit over less dry
     *   and the body is more audible. Orbit twin: `body(floor = ...)`.
     */
    @WireName("body")
    data class Body(
        val material: IgnitorDsl = IgnitorDsl.Constant(SLOT_UNSET),
        val wet: IgnitorDsl = IgnitorDsl.Constant(BODY_WET),
        val floor: IgnitorDsl = IgnitorDsl.Constant(BODY_FLOOR),
    ) : KatalystStageDsl

    /**
     * Formant bank: the vowel a sound sings, as a set of resonances over a low broadband floor.
     *
     * The vowel is an INDEX into `VowelBands.names`, the flat `"<register>:<vowel>"` catalogue, for
     * the same reason the body's material is (see [Body]). Index 0 is `none`, and so is an unset or
     * out-of-range index: the stage is off.
     *
     * @param vowel the vowel's index in `VowelBands.names`, the number `VowelBands.indexOf("a")`
     *   gives (a bare name is the soprano register). The default is [SLOT_UNSET]: the chain names
     *   no vowel and the stage is off. Write a name on either builder door
     *   (`k.vowel(vowel = "bass:a")`) and it is converted for you. Orbit twin: `vowel(vowel = "a")`.
     * @param wet how much of the orbit runs through the formant bank, 0 to 1. Orbit twin:
     *   `vowel(wet = ...)`.
     * @param floor minimum dry share kept between the formants, 0 to 1. Much lower than the body's:
     *   a vowel is a source strongly shaped by its formants. Orbit twin: `vowel(floor = ...)`.
     */
    @WireName("vowel")
    data class Vowel(
        val vowel: IgnitorDsl = IgnitorDsl.Constant(SLOT_UNSET),
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
     * The knobs follow sprudel (`threshold`, `knee`, `attack`), and so does the master limiter
     * since phase 3 step 3d (2026-09-24), which renamed its `thresholdDb` / `kneeDb`.
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
     * AND after classic's own group fader (the duck runs outside the list regardless, see [Duck]).
     * Write the stages out in order when the position matters.
     *
     * **What the position means.** The delay and the reverb are fed from the orbit mix at their own
     * position in the list (Katalyst step 5b-2), so an `eq` written BEFORE `reverb` shapes the dry
     * signal AND what the room hears: the room hears the cab. The same `eq` written AFTER it shapes
     * dry and tail together, after the room. Both are legitimate mixes and the list order is how
     * they are told apart.
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
     * **What a fader covers is list order**: the delay and the reverb are fed from the orbit's mix
     * at their own positions and add their RETURNS into it there (Katalyst step 5b-2), so a gain
     * stage written after them scales dry and returns alike, and one written BEFORE them scales the
     * dry and what they are fed, and so their returns too. The duck is
     * the one stage no list position can put before this one: it runs outside the list, in the
     * cross-orbit pass ([Duck]), so it always lands after the fader.
     *
     * That ordering has one consequence worth naming, and it is the RIGHT behaviour rather than a
     * corner: a duck on orbit B reads orbit A's mix as its trigger, and by then A's fader has
     * already run, so lowering A's fader also lowers how hard A ducks B. A group fader moves the
     * whole group, sends and sidechain sends included, which is what a fader on a desk does.
     *
     * [KatalystDsl.classic] carries one of these at unity, as its last stage before the duck, so
     * `katp("gain.gain", x)` reaches the group fader on any orbit (signal-flow plan section 6,
     * spot C). There is no sprudel door for it yet; a door is one line if the songs want it.
     *
     * **A chain may declare more than one, and they MULTIPLY**, in list order, like any two
     * stages: `k.classic().gain(0.8)` is classic's unity slot then 0.8. Two stages whose knobs are
     * `Param`s of the SAME name read the same key, so one `katp` write applies once per stage
     * (0.5 on two such stages is 0.25). Deliberate, and the reason a second fader that should move
     * on its own needs a slot name of its own.
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
