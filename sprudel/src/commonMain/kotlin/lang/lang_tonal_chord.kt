/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
import io.peekandpoke.klang.sprudel.pattern.StackPattern
import io.peekandpoke.klang.sprudel.sampleAt
import io.peekandpoke.klang.tones.Tones
import io.peekandpoke.klang.tones.chord.Chord
import io.peekandpoke.klang.tones.note.Note

// -- chord() ----------------------------------------------------------------------------------------------------------

private val chordMutation = voiceSetter { chordName ->
    val name = chordName?.toString() ?: return@voiceSetter

    // Set the chord property
    // We also set the note to the chord root/tonic to ensure it plays something meaningful if voicing is not used
    // and to provide a base for rootNotes()
    val chordObj = Chord.get(name)
    val root = if (!chordObj.empty) chordObj.tonic ?: chordObj.root else null

    chord = name
    if (root != null) {
        note = root
        freqHz = Tones.noteToFreq(root)
    }
}

// REMOVED expandChordToVoiceData and applyChord with BindPattern
// Instead, chord() is now a simple property setter pattern

private fun applyChordCreate(args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return args.toPattern(chordMutation)
}

private fun applyChordExtension(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, chordMutation) { src, ctrl ->
        src.chordMutation(ctrl.chord ?: ctrl.value?.asString)
    }
}

/**
 * Sets the chord name for each event, establishing harmonic context for [voicing].
 *
 * Chord names follow the format `"root quality"` (e.g. `"C"`, `"Am"`, `"Gmaj7"`).
 * The chord root is also set as the event's note. Use [voicing] to expand into voiced
 * notes, or [rootNotes] to extract just the bass note.
 *
 * ```KlangScript(Playable)
 * chord("<C Am F G>").voicing()         // voiced I-vi-IV-V
 * ```
 *
 * ```KlangScript(Playable)
 * chord("<Cmaj7 Am7>").voicing()        // jazzy chord alternation per cycle
 * ```
 *
 * @param name Chord type name, e.g. "major", "minor", "7", "m7", "dim", "aug". 112 types available. Default: none.
 *
 * @category tonal
 * @tags chord, harmony, chords, voicing, progression
 */
