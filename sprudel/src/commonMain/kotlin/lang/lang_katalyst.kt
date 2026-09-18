/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystValue
import io.peekandpoke.klang.audio_bridge.plus
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
 * Stamps an orbit-chain reference onto every event of [source], **appending** to the chain those
 * events already carry.
 *
 * `x.katalyst(A).katalyst(B)` is `x.katalyst(A + B)`: the pattern text is the stage order, so two
 * doors compose rather than the second replacing the first. This is the one place a Katalyst
 * differs from a master, which replaces (there is one master per playback).
 *
 * An incoming [KatalystValue.Named] cannot be appended to, because the chain behind the name lives
 * in the backend registry and not here, so a named chain is REPLACED. Named chains are a later
 * concern.
 */
private fun applyKatalyst(source: SprudelPattern, memo: KatalystAppend): SprudelPattern =
    source.reinterpretVoice { vd -> vd.copy(katalyst = memo.onto(vd.katalyst)) }

/**
 * The append rule of one `.katalyst(dsl)` door, with the memo that keeps it free per event.
 *
 * An event arriving with no chain, or with a named one, gets [fresh]: one instance, allocated
 * once, handed to every event. An event arriving with an inline chain gets that chain plus this
 * door's, memoized on the incoming value **by reference**: a pattern hands the same
 * [KatalystValue.Dsl] instance to every event it emits (that is what [fresh] guarantees one door
 * down), so after the first event the composition costs a scan of a list with one entry in it
 * instead of a fresh stage list, a fresh data class and a structural hash.
 *
 * The memo belongs to the pattern node, not to the process: re-evaluating the script drops the
 * pattern tree and the memo with it. It never decides identity: a composition built somewhere
 * else is content-equal and `uniqueId()` gives it the same name, so a miss costs work, never
 * correctness.
 *
 * Not synchronized, and it does not need to be: the query path is single-threaded by construction
 * (the modifier chain mutates voice data in place, see [io.peekandpoke.klang.sprudel.SprudelVoiceData]).
 * [LIMIT] bounds it against a pathological source that hands out a new instance per event: past
 * that many distinct incoming chains the door simply stops memoizing and keeps composing.
 */
internal class KatalystAppend(private val katalyst: KatalystDsl) {
    private val fresh = KatalystValue.Dsl(katalyst)
    private val seen = ArrayList<KatalystValue.Dsl>(LIMIT)
    private val composed = ArrayList<KatalystValue.Dsl>(LIMIT)

    fun onto(incoming: KatalystValue?): KatalystValue = when (incoming) {
        null -> fresh
        is KatalystValue.Named -> fresh
        is KatalystValue.Dsl -> compose(incoming)
    }

    private fun compose(incoming: KatalystValue.Dsl): KatalystValue.Dsl {
        for (i in seen.indices) {
            if (seen[i] === incoming) {
                return composed[i]
            }
        }

        val result = KatalystValue.Dsl(incoming.katalyst + katalyst)

        if (seen.size < LIMIT) {
            seen.add(incoming)
            composed.add(result)
        }

        return result
    }

    companion object {
        /** How many distinct incoming chains one door memoizes. A stacked pattern has a handful. */
        internal const val LIMIT: Int = 8
    }
}

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
 *   s("bd*4").orbit(1),
 *   note("c2 g2").s("supersaw").orbit(1),
 *   katalyst(Katalyst(k => k.reverb(r => r.wet(0.2).size(4)))).orbit(1),
 * )
 * ```
 *
 * The event is a **control event**: it never sounds. `.orbit(n)` on the carrier is what routes the
 * declaration to an orbit, which is why the carrier is an ordinary pattern and not a special
 * value.
 *
 * The backend registers a declared chain from this step on and **runs** it from step 2 of the
 * Katalyst work; until then an orbit keeps running its fixed historical chain, so the example
 * above declares the reverb the orbit will run rather than one you can hear today.
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
    val memo = KatalystAppend(katalyst)

    return AtomicPattern.pure.reinterpretVoice { vd ->
        vd.copy(katalyst = memo.onto(vd.katalyst), control = true)
    }
}

/**
 * Appends to the orbit chain from this pattern's events onward.
 *
 * Unlike the top-level [katalyst] carrier, these events still sound: the chain declaration simply
 * rides them. Chaining the door twice appends twice, in written order, so the chain reads like the
 * signal path:
 *
 * **A chain referenced BY NAME does not compose yet.** Appending to an event that carries a named
 * chain replaces it, because the stages behind the name live in the backend registry and this door
 * cannot see them. Only inline chains (the ones `Katalyst(...)` builds) concatenate.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw")
 *   .katalyst(Katalyst(k => k.eq(e => e.band(freq = 300, q = 0.8, db = 2.0))))
 *   .katalyst(Katalyst(k => k.reverb(r => r.wet(0.15).size(3))))
 * ```
 *
 * That declares an EQ and then a reverb on this pattern's orbit; the engine runs declared chains
 * from step 2 of the Katalyst work.
 *
 * @param katalyst The orbit chain to append.
 * @return A new pattern whose events carry the composed chain.
 *
 * @scope orbit
 * @category effects
 * @tags katalyst, orbit, chain, bus, effects, motor
 */
