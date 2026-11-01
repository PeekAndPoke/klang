/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

/**
 * Where a keybed's keys lie, drawn from [KeyBed.fromNote] + [offset]: the one geometry the drawing paints and a pointer
 * is tested against (KLANG-00402, step 3).
 */
class KeyBedLayout(private val bed: KeyBed, private val offset: Int) {
    companion object {
        private val BLACK_SEMITONES = setOf(1, 3, 6, 8, 10)

        private fun isBlack(note: Int): Boolean = (note.mod(12)) in BLACK_SEMITONES
    }

    val from: Int = bed.fromNote + offset
    val to: Int = bed.toNote + offset

    /** The white keys' notes, left to right. */
    val whites: List<Int> = (from..to).filter { !isBlack(it) }

    /** The black keys' notes, left to right. */
    val blacks: List<Int> = (from..to).filter { isBlack(it) }

    val whiteWidth: Double = bed.width.toDouble() / whites.size
    val blackWidth: Double = whiteWidth * 0.6
    val blackHeight: Double = bed.height * 0.62

    /** The left edge of a white key, or of a black key (which sits across the line between two whites). */
    fun xOf(note: Int): Double {
        val index = (from until note).count { !isBlack(it) }

        return bed.x + index * whiteWidth - if (isBlack(note)) whiteWidth * 0.3 else 0.0
    }

    /** The key at ([x], [y]): a black key first, as it lies on top; null outside the keybed. */
    fun keyAt(x: Double, y: Double): Int? {
        if (y < bed.y || y > bed.y + bed.height) {
            return null
        }

        if (y <= bed.y + blackHeight) {
            blacks.firstOrNull { x >= xOf(it) && x <= xOf(it) + blackWidth }?.let { return it }
        }

        return whites.firstOrNull { x >= xOf(it) && x < xOf(it) + whiteWidth }
    }
}

/**
 * The drawn controls as a source (KLANG-00402, section 3, "Screen"): what a pointer does on a panel's drawing, turned
 * into [ControlEvent]s, one pointer at a time, so several fingers play at once. A key and a pad go down and up; a
 * fader and a wheel follow the finger's height; a knob turns as the finger drags up or down; the endless encoder steps
 * as it drags, a whole step each way; the pitch wheel springs back to the middle when let go. Two fingers on one key
 * or button hold it as one: it goes down with the first and up with the last.
 */
class ScreenControls(private val source: SourceId) {

    /** What a pointer took hold of. Positions in the panel's own units. */
    sealed interface Grip {
        data class Key(val note: Int) : Grip

        /** A pad or a button. */
        data class Momentary(val control: ControlId) : Grip

        /** A fader or a wheel: the value follows the height from [top] (1) to [top] + [height] (0); [bipolar] -1..1. */
        data class Track(val control: ControlId, val top: Double, val height: Double, val bipolar: Boolean) : Grip

        /** A knob at [from], 0..1: dragging up turns it up. */
        data class Dial(val control: ControlId, val from: Double) : Grip

        /** An endless encoder: a step per [ENCODER_STEP] dragged. */
        data class Encoder(val control: ControlId) : Grip
    }

    companion object {
        /** How far a knob is dragged to turn from 0 to 1, in panel units. */
        const val DIAL_RANGE = 150.0

        /** How far the encoder is dragged per step, in panel units. */
        const val ENCODER_STEP = 12.0

        /** A key or a pad struck from the screen: no strength to measure, so a firm middle. */
        const val VELOCITY = 100 / 127.0

        /**
         * What the drawing of [panel] offers at ([x], [y]), its keybed drawn [noteOffset] up; [valueOf] gives a knob's
         * value now. A label or the display offers nothing.
         */
        fun gripAt(panel: MidiDevicePanel, x: Double, y: Double, noteOffset: Int, valueOf: (String) -> Double?): Grip? {
            val key = panel.keyBed?.let { bed -> KeyBedLayout(bed = bed, offset = noteOffset).keyAt(x = x, y = y) }

            if (key != null) {
                return Grip.Key(key)
            }

            val control = panel.controls.lastOrNull { it.contains(x = x, y = y) } ?: return null
            val id = ControlId(control.id)

            return when (control) {
                is PanelControl.Pad, is PanelControl.Button -> Grip.Momentary(control = id)
                is PanelControl.Knob -> when {
                    control.relative -> Grip.Encoder(id)
                    else -> Grip.Dial(control = id, from = valueOf(control.id) ?: 0.5)
                }

                // The range the cap is drawn in (a cap 10 high on a fader, 12 on a wheel), centred on the finger: a click on
                // the cap where it is moves it nowhere
                is PanelControl.Fader ->
                    Grip.Track(control = id, top = control.y + 5.0, height = control.travel - 10.0, bipolar = false)

                is PanelControl.Wheel -> Grip.Track(
                    control = id,
                    top = control.y + 6.0,
                    height = control.height - 12.0,
                    bipolar = id == ControlId.PitchBend,
                )

                is PanelControl.Display -> null
            }
        }

        /** Whether ([x], [y]) is on [this] control, with a little room around the small ones for a finger. */
        private fun PanelControl.contains(x: Double, y: Double): Boolean {
            fun inBox(left: Number, top: Number, w: Number, h: Number) =
                x in left.toDouble()..(left.toDouble() + w.toDouble()) && y in top.toDouble()..(top.toDouble() + h.toDouble())

            return when (this) {
                is PanelControl.Pad -> inBox(left = this.x, top = this.y, w = size, h = size)
                is PanelControl.Button -> inBox(left = this.x, top = this.y, w = width, h = height)
                is PanelControl.Knob -> (x - this.x) * (x - this.x) + (y - this.y) * (y - this.y) <= (radius + 6.0) * (radius + 6.0)
                is PanelControl.Fader -> inBox(left = this.x - 14, top = this.y - 6, w = 28, h = travel + 12)
                is PanelControl.Wheel -> inBox(left = this.x, top = this.y, w = width, h = height)
                is PanelControl.Display -> false
            }
        }
    }

