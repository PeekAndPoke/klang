/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.math

import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow

/**
 * Converts a number of semitones to a frequency ratio: `2^(this / 12)`.
 *
 * Equal temperament splits the octave into twelve equal steps, so a perfect fifth of seven
 * semitones is `7.0.semitones()` = 1.4983, and an octave down, `(-12.0).semitones()`, is 0.5.
 * Multiply a frequency by the result to transpose it: `440.0 * 12.0.semitones()` is 880.0 Hz,
 * the same formula the engine uses in `Double.applySemitoneDetuneToFrequency`.
 *
 * The inverse is [toSemitones]. For hundredths of a semitone use [cents].
 */
fun Double.semitones(): Double = 2.0.pow(this / 12.0)

/**
 * Converts a number of cents to a frequency ratio: `2^(this / 1200)`.
 *
 * A cent is a hundredth of a semitone, so `100.0.cents()` and `1.0.semitones()` are the same
 * number, and `50.0.cents()` = 1.0293 is a quarter tone up. Cents are the currency of detuning:
 * a few cents apart thickens a sound, tens of cents apart sound out of tune.
 *
 * The inverse is [toSemitones] times 100.
 */
fun Double.cents(): Double = 2.0.pow(this / 1200.0)

/**
 * Converts a frequency ratio to a number of semitones: `12 * log2(this)`.
 *
 * The inverse of [semitones]: `2.0.toSemitones()` = 12.0, an octave, and `1.5.toSemitones()`
 * = 7.0196, which says that the just fifth is about two cents wider than the equal tempered one.
 *
 * A ratio of 0 gives `-Infinity` and a negative ratio gives `NaN`, exactly what `kotlin.math.log2`
 * gives for those inputs. There is no clamp and no exception here.
 */
fun Double.toSemitones(): Double = 12.0 * log2(this)

/**
 * Converts decibels to a linear gain: `10^(this / 20)`.
 *
 * Every mixing knob is in dB and every engine gain is linear, so this is the conversion between
 * them: `0.0.db()` = 1.0 is unity, `(-6.0).db()` = 0.5012 is very nearly half the amplitude, and
 * `(-20.0).db()` = 0.1 is a tenth of it.
 *
 * The inverse is [toDb].
 */
fun Double.db(): Double = 10.0.pow(this / 20.0)

/**
 * Converts a linear gain to decibels: `20 * log10(this)`.
 *
 * The inverse of [db]: `1.0.toDb()` = 0.0 and `0.5.toDb()` = -6.0206.
 *
 * A gain of 0 gives `-Infinity`, which is silence, and a negative gain gives `NaN`, exactly what
 * `kotlin.math.log10` gives for those inputs. There is no clamp and no exception here.
 */
fun Double.toDb(): Double = 20.0 * log10(this)
