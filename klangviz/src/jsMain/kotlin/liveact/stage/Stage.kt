/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.band.common.dressLevel
import io.peekandpoke.klang.viz.liveact.band.common.wall
import io.peekandpoke.klang.viz.liveact.lights.Lights
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.ToneMapping
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.createFog
import io.peekandpoke.klang.workshop.threejs.depthWrite
import io.peekandpoke.klang.workshop.threejs.fog
import io.peekandpoke.klang.workshop.threejs.getWorldPosition
import io.peekandpoke.klang.workshop.threejs.scale
import io.peekandpoke.klang.workshop.threejs.toneMapping
import io.peekandpoke.kraft.addons.threejs.js.CanvasTexture
import io.peekandpoke.kraft.addons.threejs.js.MaterialParameters
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.Object3D
import io.peekandpoke.kraft.addons.threejs.js.PerspectiveCamera
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.addons.threejs.js.Texture
import io.peekandpoke.kraft.addons.threejs.js.WebGLRenderer
import io.peekandpoke.kraft.utils.jsObject
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.PI
import kotlin.math.min

// The stage: floor, the wall behind it, the lamps, the drummer's riser. Nothing here knows the band.

/**
 * The stage, built into [scene]: floor, the wall behind, the lamps ([Lights]), the drummer's [riser], the crowd's
 * lights and the fog on the floor. Sets on [renderer], [scene] and [camera] what the show needs of them: the
 * shadows, the tone mapping, the dark and the distance fog, the camera's lens.
 *
 * Each frame: [light]. Once the band stands: [shade]. When the stage goes: [dispose].
 */
