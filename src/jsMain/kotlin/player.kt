package io.peekandpoke.klang

import de.peekandpoke.kraft.utils.async
import de.peekandpoke.kraft.utils.launch
import de.peekandpoke.ultra.streams.Stream
import de.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_engine.klangPlayer
import io.peekandpoke.klang.audio_fe.create
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.Samples
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

object Player {

    enum class Status {
        NOT_LOADED,
        LOADING,
        READY,
    }

    private val _status = StreamSource<Status>(Status.NOT_LOADED)
    val status: Stream<Status> = _status.readonly

    private val _samplesStream = StreamSource<Samples?>(null)
    val samplesStream: Stream<Samples?> = _samplesStream.readonly

    private var _player: KlangPlayer? = null

    private var deferred: CompletableDeferred<KlangPlayer>? = null

    // We force downloading the sample immediately (better user experience later)
    private val samplesDeferred: Deferred<Samples> = async {
        Samples.create(catalogue = SampleCatalogue.default)
    }.also { it.start() }

    init {
        launch {
            val samples = samplesDeferred.await()
            _samplesStream(samples)
        }
    }

    fun get(): KlangPlayer? = _player

    fun ensure(): Deferred<KlangPlayer> {
        deferred?.let { return it }

        val def = CompletableDeferred<KlangPlayer>().also { deferred = it }

        launch {
            _status(Status.LOADING)

            val samples = samplesDeferred.await()
            _samplesStream(samples)

            val playerOptions = KlangPlayer.Options(samples = samples, sampleRate = 48000)
            val player = klangPlayer(playerOptions)

            _status(Status.READY)

            console.log("KlangPlayer ready", player)

            _player = player
            def.complete(player)
        }

        return def
    }
}
