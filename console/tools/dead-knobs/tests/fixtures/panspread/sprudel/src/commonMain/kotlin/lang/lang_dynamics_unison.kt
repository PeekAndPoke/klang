package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.sprudel.putIgnitorParam

// -- unison ---------------------------------------------------------------------------------------------------------

private val unisonVoicesMutation = voiceSetter { putIgnitorParam(IgnitorSlotKeys.voices, it?.asDoubleOrNull()) }

private val unisonSpreadMutation = voiceSetter { putIgnitorParam(IgnitorSlotKeys.spread, it?.asDoubleOrNull()) }

private val unisonPanMutation = voiceSetter { putIgnitorParam("panSpread", it?.asDoubleOrNull()) }

private fun applyUnisonPan(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        // A frontend read-back of its own key: not an engine reader.
        return source._mapNumericField(mapper, read = { it.ignitorParams?.get("panSpread") }, update = unisonPanMutation)
    }

    return source._liftOrReinterpretNumericalField(args, unisonPanMutation)
}
