/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystValue
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
import io.peekandpoke.klang.sprudel.putKatalystParam

// -- katalyst() -------------------------------------------------------------------------------------------------------

/**
 * Stamps an orbit-chain reference onto every event of [source], **replacing** the chain those
 * events already carry.
 *
 * `x.katalyst(A).katalyst(B)` is `x.katalyst(B)`, exactly as `sound()` replaces the instrument and
 * `master()` the master chain (decided with the maintainer, 2026-09-18, retiring the append rule of
 * Katalyst step 1). A chain is ONE instrument, and the way to build on the familiar one is
 * `k.classic()` inside the builder, where the stage order is visible on one line.
 *
 * Why the rule changed: chaining two doors stacked their stages, and duplicated stages read the
 * SAME slot names, so `.katalyst(Katalyst(k => k.classic())).katalyst(Katalyst(k => k.classic()))`
 * ran reverb into reverb and two compressors in series off one `reverb(0.3)`.
 *
 * [value] is ONE [KatalystValue.Dsl] instance per door, allocated when the door is written and
 * handed to every event, so stamping a chain costs nothing per event.
 */
private fun applyKatalyst(source: SprudelPattern, value: KatalystValue.Dsl): SprudelPattern =
    source.reinterpretVoice { vd -> vd.copy(katalyst = value) }

/**
 * Creates a pattern that declares the **orbit chain**: one silent control event per cycle that
 * carries nothing but the chain reference.
 *
 * A Katalyst is the instrument an orbit plays through: the effects its summed voices run, in the
 * order written, before the playback's master bus. Put it on an orbit and everything stacked onto
 * that orbit shares it:
 *
 * ```KlangScript(Playable)
 * stack(
 *   s("bd*4"),
 *   note("c2 g2").s("supersaw"),
 *   katalyst(Katalyst(k => k.classic().eq(e => e.band(freq = 300, q = 1.0, db = 3.0)))),
 * ).orbit(1).reverb(wet = 0.25, size = 4)
 * ```
 *
 * The declaration above adds an EQ the familiar chain does not have, which is what a declaration is
 * FOR. `Katalyst(k => k.classic())` on its own declares exactly the chain an orbit already runs and
 * therefore changes nothing: it is the one spelling that is a no-op.
 *
 * The event is a **control event**: it never sounds. `.orbit(n)` on the carrier is what routes the
 * declaration to an orbit, which is why the carrier is an ordinary pattern and not a special
 * value.
 *
 * `k.classic()` is the chain an orbit has always run, declared as named slots, so the `reverb(...)`
 * on the stack drives the declared room, for the kick and the bass alike: that is how a bus door
 * and a declaration meet. A chain
 * that declares no `reverb` stage would have nowhere for that call to land, which is what "the
 * chain is the instrument" means.
 *
 * @param katalyst The orbit chain to declare.
 * @return A pattern emitting one control event per cycle.
 *
 * @scope orbit
 * @category effects
 * @tags katalyst, orbit, chain, bus, effects, motor
 */
