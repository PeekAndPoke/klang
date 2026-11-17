/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.notstrom

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.common.BodyOutline
import io.peekandpoke.klang.viz.liveact.band.common.ClothPattern
import io.peekandpoke.klang.viz.liveact.band.common.Drummer
import io.peekandpoke.klang.viz.liveact.band.common.Entrance
import io.peekandpoke.klang.viz.liveact.band.common.Glance
import io.peekandpoke.klang.viz.liveact.band.common.HairPart
import io.peekandpoke.klang.viz.liveact.band.common.MarimbaPlayer
import io.peekandpoke.klang.viz.liveact.band.common.Place
import io.peekandpoke.klang.viz.liveact.band.common.Rig
import io.peekandpoke.klang.viz.liveact.band.common.Side
import io.peekandpoke.klang.viz.liveact.band.common.StringKind
import io.peekandpoke.klang.viz.liveact.band.common.StringPlayer
import io.peekandpoke.klang.viz.liveact.band.common.Traits
import io.peekandpoke.klang.viz.liveact.band.common.TrumpetPlayer
import io.peekandpoke.klang.viz.liveact.band.common.blend
import io.peekandpoke.klang.viz.liveact.band.common.capture
import io.peekandpoke.klang.viz.liveact.band.common.enter
import io.peekandpoke.klang.viz.liveact.band.common.pattern
import io.peekandpoke.klang.viz.liveact.band.common.stepLegs
import io.peekandpoke.klang.viz.liveact.band.common.testBeat
import io.peekandpoke.klang.viz.liveact.band.common.walkPose
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.song.Note
import io.peekandpoke.klang.viz.liveact.song.Part
import io.peekandpoke.klang.viz.liveact.song.Strum
import io.peekandpoke.klang.viz.liveact.stage.Stage
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.Gltf
import io.peekandpoke.klang.workshop.threejs.addScaledVector
import io.peekandpoke.klang.workshop.threejs.asSkinnedMesh
import io.peekandpoke.klang.workshop.threejs.getWorldPosition
import io.peekandpoke.klang.workshop.threejs.gltfLoader
import io.peekandpoke.klang.workshop.threejs.materials
import io.peekandpoke.klang.workshop.threejs.name
import io.peekandpoke.klang.workshop.threejs.quaternion
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.floor

/**
 * Notstrom, the band: who stands where, what each one looks like and how each one moves, the figures themselves,
 * the players with their instruments, and how they come onto the stage.
 *
 * Six players on one skeleton: the drummer on his riser, the bass, the lead guitar ([Player.Skalpell]), the rhythm
 * guitar ([Player.Klaue], who plays the song's `guitar2` and `guitar3`), the marimba player (the song's `lead`)
 * and the frontwoman ([Player.Sirene], who plays the song's `trommel` on a trumpet).
 */