@KlangScript.Function
fun SprudelPattern.katalyst(katalyst: KatalystDsl, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern =
    applyKatalyst(this, KatalystAppend(katalyst))

/**
 * Parses this string as a pattern and appends to its orbit chain from its events onward.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".katalyst(Katalyst(k => k.reverb(r => r.wet(0.2)))).s("supersaw")
 * ```
 *
 * @param katalyst The orbit chain to append.
 * @return A new pattern whose events carry the composed chain.
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
 * Creates a chained [PatternMapperFn] that appends to the orbit chain after the previous mapper.
 *
 * @param katalyst The orbit chain to append.
 */
@KlangScript.Function
fun PatternMapperFn.katalyst(katalyst: KatalystDsl, callInfo: CallInfo? = null): PatternMapperFn {
    // Built ONCE, outside the lambda: a mapper is invoked per pattern it is applied to, and a memo
    // created inside would be thrown away before its first hit. Sharing one memo across those
    // patterns is the point, not a leak: it is keyed on the incoming chain, not on the pattern.
    val memo = KatalystAppend(katalyst)

    return this.chain { p -> applyKatalyst(p, memo) }
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
 * declares, which for a chain built from `k.classic()` is every classic knob: `body.wet`,
 * `body.floor`, `vowel.wet`, `vowel.floor`, `delay.wet`, `delay.time`, `delay.feedback`,
 * `delay.cap`, `reverb.wet`, `reverb.size`, `reverb.lowpass`, `phaser.rate`, `phaser.wet`,
 * `phaser.center`, `phaser.sweep`, `phaser.floor`, `compressor.threshold`, `compressor.ratio`,
 * `compressor.knee`, `compressor.attack`, `compressor.release`, `duck.orbit`, `duck.depth`,
 * `duck.attack`. An authored chain names its own with `Katalyst.param("room", 5)`.
 *
 * **The orbit needs a DECLARED chain, so write one.** An orbit that declares nothing runs the chain
 * the engine has always run, whose knobs still come from the voice's own effect fields, and it
 * ignores this map; `.katalyst(Katalyst(k => k.classic()))` declares the same stages as slots and is
 * all it takes. (Until step 5b of the Katalyst work, which retires the voice fields and makes every
 * orbit read slots.)
 *
 * **What a declared chain owns, the pattern no longer sets.** The chain carries its own body
 * material and its own vowel, because those are NAMES and a slot carries a number, so
 * `body(material = "wood")` on a voice of a declared chain does not change the material; write it
 * in the chain (`k.body("wood")`). The numeric knobs of both stages, `wet` and `floor`, do reach it,
 * here and through the `body(...)` / `vowel(...)` doors. (Step 5b decides whether a name becomes a
 * slot of its own.)
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
 * A raw slot write is exactly one slot, unlike the `reverb(...)` / `delay(...)` doors, which fill
 * their companions: on a chain built from `k.classic()` a `katp("reverb.wet", 0.3)` alone stays
 * silent until `reverb.size` is written too, because the engine gates the room on its size.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").reverb(wet = 0.4).katp("reverb.size", "<2 8>")   // small room, then a hall
 *   .katalyst(Katalyst(k => k.classic()))
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
 *   .katalyst(Katalyst(k => k.classic()))
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
 *   .katalyst(Katalyst(k => k.classic()))
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
