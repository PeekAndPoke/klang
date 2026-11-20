/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.drums

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.band.figure.Side
import io.peekandpoke.klang.viz.liveact.lastIndexAt
import io.peekandpoke.klang.viz.liveact.song.Drum
import io.peekandpoke.klang.viz.liveact.song.DrumHit
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.addScaledVector
import io.peekandpoke.klang.workshop.threejs.clone
import io.peekandpoke.klang.workshop.threejs.colorSpace
import io.peekandpoke.klang.workshop.threejs.createCircleGeometry
import io.peekandpoke.klang.workshop.threejs.createConeGeometry
import io.peekandpoke.klang.workshop.threejs.createCylinderGeometry
import io.peekandpoke.klang.workshop.threejs.emissive
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.isMesh
import io.peekandpoke.klang.workshop.threejs.map
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
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.CanvasTextAlign
import org.w3c.dom.CanvasTextBaseline
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.LEFT
import org.w3c.dom.MIDDLE
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin

// The drum kit itself: the stool, the drums, the cymbals, the kick with the band's name on it, and the sticks.
// Every place is in the figure's own measures (its leg's length), so the kit fits whoever sits there.

/**
 * The kit's materials, made together and in this order the first time a kit is built (three.js numbers its
 * materials as they are made, and draws in that order where nothing else decides).
 */
internal object KitMaterials {
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

// The kick and the snare light up when hit and fade as the sound does; the eye reads light steeply, so the falls are
// short. Not the whole drum (the maintainer, 2026-11-20: "It looks 'cheap' when the whole thing lights up"): on the
// kick's front head only the ring and the band's name (the head's glow is read through a picture of just those), on
// the snare only its two hoops, the head and the shell stay dark. Thin lines carry little light, so their peaks are
// high where the whole face's was 0.03; the snare's hoops are pale and catch the eye sooner, so theirs is below the kick's
// (the maintainer, 2026-11-20: "Kick highlight is good as is. Snare highlight is a bit too bright compared to the kick").
private val KICK_GLOW = DrumGlow(color = 0xffa050, peak = 1.2, fade = 0.09)
private val SNARE_GLOW = DrumGlow(color = 0xffe8c8, peak = 0.9, fade = 0.07)

/** A snare hoop's height, as a share of the figure's scale; and how far it stands out from the shell. */
private const val HOOP_HEIGHT = 0.014
private const val HOOP_OUT = 1.035

/** A copy of [material] that can glow as [glow] says, dark for now. */
private fun lit(material: MeshStandardMaterial, glow: DrumGlow): MeshStandardMaterial {
    val m = material.clone()

    m.emissive.setHex(glow.color)
    m.emissiveIntensity = 0.0

    return m
}

/** How much of the picture's width the name on the bass drum takes: the ring round it is 0.86 of it. */
private const val NAME_ROOM = 0.72

/** How large its letters are at most, as a share of the picture's height: a short name does not fill the head. */
private const val NAME_MOST = 0.2

/**
 * A drum head with [name] written across it: the head's own dark grey, a thin ring near its rim, the name in
 * pale capitals. A picture for the head's material. [glowing]: the picture of what lights up when the drum is hit:
 * the ring and the name alone, white on black.
 */
private fun named(name: String, glowing: Boolean = false): Texture {
    val size = 512
    val canvas = document.createElement("canvas") as HTMLCanvasElement

    canvas.width = size
    canvas.height = size

    val g = canvas.getContext("2d") as CanvasRenderingContext2D

    g.fillStyle = if (glowing) "#000000" else "#1a1a20"
    g.fillRect(0.0, 0.0, size.toDouble(), size.toDouble())
    g.strokeStyle = if (glowing) "#ffffff" else "#8f93a3"
    g.lineWidth = 5.0
    g.beginPath()
    g.arc(size / 2.0, size / 2.0, size * 0.43, 0.0, 2 * PI)
    g.stroke()
    g.fillStyle = if (glowing) "#ffffff" else "#c9ccd8"
    g.textAlign = CanvasTextAlign.LEFT
    g.textBaseline = CanvasTextBaseline.MIDDLE

    // The name takes a fixed share of the ring's width whatever font the machine has for "sans-serif": the letters
    // are measured and set one by one, a hair of space between them. (A hair space character between them was as
    // wide as a word space where the font has none, and the name went over the ring: the maintainer's MacBook,
    // 2026-11-17.)
    val letters = name.map { it.toString() }
    val trial = 100.0

    g.font = "bold ${trial}px sans-serif"

    val widths = letters.map { g.measureText(it).width / trial }
    val px = nameSize(widths = widths, room = size * NAME_ROOM, most = size * NAME_MOST)
    var x = (size - px * (widths.sum() + NAME_GAP * (letters.size - 1))) / 2

    g.font = "bold ${px}px sans-serif"

    for ((letter, width) in letters.zip(widths)) {
        g.fillText(letter, x, size / 2.0 + size * 0.01)
        x += px * (width + NAME_GAP)
    }

    val texture = Three.addon.createCanvasTexture(canvas)

    texture.colorSpace = "srgb"
    texture.anisotropy = 8

    return texture
}

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

/**
 * The drum kit, built where the figure [rig] sits to play it: the stool, the drums and the cymbals, the kick with
 * [name] (the band's) on its front head, and the two sticks. A drum lights up when it is hit, a cymbal swings, the
 * kick is pushed (see [answer]).
 */
class Kit(rig: Rig, name: String) {
    private val s: Double = rig.s

