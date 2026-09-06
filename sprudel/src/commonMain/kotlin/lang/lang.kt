/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData

/**
 * Type alias for pattern-like values that can be converted to patterns.
 * Accepts:
 * - [SprudelPattern]
 * - [String]
 * - [Number]
 * - [Boolean]
 * - and other types that can be converted to patterns.
 */
typealias PatternLike = Any

/**
 * Type alias for pattern transformation functions.
 * Takes a SprudelPattern as input and returns a modified SprudelPattern.
 */
typealias PatternMapperFn = (source: SprudelPattern) -> SprudelPattern

/**
 * Hands out a [PatternMapperFn]. Field accessors (`freq`, ...) implement it.
 *
 * An accessor is deliberately NOT a [PatternMapperFn] itself: a `Function1` carries a member
 * `invoke(SprudelPattern)`, which would sit next to the accessor's setter `invoke(hz)` and give
 * `Freq(pattern)` and `freq(pattern)` opposite meanings in Kotlin. The provider keeps one meaning
 * per spelling. A setter that receives a provider unwraps it with [mapper] and applies the result
 * to its own field (see `_mapNumericField`); `docs/tasks/sprudel-field-accessors.md`.
 */
fun interface PatternMapperProvider {
    fun mapper(): PatternMapperFn
}

/**
 * Type alias for voice data transformation functions.
 * Takes a SprudelVoiceData as input and returns a modified SprudelVoiceData.
 */
typealias VoiceModifierFn = SprudelVoiceData.(Any?) -> SprudelVoiceData

/**
 * Type alias for voice data merging functions.
 * Takes two SprudelVoiceData as input and returns a merged SprudelVoiceData.
 */
typealias VoiceMergerFn = (source: SprudelVoiceData, control: SprudelVoiceData) -> SprudelVoiceData
