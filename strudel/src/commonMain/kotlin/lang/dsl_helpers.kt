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

@JvmName("dslPatternCreatorGeneric")
fun <T> dslPatternCreator(
    modify: VoiceDataModifier<T>,
    fromStr: (String) -> T,
): ReadOnlyProperty<Any?, DslPatternCreator<T>> = object : ReadOnlyProperty<Any?, DslPatternCreator<T>> {

    // Create the instance once
    val creator = DslPatternCreator(modify, fromStr)
    var registered = false

    override fun getValue(thisRef: Any?, property: KProperty<*>): DslPatternCreator<T> {
        if (!registered) {
            val name = property.name
            StrudelRegistry.functions[name] = { args ->
                val arg = args.first().toString()
                creator(arg)
            }
            registered = true
        }
        return creator
    }
}


@JvmName("dslPatternCreatorString")
fun dslPatternCreator(
    modify: VoiceDataModifier<String>,
): ReadOnlyProperty<Any?, DslPatternCreator<String>> =
    dslPatternCreator(modify) { it }

@JvmName("dslPatternCreatorNumber")
fun dslPatternCreator(
    modify: VoiceDataModifier<Number>,
): ReadOnlyProperty<Any?, DslPatternCreator<Number>> =
    dslPatternCreator(modify) { (it.toDoubleOrNull() ?: 0.0) as Number }

// --- Modifier Delegate ---

@JvmName("dslPatternModifierGeneric")
fun <T> dslPatternModifier(
    modify: VoiceDataModifier<T>,
    combine: VoiceDataMerger,
    fromStr: (String) -> T,
    toStr: (T) -> String,
): ReadOnlyProperty<Any?, DslPatternModifier<T>> = object : ReadOnlyProperty<Any?, DslPatternModifier<T>> {

    var registered = false

    override fun getValue(thisRef: Any?, property: KProperty<*>): DslPatternModifier<T> {
        // Ensure thisRef is a Pattern (since this is an extension property on StrudelPattern)
        val pattern = thisRef as? StrudelPattern ?: error("dslPatternModifier must be used on StrudelPattern")

        if (!registered) {
            val name = property.name
            StrudelRegistry.methods[name] = { receiver, args ->
                val p = receiver as StrudelPattern
                // Re-create the modifier bound to the receiver
                val modifier = DslPatternModifier(p, modify, fromStr, toStr, combine)

                val arg = args.firstOrNull() ?: error("Method $name requires an argument")

                when (arg) {
                    is StrudelPattern -> modifier(arg)
                    // For literals (Number/String), we use the string representation
                    // and let DslPatternModifier parse it or use it.
                    else -> modifier(arg.toString())
                }
            }
            registered = true
        }

        return DslPatternModifier(pattern, modify, fromStr, toStr, combine)
    }
}

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