class Stage(
    private val show: Show,
    private val scene: Scene,
    private val camera: PerspectiveCamera,
    private val renderer: WebGLRenderer,
) {
    /** The wall's material: black unless the front lamp is on. */
    private val backMaterial: MeshStandardMaterial

    /** Sets the wall's face: the ornament under the back light, the wire mesh under the front lamp (0 to 1). */
    private val wallFront: (Double) -> Unit

    private val lights: Lights

    /** The drummer's riser. */
    val riser: Mesh

    private val crowd: Crowd

    /** The fog on the floor; none with ?nofog. */
    private val groundFog: GroundFog?

    init {
        renderer.setPixelRatio(min(window.devicePixelRatio, 2.0))
        renderer.shadowMap.enabled = true
        // three.js's PCFSoftShadowMap, as the prototype asks for it. r186 removed it: the renderer warns and uses the
        // plain PCF map. Kept at the prototype's value all the same, so both draw the same.
        renderer.shadowMap.type = PCF_SOFT_SHADOW_MAP
        renderer.toneMapping = ToneMapping.ACESFilmicToneMapping

        scene.background = Three.addon.createColor(0x020308)
        scene.fog = Three.addon.createFog(color = 0x020308, near = 9.0, far = 22.0)
        // The lens and its limits; the aspect is the picture's, as the component that makes the camera sets it
        camera.fov = 35.0
        camera.near = 0.1
        camera.far = 60.0
        camera.updateProjectionMatrix()

        val floor = Three.addon.createMesh(
            Three.addon.createPlaneGeometry(240.0, 240.0),
            Three.addon.createMeshStandardMaterial(jsObject {
                color = 0x0b0c10
                roughness = 0.35
                metalness = 0.2
            }),
        )
        floor.rotation.x = -PI / 2
        floor.receiveShadow = true
        scene.add(floor)

        // A wall behind the stage, well clear of the riser: it takes the front lamp's light and the band's shadows. It
        // has two faces (see wall()): an ornament in its own faint glow under the back light, a wire mesh under the front
        // lamp. It takes no light from the other lamps (its colour is black unless the front lamp is on), and the fog
        // does not dim it.
        backMaterial = Three.addon.createMeshStandardMaterial(jsObject {
            color = 0x000000
            roughness = 0.95
        })
        backMaterial.fog = false
        val back = Three.addon.createMesh(Three.addon.createPlaneGeometry(60.0, 16.0), backMaterial)
        back.position.set(0.0, 8.0, -8.0)
        back.receiveShadow = true
        wallFront = wall(backMaterial, amount = 0.014)
        scene.add(back)

        lights = Lights(show, scene)

        riser = Three.addon.createMesh(
            Three.addon.createBoxGeometry(2.9, RISER, 2.2),
            Three.addon.createMeshStandardMaterial(jsObject {
                color = 0x08090c
                roughness = 0.5
                metalness = 0.2
            }),
        )
        riser.position.set(0.0, RISER / 2, -1.75)
        riser.castShadow = true
        riser.receiveShadow = true
        scene.add(riser)

        crowd = Crowd(scene)
        // fog on the floor (?nofog: none)
        groundFog = if (show.switches.has("nofog")) null else GroundFog(scene)
    }

    /**
     * Sets every lamp for this frame at [t]: [house] 0 dark to 1; [waiting]: before the click. Then the fog, which
     * the lamps light, and the crowd.
     */
    fun light(t: Double, house: Double, waiting: Boolean) {
        lights.mood(t)
        val front = lights.frontAt(t, waiting)
        backMaterial.color.setHex(0x8a8f9c).scale(front.level)
        wallFront(min(1.0, front.level))
        dressLevel = min(1.0, front.level)
        lights.shine(t, house, front)
        groundFog?.update(t, lights.lamps, camera)

        // the crowd moves with the music: still while it waits, and by how loud it is once it plays
        val sound = if (waiting || t < 0) null else show.energyAt(t)
        crowd.update(
            t = t,
            height = renderer.domElement.height.toDouble(),
            energy = if (sound != null) min(1.0, sound.slow) else 0.0,
            low = if (sound != null) min(1.0, sound.low) else 0.0,
            beat = t * show.bpm / 60,
        )
    }

    /**
     * What stands on the floor darkens it right below itself, whatever the lamps do: the kit and the riser. None
     * under the players' feet (the maintainer, 2026-11-15): the lamps' own shadows hold them on the floor.
     */
    fun shade(kit: Object3D) {
        // a canvas of its own, never put into the page: the texture is drawn from it
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = 128
        canvas.height = 128
        val g = canvas.getContext("2d") as CanvasRenderingContext2D
        val fill = g.createRadialGradient(64.0, 64.0, 4.0, 64.0, 64.0, 64.0)
        fill.addColorStop(0.0, "rgba(0,0,0,0.85)")
        fill.addColorStop(0.5, "rgba(0,0,0,0.5)")
        fill.addColorStop(1.0, "rgba(0,0,0,0)")
        g.fillStyle = fill
        g.fillRect(0.0, 0.0, 128.0, 128.0)
        val texture = Three.addon.createCanvasTexture(canvas)

        shadeUnder(over = kit, width = 2.2, depth = 1.9, forward = 0.5, floorY = RISER, index = 0, texture = texture)
        shadeUnder(over = riser, width = 3.6, depth = 2.9, forward = 0.0, floorY = 0.0, index = 1, texture = texture)
    }

    /**
     * The dark under [over]: [width] by [depth] metres, [forward] of its middle, on the floor at [floorY]; the
     * [index]th lies a hair above the ones before.
     */
    private fun shadeUnder(
        over: Object3D,
        width: Double,
        depth: Double,
        forward: Double,
        floorY: Double,
        index: Int,
        texture: CanvasTexture,
    ) {
        val material = Three.addon.createMeshBasicMaterial(jsObject {
            map = texture
            transparent = true
            opacity = 0.38
        })
        material.depthWrite = false
        val mesh = Three.addon.createMesh(Three.addon.createPlaneGeometry(1.0, 1.0), material)
        mesh.rotation.x = -PI / 2
        mesh.scale.set(width, depth, 1.0)
        val at = over.getWorldPosition(vec())
        mesh.position.set(at.x, floorY + 0.004 + index * 0.0004, at.z + forward)
        scene.add(mesh)
    }

    /** Stops what the stage listens for: the key f, which tries the front lamp's colours. */
    fun dispose() {
        lights.dispose()
    }

    companion object {
        /** The drummer sits on a riser at the back, so the front of the stage is free and he is seen over the others. */
        const val RISER = 0.40

        /** three.js's PCFSoftShadowMap: not in the workshop's ShadowMapType, as r186 removed it. */
        private const val PCF_SOFT_SHADOW_MAP = 2
    }
}

// TYPING: belongs in the workshop
/** A material's texture. */
private var MaterialParameters.map: Texture
    get() = asDynamic().map.unsafeCast<Texture>()
    set(value) {
        asDynamic().map = value
    }
