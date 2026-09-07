/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice

// -- sound() / s() ----------------------------------------------------------------------------------------------------

private val soundMutation = voiceSetter {
    if (it == null) return@voiceSetter

    // An inline ignitor DSL value bypasses the "name:index" string parse and is stored as-is.
    if (it is IgnitorDsl) {
        sound = SoundValue.Osc(it)
        value = null
        return@voiceSetter
    }

    val split = it.toString().split(":")

    sound = split.getOrNull(0)?.let { name -> SoundValue.Named(name) }
    // Preserve existing index if the string doesn't specify one.
    soundIndex = split.getOrNull(1)?.toIntOrNull() ?: soundIndex
    // Preserve existing gain if the string doesn't specify one.
    gain = split.getOrNull(2)?.toDoubleOrNull() ?: gain
    // clear the value
    value = null
}

private fun applySound(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // An inline ignitor DSL bypasses the mini-notation parse: stamp every source
    // event with SoundValue.Osc(dsl). Replaces any previously-set sound.
    val singleArg = args.singleOrNull()?.value
    if (singleArg is IgnitorDsl) {
        return source.reinterpretVoice { vd -> vd.copy(sound = SoundValue.Osc(singleArg), value = null) }
    }

    return if (args.isEmpty()) {
        // TODO: test this
        source.reinterpretVoice {
            it.soundMutation(it.value?.asString)
        }
    } else {
        source._applyControlFromParams(args, soundMutation) { src, ctrl ->
            src.sound = ctrl.sound ?: src.sound
            src.soundIndex = ctrl.soundIndex ?: src.soundIndex
            src
        }
    }
}

/**
 * Creates a pattern selecting a sound (instrument or sample bank) by name.
 *
 * Each event's value selects the instrument or sample bank used during playback.
 * The format `"name:index"` also sets the sample index, e.g. `"bd:2"` selects sample 2
 * from the `bd` bank.
 *
 * When [name] is omitted, reinterprets the current event values as sound names.
 *
 * ```KlangScript(Playable)
 * sound("bd sd hh")  // basic drum pattern
 * ```
 *
 * ```KlangScript(Playable)
 * sound("bd bd bd bd ").n("0 1 2 3")  // changes the sound variants
 * ```
 *
 * ```KlangScript(Playable)
 * seq("bd hh sd hh").sound()  // interprets the sequence values as sounds
 * ```
 *
 * @param name The sound/sample name pattern in mini-notation, e.g. `"bd sd hh"`.
 * @param-tool name SprudelSampleEditor, SprudelSampleSequenceEditor
 * @alias s
 * @category tonal
 * @tags sound, sample, instrument, s, pattern-creator
 */
@KlangScript.Function
fun SprudelPattern.sound(name: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySound(this, listOfNotNull(name).asSprudelDslArgs(callInfo))

/**
 * Modifies or reinterprets the sounds of a string pattern.
 *
 * @param name The sound/sample name pattern in mini-notation, e.g. `"bd sd hh"`.
 * @return A new pattern with the specified sounds applied.
 * @category tonal
 * @tags sound, sample, instrument, s, pattern-creator
 */
@KlangScript.Function
fun String.sound(name: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sound(name, callInfo)

/**
 * Creates a pattern of sounds.
 *
 * @param name The sound/sample name pattern in mini-notation, e.g. `"bd sd hh"`.
 * @param-tool name SprudelSampleEditor, SprudelSampleSequenceEditor
 */
@KlangScript.Function
fun sound(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    listOf(name).asSprudelDslArgs(callInfo).toPattern(soundMutation).sound(callInfo = callInfo)

/** Alias for [sound]. Creates a pattern selecting a sound (instrument or sample bank) by name.
 *
 * Each event's value selects the instrument or sample bank used during playback.
 * The format `"name:index"` also sets the sample index, e.g. `"bd:2"` selects sample 2
 * from the `bd` bank.
 *
 * When [name] is omitted, reinterprets the current event values as sound names.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh")  // basic drum pattern
 * ```
 *
 * ```KlangScript(Playable)
 * seq("bd hh sd hh").s()  // interprets the sequence values as sounds
 * ```
 *
 * @param name The sound/sample name pattern in mini-notation, e.g. `"bd sd hh"`.
 * @param-tool name SprudelSampleEditor, SprudelSampleSequenceEditor
 * @alias sound
 * @category tonal
 * @tags sound, sample, instrument, s, pattern-creator
 */
@KlangScript.Function
fun SprudelPattern.s(name: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.sound(name, callInfo)

/** Alias for [sound] on a string pattern. */
@KlangScript.Function
fun String.s(name: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).s(name, callInfo)

/**
 * Alias for [sound]. Creates a sound pattern.
 *
 * @param name The sound/sample name pattern in mini-notation, e.g. `"bd sd hh"`.
 * @param-tool name SprudelSampleEditor, SprudelSampleSequenceEditor
 */
@KlangScript.Function
fun s(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    sound(name, callInfo)

// -- bank() -----------------------------------------------------------------------------------------------------------

private fun applyBank(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { bankName ->
        copy(bank = bankName)
    }
}

/**
 * Sets the sample bank for each event, overriding which collection of samples is used.
 *
 * The bank determines where samples are loaded from, independently of the sound name.
 * Useful when you want to switch sample collections without changing sound identifiers.
 * When called with no argument, reinterprets the current event value as a bank name.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh").bank("RolandTR808")     // load all sounds from the TR-808 bank
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").bank("<TR808 TR909>")      // alternate sample banks each cycle
 * ```
 *
 * @param name The sample bank name, e.g. `"RolandTR808"`. Default: none (uses default bank).
 * @category tonal
 * @tags bank, sample bank, instrument
 */
@KlangScript.Function
fun SprudelPattern.bank(name: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyBank(this, listOfNotNull(name).asSprudelDslArgs(callInfo))

/** Sets the sample bank on a string pattern. */
@KlangScript.Function
fun String.bank(name: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bank(name, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sample bank for each event.
 * When called with no argument, reinterprets the current event value as a bank name.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(bank("RolandTR808"))   // mapper form
 * ```
 *
 * @category tonal
 * @tags bank, sample bank, instrument
 */
@KlangScript.Function
fun bank(name: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bank(name, callInfo) }

/** Chains a bank operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.bank(name: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bank(name, callInfo) }