@KlangScript.Function
fun katalyst(katalyst: KatalystDsl, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern {
    val value = KatalystValue.Dsl(katalyst)

    return AtomicPattern.pure.reinterpretVoice { vd ->
        vd.copy(katalyst = value, control = true)
    }
}

/**
 * Declares the orbit chain from this pattern's events onward.
 *
 * Unlike the top-level [katalyst] carrier, these events still sound: the chain declaration simply
 * rides them.
 *
 * **The door REPLACES**, like `sound()` and `master()`: the last `.katalyst(...)` on a pattern is
 * the chain its orbit runs, and writing two of them is not two halves of one chain. Build the whole
 * chain in one `Katalyst(k => ...)`, where the list order is the signal order and you can see it:
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").reverb(wet = 0.3, size = 3)
 *   .katalyst(Katalyst(k => k
 *     .classic()
 *     .eq(e => e.band(freq = 300, q = 0.8, db = 2.0))
 *   ))
 * ```
 *
 * That is the familiar orbit with a 300 Hz lift at the end of it: `k.classic()` brings the whole
 * familiar block (once, whatever else the builder says), so the `reverb(...)` on the pattern
 * still reaches the room, and the `eq` shapes the summed orbit after it.
 *
 * @param katalyst The orbit chain to declare.
 * @return A new pattern whose events carry the chain.
 *
 * @scope orbit
 * @category effects
 * @tags katalyst, orbit, chain, bus, effects, motor
 */
@KlangScript.Function
fun SprudelPattern.katalyst(katalyst: KatalystDsl, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern =
    applyKatalyst(this, KatalystValue.Dsl(katalyst))

/**
 * Parses this string as a pattern and declares its orbit chain from its events onward.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".katalyst(Katalyst(k => k.classic().gain(1.6))).s("supersaw")
 * ```
 *
 * @param katalyst The orbit chain to declare.
 * @return A new pattern whose events carry the chain.
 */
@KlangScript.Function
fun String.katalyst(katalyst: KatalystDsl, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).katalyst(katalyst, callInfo)

// NOTE: no bare `katalyst(dsl): PatternMapperFn` factory (the usual form (c) of a sprudel op).
// The top-level `katalyst(dsl)` name is taken by the *carrier* above, which is the headline surface
// (`stack(drums, katalyst(...).orbit(1))`), and a second overload with the same parameters could
// only differ by return type. Use `.katalyst(dsl)` on the pattern instead; the mapper-chaining
// form (d) below still exists for `.apply(gain(0.5).katalyst(...))`.

/**
 * Creates a chained [PatternMapperFn] that declares the orbit chain after the previous mapper.
 *
 * @param katalyst The orbit chain to declare.
 */
@KlangScript.Function
fun PatternMapperFn.katalyst(katalyst: KatalystDsl, callInfo: CallInfo? = null): PatternMapperFn {
    // Built ONCE, outside the lambda: a mapper is invoked per pattern it is applied to, and one
    // value per door is the budget, not one per application.
    val value = KatalystValue.Dsl(katalyst)

    return this.chain { p -> applyKatalyst(p, value) }
}

// -- katp() -----------------------------------------------------------------------------------------------------------

private fun applyKatp(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) return source

    val key = args[0].value?.toString() ?: return source
    val valueArgs = args.drop(1)
    val mutation = voiceSetter { putKatalystParam(key, it?.asDoubleOrNull()) }

    return source._liftOrReinterpretStringField(valueArgs, mutation)
}

/**
 * Writes one **orbit chain slot**, [per orbit](/manuals/lexikon/orbit-bus), by its `<stage>.<knob>`
 * name.
 *
 * Direct access to a declared chain's named knobs, the orbit twin of [oscparam]: `oscp` fills the
 * voice's own instrument, `katp` the chain its orbit runs. The vocabulary is what the chain
 * declares, which for a chain built from `k.classic()` is every classic knob: `body.material`,
 * `body.wet`, `body.floor`, `vowel.vowel`, `vowel.wet`, `vowel.floor`, `delay.wet`, `delay.time`,
 * `delay.feedback`, `delay.cap`, `reverb.wet`, `reverb.size`, `reverb.lowpass`, `phaser.rate`,
 * `phaser.wet`, `phaser.center`, `phaser.sweep`, `phaser.floor`, `compressor.threshold`,
 * `compressor.ratio`, `compressor.knee`, `compressor.attack`, `compressor.release`, `gain.gain`,
 * `duck.orbit`, `duck.depth`, `duck.attack`. An authored chain names its own with
 * `Katalyst.param("room", 5)`.
 *
 * `gain.gain` is the orbit's **group fader**, the last stage before the duck: one multiply of the
 * whole orbit mix, after the compressor, covering the dry signal and the delay and reverb returns
 * alike. 1 is unity and is what every orbit runs at; below 1 is quieter, above 1 louder, and a
 * move is ramped across one block so it never steps.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").orbit(1).katp("gain.gain", 0.8)   // this orbit, a little down
 * ```
 *
 * **It is a mix knob, not an articulation**, and for the ordinary reason every `katp` value is:
 * the fader follows the orbit's lease, so the first voice that sounds there owns it and a new
 * value lands once the previous owner has lapsed. Set it and leave it. For something that moves
 * per note, reach for `gain` (the level a voice leaves at) or `pregain` (how hard it is played in).
 *
 * **And patterning it through exactly 0 is not a clean mute.** The orbit's silence gate looks at
 * the mix AFTER the fader, so a stretch at zero can let the orbit deactivate while its voices are
 * still playing, and the way back up can step rather than glide. Mute with `gain` on the pattern
 * instead.
 *
 * **Which chain hears this map.** THIS IS THE ONE HOME of that rule; every other mention of it is
 * a pointer here.
 *
 * **Every chain does, for every stage it declares.** An orbit that declares nothing runs the
 * familiar chain and reads this map exactly as a declared one does, so `katp("gain.gain", 0.8)`
 * moves the fader of any orbit and needs no `.katalyst(...)` first. A bus DOOR and its slot are
 * the same knob written two ways: `reverb(wet = 0.4, size = 8)` writes `reverb.wet` and
 * `reverb.size`, and the stage reads them; `katp("reverb.size", 8)` moves that same size slot, and
 * whether a room is HEARD then depends on the wet as it always does (see the last paragraph).
 * Declaring a chain therefore changes what STAGES an orbit has, never where their knobs come from,
 * and `.katalyst(Katalyst(k => k.classic()))` on an orbit that declared nothing changes nothing at
 * all.
 *
 * The one thing to know: a slot only exists when a stage declares it. `k.classic()` declares every
 * knob listed above; a chain that declares an `eq` names the eq's own slots; and a name nothing
 * declares is simply not read, which is what a `.katalyst(Katalyst(k => k.eq(...)))` (an EQ and
 * nothing else) does to a `reverb(...)` on its voices.
 *
 * **`body.material` and `vowel.vowel` are numbers, and the number is an INDEX** into the material
 * and vowel catalogues, 0 = none (Katalyst step 5a-2, 2026-09-18). The `body(...)` and `vowel(...)`
 * doors convert a name for you, so `body(material = "wood", wet = 0.3)` reaches a declared chain
 * like every other knob. Writing an index by hand here is possible and rarely what you want: the
 * names are the readable door, and a raw index moves if the catalogue grows.
 *
 * Four more things it is NOT, and they all follow from the orbit being a bus and not a note:
 *
 *  - **No per-note snapshot.** The value is orbit state: the chain re-reads it when the owner's map
 *    changes, so a chord writes it once, not once per note.
 *  - **Only the OWNER is heard.** The first voice to sound owns the orbit; a second pattern on the
 *    same orbit writes into nothing. Give it its own orbit.
 *  - **Only a SLOT moves.** A knob the chain wrote as a number (`k.reverb(r => r.size(4))`) is
 *    fixed; write `Katalyst.param` where the chain should listen.
 *  - **A slot listens only when it IS the knob.** `Katalyst.param("room", 5).mul(2)` is an
 *    expression OVER a slot, and the bus folds it to one number when the chain is built, so
 *    `katp("room", x)` never reaches it. Put the arithmetic on the pattern side instead.
 *
 * A raw slot write is exactly one slot, unlike a compound door (`reverb(...)`, `body(...)`,
 * `compressor(...)` and the rest), which fills the companions of the stage it names: on the
 * familiar chain a `katp("reverb.wet", 0.3)` alone stays silent until `reverb.size` is written
 * too, because the engine gates the room on its size.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").reverb(wet = 0.4).katp("reverb.size", "<2 8>")   // small room, then a hall
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").katalyst(Katalyst(k => k.reverb(r => r.wet(0.5).size(Katalyst.param("room", 2)))))
 *   .katp("room", "<2 9>")
 * ```
 *
 * @param key The chain slot name.
 * @param value The slot value.
 * @return A new pattern with the orbit chain slot set.
 * @scope orbit
 * @category effects
 * @tags katalyst, orbit, chain, bus, param, slot
 */
@KlangScript.Function
fun SprudelPattern.katp(key: String, value: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyKatp(this, listOf(key, value).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and writes an orbit chain slot.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".katp("reverb.size", 6).reverb(wet = 0.4).s("supersaw").note()
 * ```
 *
 * @param key The chain slot name.
 * @param value The slot value.
 */
@KlangScript.Function
fun String.katp(key: String, value: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).katp(key, value, callInfo)

/**
 * Creates a [PatternMapperFn] that writes an orbit chain slot.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").reverb(wet = 0.4).apply(katp("reverb.size", 8))
 * ```
 *
 * @param key The chain slot name.
 * @param value The slot value.
 */
@KlangScript.Function
fun katp(key: String, value: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.katp(key, value, callInfo) }

/**
 * Chains an orbit-chain-slot write onto this [PatternMapperFn].
 *
 * @param key The chain slot name.
 * @param value The slot value.
 */
@KlangScript.Function
fun PatternMapperFn.katp(key: String, value: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.katp(key, value, callInfo) }
