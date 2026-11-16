/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.common

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.X_AXIS
import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.around
import io.peekandpoke.klang.viz.liveact.lastIndexAt
import io.peekandpoke.klang.viz.liveact.song.Drum
import io.peekandpoke.klang.viz.liveact.song.DrumHit
import io.peekandpoke.klang.viz.liveact.song.Limb
import io.peekandpoke.klang.viz.liveact.song.Part
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.addScaledVector
import io.peekandpoke.klang.workshop.threejs.createCircleGeometry
import io.peekandpoke.klang.workshop.threejs.emissive
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.isMesh
import io.peekandpoke.kraft.addons.threejs.js.BufferGeometry
import io.peekandpoke.kraft.addons.threejs.js.Group
import io.peekandpoke.kraft.addons.threejs.js.MaterialParameters
import io.peekandpoke.kraft.addons.threejs.js.MaterialSide
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.Object3D
import io.peekandpoke.kraft.addons.threejs.js.Texture
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import io.peekandpoke.kraft.utils.jsObject
import kotlinx.browser.document
import org.w3c.dom.CENTER
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.CanvasTextAlign
import org.w3c.dom.CanvasTextBaseline
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.MIDDLE
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

// The drummer: sits on a stool behind a kit and plays a list of hits. Every place is in the figure's own measures
// (its leg's length), so the kit fits whoever sits there.

/**
 * The kit's materials, made together and in this order the first time a kit is built (three.js numbers its
 * materials as they are made, and draws in that order where nothing else decides).
 */
private object KitMaterials {
    val shell: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(jsObject<MaterialParameters> {
        color = 0x08080b
        roughness = 0.3
        metalness = 0.4
    })

    val skin: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(jsObject<MaterialParameters> {
        color = 0x1a1a20
        roughness = 0.6
    })

    val brass: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(jsObject<MaterialParameters> {
        color = 0x4a3a18
        roughness = 0.25
        metalness = 0.95
        side = MaterialSide.DoubleSide
    })

    val wood: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(jsObject<MaterialParameters> {
        color = 0x2a2018
        roughness = 0.6
    })

    val steel: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(jsObject<MaterialParameters> {
        color = 0x15151a
        roughness = 0.3
        metalness = 0.9
    })
}

/**
 * How a drum lights up when hit and fades as the sound does, exponentially: the [color], how bright at the hit
 * ([peak]), and the seconds to 1/e ([fade]).
 */
private class DrumGlow(val color: Int, val peak: Double, val fade: Double)

// The kick's front head and the snare (head and shell: from the audience the head is hardly seen) light up when
// hit and fade as the sound does. The kick's head is a big face, so a little is a lot; the eye reads light steeply,
// so the falls are short.
private val KICK_GLOW = DrumGlow(color = 0xffa050, peak = 0.03, fade = 0.09)
private val SNARE_GLOW = DrumGlow(color = 0xffe8c8, peak = 0.04, fade = 0.07)

/** A copy of [material] that can glow as [glow] says, dark for now. */
private fun lit(material: MeshStandardMaterial, glow: DrumGlow): MeshStandardMaterial {
    val m = material.clone()

    m.emissive.setHex(glow.color)
    m.emissiveIntensity = 0.0

    return m
}

/**
 * A drum head with [name] written across it: the head's own dark grey, a thin ring near its rim, the name in
 * pale capitals. A picture for the head's material.
 */
private fun named(name: String): Texture {
    val size = 512
    val canvas = document.createElement("canvas") as HTMLCanvasElement

    canvas.width = size
    canvas.height = size

    val g = canvas.getContext("2d") as CanvasRenderingContext2D

    g.fillStyle = "#1a1a20"
    g.fillRect(0.0, 0.0, size.toDouble(), size.toDouble())
    g.strokeStyle = "#8f93a3"
    g.lineWidth = 5.0
    g.beginPath()
    g.arc(size / 2.0, size / 2.0, size * 0.43, 0.0, 2 * PI)
    g.stroke()
    g.fillStyle = "#c9ccd8"
    g.textAlign = CanvasTextAlign.CENTER
    g.textBaseline = CanvasTextBaseline.MIDDLE
    g.font = "bold ${size * 0.13}px sans-serif"
    // a hair of space between the letters
    g.fillText(name.toList().joinToString("\u200a"), size / 2.0, size / 2.0 + size * 0.01)

    val texture = Three.addon.createCanvasTexture(canvas)

    texture.colorSpace = "srgb"
    texture.anisotropy = 8

    return texture
}

