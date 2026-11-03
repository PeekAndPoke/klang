/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.peekandpoke.klang.common.logging.LogArea
import io.peekandpoke.klang.common.logging.Logger
import io.peekandpoke.klang.utils.BuildMode
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource

private val log = Logger(area = LogArea.Midi, source = "MidiConnector")

/**
 * The browser's MIDI, as ports and bytes (KLANG-00402, section 3): device discovery and hot-plug, and one raw
 * [MidiMessage] per message, with the port it came from. What a message means is a [DeviceProfile]'s business, what
 * a key holds is [NoteVoicing]'s: this class knows neither notes held nor voices.
 */
class MidiConnector {
    enum class Status { Unsupported, Requesting, Denied, Ready }

    data class State(
        val status: Status,
        val devices: List<String> = emptyList(),
        val error: String? = null,
    )

    /** What a port says. Exhaustive by design: a new variant fails the consumer's `when` until it decides. */
    sealed interface Event {
        /** A message from [MidiMessage.port]. System realtime bytes (clock, active sensing) never get here. */
        data class Message(val message: MidiMessage) : Event

        /** A port went away: its keys' up can never arrive. */
        data class PortGone(val port: String) : Event
    }

    private val _state = StreamSource(State(status = Status.Requesting))
    val state: Stream<State> = _state.readonly

    private val _events = StreamSource<Event?>(null)

    /** Whether the last message failed in its consumers: its failure is logged, the ones after it are not. */
    private var failing = false
    val events: Stream<Event?> = _events.readonly

    /** Requests MIDI access and hooks every input; called once, by the MIDI rig, which keeps it for the app's life. */
    fun start() {
        if (!isWebMidiSupported()) {
            log.warn("Web MIDI API not available on navigator")
            _state { it.copy(status = Status.Unsupported) }
            return
        }

        log.debug("Requesting MIDI access ...")

        requestMidiAccess()
            .then { granted ->
                log.info("MIDI access granted")
                hookInputs(granted)
                granted.onstatechange = { evt ->
                    log.debug("State change: ${evt.port.name} ${evt.port.type} ${evt.port.state}")
                    // An unplugged device can never send its note-offs
                    if (evt.port.type == "input" && evt.port.state == "disconnected") {
                        _events(Event.PortGone(port = evt.port.name ?: evt.port.id))
                    }
                    hookInputs(granted)
                }
            }
            .catch { err ->
                // Also catches a throw inside the handler above, not only permission denial. A refusal is the user's
                // choice, shown as the MIDI tool's status: a warning; anything else is a failure: an error
                val refused = err.asDynamic().name in listOf("SecurityError", "NotAllowedError")

                if (refused) {
                    log.warn("MIDI access refused", err)
                } else {
                    log.error("MIDI setup failed", err)
                }

                _state { it.copy(status = Status.Denied, error = err.message ?: err.toString()) }
            }
    }

    //  MIDI  ///////////////////////////////////////////////////////////////////////////////////

    /** (Re-)hooks all inputs. Runs on every state change, so hot-plugged devices just work. */
    private fun hookInputs(access: MidiAccess) {
        val names = mutableListOf<String>()

        access.inputs.forEach { input, _ ->
            val portName = input.name ?: input.id
            names.add(portName)
            // The port goes with each message because a controller usually exposes SEVERAL: a main port, a
            // DAW-protocol port (MCU/HUI, which encodes buttons as NOTES), a DIN-thru, and on Linux an ALSA loopback.
            // Without it, messages from all of them are indistinguishable.
            input.onmidimessage = { evt -> onMidiMessage(portName, evt) }
        }

        log.info("${names.size} input(s) hooked")
        _state { it.copy(status = Status.Ready, devices = names) }
    }

    private fun onMidiMessage(port: String, evt: MidiMessageEvent) {
        val status = evt.byteAt(0)

        // System realtime (>= 0xF8: clock at 24 ppq, active sensing) is dropped: it would bury everything else
        if (status >= 0xF8) {
            return
        }

        // Raw log of everything that reaches us, so "the device sends nothing for this button" is a checkable claim. Not
        // through the Logger, on purpose: one line per message is a stream, which the log never takes (KLANG-00407, D7),
        // and the Console would redraw on every note while playing. The developer tools only, in a development build
        if (BuildMode.isDevelopment) {
            console.asDynamic().debug(
                "[midi/MidiConnector] <$port> status=0x${status.toString(16)} d1=${evt.byteAt(1)} d2=${evt.byteAt(2)} " +
                    "len=${evt.data.length}"
            )
        }

        // A throw in a consumer would die silently inside the MIDI callback
        try {
            _events(
                Event.Message(
                    MidiMessage(port = port, status = status, data1 = evt.byteAt(1), data2 = evt.byteAt(2), tsMs = evt.timeStamp)
                )
            )
            failing = false
        } catch (e: Throwable) {
            // Logged once, not for every message that fails after it: a consumer that throws on each would make a
            // stream of errors (KLANG-00407, D7); again once a message goes through
            if (!failing) {
                failing = true
                log.error("Message handling failed; failures after it are not logged until a message goes through", e)
            }
        }
    }
}
