/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

/** One channel message as a port delivered it: [status] holds the kind and the channel, [data1] and [data2] the rest. */
data class MidiMessage(val port: String, val status: Int, val data1: Int, val data2: Int, val tsMs: Double) {
    /** The message's kind: 0x80 note off, 0x90 note on, 0xB0 control change, 0xE0 pitch bend, ... */
    val kind: Int get() = status and 0xF0

    /** The channel as every UI counts it, 1 to 16 (0 to 15 on the wire). */
    val channel: Int get() = (status and 0x0F) + 1
}

/**
 * A MIDI device's translation from bytes to [ControlEvent]s (KLANG-00402, section 3), grown out of [MidiBindings]: the
 * notes of the keybed stay notes; a note or a CC the [panel]'s bindings name becomes that control, and the control's
 * drawn kind says how (a button presses and releases, the relative encoder nudges, a knob, a fader or a wheel moves).
 * Without a panel it is the generic profile, so every MIDI keyboard plays: notes are notes, CC 64 is the sustain, the
 * bend is the bend, any other CC n moves "cc-n". CC 120 and 123 are the panic on any device.
 *
 * One per port: the pad bank it infers ([bank]) is that device's.
 */
class DeviceProfile(val source: SourceId, private val panel: MidiDevicePanel?) {

    companion object {
        const val CC_SUSTAIN = 64
        const val CC_ALL_SOUND_OFF = 120
        const val CC_ALL_NOTES_OFF = 123

        /** The KeyLab's Bank button, bound as a control (CC 118). */
        const val BANK = "bank"

        /** The profile for a port: the KeyLab's by its name, else the generic one. */
        fun forPort(port: String): DeviceProfile = when {
            port.contains("KeyLab", ignoreCase = true) ->
                DeviceProfile(source = SourceId("keylab-49-mk3"), panel = KeyLabEssential49Mk3)

            else -> DeviceProfile(source = SourceId("midi:$port"), panel = null)
        }
    }

    /**
     * The pad bank believed live, "A" or "B". The Bank button only says it was pressed, never which bank is live, so
     * this starts as a guess (A, the power-on default), turns on each press of the button, and is corrected by every
     * pad note, which says unambiguously which bank sent it ([padBankOf]): one pad hit and it is right again.
     */
    var bank: String = "A"
        private set

    private val bindings: MidiBindings = panel?.bindings ?: MidiBindings()

    private val controls: Map<String, PanelControl> = panel?.controls.orEmpty().associateBy { it.id }

    /** What [message] means; nothing for a message no control and no note stands for. */
    fun translate(message: MidiMessage): List<ControlEvent> {
        val ts = message.tsMs

        return when (message.kind) {
            // A note on with velocity 0 is a note off by convention
            0x90 -> if (message.data2 > 0) noteDown(message) else noteUp(message)

            0x80 -> noteUp(message)

            0xB0 -> controlChange(message)

            0xE0 -> {
                // 14 bits across both data bytes, centred on 8192
                val raw = (message.data2 shl 7) or message.data1
                val control = bindings.pitchBend?.let { ControlId(it) } ?: ControlId.PitchBend

                listOf(ControlEvent.Move(source = source, control = control, value = (raw - 8192) / 8192.0, tsMs = ts))
            }

            else -> emptyList()
        }
    }

    private fun noteDown(message: MidiMessage): List<ControlEvent> {
        val pad = bindings.notes[message.channel to message.data1]
            ?: return listOf(
                ControlEvent.NoteDown(
                    source = source,
                    channel = message.channel,
                    note = message.data1,
                    velocity = message.data2 / 127.0,
                    tsMs = message.tsMs,
                )
            )

        padBankOf(message.data1)?.let { bank = it }

        return listOf(
            ControlEvent.Press(source = source, control = ControlId(pad), velocity = message.data2 / 127.0, tsMs = message.tsMs)
        )
    }

    private fun noteUp(message: MidiMessage): List<ControlEvent> {
        val pad = bindings.notes[message.channel to message.data1]
            ?: return listOf(
                ControlEvent.NoteUp(source = source, channel = message.channel, note = message.data1, tsMs = message.tsMs)
            )

        return listOf(ControlEvent.Release(source = source, control = ControlId(pad), tsMs = message.tsMs))
    }

    private fun controlChange(message: MidiMessage): List<ControlEvent> {
        val cc = message.data1
        val value = message.data2
        val ts = message.tsMs

        if (cc == CC_ALL_SOUND_OFF || cc == CC_ALL_NOTES_OFF) {
            return listOf(ControlEvent.AllUp(source = source, reason = ControlEvent.Reason.Panic, tsMs = ts))
        }

        if (cc == CC_SUSTAIN) {
            return listOf(button(control = ControlId.Sustain, pressed = value >= 64, ts = ts))
        }

        val id = bindings.cc[cc]
            ?: return listOf(ControlEvent.Move(source = source, control = ControlId("cc-$cc"), value = value / 127.0, tsMs = ts))

        val control = ControlId(id)

        // The Bank button turns the bank on its press only: it also reports its release
        if (id == BANK && value > 0) {
            bank = if (bank == "A") "B" else "A"
        }

        val move = ControlEvent.Move(source = source, control = control, value = value / 127.0, tsMs = ts)

        return when (val drawn = controls[id]) {
            is PanelControl.Knob -> when {
                // The relative encoder sends a direction around 64, not a position; 64 itself is no turn
                !drawn.relative -> listOf(move)
                value == 64 -> emptyList()
                else -> listOf(ControlEvent.Nudge(source = source, control = control, steps = value - 64, tsMs = ts))
            }

            is PanelControl.Fader, is PanelControl.Wheel -> listOf(move)

            // A button, and a bound CC drawn as none (the encoder's click): pressed while the value is above 0
            is PanelControl.Button, is PanelControl.Pad, is PanelControl.Display, null ->
                listOf(button(control = control, pressed = value > 0, ts = ts))
        }
    }

    private fun button(control: ControlId, pressed: Boolean, ts: Double): ControlEvent = when {
        pressed -> ControlEvent.Press(source = source, control = control, velocity = 1.0, tsMs = ts)
        else -> ControlEvent.Release(source = source, control = control, tsMs = ts)
    }
}
