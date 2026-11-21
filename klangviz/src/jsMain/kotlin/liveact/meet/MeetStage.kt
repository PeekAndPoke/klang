/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.meet

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.stage.BlurLook
import io.peekandpoke.klang.viz.liveact.stage.MirrorFloor
import io.peekandpoke.klang.viz.liveact.stage.RippleLook
import io.peekandpoke.klang.viz.liveact.stage.StoneLook
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.ShadowMapType
import io.peekandpoke.klang.workshop.threejs.ToneMapping
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.emissive
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.toneMapping
import io.peekandpoke.klang.workshop.threejs.translate
import io.peekandpoke.klang.workshop.threejs.tuneShadow
import io.peekandpoke.klang.workshop.threejs.uniform
import io.peekandpoke.kraft.addons.threejs.js.Group
import io.peekandpoke.kraft.addons.threejs.js.MaterialParameters
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.addons.threejs.js.SpotLight
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import io.peekandpoke.kraft.addons.threejs.js.WebGLRenderer
import io.peekandpoke.kraft.utils.jsObject
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

/**
 * The room of "Meet the Band" ([MeetLayout]), built into [scene]: the show's black mirror floor ([MirrorFloor], in its
 * new look: marble, a gentle ripple, a crisp reflection), the carousel (the hexagon of fence panels, [HexFence], and a
 * small lamp fixture before each member's feet, all turning together: [turn]), and the one lamp that casts the
 * shadows, fixed in the world: low on the floor before the front place, aimed up at whoever stands there, so their
 * shadow lands large on the surface behind the fence. Sets on [renderer] what the room needs: the shadows and the
 * film's curve, as the show's stage sets them.
 *
 * The floor lies where the show's stage lies: the mirror reflects only within the stage's outline (`StageEdge`), and
 * the front place is at the world's origin, the stage's middle, so all of the floor the camera sees mirrors.
 *
 * Each frame: [turn]. When the room goes: [dispose] (the mirror's pictures and materials); the rest is in the scene
 * and is freed with it (the lamp's shadow map with it).
 */
class MeetStage(scene: Scene, renderer: WebGLRenderer) {

    /** The carousel's turning part: the hexagon and the fixtures. */
    private val carousel: Group = Three.addon.createGroup()

    private val fence: HexFence

    /** Each member's fixture's glowing top, by who stands behind it ([turn] sets how brightly it glows). */
    private val bulbs: Map<Player, MeshStandardMaterial>

    /** The lamp that casts the shadows. */
    private val lamp: SpotLight

    private val mirror: MirrorFloor

    /** Where the lamp shines from, in the world: what the shadows on the surface behind are thrown from. */
    val lampAt: Vector3 = vec(0.0, MeetLayout.LAMP_HIGH, MeetLayout.MIDDLE_Z + MeetLayout.LAMP)

    /** Where the surface behind the front member stands, along z: what Sirene's eyes in her shadow are thrown onto. */
    val surfaceZ: Double = MeetLayout.MIDDLE_Z + MeetLayout.BACK

    init {
        renderer.shadowMap.enabled = true
        renderer.shadowMap.type = ShadowMapType.PCFShadowMap
        renderer.toneMapping = ToneMapping.ACESFilmicToneMapping
        scene.background = Three.addon.createColor(DARK)

        // the floor, plain as the show's stage has it, then mirrored
        val floor = Three.addon.createMesh(
            Three.addon.createPlaneGeometry(FLOOR, FLOOR),
            Three.addon.createMeshStandardMaterial(jsObject {
                color = 0x0b0c10
                roughness = 0.35
                metalness = 0.2
            }),
        )

        floor.rotation.x = -PI / 2
        floor.receiveShadow = true
        scene.add(floor)

        mirror = MirrorFloor(floor = floor, scene = scene, renderer = renderer, outline = uniform(0.0)) { }
        mirror.ripple(RippleLook.Gentle)
        mirror.blur(BlurLook.Crisp)
        mirror.stone(StoneLook.Marble)

        carousel.position.set(0.0, 0.0, MeetLayout.MIDDLE_Z)
        scene.add(carousel)
        fence = HexFence(carousel)
        fence.glow(GLOW)
        bulbs = fixtures()

        lamp = Three.addon.createSpotLight(
            color = LAMP_COLOR,
            intensity = LAMP_LIGHT,
            distance = 0.0,
            angle = LAMP_ANGLE,
            penumbra = 0.75,
            decay = 1.0,
        )
        lamp.position.copy(lampAt)
        lamp.target.position.set(0.0, AIM_HIGH, surfaceZ)
        lamp.castShadow = true
        lamp.tuneShadow(mapSize = SHADOW_MAP, bias = -0.0006, near = 0.2, far = 12.0)
        scene.add(lamp)
        scene.add(lamp.target)
        // a breath of blue everywhere, as on the show's stage: the panels and the members at the sides just read
        scene.add(Three.addon.createAmbientLight(color = AMBIENT_COLOR, intensity = AMBIENT))
    }

