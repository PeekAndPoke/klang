/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.figure

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.X_AXIS
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.skills.EyeContact
import io.peekandpoke.klang.viz.liveact.band.skills.Skill
import io.peekandpoke.klang.viz.liveact.band.skills.contactAt
import io.peekandpoke.klang.viz.liveact.band.skills.turning
import io.peekandpoke.klang.viz.liveact.hash01
import io.peekandpoke.klang.viz.liveact.triggers.ChanceInSlots
import io.peekandpoke.klang.viz.liveact.valueNoise
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.getWorldPosition
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import kotlin.math.PI
import kotlin.math.sin

// What makes them alive apart from playing and moving with the beat: eye contact, the eyes' wavering glow, breath.
// All three are applied after the players' own poses, in this order. None of them keeps anything between frames:
// each frame is worked out from the time alone.

/**
 * The pairs that have something to say to each other in an eye contact (see [contactAt]): the rhythm section,
 * neighbours, the frontwoman and whoever she passes. Each of the two turns only with the skill ([EyeContact]): one
 * without it does not answer, the other still offers the look.
 */
val PAIRS: List<Pair<Player, Player>> = listOf(
    Player.Beben to Player.Wucht,
    Player.Skalpell to Player.Beben,
    Player.Sirene to Player.Skalpell,
    Player.Rune to Player.Klaue,
    Player.Wucht to Player.Rune,
    Player.Sirene to Player.Rune,
    Player.Sirene to Player.Wucht,
    Player.Beben to Player.Sirene,
    Player.Klaue to Player.Wucht,
    Player.Skalpell to Player.Klaue,
)

/** The salt of the band's chance for the eye contact: the show's chance under it (a member's own is under their seed). */
private const val CONTACT_SALT = 311.7

/**
 * Turns the heads of the two who look at each other at [t] (see [contactAt]), by the band's [timing] (the score's),
 * each only where their [skills] give them [EyeContact] (see [turning]). [her]: the trumpet player (the cast's), who
 * looks at nobody while she plays; [herNotes]: when she plays.
 */
fun eyeContact(
    show: Show,
    timing: ChanceInSlots,
    rigs: Map<Player, Rig>,
    her: Player?,
    herNotes: List<Double>,
    t: Double,
    skills: (Player) -> List<Skill>,
) {
    val contact = contactAt(
        t = t,
        timing = timing,
        roll = { index -> show.own(index, CONTACT_SALT) },
        pairs = PAIRS,
        her = her,
        herNotes = herNotes,
    )

    if (contact == null || (contact.firstTurn <= 0 && contact.secondTurn <= 0)) {
        return
    }

    val turns = turning(first = contact.first, second = contact.second, skills = skills)
    val firstTurns = contact.first in turns
    val secondTurns = contact.second in turns
    val a = rigs.getValue(contact.first)
    val b = rigs.getValue(contact.second)

    a.update()
    b.update()

    val ea = eyesAt(a)
    val eb = eyesAt(b)

    if (firstTurns) {
        a.faceTo(eb, contact.firstTurn)
    }

    if (secondTurns) {
        b.faceTo(ea, contact.secondTurn)
    }

    if (firstTurns) {
        a.turn("head", X_AXIS, 0.09 * contact.firstNod)
    }

    if (secondTurns) {
        b.turn("head", X_AXIS, 0.09 * contact.secondNod)
    }
}

/** Where the eyes of [rig] are as it is posed now, in the world: what another looks at (a little above the head's bone). */
fun eyesAt(rig: Rig): Vector3 = rig.bone("head").getWorldPosition(vec()).add(vec(0.0, 0.08, 0.0))

/** The rigs in the players' order ([Player.entries]), those there are. */
private fun inOrder(rigs: Map<Player, Rig>): List<Rig> = Player.entries.mapNotNull { rigs[it] }

/**
 * The eyes' colour: each player's by how warm they are just now ([warmth], 0 to 1; see [Rig.warmEyes]). Once a
 * frame.
 */
fun eyesWarm(rigs: Map<Player, Rig>, warmth: (Player) -> Double) {
    for ((player, rig) in rigs) {
        rig.warmEyes(warmth(player))
    }
}

/**
 * The eyes: a third less bright than they are set (by their players, then by their warmth), and never steady. Their glow wanders by smooth noise,
 * each pair on its own (two layers: a slow drift and a quicker waver), by about a third up and down. It scales what
 * is set this frame, so it runs once a frame, after [eyesWarm].
 */
fun eyesWaver(show: Show, rigs: Map<Player, Rig>, t: Double) {
    eyesWaver(seed = show.seed, rigs = rigs, t = t)
}

/** The eyes' waver as [eyesWaver] of a show has it, by the chance of [seed] (a show's seed) alone. */
fun eyesWaver(seed: Int, rigs: Map<Player, Rig>, t: Double) {
    inOrder(rigs).forEachIndexed { i, rig ->
        val eyes = rig.eyes ?: return@forEachIndexed

        fun noise(x: Double, own: Int): Double = valueNoise(x) { k -> hash01((k + own * 57.3) * 12.9898 + seed * 0.6180339) }

        val waver = 0.62 * noise(t * 0.7, i + 1) + 0.38 * noise(t * 2.9, i + 11)

        eyes.emissiveIntensity *= 0.66 * (0.68 + 0.64 * waver)
    }
}

/**
 * Everyone breathes: at their own pace (14 to 19 breaths a minute), in a little faster than out, and deeper when
 * the band is loud ([slow]: the sound's energy). Last of all that moves bones: it widens them.
 */
fun breathing(show: Show, rigs: Map<Player, Rig>, t: Double, slow: Double) {
    breathing(seed = show.seed, rigs = rigs, t = t, slow = slow)
}

/** The breath as [breathing] of a show has it, by the chance of [seed] (a show's seed) alone. */
fun breathing(seed: Int, rigs: Map<Player, Rig>, t: Double, slow: Double) {
    inOrder(rigs).forEachIndexed { i, rig ->
        val phase = 2 * PI * (0.23 + 0.016 * ((i * 7) % 6)) * t + i * 1.9 + seed * 0.7
        val breath = 0.5 + 0.5 * sin(phase + 0.45 * sin(phase))

        rig.breathe(breath, 0.045 * (0.7 + 0.6 * slow))
    }
}
