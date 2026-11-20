/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.band.dress.dressLevel
import io.peekandpoke.klang.viz.liveact.lights.Lights
import io.peekandpoke.klang.viz.liveact.lights.ShadowQuality
import io.peekandpoke.klang.viz.liveact.theatre.Crowd
import io.peekandpoke.klang.viz.liveact.theatre.CrowdPlaces
import io.peekandpoke.klang.viz.liveact.theatre.NightSky
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.ToneMapping
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.createFog
import io.peekandpoke.klang.workshop.threejs.depthWrite
import io.peekandpoke.klang.workshop.threejs.fog
import io.peekandpoke.klang.workshop.threejs.getPixelRatio
import io.peekandpoke.klang.workshop.threejs.getWorldPosition
import io.peekandpoke.klang.workshop.threejs.map
import io.peekandpoke.klang.workshop.threejs.toneMapping
import io.peekandpoke.kraft.addons.threejs.js.CanvasTexture
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.Object3D
import io.peekandpoke.kraft.addons.threejs.js.PerspectiveCamera
import io.peekandpoke.kraft.addons.threejs.js.Scene
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
 * lights, the night sky and the fog on the floor; with `?stage=mesh` ([StageLook.Mesh]) a wire-mesh [Fence] in place
 * of the wall, with the sun behind the drummer, and the fog as a haze behind the mesh; with `?floor=mirror`
 * ([FloorLook.Mirror]) the floor as polished stone that mirrors the stage ([MirrorFloor]). The look as [variants] says
 * (the address's, without them), the fog at [fog]'s quality, the lamps' shadows at [shadows]'. Sets on [renderer],
 * [scene] and [camera] what the show needs of them: the shadows, the tone mapping, the dark and the distance fog, the
 * camera's lens.
 *
 * Each frame: [light], and [follow] once the camera is placed. Once the band stands: [shade]. The fog at another
 * quality, while the show plays: [fog]; the shadows: [shadows]; the look behind the band: [look]; the floor: [floor].
 * When the stage goes: [dispose].
 */
class Stage(
    private val show: Show,
    private val scene: Scene,
    private val camera: PerspectiveCamera,
    private val renderer: WebGLRenderer,
    fog: FogQuality = FogQuality.of(show.switches),
    shadows: ShadowQuality = ShadowQuality.of(show.switches),
    variants: LookVariants = LookVariants.of(show.switches),
) {
    /** What stands behind the band and the floor, as chosen now. */
    private var variants: LookVariants = variants

    /** The floor, plain or (with [mirror]) polished. */
    private val floor: Mesh

    /** The wall behind the band, where the look is the wall; none where the [fence] stands. */
    private var wall: BackWall? = null

    /** The wire-mesh fence in place of the wall, where the look is the fence; none else. */
    private var fence: Fence? = null

    /** The mirror in the floor, where it is chosen and the fog's quality lets it be drawn ([LookVariants.mirrors]). */
    private var mirror: MirrorFloor? = null

    /** The sun's colour behind the fence, set each frame. */
    private val sunColor = Three.addon.createColor(0xffffff)

    private val lights: Lights

    /** The drummer's riser. */
    val riser: Mesh

    private val crowd: Crowd

    /** The night sky over the theatre; none with ?nosky. */
    private val sky: NightSky?

    /** The quality the fog on the floor is drawn at. */
    private var fogQuality: FogQuality = fog

    /** The fog on the floor; none at [FogQuality.Off] (`?fog=off`, `?nofog`). */
    private var groundFog: GroundFog?

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
        camera.far = FAR
        camera.updateProjectionMatrix()

        floor = Three.addon.createMesh(
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

        buildBack()

        lights = Lights(show = show, scene = scene, shadows = shadows)

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
        sky = if (show.switches.has(Switch.NoSky)) null else NightSky(scene)
        groundFog = makeFog(fog)
        mirrorAsChosen()
    }

    /** What stands behind the band as [variants] says: the wall ([BackWall]) or the fence ([Fence]). */
    private fun buildBack() {
        when (variants.stage) {
            StageLook.Wall -> wall = BackWall(scene)
            StageLook.Mesh -> fence = Fence(scene)
        }
    }

    /**
     * What stands behind the band turned to [to], at once, where it is another than now: the old wall or fence leaves
     * the scene with all it holds freed, the new one is built, and the fog is made anew where it lies there (on the
     * floor before the wall, behind the fence). The first frame with a look not drawn before builds its materials
     * (a short hitch).
     */
    fun look(to: StageLook) {
        if (to == variants.stage) {
            return
        }

        variants = variants.copy(stage = to)
        wall?.dispose()
        wall = null
        fence?.dispose()
        fence = null
        buildBack()
        groundFog?.dispose()
        groundFog = null
        groundFog = makeFog(fogQuality)
    }

    /**
     * The floor turned to [to], at once: the mirror is made where it is chosen and the fog's quality lets it be drawn,
     * and freed where not ([mirrorAsChosen]).
     */
    fun floor(to: FloorLook) {
        variants = variants.copy(floor = to)
        mirrorAsChosen()
    }

    /**
     * The mirror in the floor as [variants] and the fog's quality say ([LookVariants.mirrors]): made where it is wanted
     * and there is none, freed where it is not wanted (its picture is freed with it, not kept hidden).
     */
    private fun mirrorAsChosen() {
        val wanted = variants.mirrors(fogQuality)
        val now = mirror

        if (wanted && now == null) {
            // the fog on the floor lies on the mirror: it is not mirrored as well; nor is the sky, whose stars would
            // glitter in it like noise
            mirror = MirrorFloor(floor = floor, scene = scene, renderer = renderer) { hidden ->
                sky?.hide(hidden)

                if (variants.stage == StageLook.Wall) {
                    groundFog?.hide(hidden)
                }
            }
        }

        if (!wanted && now != null) {
            now.dispose()
            mirror = null
        }
    }

    /**
     * The fog on the floor at [quality], where it is another than now: the old fog leaves the scene, with all it
     * holds on the graphics card ([GroundFog.dispose]), and the new one is made (none at [FogQuality.Off]).
     */
    fun fog(quality: FogQuality) {
        if (quality == fogQuality) {
            return
        }

        fogQuality = quality
        groundFog?.dispose()
        // where the new one cannot be made, no freed fog is left to draw
        groundFog = null
        groundFog = makeFog(quality)
        // the mirror goes where the fog went down to low or off, and comes back with it
        mirrorAsChosen()
    }

    /**
     * The lamps' shadows at [quality] ([Lights.shadows]): a choice by hand, with a short hitch where lamps stop or
     * start casting.
     */
    fun shadows(quality: ShadowQuality) {
        lights.shadows(quality)
    }

    /**
     * A fog at [quality], on the floor or (with the [fence]) behind the mesh: none at [FogQuality.Off]; with
     * `?fullfog` every pixel works it out for itself.
     */
    private fun makeFog(quality: FogQuality): GroundFog? = when (quality) {
        FogQuality.Off -> null

        FogQuality.Low, FogQuality.Medium, FogQuality.High, FogQuality.XHigh -> GroundFog(
            scene = scene,
            renderer = renderer,
            quality = quality,
            mapped = !show.switches.has(Switch.FullFog),
            bed = when (variants.stage) {
                StageLook.Wall -> FogBed.Floor
                StageLook.Mesh -> FogBed.BehindMesh
            },
        )
    }

    /**
     * Sets every lamp for this frame at [t]: [house] 0 dark to 1; [waiting]: before the click. Then the fence and its
     * sun, where it stands, the fog, which the lamps (and the sun) light, and the crowd.
     */
    fun light(t: Double, house: Double, waiting: Boolean) {
        lights.mood(t)
        val front = lights.frontAt(t, waiting)
        wall?.light(front.level)
        dressLevel = min(1.0, front.level)
        lights.shine(t, house, front)

        val fence = fence

        if (fence != null) {
            val sun = lights.sun(t = t, house = house, front = front, into = sunColor)

            fence.light(front = front.level, sun = sunColor, level = sun)
            groundFog?.sun(color = sunColor, level = sun)
        }

        groundFog?.update(t, lights.lamps, camera)

        sky?.update(t = t, ratio = renderer.getPixelRatio())

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

    /** What goes with the camera (the sky): call it once the camera stands where this frame is seen from. */
    fun follow() {
        sky?.follow(camera)
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

    /**
     * Stops what the stage listens for (the key f, which tries the front lamp's colours) and frees what the fog and the
     * mirror hold (the floor has its own material again). The rest is in the scene and is freed with it.
     */
    fun dispose() {
        lights.dispose()
        groundFog?.dispose()
        groundFog = null
        mirror?.dispose()
        mirror = null
    }

    companion object {
        /** The drummer sits on a riser at the back, so the front of the stage is free and he is seen over the others. */
        const val RISER = 0.40

        /**
         * How far the camera sees, in metres. The audience stands up to [CrowdPlaces.LAST] m from the stage's
         * middle, and the camera goes up to some 9 m behind it or to its side (the flight round the band, the
         * reverse total); from the mixing desk the lights at the far sides are some 82 m off. 60 m, as it was at
         * first, cut the last rows off whenever the camera went round (the maintainer, 2026-11-17).
         */
        const val FAR = 110.0

        /** three.js's PCFSoftShadowMap: not in the workshop's ShadowMapType, as r186 removed it. */
        private const val PCF_SOFT_SHADOW_MAP = 2
    }
}
