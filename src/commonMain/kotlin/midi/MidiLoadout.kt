/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.peekandpoke.klang.midi.MidiPlayCode.Sound

/**
 * The MIDI loadout (KLANG-00402, step 5c, the maintainer, 2026-11-01: "a loadout again, so the user can configure the
 * instruments that will be switched through by the change instrument inputs"): the instruments the instrument keys (a
 * KeyLab's main encoder, the laptop's `[` and `]`) step through, in their order, each once, [MAX] at most. Filled by
 * drag and drop on the MIDI page, as the Looks page equips. An empty loadout steps through every instrument there is.
 */
data class MidiLoadout(val sounds: List<Sound> = emptyList()) {

    companion object {
        /** The slots a loadout has at most. */
        const val MAX = 12

        /** A loadout as its sounds' codes ([Sound.code]) read back: a name in quotes is a built-in, else a let. */
        fun ofCodes(codes: List<String>): MidiLoadout = MidiLoadout(
            codes.mapNotNull { code ->
                val trimmed = code.trim()

                when {
                    trimmed.isEmpty() -> null
                    trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"") ->
                        Sound.BuiltIn(trimmed.substring(1, trimmed.length - 1))

                    else -> Sound.Own(trimmed)
                }
            }.distinct().take(MAX)
        )
    }

    /** The sounds' codes, for the store. */
    val codes: List<String> get() = sounds.map { it.code }

    /** [sound] at the end, where it is not in the loadout yet and a slot is free. */
    fun add(sound: Sound): MidiLoadout = when {
        sound in sounds || sounds.size >= MAX -> this
        else -> copy(sounds = sounds + sound)
    }

    /**
     * [sound] dropped on slot [index]: one already in another slot swaps with the one there (the maintainer, 2026-11-02:
     * "Swap them"; as the Looks page), a new one takes the slot in place of the one there; past the end, a new one is
     * added and one already in the loadout moves to the end. Onto its own slot, or a slot before the first, nothing
     * changes.
     */
    fun place(index: Int, sound: Sound): MidiLoadout {
        val from = sounds.indexOf(sound)

        return when {
            // Onto its own slot it swaps with itself: nothing changes
            index < 0 -> this
            index >= sounds.size -> if (from >= 0) copy(sounds = sounds.filter { it != sound } + sound) else add(sound)
            from >= 0 -> copy(sounds = sounds.toMutableList().also { list -> list[from] = sounds[index]; list[index] = sound })
            else -> copy(sounds = sounds.toMutableList().also { list -> list[index] = sound })
        }
    }

    /** The loadout without slot [index]. */
    fun remove(index: Int): MidiLoadout = copy(sounds = sounds.filterIndexed { i, _ -> i != index })

    /** What the instrument keys step through, of the sounds [available]: the loadout's that still are, else all of them. */
    fun stepsThrough(available: List<Sound>): List<Sound> = sounds.filter { it in available }.ifEmpty { available }

    /**
     * The sound [steps] away from [from] in what the keys step through ([stepsThrough] of [available]), round the list;
     * from a sound not in it, forward from before the first and back from the first. Null with nothing to step through.
     */
    fun next(from: Sound?, steps: Int, available: List<Sound>): Sound? {
        val list = stepsThrough(available).takeIf { it.isNotEmpty() } ?: return null
        val at = list.indexOf(from).takeIf { it >= 0 } ?: if (steps > 0) -1 else 0

        return list[((at + steps) % list.size + list.size) % list.size]
    }
}