    /** The kit: the stage shades it. */
    val group: Group = Three.addon.createGroup()

    private val pieces = mutableMapOf<Drum, KitPiece>()

    /** The stool's height. */
    val seat: Double = 0.50 * s

    /**
     * The feet's places, the ankles: both on the kick's double pedal, the right one at the drum, the left one beside
     * it.
     */
    val feet: Map<Side, Vector3> = mapOf(
        Side.R to vec(-0.13 * s, 0.10 * s, 0.58 * s),
        Side.L to vec(0.20 * s, 0.10 * s, 0.56 * s),
    )

    /** The sticks' length, as the drummer's equipment says ([DRUMS_EQUIPMENT]). */
    val stickLength: Double = DRUMS_EQUIPMENT.length * s

    /** The sticks, one in each hand: they stand on the stage beside the figure, not in its skeleton. */
    val sticks: Map<Side, Mesh>

    init {
        val stage = rig.root.parent ?: error("The drummer's figure stands on no stage")

        // the kit stands where he plays, whether he sits there yet or not
        stage.add(group)
        group.position.copy(rig.root.position)
        group.rotation.y = rig.root.rotation.y

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
        group.add(stool)
        group.add(leg)

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
        // only the ring and the name light up when it is hit
        frontMaterial.asDynamic().emissiveMap = named(name = name, glowing = true)
        group.add(kick)
        group.add(front)

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

            // a stick stands on the stage, not in the kit's group: it casts its shadow by its own flag
            stick.castShadow = true
            stage.add(stick)

            stick
        }

        group.traverse { o ->
            if (o.isMesh) {
                o.castShadow = true
            }
        }
    }

    /** Where a stick lands on [drum], in the figure's space. */
    fun at(drum: Drum): Vector3 = pieces.getValue(drum).at

