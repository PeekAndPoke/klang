package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.parser.MiniNotationParser
import kotlin.jvm.JvmName

typealias VoiceDataModifier<T> = VoiceData.(T) -> VoiceData

typealias VoiceDataMerger = (source: VoiceData, control: VoiceData) -> VoiceData

fun <T> voiceModifier(modify: VoiceDataModifier<T>): VoiceDataModifier<T> = modify

@JvmName("dslPatternCreatorString")
fun dslPatternCreator(modify: VoiceDataModifier<String>): DslPatternCreator<String> =
    dslPatternCreator(modify) { it }

@JvmName("dslPatternCreatorNumber")
fun dslPatternCreator(modify: VoiceDataModifier<Number>): DslPatternCreator<Number> =
    dslPatternCreator(modify) { (it.toDoubleOrNull() ?: 0.0) as Number }

@JvmName("dslPatternCreatorGeneric")
fun <T> dslPatternCreator(modify: VoiceDataModifier<T>, strToT: (String) -> T) = DslPatternCreator(modify, strToT)

@JvmName("dslPatternModifierString")
fun StrudelPattern.dslPatternModifier(modify: VoiceDataModifier<String>, combine: VoiceDataMerger) = dslPatternModifier(
    modify = modify,
    strToT = { it },
    combine = combine
)

@JvmName("dslPatternModifierNumber")
fun StrudelPattern.dslPatternModifier(modify: VoiceDataModifier<Number>, combine: VoiceDataMerger) = dslPatternModifier(
    modify = modify,
    strToT = { (it.toDoubleOrNull() ?: 0.0) as Number },
    combine = combine
)

@JvmName("dslPatternModifierGeneric")
fun <T> StrudelPattern.dslPatternModifier(
    modify: VoiceDataModifier<T>,
    strToT: (String) -> T,
    combine: VoiceDataMerger,
) = DslPatternModifier(this, modify, strToT, combine)

class DslPatternCreator<T>(
    val modify: VoiceDataModifier<T>,
    val strToT: (String) -> T,
) {
    operator fun invoke(mini: String): StrudelPattern =
        MiniNotationParser(input = mini) {
            AtomicPattern(
                VoiceData.empty.modify(
                    strToT(it),
                )
            )
        }.parse()
}

class DslPatternModifier<T>(
    val pattern: StrudelPattern,
    val modify: VoiceDataModifier<T>,
    val strToT: (String) -> T,
    val combine: VoiceDataMerger,
) {
    /** Parses mini notation and uses the resulting pattern as a control pattern  */
    operator fun invoke(mini: String): StrudelPattern {
        return invoke(
            control = MiniNotationParser(input = mini) {
                AtomicPattern(
                    VoiceData.empty.modify(
                        strToT(it),
                    )
                )
            }.parse()
        )
    }

    /** Uses a control pattern to modify voice events */
    operator fun invoke(control: StrudelPattern): StrudelPattern {
        return pattern.applyControl(control, combine)
    }

}

