package io.peekandpoke.klang.audio_be.signalgen

import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.osci.OscFn

/**
 * Adapts a legacy [OscFn] to the [SignalGen] interface.
 *
 * The OscFn interface expects phase/phaseInc from the caller, while SignalGen manages phase internally.
 * This bridge:
 * 1. Converts freqHz to phaseInc
 * 2. Manages phase state in its closure
 * 3. Delegates to OscFn.process()
 */
fun OscFn.toSignalGen(): SignalGen {
    var phase = 0.0

    return SignalGen { buffer, freqHz, ctx ->
        val phaseInc = TWO_PI * freqHz / ctx.sampleRateD

        phase = this.process(
            buffer = buffer,
            offset = ctx.offset,
            length = ctx.length,
            phase = phase,
            phaseInc = phaseInc,
            phaseMod = ctx.phaseMod,
        )
    }
}