    /** A drum [name], its head's middle [at], its [radius], the [height] of its shell and its [tilt] toward the player. */
    private fun drum(name: Drum, at: KitSpot, radius: Double, height: Double, tilt: Double) {
        val piece = Three.addon.createGroup()

        piece.position.set(at.x * s, at.y * s, at.z * s)
        piece.rotation.x = -tilt

        val snare = name == Drum.Snare
        val bodyMaterial = KitMaterials.shell
        val body = Three.addon.createMesh(
            cylinder(
                radiusTop = radius * s, radiusBottom = radius * s, height = height * s, segments = 28,
            ),
            bodyMaterial,
        )
        body.position.y = -height * s / 2

        val headMaterial = KitMaterials.skin
        val head = Three.addon.createMesh(
            Three.addon.createCircleGeometry(radius = radius * s * 0.94, segments = 28),
            headMaterial,
        )
        head.rotation.x = -PI / 2
        head.position.y = 0.002
        piece.add(body)
        piece.add(head)

        // the snare's two hoops, at the head's rim and at the shell's foot: they are what lights up when it is hit
        val hoopMaterial = if (snare) lit(KitMaterials.steel, SNARE_GLOW).also { it.side = MaterialSide.DoubleSide } else null

        if (hoopMaterial != null) {
            for (y in listOf(-HOOP_HEIGHT * s / 2, -height * s + HOOP_HEIGHT * s / 2)) {
                val hoop = Three.addon.createMesh(
                    Three.addon.createCylinderGeometry(
                        radiusTop = radius * s * HOOP_OUT, radiusBottom = radius * s * HOOP_OUT, height = HOOP_HEIGHT * s,
                        radialSegments = 28, heightSegments = 1, openEnded = true, thetaStart = 0.0, thetaLength = 2 * PI,
                    ),
                    hoopMaterial,
                )

                hoop.position.y = y
                piece.add(hoop)
            }
        }

        if (name != Drum.Floor) {
            val stand = Three.addon.createMesh(
                cylinder(radiusTop = 0.012 * s, radiusBottom = 0.012 * s, height = at.y * s, segments = 6),
                KitMaterials.steel,
            )
            stand.position.set(at.x * s, at.y * s / 2 - height * s * 0.5, at.z * s + 0.02)
            group.add(stand)
        }

        group.add(piece)

        pieces[name] = KitPiece(
            mesh = piece,
            at = vec(at.x * s, at.y * s, at.z * s),
            answer = KitAnswer.Still,
            lights = hoopMaterial?.let { DrumLights(materials = listOf(it), glow = SNARE_GLOW) },
        )
    }

    /** A cymbal [name] at [at], its [radius] and its [tilt]. */
    private fun cymbal(name: Drum, at: KitSpot, radius: Double, tilt: Double) {
        val piece = Three.addon.createGroup()

        piece.position.set(at.x * s, at.y * s, at.z * s)

        val disc = Three.addon.createMesh(cone(radius = radius * s, height = 0.025 * s), KitMaterials.brass)
        piece.add(disc)

        if (name == Drum.Hat || name == Drum.Hat2) {
            val lower = Three.addon.createMesh(cone(radius = radius * s, height = 0.025 * s), KitMaterials.brass)
            lower.rotation.x = PI
            // the open one: a gap between its two cymbals
            lower.position.y = (if (name == Drum.Hat) -0.03 else -0.075) * s
            piece.add(lower)
        }

        val stand = Three.addon.createMesh(
            cylinder(radiusTop = 0.01 * s, radiusBottom = 0.01 * s, height = at.y * s, segments = 6),
            KitMaterials.steel,
        )
        stand.position.set(at.x * s, at.y * s / 2, at.z * s)
        group.add(piece)
        group.add(stand)

        // a cymbal is hit near its edge, the side toward the player
        val edge = vec(at.x * s, at.y * s, at.z * s)
        val toward = vec(sign(at.x) * 0.25 - at.x, 0.0, 0.1 - at.z).normalize()
        edge.addScaledVector(toward, radius * s * 0.75)

        pieces[name] = KitPiece(mesh = piece, at = edge, answer = KitAnswer.Swing(tilt = tilt))
    }

    /**
     * The kit at [t] (seconds), answering [hits] (sorted by time): what was hit lately lights up, a cymbal swings,
     * the kick is pushed.
     */
    fun answer(t: Double, hits: List<DrumHit>) {
        for (piece in pieces.values) {
            piece.hitT = -9.0
        }

        var i = hits.lastIndexAt(t)

        while (i >= 0 && hits[i].t > t - 1.5) {
            val h = hits[i]
            val d = pieces.getValue(h.drum)

            if (d.hitT < 0) {
                d.hitT = h.t
                d.v = h.v
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
    }

    companion object {
        private fun cylinder(radiusTop: Double, radiusBottom: Double, height: Double, segments: Int): BufferGeometry =
            Three.addon.createCylinderGeometry(
                radiusTop = radiusTop, radiusBottom = radiusBottom, height = height, radialSegments = segments,
            )

        /** A cymbal's cone: open, so it is a thin shell of brass. */
        private fun cone(radius: Double, height: Double): BufferGeometry =
            Three.addon.createConeGeometry(radius = radius, height = height, radialSegments = 32, heightSegments = 1, openEnded = true)
    }
}
