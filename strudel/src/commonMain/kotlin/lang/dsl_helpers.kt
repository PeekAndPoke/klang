package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.parser.MiniNotationParser
import kotlin.jvm.JvmName
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// --- Registry ---

object StrudelRegistry {
    val functions = mutableMapOf<String, (List<Any>) -> Any>()
    val methods = mutableMapOf<String, (Any, List<Any>) -> Any>()
}

// --- Type Aliases ---

typealias VoiceDataModifier<T> = VoiceData.(T) -> VoiceData
typealias VoiceDataMerger = (source: VoiceData, control: VoiceData) -> VoiceData

fun <T> voiceModifier(modify: VoiceDataModifier<T>): VoiceDataModifier<T> = modify

// --- Creator Delegate ---

class DslPatternCreatorProvider<T>(
    private val modify: VoiceDataModifier<T>,
    private val fromStr: (String) -> T,
) {
    operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): ReadOnlyProperty<Any?, DslPatternCreator<T>> {
        val name = prop.name
        val creator = DslPatternCreator(modify, fromStr)

        StrudelRegistry.functions[name] = { args ->
            val arg = args.first().toString()
            creator(arg)
        }

        return ReadOnlyProperty { _, _ -> creator }
    }
}

@JvmName("dslPatternCreatorGeneric")
fun <T> dslPatternCreator(
    modify: VoiceDataModifier<T>,
    fromStr: (String) -> T,
) = DslPatternCreatorProvider(modify, fromStr)

@JvmName("dslPatternCreatorString")
fun dslPatternCreator(
    modify: VoiceDataModifier<String>,
) = dslPatternCreator(modify) { it }

@JvmName("dslPatternCreatorNumber")
fun dslPatternCreator(
    modify: VoiceDataModifier<Number>,
) = dslPatternCreator(modify) { (it.toDoubleOrNull() ?: 0.0) as Number }

// --- Modifier Delegate ---

class DslPatternModifierProvider<T>(
    private val modify: VoiceDataModifier<T>,
    private val combine: VoiceDataMerger,
    private val fromStr: (String) -> T,
    private val toStr: (T) -> String,
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>,
    ): ReadOnlyProperty<StrudelPattern, DslPatternModifier<T>> {
        val name = prop.name

        // Logic for the registered method
        StrudelRegistry.methods[name] = { receiver, args ->
            val p = receiver as StrudelPattern
            val modifier = DslPatternModifier(p, modify, fromStr, toStr, combine)
            val arg = args.firstOrNull() ?: error("Method $name requires an argument")

            when (arg) {
                is StrudelPattern -> modifier(arg)
                else -> modifier(arg.toString())
            }
        }

        return ReadOnlyProperty { thisRef, _ ->
            DslPatternModifier(thisRef, modify, fromStr, toStr, combine)
        }
    }
}

@JvmName("dslPatternModifierGeneric")
fun <T> dslPatternModifier(
    modify: VoiceDataModifier<T>,
    combine: VoiceDataMerger,
    fromStr: (String) -> T,
    toStr: (T) -> String,
) = DslPatternModifierProvider(modify, combine, fromStr, toStr)

@JvmName("dslPatternModifierString")
fun dslPatternModifier(modify: VoiceDataModifier<String>, combine: VoiceDataMerger) =
    dslPatternModifier(
        modify = modify,
        fromStr = { it },
        toStr = { it },
        combine = combine
    )

@JvmName("dslPatternModifierNumber")
fun dslPatternModifier(modify: VoiceDataModifier<Number>, combine: VoiceDataMerger) =
    dslPatternModifier(
        modify = modify,
        fromStr = { (it.toDoubleOrNull() ?: 0.0) as Number },
        toStr = { it.toString() },
        combine = combine
    )

// --- Implementations ---

class DslPatternCreator<T>(
    val modify: VoiceDataModifier<T>,
    val fromStr: (String) -> T,
) {
    operator fun invoke(mini: String): StrudelPattern =
        MiniNotationParser(input = mini) {
            AtomicPattern(
                VoiceData.empty.modify(
                    fromStr(it),
                )
            )
        }.parse()
}

class DslPatternModifier<T>(
    val pattern: StrudelPattern,
    val modify: VoiceDataModifier<T>,
    val fromStr: (String) -> T,
    val toStr: (T) -> String,
    val combine: VoiceDataMerger,
) {
    /** Parses mini notation and uses the resulting pattern as a control pattern  */
    operator fun invoke(mini: String): StrudelPattern {
        return invoke(
            control = MiniNotationParser(input = mini) {
                AtomicPattern(
                    VoiceData.empty.modify(
                        fromStr(it),
                    )
                )
            }.parse()
        )
    }

    operator fun invoke(vararg values: T): StrudelPattern {
        return invoke(values.joinToString(" ") { toStr(it) })
    }

    /** Uses a control pattern to modify voice events */
    operator fun invoke(control: StrudelPattern): StrudelPattern {
        return pattern.applyControl(control, combine)
    }
}

