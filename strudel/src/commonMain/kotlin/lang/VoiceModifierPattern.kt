package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent

internal class VoiceModifierPattern private constructor(
    val pattern: StrudelPattern,
    val mod: (VoiceData) -> VoiceData,
) : StrudelPattern {
    companion object {
        fun StrudelPattern.modifyVoice(block: (VoiceData) -> VoiceData): StrudelPattern {
            return if (this is VoiceModifierPattern) {
                // Flatten: Return a new modifier that wraps the *original* pattern
                // and applies the *old* modification followed by the *new* one.
                VoiceModifierPattern(this.pattern) { voice ->
                    block(this.mod(voice))
                }
            } else {
                VoiceModifierPattern(this, block)
            }
        }
    }

    override fun queryArc(
        from: Double,
        to: Double,
    ): List<StrudelPatternEvent> {
        return pattern.queryArc(from, to).map {
            it.copy(data = mod(it.data))
        }
    }
}