    /**
     * The carousel at [turn] (radians, [MeetLayout.turnFor]): the hexagon and the fixtures turned, each fixture glowing
     * as near to the front as its member is ([MeetLayout.glow]), and the lamp dipping a little between two places
     * ([MeetLayout.dip]).
     */
    fun turn(turn: Double) {
        carousel.rotation.y = turn
        lamp.intensity = LAMP_LIGHT * MeetLayout.dip(turn)

        for ((player, bulb) in bulbs) {
            bulb.emissiveIntensity = BULB * (BULB_LEAST + (1.0 - BULB_LEAST) * MeetLayout.glowOf(player = player, turn = turn))
        }
    }

    /** Frees the mirror's pictures and materials (the floor gets its plain one back, freed with the scene). Call it once. */
    fun dispose() {
        mirror.dispose()
    }

    /**
     * The fixtures, one before each member's feet, in the carousel: a small dark foot and a glowing top. They cast no
     * shadow: the lamp that casts the shadows shines from as low, farther out ([MeetLayout.LAMP]).
     */
    private fun fixtures(): Map<Player, MeshStandardMaterial> {
        val steel = Three.addon.createMeshStandardMaterial(jsObject {
            color = 0x1c1a18
            roughness = 0.35
            metalness = 0.8
        })
        val foot = Three.addon.createCylinderGeometry(radiusTop = 0.045, radiusBottom = 0.07, height = FOOT, radialSegments = 20)
        val top = Three.addon.createSphereGeometry(radius = BULB_RADIUS, widthSegments = 20, heightSegments = 12)

        foot.translate(x = 0.0, y = FOOT / 2, z = 0.0)

        return MeetLayout.ORDER.associateWith { player ->
            val angle = MeetLayout.angleOf(player)
            val glow = Three.addon.createMeshStandardMaterial(jsObject<MaterialParameters> {
                color = 0x120d08
                roughness = 0.3
            })

            glow.emissive.setHex(BULB_COLOR)
            glow.emissiveIntensity = 0.0

            val base = Three.addon.createMesh(foot, steel)
            val bulb = Three.addon.createMesh(top, glow)

            base.position.set(MeetLayout.FIXTURE * sin(angle), 0.0, MeetLayout.FIXTURE * cos(angle))
            bulb.position.set(MeetLayout.FIXTURE * sin(angle), FOOT + BULB_RADIUS * 0.7, MeetLayout.FIXTURE * cos(angle))
            carousel.add(base)
            carousel.add(bulb)

            glow
        }
    }

    private companion object {
        /** The dark behind everything: the show's, a deep night blue the panels at the sides stand against. */
        const val DARK = 0x020308

        /** The floor's size each way, in metres: past all the camera sees. */
        const val FLOOR = 60.0

        /** How bright the amber glow on the surfaces behind is ([HexFence.glow]): faint. */
        const val GLOW = 0.04

        /**
         * The lamp: its colour, its light at full, how wide its cone is (radians, from its axis), its shadow map. The
         * light and the cone go with how far the lamp stands from the surface behind ([REACH]), so the circle it
         * lights there stays [CIRCLE] wide each way and as bright wherever the lamp stands.
         */
        const val LAMP_COLOR = 0xffa45a
        const val REACH = MeetLayout.LAMP - MeetLayout.BACK
        const val CIRCLE = 1.75
        const val LAMP_LIGHT = 3.125 * REACH
        val LAMP_ANGLE = atan(CIRCLE / REACH)
        const val SHADOW_MAP = 2048

        /** The faint light everywhere: its colour and how bright (the show's). */
        const val AMBIENT_COLOR = 0x8090ff
        const val AMBIENT = 0.05

        /** How high on the surface behind the front member the lamp is aimed, in metres. */
        const val AIM_HIGH = 2.1

        /** A fixture: its foot's height, its top's size and colour, how bright it glows at the front and at the least. */
        const val FOOT = 0.12
        const val BULB_RADIUS = 0.04
        const val BULB_COLOR = 0xffb066
        const val BULB = 3.0
        const val BULB_LEAST = 0.03
    }
}
