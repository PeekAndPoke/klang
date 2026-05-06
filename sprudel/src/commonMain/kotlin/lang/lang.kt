@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData

@DslMarker
annotation class SprudelDsl

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
 * Type alias for voice data transformation functions.
 * Takes a SprudelVoiceData as input and returns a modified SprudelVoiceData.
 */
typealias VoiceModifierFn = SprudelVoiceData.(Any?) -> SprudelVoiceData

/**
 * Type alias for voice data merging functions.
 * Takes two SprudelVoiceData as input and returns a merged SprudelVoiceData.
 */
typealias VoiceMergerFn = (source: SprudelVoiceData, control: SprudelVoiceData) -> SprudelVoiceData
