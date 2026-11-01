/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.midi.NoteVoicing.Out

/** The held notes of every source (KLANG-00402, section 5): starts, stops, retrigger, sustain, one source's AllUp. */
class NoteVoicingSpec : StringSpec({

    val keylab = SourceId("keylab")
    val laptop = SourceId.Laptop

    fun voicing(): NoteVoicing {
        var next = 0

        return NoteVoicing(nextLiveId = { ++next })
    }

    fun down(source: SourceId, note: Int, channel: Int = 1) =
        ControlEvent.NoteDown(source = source, channel = channel, note = note, velocity = 0.5, tsMs = 0.0)

    fun up(source: SourceId, note: Int, channel: Int = 1) =
        ControlEvent.NoteUp(source = source, channel = channel, note = note, tsMs = 0.0)

    fun key(source: SourceId, note: Int, channel: Int = 1) = NoteVoicing.Key(source = source, channel = channel, note = note)

    /** The voices started and stopped, as "+liveId" and "-liveId". */
    fun List<Out>.ids(): List<String> = map {
        when (it) {
            is Out.Start -> "+${it.liveId}"
            is Out.Stop -> "-${it.liveId}"
        }
    }

    "a key down starts a voice with a new id, its up stops that voice; an up of a key not held does nothing" {
        val v = voicing()

        v.down(down(keylab, 60)) shouldBe listOf(Out.Start(key = key(keylab, 60), velocity = 0.5, liveId = 1))
        v.up(up(keylab, 60)) shouldBe listOf(Out.Stop(liveId = 1))
        v.up(up(keylab, 60)).shouldBeEmpty()
        v.held.shouldBe(emptyMap())
    }

    "a retrigger of a held key stops the old voice before the new one starts" {
        val v = voicing()

        v.down(down(keylab, 60))

        v.down(down(keylab, 60)).ids() shouldContainExactly listOf("-1", "+2")
        v.up(up(keylab, 60)).ids() shouldContainExactly listOf("-2")
    }

    "the same note on two sources, or on two channels, is two voices" {
        val v = voicing()

        v.down(down(keylab, 60)).ids() shouldContainExactly listOf("+1")
        v.down(down(laptop, 60)).ids() shouldContainExactly listOf("+2")
        v.down(down(keylab, 60, channel = 2)).ids() shouldContainExactly listOf("+3")

        v.up(up(laptop, 60)).ids() shouldContainExactly listOf("-2")
        v.held.keys shouldContainExactlyInAnyOrder listOf(key(keylab, 60), key(keylab, 60, channel = 2))
    }

    "sustain holds a voice past its key's up, until the sustain is up; a key still down sounds on" {
        val v = voicing()

        v.down(down(keylab, 60))
        v.down(down(keylab, 64))
        v.sustain(source = keylab, on = true).shouldBeEmpty()

        v.up(up(keylab, 60)).shouldBeEmpty()
        v.held[key(keylab, 60)]?.down shouldBe false

        v.sustain(source = keylab, on = false).ids() shouldContainExactly listOf("-1")
        v.held.keys shouldContainExactly setOf(key(keylab, 64))
    }

    "a sustained key struck again stops its sustained voice first" {
        val v = voicing()

        v.sustain(source = keylab, on = true)
        v.down(down(keylab, 60))
        v.up(up(keylab, 60))

        v.down(down(keylab, 60)).ids() shouldContainExactly listOf("-1", "+2")
    }

    "sustain is per source: the KeyLab's pedal does not hold the laptop's notes" {
        val v = voicing()

        v.sustain(source = keylab, on = true)
        v.down(down(laptop, 60))

        v.up(up(laptop, 60)).ids() shouldContainExactly listOf("-1")

        // The KeyLab's own notes it does hold
        v.down(down(keylab, 62))
        v.up(up(keylab, 62)).shouldBeEmpty()
    }

    "one source's AllUp stops its notes and its sustain only; the other source plays on" {
        val v = voicing()

        v.down(down(keylab, 60))
        v.down(down(laptop, 62))
        v.sustain(source = laptop, on = true)
        v.down(down(laptop, 64))
        v.up(up(laptop, 64))

        val gone = v.allUp(ControlEvent.AllUp(source = laptop, reason = ControlEvent.Reason.Disconnected, tsMs = 0.0))

        gone.ids() shouldContainExactlyInAnyOrder listOf("-2", "-3")
        v.held.keys shouldContainExactly setOf(key(keylab, 60))

        // Its sustain went with it: a laptop key's up stops its voice again
        v.down(down(laptop, 65))
        v.up(up(laptop, 65)).ids() shouldContainExactly listOf("-4")
    }

    "the panic stops every voice of every source and lifts every sustain" {
        val v = voicing()

        v.down(down(keylab, 60))
        v.down(down(laptop, 62))
        v.sustain(source = keylab, on = true)

        v.panic().ids() shouldContainExactlyInAnyOrder listOf("-1", "-2")
        v.held.shouldBe(emptyMap())

        // The sustain lifted: a KeyLab key's up stops its voice again
        v.down(down(keylab, 64))
        v.up(up(keylab, 64)).ids() shouldContainExactly listOf("-3")
    }
})
