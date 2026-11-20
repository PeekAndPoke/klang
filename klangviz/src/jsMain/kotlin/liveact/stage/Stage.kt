/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.band.dress.dressLevel
import io.peekandpoke.klang.viz.liveact.lights.FrontFade
import io.peekandpoke.klang.viz.liveact.lights.FrontLight
import io.peekandpoke.klang.viz.liveact.lights.Lights
import io.peekandpoke.klang.viz.liveact.lights.ShadowQuality
import io.peekandpoke.klang.viz.liveact.lights.WorldFade
import io.peekandpoke.klang.viz.liveact.lights.WorldLight
import io.peekandpoke.klang.viz.liveact.lights.WorldTime
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
import io.peekandpoke.kraft.addons.threejs.js.Vector3
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
 * ([FloorLook.Mirror]) the floor as polished stone that mirrors the stage ([MirrorFloor]); with `?haze=parts`
 * ([HazeLook.Parts]) the haze behind the fence gives way to the band's shadows; with `?world=night` ([WorldLook]) the
 * time of day of the two worlds drives the lamps, the sun, the haze and, behind the fence, the cold world seen through
 * the band's shadows ([WorldLight]), and by night the lamps' light on the wall and the fence leans cold ([WallLean]);
 * with `?underworld=on` ([UnderworldLook]) the mirror floor shows the other world. The look as [variants] says
 * (the address's, without them), the fog at [fog]'s quality, the lamps' shadows at [shadows]'. Sets on [renderer],
 * [scene] and [camera] what the show needs of them: the shadows, the tone mapping, the dark and the distance fog, the
 * camera's lens.
 *
 * Each frame: [light], and [follow] once the camera is placed. Once the band stands: [shade]. The fog at another
 * quality, while the show plays: [fog]; the shadows: [shadows]; the look behind the band: [look]; the floor: [floor];
 * the haze: [haze]; the time of day: [world]; the underworld: [underworld]; all of them at once: [turn]. What
 * the band needs of the front lamp's shadows ([front], [frontLamp], [shadowsAt]): after [light]. Who has to know when
 * the mirror draws ([mirrorDrawing]). When the stage goes: [dispose].
 *
 * What a turn of the look makes is kept while the stage stands, hidden where it is not chosen (the wall and the fence,
 * the fog on the floor and the haze behind the fence, the mirror's material), so turning back costs nothing new: the
 * key x flips the whole look, and only its first turn to each side builds and compiles what that side needs.
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

    /** The wall behind the band, once the look was the wall; hidden while the [fence] stands. */
    private var wall: BackWall? = null

    /** The wire-mesh fence in place of the wall, once the look was the fence; hidden while the wall stands. */
    private var fence: Fence? = null

    /**
     * The mirror in the floor, once it was chosen and the fog's quality let it be drawn ([LookVariants.mirrors]); on the
     * floor only while it is ([MirrorFloor.use]).
     */
    private var mirror: MirrorFloor? = null

    /**
     * Told when the mirror starts drawing the stage (true) and when it is done (false), each frame it draws: for what
     * looks other in the mirror than on the stage (Sirene's eyes in her reflection, in the underworld). None: nobody.
     */
    var mirrorDrawing: ((Boolean) -> Unit)? = null

    /** How far the mirror shows the underworld: it comes in over 2 s, goes over 3 s ([UnderworldLook]). */
    private val underworldFade = FrontFade(rise = UnderworldLook.FADE_IN, fall = UnderworldLook.FADE_OUT)

    /** How far the mirror showed the underworld at the last [light], 0 to 1 (0 where no mirror is drawn). */
    val underworld: Double
        get() = underworldFade.share

    /** The sun's colour behind the fence, set each frame. */
    private val sunColor = Three.addon.createColor(0xffffff)

    private val lights: Lights

    /** The front lamp as the last [light] set it: how much it is on, and whether it strobes. */
    var front: FrontLight = FrontLight.off
        private set

    /**
     * How far the haze has drawn back from the band's shadows (`?haze=parts`): it opens with the front lamp, in a
     * second, and closes again over 1.6 s after the lamp went off (the key f turns the lamp off in one frame).
     */
    private val clearing = FrontFade(rise = 1.0, fall = 1.6)

    /**
     * How far the cold world shows through the band's shadows, by the front lamp (with [WorldLight.beyond] of the time
     * of day): it opens with the lamp, in a second, and closes over 1.6 s after it went off, as the haze's clearings.
     */
    private val opening = FrontFade(rise = 1.0, fall = 1.6)

    /** How long a round of the drift takes (`?drift=30`), in seconds. */
    private val driftPeriod = WorldLook.period(show.switches)

    /** The time of day as the stage shows it: it follows the one chosen at its own pace ([WorldFade]). */
    private val worldFade = WorldFade(start = variants.world.target(t = 0.0, period = driftPeriod))

    /** The time of day of the two worlds as the last [light] set it ([WorldTime.OFF]: the stage as made). */
    var world: WorldTime = worldFade.now
        private set

    /** When the last [light] ran, in milliseconds of the page; none before the first. */
    private var lightAt = Double.NaN

    /** The drummer's riser. */
    val riser: Mesh

    private val crowd: Crowd

    /** The night sky over the theatre; none with ?nosky. */
    private val sky: NightSky?

    /** The quality the fog on the floor is drawn at. */
    private var fogQuality: FogQuality = fog

    /**
     * The fog made so far for each bed: on the floor before the wall, behind the fence; none at [FogQuality.Off]
     * (`?fog=off`, `?nofog`). The one of the bed not chosen now is hidden and not worked out.
     */
    private val fogs = mutableMapOf<FogBed, GroundFog>()

    /** The fog of the look chosen now, where there is one. */
    private val groundFog: GroundFog?
        get() = fogs[bed]

    /** Where the fog lies with the look chosen now. */
    private val bed: FogBed
        get() = when (variants.stage) {
            StageLook.Wall -> FogBed.Floor
            StageLook.Mesh -> FogBed.BehindMesh
        }

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
        fogAsChosen()
        mirrorAsChosen()
    }

    /**
     * What stands behind the band as [variants] says: the wall ([BackWall]) or the fence ([Fence]), built where it was
     * not yet, shown; the other hidden.
     */
    private fun buildBack() {
        when (variants.stage) {
            StageLook.Wall -> if (wall == null) {
                wall = BackWall(scene)
            }

            StageLook.Mesh -> if (fence == null) {
                fence = Fence(scene)
            }
        }

        wall?.show(variants.stage == StageLook.Wall)
        fence?.show(variants.stage == StageLook.Mesh)
    }

    /**
     * The fog as [variants] and its quality say: the one of the chosen bed made where there is none yet, and shown;
     * the other hidden.
     */
    private fun fogAsChosen() {
        val chosen = bed

        if (fogs[chosen] == null) {
            makeFog(quality = fogQuality, bed = chosen)?.let { fogs[chosen] = it }
        }

        for ((at, fog) in fogs) {
            fog.hide(at != chosen)
        }
    }

    /**
     * What stands behind the band turned to [to], at once, where it is another than now: the old wall or fence is
     * hidden, the new one shown, and the fog of where it lies there (on the floor before the wall, behind the fence).
     * What was not made before is made now, and the first frame that draws it builds its materials (a short hitch);
     * turning back later costs nothing new.
     */
    fun look(to: StageLook) {
        if (to == variants.stage) {
            return
        }

        variants = variants.copy(stage = to)
        buildBack()
        fogAsChosen()
    }

    /**
     * The whole look turned to [to] at once (the key x): what stands behind the band, the floor, the haze, the time of
     * day and the underworld. Sirene's eyes and the band's skin are the band's: the live act sets them.
     */
    fun turn(to: LookVariants) {
        look(to.stage)
        floor(to.floor)
        haze(to.haze)
        world(to.world)
        underworld(to.underworld)
    }

    /**
     * The floor turned to [to], at once: the mirror is made (or put back) where it is chosen and the fog's quality lets
     * it be drawn, and taken off the floor where not ([mirrorAsChosen]).
     */
    fun floor(to: FloorLook) {
        variants = variants.copy(floor = to)
        mirrorAsChosen()
    }

    /**
     * The haze behind the fence turned to [to]: it gives way to the band's shadows ([HazeLook.Parts]) or stays whole.
     * Nothing to make or free: the haze reads the front lamp's shadows only while it parts, and closes again over a
     * second or two when it is turned back.
     */
    fun haze(to: HazeLook) {
        variants = variants.copy(haze = to)
    }

    /**
     * The time of day of the two worlds turned to [to] ([WorldLook]): the stage goes there at its own pace, in a second
     * or three ([WorldFade]). Nothing to make or free.
     */
    fun world(to: WorldLook) {
        variants = variants.copy(world = to)
    }

    /**
     * The underworld in the mirror turned to [to] ([UnderworldLook]): it comes over 2 s, or goes over 3 s, where the
     * mirror is drawn. Nothing to make or free.
     */
    fun underworld(to: UnderworldLook) {
        variants = variants.copy(underworld = to)
    }

    /** Where the front lamp stands, in the world. */
    val frontLamp: Vector3
        get() = lights.front.light.position

    /**
     * Where the front lamp's shadows of the band land, in metres along z: on the wall ([BackWall.Z]), or, behind the
     * fence, on the dark surface behind it ([Fence.BACK_Z]).
     */
    val shadowsAt: Double
        get() = when (variants.stage) {
            StageLook.Wall -> BackWall.Z
            StageLook.Mesh -> Fence.BACK_Z
        }

    /**
     * The mirror in the floor as [variants] and the fog's quality say ([LookVariants.mirrors]): made where it is wanted
     * and there is none, put on the floor where it is wanted, taken off where not (its picture is freed then, its
     * material kept).
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

                mirrorDrawing?.invoke(hidden)
            }
        }

        now?.use(wanted)
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

        // every fog made so far is of the old quality: freed, and the chosen one made anew
        val old = fogs.values.toList()

        // where the new one cannot be made, no freed fog is left to draw
        fogs.clear()
        old.forEach { it.dispose() }
        fogAsChosen()
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
     * A fog at [quality], on the floor or behind the mesh as [bed] says: none at [FogQuality.Off]; with `?fullfog`
     * every pixel works it out for itself.
     */
    private fun makeFog(quality: FogQuality, bed: FogBed): GroundFog? = when (quality) {
        FogQuality.Off -> null

        FogQuality.Low, FogQuality.Medium, FogQuality.High, FogQuality.XHigh -> GroundFog(
            scene = scene,
            renderer = renderer,
            quality = quality,
            mapped = !show.switches.has(Switch.FullFog),
            bed = bed,
        )
    }

    /**
     * Sets every lamp for this frame at [t]: [house] 0 dark to 1; [waiting]: before the click; all at the time of day
     * ([world]). Then the fence and its sun, where it stands, the fog, which the lamps (and the sun) light, and the
     * crowd.
     */
    fun light(t: Double, house: Double, waiting: Boolean) {
        val nowMs = window.performance.now()
        // the time since the last frame, for what follows the front lamp at its own pace; a long gap (a hidden page,
        // the first frame) counts as a short one
        val dt = if (lightAt.isNaN()) 0.0 else ((nowMs - lightAt) / 1000).coerceIn(0.0, 0.25)

        lightAt = nowMs
        lights.mood(t)

        val front = lights.frontAt(t, waiting)
        val world = worldFade.follow(target = variants.world.target(t = t, period = driftPeriod), dt = dt)

        this.front = front
        this.world = world

        // the one that stands: the other is hidden and kept as it was
        val wall = if (variants.stage == StageLook.Wall) wall else null
        val fence = if (variants.stage == StageLook.Mesh) fence else null

        wall?.light(front.level)
        wall?.world(world)
        fence?.world(world)
        dressLevel = min(1.0, front.level)
        lights.shine(t = t, house = house, front = front, world = world)

        // the cold world through the shadows opens with the front lamp, as far as the time of day lets it
        val beyond = opening.follow(target = FrontFade.level(front), dt = dt) * WorldLight.beyond(world)

        if (fence != null) {
            val sun = lights.sun(t = t, house = house, front = front, into = sunColor, world = world)
            val size = WorldLight.sunSize(world)
            val height = WorldLight.sunHeight(world)

            fence.light(front = front.level, sun = sunColor, level = sun, size = size, height = height)
            fence.beyond(front = lights.front, share = beyond)
            groundFog?.sun(color = sunColor, level = sun, size = size, height = height)
            groundFog?.world(world)
        }

        // the haze draws back from the band's shadows as much as the lamp is on, where it parts and the fence stands
        val parting = clearing.follow(target = if (variants.partsHaze) FrontFade.level(front) else 0.0, dt = dt)

        if (fence != null) {
            groundFog?.clearings(front = lights.front, share = parting)
        }

        groundFog?.update(t, lights.lamps, camera)

        // the underworld comes and goes in the mirror where it is chosen and the mirror is drawn
        val under = underworldFade.follow(target = if (variants.underworlds(fogQuality)) 1.0 else 0.0, dt = dt)

        mirror?.underworld(under)

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
        fogs.values.forEach { it.dispose() }
        fogs.clear()
        mirror?.dispose()
        mirror = null
        // the wall and the fence are in the scene, freed with it; the one hidden as well
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
