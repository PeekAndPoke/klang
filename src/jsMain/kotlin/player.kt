package io.peekandpoke.klang

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_engine.klangPlayer
import io.peekandpoke.klang.audio_fe.create
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.script.KlangScriptEngine
import io.peekandpoke.klang.script.stdlib.ConsoleLevel
import io.peekandpoke.klang.script.stdlib.KlangScriptOsc
import io.peekandpoke.klang.script.stdlib.KlangStdLib
import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import io.peekandpoke.kraft.utils.async
import io.peekandpoke.kraft.utils.launch
import io.peekandpoke.ultra.common.MutableTypedAttributes
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
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

    private val _player = StreamSource<KlangPlayer?>(null)
    val player: Stream<KlangPlayer?> = _player.readonly

    private val _samples = StreamSource<Samples?>(null)
    val samples: Stream<Samples?> = _samples.readonly

//    private var _playerInstance: KlangPlayer? = null

    private var deferred: CompletableDeferred<KlangPlayer>? = null

    // We force downloading the sample immediately (better user experience later)
    private val samplesDeferred: Deferred<Samples> = async {
        Samples.create(catalogue = SampleCatalogue.default)
    }.also { it.start() }

    init {
        launch {
            val samples = samplesDeferred.await()
            _samples(samples)
        }
    }

    fun get(): KlangPlayer? = player()

    /**
     * Creates a KlangScriptEngine with sprudel and stdlib registered.
     *
     * If a [player] is available, `Osc.register()` is wired to send commands to the audio backend.
     * If [outputHandler] is provided, console/print output goes there (e.g. REPL capture).
     */
    fun createEngine(
        player: KlangPlayer? = get(),
        outputHandler: ((ConsoleLevel, List<String>) -> Unit)? = null,
    ): KlangScriptEngine {
        val attrs = MutableTypedAttributes {
            if (player != null) {
                add(KlangScriptOsc.REGISTRAR_KEY) { name: String, dsl: IgnitorDsl ->
                    player.sendControl(
                        KlangCommLink.Cmd.RegisterIgnitor(
                            playbackId = KlangCommLink.SYSTEM_PLAYBACK_ID,
                            name = name,
                            dsl = dsl,
                        )
                    )
                    name
                }
            }
        }

        val stdlib = if (outputHandler != null) {
            KlangStdLib.create(outputHandler = outputHandler)
        } else {
            stdlibLib
        }

        val engineBuilder = KlangScriptEngine.Builder()
        engineBuilder.registerLibrary(stdlib)
        engineBuilder.registerLibrary(sprudelLib)
        return engineBuilder.build(attrs)
    }

    fun ensure(): Deferred<KlangPlayer> {
        deferred?.let { return it }

        val def = CompletableDeferred<KlangPlayer>().also { deferred = it }

        launch {
            _status(Status.LOADING)

            val samples = samplesDeferred.await()
            _samples(samples)

            val playerOptions = KlangPlayer.Options(samples = samples, sampleRate = 48000)

            klangPlayer(playerOptions) { playerInstance ->
                console.log("KlangPlayer ready", playerInstance)
                // Submit Status
                _status(Status.READY)
                // Submit the player instance
                _player(playerInstance)
                // Complete the deferred
                def.complete(playerInstance)
            }
        }

        return def
    }
}