// TYPING: belongs in the workshop
private var Texture.colorSpace: String
    get() = asDynamic().colorSpace.unsafeCast<String>()
    set(value) {
        asDynamic().colorSpace = value
    }

// TYPING: belongs in the workshop
private var MeshStandardMaterial.map: Texture?
    get() = asDynamic().map.unsafeCast<Texture?>()
    set(value) {
        asDynamic().map = value
    }

// TYPING: belongs in the workshop
private fun MeshStandardMaterial.clone(): MeshStandardMaterial = asDynamic().clone().unsafeCast<MeshStandardMaterial>()

/** A place of the kit in the figure's own measures (times its scale gives the place on the stage). */
private class KitSpot(val x: Double, val y: Double, val z: Double)

/** How a piece of the kit answers a hit, besides its glow. */
private sealed interface KitAnswer {
    /** A cymbal swings about its [tilt]. */
    class Swing(val tilt: Double) : KitAnswer

    /** The kick is pushed toward the audience from [base], its [front] head (resting at [frontZ]) with it. */
    class Push(val base: Vector3, val front: Object3D, val frontZ: Double) : KitAnswer

    /** A drum stands still. */
    data object Still : KitAnswer
}

/** The [materials] of a piece that light up by [glow] when it is hit. */
private class DrumLights(val materials: List<MeshStandardMaterial>, val glow: DrumGlow)

/**
 * A piece of the kit: what moves ([mesh]), where a stick lands on it ([at], the figure's space), how it answers a
 * hit, and what of it lights up when hit ([lights], nothing for most).
 */
private class KitPiece(
    val mesh: Object3D,
    val at: Vector3,
    val answer: KitAnswer,
    val lights: DrumLights? = null,
) {
    /** When it was last hit, within the last moments; -9 where it was not. */
    var hitT: Double = -9.0

    /** How hard it was last hit. It is kept from frame to frame, as the prototype keeps it. */
    var v: Double = 0.0
}

/** One hit of one limb, with the place it lands at. */
private class KitHit(override val t: Double, override val at: Vector3, override val v: Double) : Stroke

/**
 * The drummer of [show]: the figure [rig] on a stool behind a kit, playing [hits] (sorted by time, as far as
 * the song is known). He moves with the music by his [traits], his chance drawn under [seed].
 */