    /** A pointer holding a grip: where it went down and the encoder's steps sent so far. */
    private data class Hold(val grip: Grip, val startY: Double, val steps: Int = 0)

    private val holds = mutableMapOf<Int, Hold>()

    /**
     * [pointer] went down on [grip] at height [y]. A hold the same pointer still had (its up lost: the mouse keeps one
     * pointer id) lets go first; a key or a button another finger already holds goes down no second time.
     */
    fun down(pointer: Int, grip: Grip, y: Double, tsMs: Double): List<ControlEvent> {
        val lost = up(pointer = pointer, tsMs = tsMs)
        val heldByAnother = holds.values.any { it.grip == grip }

        holds[pointer] = Hold(grip = grip, startY = y)

        return lost + when (grip) {
            is Grip.Key -> when {
                heldByAnother -> emptyList()
                else -> listOf(ControlEvent.NoteDown(source = source, channel = 1, note = grip.note, velocity = VELOCITY, tsMs = tsMs))
            }

            is Grip.Momentary -> when {
                heldByAnother -> emptyList()
                else -> listOf(ControlEvent.Press(source = source, control = grip.control, velocity = VELOCITY, tsMs = tsMs))
            }

            is Grip.Track -> listOf(trackMove(grip = grip, y = y, tsMs = tsMs))
            // A knob and the encoder wait for the drag
            is Grip.Dial, is Grip.Encoder -> emptyList()
        }
    }

    /** [pointer] moved to height [y]. */
    fun move(pointer: Int, y: Double, tsMs: Double): List<ControlEvent> {
        val hold = holds[pointer] ?: return emptyList()

        return when (val grip = hold.grip) {
            is Grip.Track -> listOf(trackMove(grip = grip, y = y, tsMs = tsMs))

            is Grip.Dial -> {
                val value = (grip.from + (hold.startY - y) / DIAL_RANGE).coerceIn(0.0, 1.0)

                listOf(ControlEvent.Move(source = source, control = grip.control, value = value, tsMs = tsMs))
            }

            is Grip.Encoder -> {
                // Toward zero, a whole step each way: a click's wobble of a pixel steps nothing
                val steps = ((hold.startY - y) / ENCODER_STEP).toInt()

                if (steps == hold.steps) {
                    emptyList()
                } else {
                    holds[pointer] = hold.copy(steps = steps)
                    listOf(ControlEvent.Nudge(source = source, control = grip.control, steps = steps - hold.steps, tsMs = tsMs))
                }
            }

            // A key and a pad stay what they were struck as, wherever the finger slides
            is Grip.Key, is Grip.Momentary -> emptyList()
        }
    }

    /**
     * [pointer] came up, or was taken away (a cancel): the key, the pad or the button comes up, unless another finger
     * still holds it; the bend springs back.
     */
    fun up(pointer: Int, tsMs: Double): List<ControlEvent> {
        val hold = holds.remove(pointer) ?: return emptyList()
        val heldByAnother = holds.values.any { it.grip == hold.grip }

        return when (val grip = hold.grip) {
            is Grip.Key -> when {
                heldByAnother -> emptyList()
                else -> listOf(ControlEvent.NoteUp(source = source, channel = 1, note = grip.note, tsMs = tsMs))
            }

            is Grip.Momentary -> when {
                heldByAnother -> emptyList()
                else -> listOf(ControlEvent.Release(source = source, control = grip.control, tsMs = tsMs))
            }

            // The bend springs back when the last finger on the wheel lets go
            is Grip.Track -> when {
                grip.bipolar && !heldByAnother ->
                    listOf(ControlEvent.Move(source = source, control = grip.control, value = 0.0, tsMs = tsMs))

                else -> emptyList()
            }

            is Grip.Dial, is Grip.Encoder -> emptyList()
        }
    }

    /** Every pointer lets go: the drawing goes away, and a removed element sends no up. */
    fun releaseAll(tsMs: Double): List<ControlEvent> = holds.keys.toList().flatMap { up(pointer = it, tsMs = tsMs) }

    /** The finger's height on a track, as the track's value: -1..1 for a bipolar one. */
    private fun trackMove(grip: Grip.Track, y: Double, tsMs: Double): ControlEvent {
        val fraction = (1.0 - (y - grip.top) / grip.height).coerceIn(0.0, 1.0)
        val value = if (grip.bipolar) fraction * 2 - 1 else fraction

        return ControlEvent.Move(source = source, control = grip.control, value = value, tsMs = tsMs)
    }
}