@KlangScript.Function
fun chord(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyChordCreate(listOf(name).asSprudelDslArgs(callInfo))

/** Sets the chord name on this pattern for use with [voicing] and [rootNotes]. */
@KlangScript.Function
fun SprudelPattern.chord(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyChordExtension(this, listOf(name).asSprudelDslArgs(callInfo))

/** Sets the chord name on a string pattern. */
@KlangScript.Function
fun String.chord(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chord(name, callInfo)

// -- rootNotes() ------------------------------------------------------------------------------------------------------

/**
 * Extracts the root note from a chord pattern.
 * If octave is specified, forces the root to that octave.
 */
fun SprudelVoiceData.extractRootNote(octave: Int? = null): SprudelVoiceData {
    // With the new chord() implementation, 'note' is already set to root.
    // But we might want to force octave or handle cases where note was changed.

    val chordName = chord ?: return this

    try {
        val chordObj = Chord.get(chordName)
        if (chordObj.empty) return this

        val tonic = chordObj.tonic
        if (tonic.isNullOrEmpty()) return this

        val rootNote = if (octave != null) {
            // Force specific octave
            val rootPc = Note.get(tonic).pc
            rootPc + octave.toString()
        } else {
            // Use tonic as-is, or add default octave if missing
            val rootNoteObj = Note.get(tonic)
            if (rootNoteObj.oct != null) {
                tonic
            } else {
                rootNoteObj.pc + "4" // Default to octave 4
            }
        }

        return copy(
            note = rootNote,
            freqHz = Tones.noteToFreq(rootNote),
        )
    } catch (_: Exception) {
        return this
    }
}

private fun applyRootNotes(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val octave = args.firstOrNull()?.value?.asIntOrNull()

    // No need for distinct() anymore since chord() doesn't expand
    return source.reinterpretVoice { voiceData ->
        voiceData.extractRootNote(octave)
    }
}

/**
 * Extracts the root (bass) note from a chord pattern.
 *
 * Given a pattern with chord names set via [chord], `rootNotes` produces events carrying
 * only the chord root note. An optional integer argument forces the root to a specific octave.
 *
 * ```KlangScript(Playable)
 * chord("C:major Am:minor F:major").rootNotes()   // root notes: C, A, F
 * ```
 *
 * ```KlangScript(Playable)
 * chord("Cmaj7 Am7 Fmaj7").rootNotes(3)           // roots forced to octave 3
 * ```
 *
 * @param octave Root note extraction mode or offset. Default: extracts the root note from the current chord.
 *
 * @category tonal
 * @tags rootNotes, chord root, bass, harmony
 */
@KlangScript.Function
fun SprudelPattern.rootNotes(octave: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyRootNotes(this, listOfNotNull(octave).asSprudelDslArgs(callInfo))

/** Extracts root notes from chord events in a string pattern, forcing to the given octave. */
@KlangScript.Function
fun String.rootNotes(octave: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).rootNotes(octave, callInfo)

/** Returns a [PatternMapperFn] that extracts root notes from chord events, forcing to the given octave. */
@KlangScript.Function
fun rootNotes(octave: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.rootNotes(octave, callInfo) }

/** Chains a rootNotes step (with forced octave) onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.rootNotes(octave: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.rootNotes(octave, callInfo) }

// -- voicing() --------------------------------------------------------------------------------------------------------

/**
 * Helper to get voiced notes for a chord, picking the [rank]-th candidate.
 *
 * Delegates to [io.peekandpoke.klang.tones.voicing.Voicing.getRanked] which guarantees ≥1
 * voicing for any chord string [io.peekandpoke.klang.tones.chord.Chord.get] can parse, by
 * falling back to chord-from-intervals at octave 4 when the dictionary search is empty.
 *
 * @param rank 0 = best, 1 = second-best, etc. Clamped into [0, ranked.lastIndex].
 */
internal fun getVoicedNotes(
    chordName: String,
    range: List<String>,
    lastVoicing: List<String>,
    rank: Int = 0,
): List<String> = try {
    val ranked = io.peekandpoke.klang.tones.voicing.Voicing.getRanked(
        chord = chordName,
        range = range,
        lastVoicing = lastVoicing,
    )
    if (ranked.isEmpty()) emptyList() else ranked[rank.coerceIn(0, ranked.lastIndex)]
} catch (_: Exception) {
    emptyList()
}

/**
 * Applies voice leading to chord patterns.
 *
 * Uses the Tones library's Voicing module for smooth transitions between chords. The optional
 * [lowPattern] / [highPattern] / [rankPattern] are sampled per source event to vary the voicing
 * range and pick the Nth-best candidate.
 *
 * Inlines BindPattern's intersection logic so we can sample the control patterns with the live
 * [SprudelPattern.QueryContext] at each source event's onset.
 */
private fun applyVoicing(
    source: SprudelPattern,
    lowPattern: SprudelPattern? = null,
    highPattern: SprudelPattern? = null,
    rankPattern: SprudelPattern? = null,
): SprudelPattern {
    return object : SprudelPattern {
        override val weight: Double get() = source.weight
        override val numSteps: Double? get() = source.numSteps
        override fun estimateCycleDuration(): Double = source.estimateCycleDuration()

        override fun queryArcContextual(
            from: CycleTime,
            to: CycleTime,
            ctx: SprudelPattern.QueryContext,
        ): List<SprudelPatternEvent> {
            val outerEvents = source.queryArcContextual(from, to, ctx)
            val result = mutableListOf<SprudelPatternEvent>()

            // Voice-leading state shared across the source events in this query.
            var lastVoicing: List<String> = emptyList()

            for (outerEvent in outerEvents) {
                val intersectStart = maxOf(from, outerEvent.part.begin)
                val intersectEnd = minOf(to, outerEvent.part.end)
                if (intersectEnd <= intersectStart) continue

                val chordName = outerEvent.data.chord

                val innerPattern: SprudelPattern = if (chordName == null) {
                    AtomicPattern(data = outerEvent.data, sourceLocations = outerEvent.sourceLocations)
                } else {
                    val sampleTime = outerEvent.whole.begin

                    val low = lowPattern?.sampleAt(sampleTime, ctx)?.data?.value?.asString ?: "C3"
                    val high = highPattern?.sampleAt(sampleTime, ctx)?.data?.value?.asString ?: "C5"
                    val range = listOf(low, high)

                    val rankInt = rankPattern
                        ?.sampleAt(sampleTime, ctx)?.data?.value?.asDouble?.toInt()
                        ?: 0

                    val voicedNotes = getVoicedNotes(chordName, range, lastVoicing, rankInt)

                    if (voicedNotes.isEmpty()) {
                        AtomicPattern(data = outerEvent.data, sourceLocations = outerEvent.sourceLocations)
                    } else {
                        lastVoicing = voicedNotes
                        StackPattern(
                            voicedNotes.map { noteName ->
                                AtomicPattern(
                                    data = outerEvent.data.copy(
                                        note = noteName,
                                        freqHz = Tones.noteToFreq(noteName),
                                        chord = null,
                                    ),
                                    sourceLocations = outerEvent.sourceLocations,
                                )
                            }
                        )
                    }
                }

                val innerEvents = innerPattern.queryArcContextual(intersectStart, intersectEnd, ctx)
                for (innerEvent in innerEvents) {
                    val clippedPart = innerEvent.part.clipTo(outerEvent.part)
                    if (clippedPart != null) {
                        result.add(innerEvent.copy(part = clippedPart))
                    }
                }
            }

            return result
        }
    }
}

/** Converts a [value] [PatternLike] argument into a control [SprudelPattern], or null if no rank is given. */
private fun toControlPattern(value: PatternLike?, callInfo: CallInfo?): SprudelPattern? =
    value?.let { listOf<Any?>(it).asSprudelDslArgs(callInfo).toPattern() }

/**
 * Expands chord patterns into voiced notes using voice leading.
 *
 * Converts each event carrying a chord name (set via [chord]) into a stack of notes that
 * form the chord, applying smooth voice leading to minimise large jumps between chords.
 *
 * All three parameters are optional and accept any [PatternLike] — constants, mininotation
 * patterns, or continuous control patterns. They are sampled per chord event, so range and
 * rank can vary in time.
 *
 * - `rank` picks which candidate voicing to use: `0` (default) is the best fit by voice
 *   leading, `1` is the second-best, etc. Out-of-range values clamp to the last available
 *   candidate; non-integer values are floored.
 * - `low` and `high` set the search-range bottom and top as note-name strings (`"C3"`,
 *   `"E5"`, …). When omitted, the default range is `"C3"` to `"C5"`.
 *
 * ```KlangScript(Playable)
 * chord("C:major Am:minor F:major G:major").voicing()                                // best voicing (rank = 0), default range
 * ```
 *
 * ```KlangScript(Playable)
 * chord("C:major Am:minor F:major G:major").voicing(rank = 1)                        // second-best every event
 * ```
 *
 * ```KlangScript(Playable)
 * chord("<C Am F G>").voicing(rank = "<0 1 0 2>")                                    // rank varies per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * chord("<C Am F G>").voicing(rank = sine.range(0, 3).segment(4))                    // rank from a control pattern
 * ```
 *
 * ```KlangScript(Playable)
 * chord("Cmaj7 Am7 Fmaj7").voicing(low = "C3", high = "C5")                          // explicit fixed range
 * ```
 *
 * ```KlangScript(Playable)
 * chord("<C F G C>").voicing(low = "<C3 D3 E3 F3>", high = "<C5 D5 E5 F5>")          // range slides up per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * chord("Dm7 G7").voicing(rank = 1, low = "C3", high = "C5")                         // second-best, in C3–C5
 * ```
 *
 * @param rank Which candidate voicing to pick: `0` = best (default), `1` = second-best, etc. Floored, then clamped to the candidate count. [PatternLike], sampled per event.
 * @param low Bottom of the voicing range as a note name (e.g. `"C3"`). [PatternLike], sampled per event. Default: `"C3"`.
 * @param high Top of the voicing range as a note name (e.g. `"C5"`). [PatternLike], sampled per event. Default: `"C5"`.
 *
 * @category tonal
 * @tags voicing, voice leading, chord, harmony, rank, range
 */
@KlangScript.Function
fun SprudelPattern.voicing(
    rank: PatternLike? = null,
    low: PatternLike? = null,
    high: PatternLike? = null,
    callInfo: CallInfo? = null,
): SprudelPattern =
    applyVoicing(
        source = this,
        lowPattern = toControlPattern(low, callInfo),
        highPattern = toControlPattern(high, callInfo),
        rankPattern = toControlPattern(rank, callInfo),
    )

/**
 * Expands chord events in a string pattern into voiced notes with voice leading.
 *
 * See [SprudelPattern.voicing] for the meaning of [rank], [low], and [high].
 */
@KlangScript.Function
fun String.voicing(
    rank: PatternLike? = null,
    low: PatternLike? = null,
    high: PatternLike? = null,
    callInfo: CallInfo? = null,
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).voicing(rank, low, high, callInfo)

/**
 * Returns a [PatternMapperFn] that applies voicing to chord events.
 *
 * See [SprudelPattern.voicing] for the meaning of [rank], [low], and [high].
 */
@KlangScript.Function
fun voicing(
    rank: PatternLike? = null,
    low: PatternLike? = null,
    high: PatternLike? = null,
    callInfo: CallInfo? = null,
): PatternMapperFn =
    { p -> p.voicing(rank, low, high, callInfo) }

/**
 * Chains a voicing step onto this [PatternMapperFn].
 *
 * See [SprudelPattern.voicing] for the meaning of [rank], [low], and [high].
 */
@KlangScript.Function
fun PatternMapperFn.voicing(
    rank: PatternLike? = null,
    low: PatternLike? = null,
    high: PatternLike? = null,
    callInfo: CallInfo? = null,
): PatternMapperFn =
    this.chain { p -> p.voicing(rank, low, high, callInfo) }
