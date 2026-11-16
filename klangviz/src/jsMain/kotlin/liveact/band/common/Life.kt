/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.common

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.X_AXIS
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.hash01
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.valueNoise
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.getWorldPosition
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

// What makes them alive apart from playing and moving with the beat: eye contact, the eyes' wavering glow, breath.
// All three are applied after the players' own poses, in this order. None of them keeps anything between frames:
// each frame is worked out from the time alone.

/**
 * Eye contact: every now and then two of them look at each other for a few seconds, then back to their playing.
 * Chance picks the moment and the pair, the same at the same place in the song. Pairs that have something to say
 * to each other: the rhythm section, neighbours, the frontwoman and whoever she passes.
 */
val PAIRS: List<Pair<Player, Player>> = listOf(
    Player.Bass to Player.Drums,
    Player.Guitar1 to Player.Bass,
    Player.Eimer to Player.Guitar1,
    Player.Marimba to Player.Guitar2,
    Player.Drums to Player.Marimba,
    Player.Eimer to Player.Marimba,
    Player.Eimer to Player.Drums,
    Player.Bass to Player.Eimer,
    Player.Guitar2 to Player.Drums,
    Player.Guitar1 to Player.Guitar2,
)

/** How often eye contact may happen: a chance every [every] seconds, of which [chance] are taken. */
data class ContactTiming(val every: Double, val chance: Double)

/** The timing of eye contact. */
val CONTACT = ContactTiming(every = 11.0, chance = 0.4)

/**
 * Who looks at whom: the [first] starts it, the [second] answers. [wFirst], [wSecond]: how far each head has turned
 * toward the other (0 to 1); [nodFirst], [nodSecond]: the small nod each gives as their eyes meet.
 */
private class Contact(
    val first: Player,
    val second: Player,
    val wFirst: Double,
    val wSecond: Double,
    val nodFirst: Double,
    val nodSecond: Double,
)

/** Who looks at whom at [t], and how far each head has turned; null when nobody does. [herNotes]: when the frontwoman plays. */
private fun contactAt(show: Show, t: Double, herNotes: List<Double>): Contact? {
    if (t < 1) {
        return null
    }

    val slot = floor(t / CONTACT.every)

    fun roll(n: Int): Double = hash01((slot + n) * 12.9898 + 311.7 + show.seed * 0.6180339)

    if (roll(0) > CONTACT.chance) {
        return null
    }

    // one of the two starts it; the other notices and answers one to three seconds later
    val drawn = PAIRS[floor(roll(1) * PAIRS.size).toInt() % PAIRS.size]
    val pair = if (roll(4) < 0.5) drawn else drawn.second to drawn.first
    val late = 1.0 + 2.0 * roll(5)
    val start = slot * CONTACT.every + 0.2 + roll(2) * 1.2
    val hold = 0.7 + 0.9 * roll(3)                 // how long they really look at each other: short
    val meet = late + 1.3                          // when both look
    val away = meet + hold                         // when the first one looks away again

    // she does not look away while she plays: the trumpet is at her lips
    val hers = pair.first == Player.Eimer || pair.second == Player.Eimer

    if (hers && herNotes.any { x -> x > start - 3 && x < start + away + 3 }) {
        return null
    }

    val x = t - start
    // No staring. The first one glances over, a second or so, and is half back at the playing when the other
    // notices and looks; then the first looks again and they share it briefly. A head never turns all the way: a
    // good half, as people look with their eyes; the beat's nod goes on underneath.
    val glance = 0.50 * smooth(x / 1.2) * smooth((2.0 - x) / 0.9)
    val again = 0.62 * smooth((x - late - 0.4) / 0.9) * smooth((away + 0.9 - x) / 0.9)
    val second = 0.58 * smooth((x - late) / 1.1) * smooth((away + 0.35 + 0.9 - x) / 0.9)

    // as their eyes meet, a small nod to each other, the second a moment after the first
    fun greet(at: Double): Double {
        val d = (x - at) / 0.28

        return exp(-d * d)
    }

    return Contact(
        first = pair.first,
        second = pair.second,
        wFirst = max(glance, again),
        wSecond = second,
        nodFirst = greet(meet + 0.25),
        nodSecond = greet(meet + 0.45),
    )
}

/** Turns the heads of the two who look at each other at [t]. [herNotes]: when the frontwoman plays. */
fun eyeContact(show: Show, rigs: Map<Player, Rig>, herNotes: List<Double>, t: Double) {
    val contact = contactAt(show, t, herNotes)

    if (contact == null || (contact.wFirst <= 0 && contact.wSecond <= 0)) {
        return
    }

    val a = rigs.getValue(contact.first)
    val b = rigs.getValue(contact.second)

    a.update()
    b.update()

    fun eyes(rig: Rig): Vector3 = rig.bone("head").getWorldPosition(vec()).add(vec(0.0, 0.08, 0.0))

    val ea = eyes(a)
    val eb = eyes(b)

    a.faceTo(eb, contact.wFirst)
    b.faceTo(ea, contact.wSecond)
    a.turn("head", X_AXIS, 0.09 * contact.nodFirst)
    b.turn("head", X_AXIS, 0.09 * contact.nodSecond)
}

/** The rigs in the players' order ([Player.entries]), those there are. */
private fun inOrder(rigs: Map<Player, Rig>): List<Rig> = Player.entries.mapNotNull { rigs[it] }

/**
 * The eyes: a third less bright than their players set them, and never steady. Their glow wanders by smooth noise,
 * each pair on its own (two layers: a slow drift and a quicker waver), by about a third up and down. It scales what
 * the players set this frame, so it runs once a frame, after them.
 */
fun eyesWaver(show: Show, rigs: Map<Player, Rig>, t: Double) {
    inOrder(rigs).forEachIndexed { i, rig ->
        val eyes = rig.eyes ?: return@forEachIndexed

        fun noise(x: Double, own: Int): Double = valueNoise(x) { k -> hash01((k + own * 57.3) * 12.9898 + show.seed * 0.6180339) }

        val waver = 0.62 * noise(t * 0.7, i + 1) + 0.38 * noise(t * 2.9, i + 11)

        eyes.emissiveIntensity *= 0.66 * (0.68 + 0.64 * waver)
    }
}

/**
 * Everyone breathes: at their own pace (14 to 19 breaths a minute), in a little faster than out, and deeper when
 * the band is loud ([slow]: the sound's energy). Last of all that moves bones: it widens them.
 */
fun breathing(show: Show, rigs: Map<Player, Rig>, t: Double, slow: Double) {
    inOrder(rigs).forEachIndexed { i, rig ->
        val phase = 2 * PI * (0.23 + 0.016 * ((i * 7) % 6)) * t + i * 1.9 + show.seed * 0.7
        val breath = 0.5 + 0.5 * sin(phase + 0.45 * sin(phase))

        rig.breathe(breath, 0.045 * (0.7 + 0.6 * slow))
    }
}
