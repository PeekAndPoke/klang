package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.VoiceModifierPattern.Companion.modifyVoice

fun <T> dslPatternCreator(create: (T) -> VoiceData) = PatternCreator(create)

fun <T> StrudelPattern.dslPatternModifier(
    mod: VoiceData.(T) -> VoiceData,
    combine: (source: VoiceData, control: VoiceData) -> VoiceData,
) = DslPatternModifier(this, mod, combine)

class PatternCreator<T>(
    val create: (T) -> VoiceData,
) {
    operator fun invoke(value: T): StrudelPattern =
        AtomicPattern(create(value))

    operator fun invoke(vararg value: T): StrudelPattern =
        seq(*value.map { invoke(it) }.toTypedArray())
}

class DslPatternModifier<T>(
    val pattern: StrudelPattern,
    val mod: VoiceData.(T) -> VoiceData,
    val combine: (source: VoiceData, control: VoiceData) -> VoiceData,
) {
    operator fun invoke(value: T): StrudelPattern =
        pattern.modifyVoice { mod(it, value) }

    operator fun invoke(vararg values: T): StrudelPattern =
        invoke(seq(*values.map { invoke(it) }.toTypedArray()))

    /** Uses a control pattern to set notes */
    operator fun invoke(control: StrudelPattern): StrudelPattern =
        pattern.applyControl(control, combine)
}