class Notstrom private constructor(
    private val show: Show,
    /** Every player's rig, in the players' order. */
    val rigs: Map<Player, Rig>,
    viewer: Vector3,
) {
    /** A player's character with the seed of their own chance. */
    private class Character(val seed: Double, val traits: Traits)

    companion object {
        /** The band's name, as it stands on the bass drum. */
        const val NAME = "NOTSTROM"

        /** The walk-in: the stage's clock starts this many seconds before the song. */
        const val INTRO = 15.0

        /**
         * Where each player stands: x to the audience's right, z toward the audience, the turn, and how high (the
         * riser).
         */
        val PLACES: Map<Player, Place> = mapOf(
            Player.Wucht to Place(x = 0.1, z = -2.1, turn = 0.0, y = Stage.RISER),
            Player.Beben to Place(x = -2.15, z = 0.4, turn = 0.15),
            Player.Skalpell to Place(x = -3.0, z = 1.7, turn = 0.3),
            Player.Sirene to Place(x = -0.7, z = 2.2, turn = 0.1),
            Player.Rune to Place(x = 1.7, z = 0.4, turn = -0.15),
            Player.Klaue to Place(x = 3.0, z = 1.7, turn = -0.3),
        )

        /** Each player's pattern, seen only in how the body throws back the light. */
        private val SKINS = mapOf(
            Player.Wucht to ClothPattern.Tiger,
            Player.Beben to ClothPattern.Pinstripe,
            Player.Skalpell to ClothPattern.Spiral,
            Player.Klaue to ClothPattern.Claws,
            Player.Rune to ClothPattern.Diamonds,
            Player.Sirene to ClothPattern.Braid,
        )

        /** Who wears hair as a pattern in the light, combed straight back. */
        private val HAIR = setOf(Player.Skalpell)

        /** Everyone glitters; the two women a little more (the share of spangles in the skin). */
        private val GLITTER = mapOf(Player.Sirene to 0.21, Player.Rune to 0.21)

        /** Each player's character: how they move with the music apart from playing (see `Mover`). */
        private val CHARACTERS: Map<Player, Character> = mapOf(
            // the engine of the band: on every beat, steady, a touch ahead
            Player.Wucht to Character(
                seed = 11.0,
                traits = Traits(
                    depth = 1.0, beats = listOf(1.0, 0.7, 0.9, 0.7), lag = -0.02, soft = 0.10, chance = 0.15,
                    look = 0.05, tilt = 0.03, lean = 0.01,
                    // from the trunk: his whole upper body goes
                    parts = Traits.Parts(head = 1.0, trunk = 1.3, knees = 0.8),
                ),
            ),
            // the steady one: late, mostly on one and three, always the same whatever the band does; looks about slowly
            Player.Beben to Character(
                seed = 23.0,
                traits = Traits(
                    steady = 0.9, depth = 0.9, beats = listOf(1.0, 0.2, 0.7, 0.2), lag = 0.07, soft = 0.18, chance = 0.12,
                    skip = 0.0, look = 0.22, tilt = 0.06, lean = 0.02,
                    // from the knees and hips, the head hardly
                    parts = Traits.Parts(head = 0.6, trunk = 0.8, knees = 1.0, sway = 1.3),
                ),
            ),
            // loose and restless: uneven, looks round a lot, skips nods
            Player.Skalpell to Character(
                seed = 37.0,
                traits = Traits(
                    depth = 0.8, beats = listOf(0.9, 0.55, 0.9, 0.55), lag = 0.02, soft = 0.13, chance = 0.6, skip = 0.18,
                    look = 0.30, tilt = 0.10, lean = 0.03,
                    // leans his whole trunk into it
                    parts = Traits.Parts(head = 0.9, trunk = 1.5, knees = 0.7, sway = 1.2),
                ),
            ),
            // the headbanger, but only in fits: about a quarter of the time he goes all the way, else he nods like the
            // others
            Player.Klaue to Character(
                seed = 41.0,
                traits = Traits(
                    fits = Traits.Fits(every = 12.0, share = 0.22, calm = 0.30),
                    depth = 0.85, beats = listOf(1.0, 0.85, 1.0, 0.85), lag = -0.03, soft = 0.11, chance = 0.25,
                    look = 0.08, tilt = 0.05, lean = 0.02,
                    // all head, planted feet
                    parts = Traits.Parts(head = 1.15, trunk = 0.9, knees = 0.8, sway = 0.6),
                ),
            ),
            // composed: modest nods (a little more than at first: the maintainer, 2026-11-16), her head tilting from
            // side to side
            Player.Rune to Character(
                seed = 53.0,
                traits = Traits(
                    depth = 0.68, beats = listOf(1.0, 0.25, 0.6, 0.25), lag = 0.03, soft = 0.18, chance = 0.35,
                    look = 0.10, tilt = 0.12, lean = 0.015,
                    // upright, quiet below the neck
                    parts = Traits.Parts(head = 1.0, trunk = 0.6, knees = 0.5),
                ),
            ),
            // the frontman: the balanced one. Every part takes the beat alike, on time, even, with little chance in it
            Player.Sirene to Character(
                seed = 67.0,
                traits = Traits(
                    depth = 0.9, beats = listOf(1.0, 0.5, 0.8, 0.5), lag = 0.0, soft = 0.13, chance = 0.2, skip = 0.0,
                    look = 0.14, tilt = 0.04, lean = 0.02,
                    parts = Traits.Parts(head = 1.0, trunk = 1.0, knees = 1.0, sway = 1.0),
                ),
            ),
        )

        /**
         * The walk-in: each player comes from off the stage between two times; the drummer first, from the back and
         * up onto the riser, the frontwoman last. In this order they are placed each frame.
         */
        private val ENTRANCES: Map<Player, Entrance> = linkedMapOf(
            Player.Wucht to Entrance(fromX = 0.1, fromZ = -7.5, start = -15.0, end = -9.0),
            Player.Beben to Entrance(fromX = -8.5, fromZ = 0.9, start = -13.5, end = -7.5),
            Player.Klaue to Entrance(fromX = 8.5, fromZ = 2.0, start = -12.5, end = -6.5),
            Player.Skalpell to Entrance(fromX = -8.5, fromZ = 2.1, start = -11.5, end = -5.5),
            Player.Rune to Entrance(fromX = 8.5, fromZ = 0.9, start = -10.5, end = -4.8),
            Player.Sirene to Entrance(fromX = -8.5, fromZ = 2.9, start = -8.5, end = -2.4),
        )

        /** The head's middle and size, in the body's rest space: a little above and behind where the head's bone starts. */
        private fun hairOf(rig: Rig): HairPart {
            val neck = rig.restStart("head")
            val size = rig.height / 1.60

            return HairPart(centre = vec(neck.x, neck.y + 0.115 * size, neck.z - 0.012 * size), radius = 0.104 * size)
        }

        /**
         * Loads the six figures (from [models], a folder's address ending in a slash) into [scene], each at its
         * place, and casts the band: every figure gets its instrument and its part of the song.
         *
         * The files are fetched together, but the figures enter the scene in the players' order: what is made first
         * is drawn first. [viewer]: where the camera is (who looks at the viewer looks there); it is read every
         * frame, so the show keeps it up to date.
         */
        suspend fun load(show: Show, scene: Scene, models: String, viewer: Vector3): Notstrom {
            val loader = Three.addon.gltfLoader()

            val loaded: List<Gltf> = coroutineScope {
                Player.entries.map { player -> async { loader.load("$models${player.key}.glb") } }.awaitAll()
            }

            val rigs = linkedMapOf<Player, Rig>()

            Player.entries.forEachIndexed { index, player ->
                val place = PLACES.getValue(player)
                val figure = loaded[index].scene

                figure.position.set(place.x, place.y, place.z)
                figure.rotation.y = place.turn
                scene.add(figure)

                val rig = Rig(figure)
                rigs[player] = rig

                figure.traverse { o ->
                    o.asSkinnedMesh?.materials?.forEach { m ->
                        val material = m.unsafeCast<MeshStandardMaterial>()

                        if (m.name.endsWith("_hair")) {
                            pattern(material = material, kind = ClothPattern.Strands, gloss = 0.07, matt = 0.62, hair = null, glitter = 0.05)
                        }

                        if (m.name.endsWith("_dark")) {
                            pattern(
                                material = material,
                                kind = SKINS.getValue(player),
                                gloss = 0.12,
                                matt = 0.85,
                                hair = if (player in HAIR) hairOf(rig) else null,
                                glitter = GLITTER[player] ?: 0.14,
                            )
                        }
                    }
                }
            }

            return Notstrom(show = show, rigs = rigs, viewer = viewer)
        }

        ////  Test notes, for a show without a recording  //////////////////////////////////////////////////////////////

        private fun testTune(): List<Note> = List(256) { i ->
            Note(
                t = i * 0.25,
                midi = 64 + listOf(0, 3, 7, 10, 12, 10, 7, 3)[i % 8] + (if (i % 32 >= 16) 5 else 0),
                v = if (i % 4 == 0) 1.0 else 0.6,
                // the prototype's test tune names no length; nothing reads it for the marimba
                d = 0.0,
            )
        }

        private fun testCall(): List<Note> = List(128) { i ->
            Note(
                t = 1 + i * 0.75 + (if (i % 8 >= 6) 3 else 0),
                d = 0.6,
                midi = 65 + listOf(0, 2, 3, 7, 5, 3, 10, 12)[i % 8],
                v = 1.0,
            )
        }.sortedBy { it.t }

        private fun testRiff(step: Double, low: Int): List<Strum> = List(512) { i ->
            val midi = low + listOf(0, 0, 3, 0, 5, 0, 7, 3)[floor(i * step * 2).toInt() % 8]

            // the prototype's riff names no length and no chord: its player takes 0.3 s and the one note then
            Strum(t = i * step, d = 0.3, midi = midi, v = if (i % 2 == 0) 1.0 else 0.7, all = listOf(midi))
        }
    }

    private fun character(player: Player) = CHARACTERS.getValue(player)

    private fun rig(player: Player) = rigs.getValue(player)

    private val song = show.song

    val drummer = Drummer(
        show = show,
        rig = rig(Player.Wucht),
        hits = song?.part { drumHits() } ?: Part.fixed(testBeat(64, show.bpm)),
        traits = character(Player.Wucht).traits,
        seed = character(Player.Wucht).seed,
        name = NAME,
    )

    val marimba = MarimbaPlayer(
        show = show,
        rig = rig(Player.Rune),
        part = song?.part { notesOf("lead") } ?: Part.fixed(testTune()),
        traits = character(Player.Rune).traits,
        seed = character(Player.Rune).seed,
    )

    val frontwoman = TrumpetPlayer(
        show = show,
        rig = rig(Player.Sirene),
        part = song?.part { notesOf("trommel") } ?: Part.fixed(testCall()),
        traits = character(Player.Sirene).traits,
        seed = character(Player.Sirene).seed,
    )

    /** The string players, in the order they are moved each frame. */
    val strings: List<Pair<Player, StringPlayer>> = listOf(
        Player.Beben to StringPlayer(
            show = show,
            rig = rig(Player.Beben),
            kind = StringKind.Bass,
            part = song?.part { strumsOf(listOf("bass")) } ?: Part.fixed(testRiff(step = 0.5, low = 28)),
            traits = character(Player.Beben).traits,
            seed = character(Player.Beben).seed,
            lean = 0.10,
            bang = 0.16,
            bounce = 0.045,
        ),
        Player.Skalpell to StringPlayer(
            show = show,
            rig = rig(Player.Skalpell),
            kind = StringKind.Guitar,
            part = song?.part { strumsOf(listOf("guitar1")) } ?: Part.fixed(testRiff(step = 0.25, low = 52)),
            traits = character(Player.Skalpell).traits,
            seed = character(Player.Skalpell).seed,
            lean = -0.05,
            bang = 0.14,
            bounce = 0.025,
        ),
        Player.Klaue to StringPlayer(
            show = show,
            rig = rig(Player.Klaue),
            kind = StringKind.Guitar,
            part = song?.part { strumsOf(listOf("guitar2", "guitar3")) } ?: Part.fixed(testRiff(step = 0.125, low = 40)),
            traits = character(Player.Klaue).traits,
            seed = character(Player.Klaue).seed,
            lean = 0.10,
            bang = 0.24,
            bounce = 0.028,
            outline = BodyOutline.SingleCutaway,
            glance = Glance(at = viewer, every = 11.0, chance = 0.5),
        ),
    )

    /** The band at [t]: everyone where their walk-in has them, playing; the legs of those who walk step. */
    fun play(t: Double) {
        val walks = ENTRANCES.mapValues { (player, entrance) ->
            enter(rig = rig(player), entrance = entrance, home = PLACES.getValue(player), t = t)
        }

        // the string players and the marimba player walk as they stand, the legs stepping
        for ((player, stringPlayer) in strings) {
            stringPlayer.update(t)
            stepLegs(rig(player), walks.getValue(player))
        }

        marimba.update(t)
        stepLegs(rig(Player.Rune), walks.getValue(Player.Rune))

        // the frontwoman: put where her walk-in has her; once there she is her own (her place, her strolls)
        val herRig = rig(Player.Sirene)
        val herWalk = walks.getValue(Player.Sirene)
        val her = if (herWalk.since < 0) {
            TrumpetPlayer.Walk(at = herRig.root.position.clone(), turn = herRig.root.rotation.y, speed = 0.0, going = 0.0)
        } else {
            null
        }

        frontwoman.update(t, her)
        stepLegs(herRig, herWalk)

        // the drummer walks in with the sticks in his hands, and sits down once he is at the stool
        drummer.update(t)

        val drumRig = rig(Player.Wucht)
        val drumWalk = walks.getValue(Player.Wucht)
        val sit = smooth(drumWalk.since / 1.6)

        if (sit < 1) {
            val seated = capture(drumRig)

            walkPose(drumRig, drumWalk)
            blend(rig = drumRig, a = capture(drumRig), b = seated, w = sit)

            for (side in Side.entries) {
                val d = drumRig.axisOf("hand$side")
                val stick = drummer.sticks.getValue(side)

                stick.position.copy(drumRig.bone("hand$side").getWorldPosition(vec())).addScaledVector(d, drummer.stickLength * 0.22)
                stick.quaternion.setFromUnitVectors(Y_AXIS, d)
            }
        }
    }
}