class Drummer(
    private val show: Show,
    private val rig: Rig,
    private val hits: Part<DrumHit>,
    traits: Traits,
    seed: Double = 1.0,
    /** The band's name, written on the bass drum's front head. */
    name: String = "",
) {
    private val mover = Mover(show = show, seed = seed, traits = traits)
    private val s: Double = rig.s
    private var all: List<DrumHit> = emptyList()

    /** The kit: the stage shades it. */
    val kit: Group = Three.addon.createGroup()

    private val pieces = mutableMapOf<Drum, KitPiece>()
    private val byLimb: Map<Limb, MutableList<Stroke>> = Limb.entries.associateWith { mutableListOf() }

    /** The stool's height. */
    private val seat: Double = 0.50 * s

    /**
     * The feet's places, the ankles: both on the kick's double pedal, the right one at the drum, the left one beside
     * it.
     */
    private val feet: Map<Side, Vector3> = mapOf(
        Side.R to vec(-0.13 * s, 0.10 * s, 0.58 * s),
        Side.L to vec(0.20 * s, 0.10 * s, 0.56 * s),
    )

    /** The sticks' length. */
    val stickLength: Double = 0.40 * s

    /** The sticks, one in each hand: they stand on the stage beside the figure, not in its skeleton. */
    val sticks: Map<Side, Mesh>

    init {
        val stage = rig.root.parent ?: error("The drummer's figure stands on no stage")

        // the kit stands where he plays, whether he sits there yet or not
        stage.add(kit)
        kit.position.copy(rig.root.position)
        kit.rotation.y = rig.root.rotation.y

        // the stool
        val stool = Three.addon.createMesh(
            cylinder(radiusTop = 0.17 * s, radiusBottom = 0.17 * s, height = 0.07 * s, segments = 24),
            KitMaterials.shell,
        )
        stool.position.set(0.0, seat - 0.035 * s, -0.02 * s)

        val leg = Three.addon.createMesh(
            cylinder(radiusTop = 0.03 * s, radiusBottom = 0.03 * s, height = seat, segments = 8),
            KitMaterials.steel,
        )
        leg.position.set(0.0, seat / 2, -0.02 * s)
        kit.add(stool)
        kit.add(leg)

        // the drums: where the stick lands (the head's middle), the radius, the height of the shell, the tilt toward
        // the player
        drum(Drum.Snare, at = KitSpot(0.14, 0.66, 0.58), radius = 0.17, height = 0.14, tilt = 0.10)
        drum(Drum.Tom, at = KitSpot(-0.04, 0.84, 0.80), radius = 0.14, height = 0.16, tilt = 0.45)
        drum(Drum.Floor, at = KitSpot(-0.50, 0.60, 0.42), radius = 0.20, height = 0.40, tilt = 0.05)
        cymbal(Drum.Hat, at = KitSpot(0.52, 0.88, 0.56), radius = 0.17, tilt = 0.05)
        // a second hat, standing open, for the open hat's sound: beside the first, a little further out and higher
        cymbal(Drum.Hat2, at = KitSpot(0.80, 0.96, 0.40), radius = 0.16, tilt = 0.10)
        cymbal(Drum.Crash, at = KitSpot(0.55, 1.26, 0.90), radius = 0.22, tilt = 0.25)
        cymbal(Drum.Ride, at = KitSpot(-0.80, 0.86, 0.82), radius = 0.25, tilt = 0.30)

        // the kick: lying, its head toward the audience
        val kick = Three.addon.createMesh(
            cylinder(radiusTop = 0.29 * s, radiusBottom = 0.29 * s, height = 0.40 * s, segments = 32),
            KitMaterials.shell,
        )
        kick.rotation.x = PI / 2
        kick.position.set(-0.06 * s, 0.29 * s, 1.00 * s)

        val frontMaterial = lit(KitMaterials.skin, KICK_GLOW)
        val front = Three.addon.createMesh(Three.addon.createCircleGeometry(radius = 0.27 * s, segments = 32), frontMaterial)
        front.position.set(-0.06 * s, 0.29 * s, 1.201 * s)
        // the band's name on it; the head's own colour is in the picture, so the material's is white
        frontMaterial.color.setHex(0xffffff)
        frontMaterial.map = named(name)
        kit.add(kick)
        kit.add(front)

        pieces[Drum.Kick] = KitPiece(
            mesh = kick,
            at = vec(),
            answer = KitAnswer.Push(base = kick.position.clone(), front = front, frontZ = front.position.z),
            lights = DrumLights(materials = listOf(frontMaterial), glow = KICK_GLOW),
        )

        // the sticks
        sticks = listOf(Side.L, Side.R).associateWith {
            val stick = Three.addon.createMesh(
                cylinder(
                    radiusTop = 0.008 * s, radiusBottom = 0.011 * s, height = stickLength, segments = 8,
                ),
                KitMaterials.wood,
            )

            stage.add(stick)

            stick
        }

        kit.traverse { o ->
            if (o.isMesh) {
                o.castShadow = true
            }
        }

        hits.refresh()
        plan()
    }

    /**
     * Which limb hits what and when, from the hits as far as they are known. Where the song has forgotten its past,
     * each limb's list begins with its last hit forgotten: the stick stays where that hit left it, as it would with
     * the whole song.
     */
    private fun plan() {
        val from = hits.events.firstOrNull()?.t ?: Double.POSITIVE_INFINITY
        val gone = byLimb.mapValues { (_, list) -> list.lastOrNull { it.t < from } }

        all = hits.events

        for ((limb, list) in byLimb) {
            list.clear()
            gone.getValue(limb)?.let { list.add(it) }
        }

        for (h in all) {
            byLimb.getValue(h.limb).add(KitHit(t = h.t, at = pieces.getValue(h.drum).at, v = h.v))
        }
    }

    /** A drum [name], its head's middle [at], its [radius], the [height] of its shell and its [tilt] toward the player. */
    private fun drum(name: Drum, at: KitSpot, radius: Double, height: Double, tilt: Double) {
        val group = Three.addon.createGroup()

        group.position.set(at.x * s, at.y * s, at.z * s)
        group.rotation.x = -tilt

        val snare = name == Drum.Snare
        val bodyMaterial = if (snare) lit(KitMaterials.shell, SNARE_GLOW) else KitMaterials.shell
        val body = Three.addon.createMesh(
            cylinder(
                radiusTop = radius * s, radiusBottom = radius * s, height = height * s, segments = 28,
            ),
            bodyMaterial,
        )
        body.position.y = -height * s / 2

        val headMaterial = if (snare) lit(KitMaterials.skin, SNARE_GLOW) else KitMaterials.skin
        val head = Three.addon.createMesh(
            Three.addon.createCircleGeometry(radius = radius * s * 0.94, segments = 28),
            headMaterial,
        )
        head.rotation.x = -PI / 2
        head.position.y = 0.002
        group.add(body)
        group.add(head)

        if (name != Drum.Floor) {
            val stand = Three.addon.createMesh(
                cylinder(radiusTop = 0.012 * s, radiusBottom = 0.012 * s, height = at.y * s, segments = 6),
                KitMaterials.steel,
            )
            stand.position.set(at.x * s, at.y * s / 2 - height * s * 0.5, at.z * s + 0.02)
            kit.add(stand)
        }

        kit.add(group)

        pieces[name] = KitPiece(
            mesh = group,
            at = vec(at.x * s, at.y * s, at.z * s),
            answer = KitAnswer.Still,
            lights = if (snare) DrumLights(materials = listOf(headMaterial, bodyMaterial), glow = SNARE_GLOW) else null,
        )
    }

    /** A cymbal [name] at [at], its [radius] and its [tilt]. */
    private fun cymbal(name: Drum, at: KitSpot, radius: Double, tilt: Double) {
        val group = Three.addon.createGroup()

        group.position.set(at.x * s, at.y * s, at.z * s)

        val disc = Three.addon.createMesh(cone(radius = radius * s, height = 0.025 * s), KitMaterials.brass)
        group.add(disc)

        if (name == Drum.Hat || name == Drum.Hat2) {
            val lower = Three.addon.createMesh(cone(radius = radius * s, height = 0.025 * s), KitMaterials.brass)
            lower.rotation.x = PI
            // the open one: a gap between its two cymbals
            lower.position.y = (if (name == Drum.Hat) -0.03 else -0.075) * s
            group.add(lower)
        }

        val stand = Three.addon.createMesh(
            cylinder(radiusTop = 0.01 * s, radiusBottom = 0.01 * s, height = at.y * s, segments = 6),
            KitMaterials.steel,
        )
        stand.position.set(at.x * s, at.y * s / 2, at.z * s)
        kit.add(group)
        kit.add(stand)

        // a cymbal is hit near its edge, the side toward the player
        val edge = vec(at.x * s, at.y * s, at.z * s)
        val toward = vec(sign(at.x) * 0.25 - at.x, 0.0, 0.1 - at.z).normalize()
        edge.addScaledVector(toward, radius * s * 0.75)

        pieces[name] = KitPiece(mesh = group, at = edge, answer = KitAnswer.Swing(tilt = tilt))
    }

    /** The drummer and his kit at [t] (seconds). */
    fun update(t: Double) {
        if (hits.refresh()) {
            plan()
        }

        // what was hit: a cymbal swings, a drum dips, and it all feeds the body
        var energy = 0.0
        var snare = 0.0

        for (piece in pieces.values) {
            piece.hitT = -9.0
        }

        var i = all.lastIndexAt(t)

        while (i >= 0 && all[i].t > t - 1.5) {
            val h = all[i]
            val d = pieces.getValue(h.drum)

            if (d.hitT < 0) {
                d.hitT = h.t
                d.v = h.v
            }

            val age = t - h.t
            energy += h.v * exp(-age / 0.35) * (if (h.drum == Drum.Hat) 0.25 else 1.0)

            if (h.drum == Drum.Snare || h.drum == Drum.Kick) {
                snare = max(snare, h.v * exp(-age / 0.12))
            }

            i--
        }

        for (d in pieces.values) {
            val age = t - d.hitT

            d.lights?.let { lights ->
                for (m in lights.materials) {
                    m.emissiveIntensity = if (d.hitT < 0) 0.0 else lights.glow.peak * max(0.6, d.v) * exp(-age / lights.glow.fade)
                }
            }

            when (val answer = d.answer) {
                is KitAnswer.Swing -> {
                    val swing = d.v * 0.22 * exp(-age / 0.5) * sin(age * 22)
                    d.mesh.rotation.set(answer.tilt * 0.5 + swing, 0.0, swing * 0.6)
                }

                is KitAnswer.Push -> {
                    val push = d.v * 0.012 * exp(-age / 0.06)
                    d.mesh.position.z = answer.base.z + push
                    // the head goes with its drum (else the shell hides it, and its light)
                    answer.front.position.z = answer.frontZ + push
                }

                KitAnswer.Still -> {}
            }
        }

        val hands = listOf(Side.L, Side.R).associateWith { side ->
            strokeAt(
                list = byLimb.getValue(side.hand),
                t = t,
                rest = pieces.getValue(if (side == Side.L) Drum.Snare else Drum.Hat).at,
                home = vec(rig.shoulder.getValue(side).x, 0.0, 0.30 * s),
                height = 0.40 * s,
            )
        }
        val left = hands.getValue(Side.L)
        val right = hands.getValue(Side.R)

        rig.reset()
        rig.update()
        // sitting: the pelvis onto the stool, the trunk leaning in, nodding with the kick and the snare
        val nod = mover.nod(t * show.bpm / 60)
        val body = nod + mover.burst()
        val parts = mover.parts
        val bob = 0.012 * s * body * parts.knees + 0.004 * s * snare

        rig.moveHips(vec(0.0, seat + 0.085 * s - rig.pelvis.y - bob, -0.02 * s))
        rig.turn("belly", X_AXIS, 0.19 + 0.05 * body * parts.trunk + 0.015 * snare)
        rig.turn("chest", Y_AXIS, 0.35 * (left.aim.x + right.aim.x) / s)
        rig.turn("chest", X_AXIS, 0.06)
        rig.turn("neck", X_AXIS, 0.06 * nod * parts.head)
        rig.turn("head", X_AXIS, -0.12 + 0.24 * nod * parts.head + 0.02 * snare)
        mover.apply(rig, t)

        // the feet on their pedals; the right one stamps the kick
        for (side in listOf(Side.L, Side.R)) {
            val foot = feet.getValue(side).clone()
            // each foot stamps its own kicks: up just before, down at the hit
            val (_, next) = byLimb.getValue(side.kick).around(t)
            val until = if (next != null) next.t - t else 9.0

            foot.y += 0.05 * s * (if (until < 0.16) sin(PI * (1 - until / 0.16)) else 0.0)
            rig.foot(side, rig.root.localToWorld(foot), rig.dir(0.5 * side.sign, 0.6, 1.0))
        }

        // the hands, and the sticks in them
        for (side in listOf(Side.L, Side.R)) {
            val hand = hands.getValue(side)

            holdStick(
                rig = rig,
                side = side,
                tip = hand.tip,
                bend = hand.bend,
                length = stickLength,
                pole = rig.dir(0.55 * side.sign, -1.0, -0.5),
                mesh = sticks.getValue(side),
            )
        }

        val eyes = rig.eyes

        if (eyes != null) {
            eyes.emissiveIntensity = 2 + 14 * min(1.6, energy)
        }
    }

    private companion object {
        fun cylinder(radiusTop: Double, radiusBottom: Double, height: Double, segments: Int): BufferGeometry =
            Three.addon.createCylinderGeometry(
                radiusTop = radiusTop, radiusBottom = radiusBottom, height = height, radialSegments = segments,
            )

        /** A cymbal's cone: open, so it is a thin shell of brass. */
        fun cone(radius: Double, height: Double): BufferGeometry =
            Three.addon.create("ConeGeometry", radius, height, 32, 1, true)

        /** The hand of a side. */
        val Side.hand: Limb
            get() = when (this) {
                Side.L -> Limb.L
                Side.R -> Limb.R
            }

        /** The foot of a side, on the kick's double pedal. */
        val Side.kick: Limb
            get() = when (this) {
                Side.L -> Limb.KickL
                Side.R -> Limb.KickR
            }
    }
}

