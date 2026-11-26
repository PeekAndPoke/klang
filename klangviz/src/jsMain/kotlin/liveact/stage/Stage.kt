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
import io.peekandpoke.klang.viz.liveact.lights.GhostVisits
import io.peekandpoke.klang.viz.liveact.camera.TOTAL_FROM
import io.peekandpoke.klang.viz.liveact.lights.Lights
import io.peekandpoke.klang.viz.liveact.lights.ShadowQuality
import io.peekandpoke.klang.viz.liveact.lights.WorldCourse
import io.peekandpoke.klang.viz.liveact.lights.WorldFade
import io.peekandpoke.klang.viz.liveact.lights.WorldLight
import io.peekandpoke.klang.viz.liveact.lights.WorldTime
import io.peekandpoke.klang.viz.liveact.theatre.Crowd
import io.peekandpoke.klang.viz.liveact.theatre.CrowdPlaces
import io.peekandpoke.klang.viz.liveact.theatre.NightSky
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.CompiledShader
import io.peekandpoke.klang.workshop.threejs.ToneMapping
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.createFog
import io.peekandpoke.klang.workshop.threejs.depthWrite
import io.peekandpoke.klang.workshop.threejs.fog
import io.peekandpoke.klang.workshop.threejs.getPixelRatio
import io.peekandpoke.klang.workshop.threejs.getWorldPosition
import io.peekandpoke.klang.workshop.threejs.scale
import io.peekandpoke.klang.workshop.threejs.map
import io.peekandpoke.klang.workshop.threejs.onBeforeCompile
import io.peekandpoke.klang.workshop.threejs.toneMapping
import io.peekandpoke.klang.workshop.threejs.uniform
import io.peekandpoke.kraft.addons.threejs.js.CanvasTexture
import io.peekandpoke.kraft.addons.threejs.js.Material
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
import kotlin.math.max
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
 * with `?opening=ember` the show opens in that world before its song, and turns slowly to its own when the song begins
 * ([WorldCourse]); with `?underworld=on` ([UnderworldLook]) the mirror floor shows the other world; with `?around=forest`
 * ([AroundLook], and by turns in the new look) the surroundings frame the stage and the audience ([Around]). The look as [variants] says
 * (the address's, without them), the fog at [fog]'s quality, the lamps' shadows at [shadows]', the mirror floor's
 * reflections as [reflections] say (`?reflections=off`: the mirror floor drawn plain); the stroboscope on the song's
 * tag [kick] (see [Lights]). Sets on [renderer],
 * [scene] and [camera] what the show needs of them: the shadows, the tone mapping, the dark and the distance fog, the
 * camera's lens.
 *
 * Each frame: [light], and [follow] once the camera is placed. Once the band stands: [shade]. The fog at another
 * quality, while the show plays: [fog]; the shadows: [shadows]; the reflections: [reflections]; the look behind the
 * band: [look]; the floor: [floor];
 * the haze: [haze]; the time of day: [world]; the underworld: [underworld]; the surroundings: [around]; all of them at
 * once: [turn]. What
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
    private val kick: String,
    fog: FogQuality = FogQuality.of(show.switches),
    shadows: ShadowQuality = ShadowQuality.of(show.switches),
    reflections: ReflectionQuality = ReflectionQuality.of(show.switches),
    variants: LookVariants = LookVariants.of(show.switches),
) {
    /** What stands behind the band and the floor, as chosen now. */
    private var variants: LookVariants = variants

    /** The floor, plain or (with [mirror]) polished. */
    private val floor: Mesh

    /**
     * How bright the stage's outline is drawn on the floor, 0 to 1 ([outline], [light]): read by the plain floor and the
     * mirror.
     */
    private val outlined = uniform(if (variants.outline == OutlineLook.On) 1.0 else 0.0)

    /**
     * Whether the show opens (`?opening=ember`, [WorldLook.opens]): its light comes up from pitch black, and what gives
     * light of its own here (the background, the distance's dark, the fog's own light, the faint light everywhere, the
     * wall's glow, the cold world and the ghosts behind the fence, the crowd's lights, the sky, the outline) comes up
     * with the house level ([light]). The band's eyes are not under it: they stay lit, the first thing seen as the
     * band walks in out of the dark (the band's, not the stage's: to make them dark too, their glow would take the
     * same level).
     */
    private val opens: Boolean = WorldLook.opens(show.switches)

    /** The dark behind everything, and the distance's dark: in a show that opens they come up with the house ([light]). */
    private val dark = Three.addon.createColor(DARK)
    private val distance = Three.addon.createFog(color = DARK, near = 9.0, far = 22.0)

    /** The wall behind the band, once the look was the wall; hidden while the [fence] stands. */
    private var wall: BackWall? = null

    /** The wire-mesh fence in place of the wall, once the look was the fence; hidden while the wall stands. */
    private var fence: Fence? = null

    /** The surroundings, once the look had them; hidden while it has none. */
    private var around: Around? = null

    /**
     * The mirror in the floor, once it was chosen and the reflections were on ([LookVariants.mirrors]); on the floor
     * only while both are ([MirrorFloor.use]).
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

    /**
     * The time of day as the stage shows it: it follows the one chosen at its own pace, and where the address asks
     * for an opening (`?opening=ember`) the show opens in that world ([WorldCourse]); the drift's round as the address
     * asks (`?drift=30`).
     */
    private val worldCourse = WorldCourse(opening = WorldLook.opening(show.switches), period = WorldLook.period(show.switches))

    /** The time of day of the two worlds as the last [light] set it ([WorldTime.OFF]: the stage as made). */
    var world: WorldTime = WorldTime.OFF
        private set

    /** Whether the show is still in its opening ([WorldCourse.opens]): for the line at the top. */
    val worldOpens: Boolean
        get() = worldCourse.opens

    /**
     * How bright what gives light of its own is this frame, 0 to 1: the house level in a show that opens, else 1
     * ([opens], [light]).
     */
    private var glow = 1.0

    /** When the last [light] ran, in milliseconds of the page; none before the first. */
    private var lightAt = Double.NaN

    /** The drummer's riser. */
    private val riser: Riser

    /** How far the front lamp has crept toward the band for the ghosts, in metres ([WorldLight.ghostCreep]). */
    private var ghostCreep = 0.0

    /** The lamp's way as it is shown: the creep, followed softly ([WorldLight.ghostEase]). */
    private var ghostNear = 0.0

    /**
     * How far the band's shadows show the ghosts now, 0 to 1 ([WorldLight.ghostShare]), for what belongs to them (the
     * eyes in a shadow); none before the wall, which shows no ghosts, and none where the haze stays whole
     * ([LookVariants.partsHaze], which holds only with the fence): it hides the figures, and the eyes drawn after it
     * would show over nothing (the maintainer, 2026-11-23). The lamp's creep and the camera do not go by it.
     */
    val ghosts: Double
        get() = if (variants.partsHaze) WorldLight.ghostShare(world) else 0.0

    /** How far the total's camera goes back for the ghosts now, in metres ([WorldLight.ghostPull]). */
    val ghostPull: Double
        get() = WorldLight.ghostPull(creep = ghostNear, lamp = lights.frontStands, wall = shadowsAt, band = 0.0, camera = TOTAL_FROM.z)

    private val crowd: Crowd

    /** The night sky over the theatre; none with ?nosky. */
    private val sky: NightSky?

    /** The quality the fog on the floor is drawn at. */
    private var fogQuality: FogQuality = fog

    /** Whether and how finely the mirror floor's reflections are drawn ([ReflectionQuality]); the fog has no say in it. */
    private var reflectionQuality: ReflectionQuality = reflections

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

        scene.background = dark
        scene.fog = distance
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
        // the stage's outline (a working aid) on the plain floor as well
        floor.material.unsafeCast<Material>().onBeforeCompile("floor-plain") { shader: CompiledShader ->
            outlineOn(shader = shader, outline = outlined, placed = false)
        }
        scene.add(floor)

        buildBack()

        lights = Lights(show = show, scene = scene, kick = kick, shadows = shadows)

        riser = Riser(scene)

        crowd = Crowd(scene)
        sky = if (show.switches.has(Switch.NoSky)) null else NightSky(scene)
        fogAsChosen()
        mirrorAsChosen()
        aroundAsChosen()
    }

    /**
     * The surroundings as [variants] says: made where they are wanted and there are none yet (their two pictures are
     * drawn then), shown; hidden where they are not wanted.
     */
    private fun aroundAsChosen() {
        val wanted = variants.around != AroundLook.Off

        if (wanted && around == null) {
            around = Around(scene)
        }

        around?.show(wanted)
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

    /** The front lamp by hand, as the key f ([Lights.tryFront]). */
    fun tryFront() {
        lights.tryFront()
    }

    /**
     * The whole look turned to [to] at once (the key x): what stands behind the band, the floor, the haze, the time of
     * day, the underworld, the stone's unevenness, the reflection's blur, the stone and the stage's outline. Sirene's
     * eyes and the band's skin are the band's: the live act sets them.
     */
    fun turn(to: LookVariants) {
        look(to.stage)
        floor(to.floor)
        haze(to.haze)
        world(to.world)
        ghosts(to.ghosts)
        underworld(to.underworld)
        ripple(to.ripple)
        blur(to.blur)
        stone(to.stone)
        outline(to.outline)
        around(to.around)
    }

    /**
     * The surroundings turned to [to] ([AroundLook]), at once: made the first time they are wanted, hidden where they
     * are off, and kept.
     */
    fun around(to: AroundLook) {
        variants = variants.copy(around = to)
        aroundAsChosen()
    }

    /**
     * The floor turned to [to], at once: the mirror is made (or put back) where it is chosen and the reflections are
     * on, and taken off the floor where not ([mirrorAsChosen]).
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
     * or three ([WorldFade]). Another world than the one shown ends the show's opening at once ([WorldCourse.chosen]).
     * Nothing to make or free.
     */
    fun world(to: WorldLook) {
        if (to != variants.world) {
            worldCourse.chosen()
        }

        variants = variants.copy(world = to)
    }

    /**
     * The ghosts' colour turned to [to] ([GhostLight]): it shows at once where the world is the ghosts. Nothing to make
     * or free, and the show's opening goes on.
     */
    fun ghosts(to: GhostLight) {
        variants = variants.copy(ghosts = to)
    }

    /**
     * The underworld in the mirror turned to [to] ([UnderworldLook]): it comes over 2 s, or goes over 3 s, where the
     * mirror is drawn. Nothing to make or free.
     */
    fun underworld(to: UnderworldLook) {
        variants = variants.copy(underworld = to)
    }

    /**
     * The mirror floor's stone made as uneven as [to] ([RippleLook]), at once: the mirror's reflection and the floor's
     * shading, now and in a mirror made later. Nothing to make or free.
     */
    fun ripple(to: RippleLook) {
        variants = variants.copy(ripple = to)
        mirror?.ripple(to)
    }

    /**
     * The mirror floor's reflection blurred as [to] ([BlurLook]), at once, now and in a mirror made later. Nothing to
     * make or free.
     */
    fun blur(to: BlurLook) {
        variants = variants.copy(blur = to)
        mirror?.blur(to)
    }

    /**
     * The mirror floor's stone turned to [to] ([StoneLook]), at once, now and in a mirror made later. Nothing to make or
     * free.
     */
    fun stone(to: StoneLook) {
        variants = variants.copy(stone = to)
        mirror?.stone(to)
    }

    /** The stage's outline (a working aid, [OutlineLook]) drawn on the floor or not, at once. Nothing to make or free. */
    fun outline(to: OutlineLook) {
        variants = variants.copy(outline = to)
        outlined.value = if (to == OutlineLook.On) glow else 0.0
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
     * The mirror in the floor as [variants] and the reflections say ([LookVariants.mirrors]): made where it is wanted
     * and there is none, put on the floor where it is wanted, taken off where not (its picture is freed then, its
     * material kept). At every fog: with none (off) there is nothing to hide from it, and the haze behind the fence,
     * at any number of sheets, is mirrored as it stands.
     */
    private fun mirrorAsChosen() {
        val wanted = variants.mirrors(reflectionQuality)
        val now = mirror

        if (wanted && now == null) {
            // the fog on the floor lies on the mirror: it is not mirrored as well; nor is the sky, whose stars would
            // glitter in it like noise
            mirror = MirrorFloor(
                floor = floor,
                scene = scene,
                renderer = renderer,
                outline = outlined,
                hide = { hidden ->
                    sky?.hide(hidden)
                    // nor is the horizon round the audience, which it never sees
                    around?.hideFar(hidden)

                    if (variants.stage == StageLook.Wall) {
                        groundFog?.hide(hidden)
                    }

                    mirrorDrawing?.invoke(hidden)
                },
                quality = reflectionQuality,
            ).also {
                it.ripple(variants.ripple)
                it.blur(variants.blur)
                it.stone(variants.stone)
            }
        }

        now?.use(wanted)
    }

    /**
     * The fog on the floor at [quality], where it is another than now: the old fog leaves the scene, with all it
     * holds on the graphics card ([GroundFog.dispose]), and the new one is made (none at [FogQuality.Off]). The mirror
     * floor stays as it is ([reflections] decide it): while it draws it hides whichever fog lies on the floor then.
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
    }

    /**
     * The mirror floor's reflections at [quality], at once: off, the mirror is taken off the floor and its pictures are
     * freed (its material kept for the next time); low, mid or high, it is put back, or made where the floor is the
     * mirror and none was made yet ([mirrorAsChosen]), and draws its picture as finely as the step says (made anew at
     * its size at the next frame).
     */
    fun reflections(quality: ReflectionQuality) {
        reflectionQuality = quality
        mirror?.quality(quality)
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

        FogQuality.Low, FogQuality.High -> GroundFog(
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

        val front = lights.frontAt(t, waiting)

        // in a show that opens, what gives light of its own comes up with the house, from pitch black (see opens)
        glow = if (opens) house.coerceIn(0.0, 1.0) else 1.0
        lights.ambient(glow)
        outlined.value = if (variants.outline == OutlineLook.On) glow else 0.0

        if (opens) {
            dark.setHex(DARK).scale(glow)
            distance.color.setHex(DARK).scale(glow)
        }

        // where the world drifts, the ghosts visit now and then, in their three colours in turn (GhostVisits); the
        // stage turns to them and back slowly, and a world chosen by hand has none
        val drifts = variants.world == WorldLook.Drift
        val visited = drifts && GhostVisits.visiting(t = t, seed = show.seed)
        val look = if (visited) WorldLook.Ghosts else variants.world
        val world = worldCourse.follow(
            t = t,
            look = look,
            dt = dt,
            ghost = if (drifts) GhostVisits.light(t = t, seed = show.seed) else variants.ghosts,
            turnSeconds = if (drifts) GhostVisits.TURN_SECONDS else WorldFade.TURN_SECONDS,
        )

        // while the ghosts come and stay the front lamp creeps toward the band, so they grow for as long as they are
        // seen; as they fade it goes back, and they shrink again
        // (only where the fence stands: the wall shows no ghosts, so nothing grows there and no camera goes back)
        ghostCreep = WorldLight.ghostCreep(
            from = ghostCreep,
            coming = look == WorldLook.Ghosts && variants.stage == StageLook.Mesh,
            dt = dt,
        )
        ghostNear = WorldLight.ghostEase(from = ghostNear, to = ghostCreep, dt = dt)
        lights.approach(by = ghostNear, surface = shadowsAt, wider = Fence.GHOST_WIDER)

        this.front = front
        this.world = world
        // the riser's glow beats with the bass drum; in the ghosts, where the stage is all but dark, it nearly goes
        riser.light(level = glow * (1.0 - 0.85 * WorldLight.ghostShare(world)), sinceKick = sinceKick(t))

        // the one that stands: the other is hidden and kept as it was
        val wall = if (variants.stage == StageLook.Wall) wall else null
        val fence = if (variants.stage == StageLook.Mesh) fence else null

        wall?.light(front = front.level, level = glow)
        wall?.world(world)
        fence?.world(world = world, level = glow)
        dressLevel = min(1.0, front.level)
        // the mood first: the two main lamps behind take its colour for this time of day, and shine leans them on
        lights.mood(t = t, world = world)
        lights.shine(t = t, house = house, front = front, world = world)

        // the cold world through the shadows opens with the front lamp, as far as the time of day lets it; in the ghosts
        // the shadows stay open whatever the lamp does (its shadow is drawn while they are read, its light gives nothing)
        val held = max(FrontFade.level(front), WorldLight.ghostShare(world))
        val beyond = opening.follow(target = held, dt = dt) * WorldLight.beyond(world) * glow

        if (fence != null) {
            val sun = lights.sun(t = t, house = house, front = front, into = sunColor, world = world)
            val size = WorldLight.sunSize(world)
            val height = WorldLight.sunHeight(world)

            fence.light(front = front.level, sun = sunColor, level = sun, size = size, height = height)
            fence.beyond(front = lights.front, share = beyond)
            groundFog?.sun(color = sunColor, level = sun, size = size, height = height)
            groundFog?.world(world)
        }

        groundFog?.house(glow)

        // the haze draws back from the band's shadows as much as the lamp is on, where it parts and the fence stands; in
        // the ghosts part of the way all the time, a veil of the glow before the figures
        val parting = clearing.follow(
            target = if (variants.partsHaze) max(FrontFade.level(front), WorldLight.ghostClearing(world)) else 0.0,
            dt = dt,
        )

        if (fence != null) {
            groundFog?.clearings(front = lights.front, share = parting)
        }

        // the clearings and the windows read the front lamp's shadow until they have closed, also after it went dark
        lights.keepFrontShadow(read = fence != null && (parting > 0.0 || beyond > 0.0))

        groundFog?.update(t, lights.lamps, camera)

        // the underworld comes and goes in the mirror where it is chosen and the mirror is drawn
        val under = underworldFade.follow(target = if (variants.underworlds(reflectionQuality)) 1.0 else 0.0, dt = dt)

        mirror?.underworld(under)

        // the surroundings, by turns: the next one at each ghosts' visit, also where the world is held and none comes
        around?.let {
            it.sides(variants.stage == StageLook.Mesh)
            it.light(
                moment = AroundCourse.at(t = t, seed = show.seed, look = variants.around),
                world = world,
                level = glow,
            )
        }

        sky?.update(t = t, ratio = renderer.getPixelRatio(), level = glow)

        // the crowd moves with the music: still while it waits, and by how loud it is once it plays
        val sound = if (waiting || t < 0) null else show.energyAt(t)
        crowd.update(
            t = t,
            height = renderer.domElement.height.toDouble(),
            energy = if (sound != null) min(1.0, sound.slow) else 0.0,
            low = if (sound != null) min(1.0, sound.low) else 0.0,
            beat = show.beatAt(t),
            level = glow,
        )
    }

    /** What goes with the camera (the sky): call it once the camera stands where this frame is seen from. */
    fun follow() {
        sky?.follow(camera)
    }

    /** How long ago the bass drum was last hit at [t], in seconds; null before its first hit and without a song. */
    private fun sinceKick(t: Double): Double? {
        val kicks = show.times(kick)
        // the last hit at or before t: the first one after it, less one
        val after = kicks.binarySearch { if (it <= t) -1 else 1 }.let { -(it + 1) }

        return kicks.getOrNull(after - 1)?.let { t - it }
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
        shadeUnder(over = riser.body, width = 3.9, depth = 3.0, forward = 0.0, floorY = 0.0, index = 1, texture = texture)
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
     * Stops what the stage listens for (the key f, which tries the front lamp's colours) and frees what the fog, the
     * mirror and the surroundings hold (the floor has its own material again). The rest is in the scene and is freed
     * with it.
     */
    fun dispose() {
        lights.dispose()
        fogs.values.forEach { it.dispose() }
        fogs.clear()
        mirror?.dispose()
        mirror = null
        around?.dispose()
        around = null
        // the wall and the fence are in the scene, freed with it; the one hidden as well
    }

    companion object {
        /** The dark behind everything and in the distance, as made: a breath of blue in the black. */
        private const val DARK = 0x020308

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
