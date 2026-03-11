package io.peekandpoke.klang

import de.peekandpoke.ultra.streams.Stream
import de.peekandpoke.ultra.streams.ops.filterIsInstance
import de.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignal
import io.peekandpoke.klang.script.ast.SourceLocationChain
import io.peekandpoke.klang.ui.PlaybackVoiceEvent

fun Stream<KlangPlaybackSignal>.asPlaybackVoiceEvents(): Stream<List<PlaybackVoiceEvent>> {

    val voicesScheduled: Stream<KlangPlaybackSignal.VoicesScheduled?> = filterIsInstance()

    return voicesScheduled.map { signal ->
        (signal?.voices ?: emptyList()).map { voice ->
            PlaybackVoiceEvent(
                startTime = voice.startTime,
                endTime = voice.endTime,
                sourceLocations = voice.sourceLocations as? SourceLocationChain,
            )
        }
    }
}