/**
 * A test beat for [bars] bars at [bpm], for the page without the recording: hats in eighths, kick and snare, and
 * every fourth bar a fill round the toms into a crash. The kick is the right foot's.
 */
fun testBeat(bars: Int, bpm: Double): List<DrumHit> {
    val hits = mutableListOf<DrumHit>()
    val sec = 60 / bpm

    fun add(beat: Double, drum: Drum, limb: Limb, v: Double) {
        hits.add(DrumHit(t = beat * sec, drum = drum, limb = limb, v = v))
    }

    for (bar in 0 until bars) {
        val b = (bar * 4).toDouble()
        val fill = bar % 4 == 3
        val afterFill = bar % 4 == 0 && bar > 0

        for (i in 0 until (if (fill) 4 else 8)) {
            if (i == 0 && afterFill) {
                add(beat = b, drum = Drum.Crash, limb = Limb.R, v = 1.0)
            } else {
                val drum = if (bar % 8 >= 4) Drum.Ride else Drum.Hat

                add(beat = b + i * 0.5, drum = drum, limb = Limb.R, v = if (i % 2 == 0) 0.8 else 0.45)
            }
        }

        add(beat = b, drum = Drum.Kick, limb = Limb.KickR, v = 1.0)
        add(beat = b + 1, drum = Drum.Snare, limb = Limb.L, v = 0.9)

        if (!fill) {
            add(beat = b + 2, drum = Drum.Kick, limb = Limb.KickR, v = 0.9)
            add(beat = b + 2.5, drum = Drum.Kick, limb = Limb.KickR, v = 0.7)
            add(beat = b + 3, drum = Drum.Snare, limb = Limb.L, v = 1.0)
        } else {
            val order = listOf(Drum.Snare, Drum.Snare, Drum.Tom, Drum.Tom, Drum.Tom, Drum.Floor, Drum.Floor, Drum.Floor)

            order.forEachIndexed { i, drum ->
                add(beat = b + 2 + i * 0.25, drum = drum, limb = if (i % 2 == 0) Limb.R else Limb.L, v = 0.6 + 0.05 * i)
            }

            add(beat = b + 2, drum = Drum.Kick, limb = Limb.KickR, v = 0.9)
            add(beat = b + 3, drum = Drum.Kick, limb = Limb.KickR, v = 0.9)
        }
    }

    return hits.sortedBy { it.t }
}
