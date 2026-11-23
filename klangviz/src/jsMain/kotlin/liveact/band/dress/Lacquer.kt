/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.dress

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.band.instruments.drums.KitMaterials
import io.peekandpoke.klang.viz.liveact.band.instruments.marimba.MarimbaMaterials
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.StringMaterials
import io.peekandpoke.klang.viz.liveact.disposeTree
import io.peekandpoke.klang.viz.liveact.lights.WorldLight
import io.peekandpoke.klang.viz.liveact.lights.WorldTime
import io.peekandpoke.klang.viz.liveact.toFixed
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.CompiledShader
import io.peekandpoke.klang.workshop.threejs.SkinnedMesh
import io.peekandpoke.klang.workshop.threejs.Uniform
import io.peekandpoke.klang.workshop.threejs.WebGLRenderTarget
import io.peekandpoke.klang.workshop.threejs.copied
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.name
import io.peekandpoke.klang.workshop.threejs.onBeforeCompile
import io.peekandpoke.klang.workshop.threejs.set
import io.peekandpoke.klang.workshop.threejs.setTo
import io.peekandpoke.klang.workshop.threejs.uniform
import io.peekandpoke.kraft.addons.threejs.js.Color
import io.peekandpoke.kraft.addons.threejs.js.MaterialParameters
import io.peekandpoke.kraft.addons.threejs.js.MaterialSide
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.Texture
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import io.peekandpoke.kraft.addons.threejs.js.WebGLRenderer
import io.peekandpoke.kraft.utils.jsObject
import kotlinx.browser.window
import kotlin.math.abs

/**
 * A part of a figure that wears a pattern ([pattern]): its [mesh], the [material] it was dressed in, the pattern
 * ([cloth]) and, where the hair is worn as a pattern on the head, where that head is ([hair]). Kept by the band, so
 * the lacquer ([Lacquer]) can dress it anew and give it back.
 */
class DressedBody(val mesh: SkinnedMesh, val material: MeshStandardMaterial, val cloth: ClothPattern, val hair: HairPart?)

/**
 * The band in black lacquer with gold inlay (`?skin=lacquer`, KLANG-00478, to be judged by eye): while it is made,
 * every [DressedBody] wears a material of its own in place of its suit, and the instruments are polished. Made the
 * first time the look is chosen (its materials are built then, a short hitch); turned off and on again with [wear]
 * (the suits and the instruments as they were, the lacquer's materials and its room kept, so it comes back at once);
 * [dispose] gives back what they wore and frees what it made.
 *
 * - **The lacquer:** three.js's physical material, near black, under a clear coat that is almost a mirror; no
 *   glitter. The lamps leave long, clean highlights along the limbs, and the room (below) what it shows.
 * - **The gold:** each member's pattern as thin lines ([ClothPattern.inlay]), the same lines in the same places,
 *   polished gold metal. Metal has no colour of its own to show: the gold is seen only where it mirrors a lamp or a
 *   bright part of the room, and is nearly black elsewhere. Never emissive (the glowing suits were turned down,
 *   the maintainer, 2026-11-19).
 * - **The room:** what the lacquer and the gold mirror where no lamp is, made once in code: a dark room, a warm low
 *   strip behind the band (where the sun stands behind the drummer), two cool soft strips high up, and two faint tall
 *   ones at the front sides, which draw the long lines down the bodies seen from the hall. three.js filters it for
 *   every roughness (a PMREM, 128 pixels a side, some 1.6 MB on the graphics card). Only the lacquered materials see
 *   it: the rest of the stage looks as it did. It dims with the house ([light]), so the band is dark where the
 *   stage is.
 * - **The two worlds** (`?world=night`, [WorldLight]): the time of day makes the clear coat duller by night, the gold
 *   pale and cold by night and red at ember, and the room's warm strip follows the sun (by night a faint cold moon).
 *   In the ghosts the room is mirrored dimly and its strip is the ghosts' glow, so the band stays dark with a
 *   rim of the colour ([WorldLight.roomLight]).
 *   Where the strip changes, the room is drawn and filtered anew, at most some eight times a second and only while
 *   the time moves; with the time off it is never drawn again.
 * - **The instruments:** the drums' shells and the string instruments' bodies lacquered (black, smooth, mirroring the
 *   room), the cymbals' and the trumpet's brass polished brighter, the steel and the string instruments' gold
 *   hardware mirroring the room; their colours and finishes as they were when it is turned off. A string
 *   instrument's body with gold of its own takes the lacquered finish from the shared one (`StrungInstrument`), its
 *   gold drawn over it. The marimba's bars and every light stay as they are.
 *
 * The eyes are not touched: they are meshes of their own.
 */
class Lacquer private constructor(
    private val bodies: List<DressedBody>,
    private val polished: List<Polish>,
    private val room: Room,
) {
    /** The gold's colour, one for every body: it follows the time of day ([light]). */
    private val gold: Uniform<Vector3> = goldUniform()

    /** What each body wears now, in the order of [bodies]. */
    private val lacquered: List<MeshStandardMaterial> = bodies.map {
        lacquerFor(name = it.material.name, cloth = it.cloth, hair = it.hair, room = room.texture, gold = gold)
    }

    /** Whether the band wears the lacquer now ([wear]). */
    var worn: Boolean = false
        private set

    init {
        wear(true)
    }

    /**
     * Puts the lacquer on the band ([on]) or takes it off: off, every body wears its suit again and the instruments
     * are as they were; the lacquer's materials and its room are kept for the next time.
     */
    fun wear(on: Boolean) {
        if (on == worn) {
            return
        }

        worn = on

        if (on) {
            bodies.forEachIndexed { i, body -> body.mesh.material = lacquered[i] }
            polished.forEach { it.on(room.texture) }
        } else {
            bodies.forEach { it.mesh.material = it.material }
            polished.forEach { it.off() }
        }
    }

    /**
     * How bright the room is mirrored, by [house] (0 dark to 1, as the stage's lamps are dimmed), and the lacquer, the
     * gold and the room's sun at the time of day [world] ([WorldLight]; off: as made).
     */
    fun light(house: Double, world: WorldTime = WorldTime.OFF) {
        val level = ROOM * house.coerceIn(0.0, 1.0) * WorldLight.roomLight(world)
        val coat = WorldLight.coatRoughness(world)
        val (r, g, b) = WorldLight.gold(world)

        gold.value.set(r, g, b)

        if (room.sun(WorldLight.roomSun(world))) {
            lacquered.forEach { it.asDynamic().envMap = room.texture }
            polished.forEach { it.room(room.texture) }
        }

        lacquered.forEach {
            it.asDynamic().envMapIntensity = level
            it.asDynamic().clearcoatRoughness = coat
        }
        polished.forEach { it.light(level) }
    }

    /** Gives every body and instrument back what it wore, and frees the lacquered materials and the room. */
    fun dispose() {
        wear(false)
        lacquered.forEach { it.dispose() }
        room.dispose()
    }

    /**
     * The room the lacquer mirrors (see [Lacquer]): a small scene made once, drawn by [renderer] and filtered by
     * three.js's PMREM generator into [texture]. Its warm strip follows the sun ([sun]); where it changes, the room is
     * drawn anew into the same picture: one picture for the room's whole life, so whatever mirrors it never holds an
     * old one (before, each drawing made a new picture and freed the last: about 1.5 MB made and freed twice a
     * second while the world drifts, and a material that took the new one a frame late mirrored nothing for that
     * frame). [dispose] frees the scene, the generator and the pictures.
     */
    private class Room(private val renderer: WebGLRenderer) {
        private val scene = Three.addon.createScene()
        private val generator: dynamic = Three.addon.create<Any>("PMREMGenerator", renderer)

        /**
         * The room as seen from where the band stands, all six ways: drawn anew where the sun's strip changes, and
         * filtered from there into [target]. Its colours may be brighter than white: half floats.
         */
        private val cube: dynamic = Three.addon.create<Any>("WebGLCubeRenderTarget", ROOM_PIXELS, js("({ type: 1016 })"))
        private val eye: dynamic = Three.addon.create<Any>("CubeCamera", 0.1, 100.0, cube)

        /** The warm strip's own colour: the sun's. */
        private val sunStrip: Color

        /** The sun's strip as the room was last drawn with it, linear red, green and blue. */
        private var shown: Triple<Double, Double, Double> = ROOM_SUN

        /** When the room was last drawn, in milliseconds of the page. */
        private var drawnAt = window.performance.now()

        var texture: Texture
            private set

        private var target: WebGLRenderTarget

        init {
            val dark = Three.addon.createColor(0)

            dark.setRGB(0.002, 0.0025, 0.004)
            scene.background = dark

            /** A strip of light, [width] by [height] metres, at x, y, z, facing the band, in the linear colour r, g, b. */
            fun strip(width: Double, height: Double, at: Triple<Double, Double, Double>, rgb: Triple<Double, Double, Double>): Color {
                val material = Three.addon.createMeshBasicMaterial(jsObject {
                    side = MaterialSide.DoubleSide
                })

                material.color.setRGB(rgb.first, rgb.second, rgb.third)

                val mesh = Three.addon.createMesh(Three.addon.createPlaneGeometry(width, height), material)

                mesh.position.set(at.first, at.second, at.third)
                mesh.lookAt(0.0, EYE, 0.0)
                scene.add(mesh)

                return material.color
            }

            // the sun behind the drummer: warm, low, wide
            sunStrip = strip(width = 9.0, height = 1.6, at = Triple(0.0, 1.4, -7.0), rgb = ROOM_SUN)
            // two cool soft strips high up, over the stage's sides, running from the back to the front
            strip(width = 0.8, height = 9.0, at = Triple(-2.6, 6.0, 0.0), rgb = Triple(0.5, 0.58, 0.8))
            strip(width = 0.8, height = 9.0, at = Triple(2.6, 6.0, 0.0), rgb = Triple(0.5, 0.58, 0.8))
            // two faint tall ones at the front sides: the long lines down the bodies seen from the hall
            strip(width = 0.7, height = 6.0, at = Triple(-5.0, 2.0, 5.0), rgb = Triple(0.22, 0.24, 0.3))
            strip(width = 0.7, height = 6.0, at = Triple(5.0, 2.0, 5.0), rgb = Triple(0.22, 0.24, 0.3))

            eye.position.set(0.0, EYE, 0.0)
            target = draw(into = null)
            texture = target.texture
        }

        /**
         * The sun's strip in [rgb] (linear): where it differs from the room as drawn, by more than [ROOM_STEP] or for
         * longer than half a second, and the last drawing is more than [ROOM_EVERY_MS] old, the room is drawn anew;
         * gives whether it was (into the same [texture]).
         */
        fun sun(rgb: Triple<Double, Double, Double>): Boolean {
            val off = maxOf(abs(rgb.first - shown.first), abs(rgb.second - shown.second), abs(rgb.third - shown.third))
            val nowMs = window.performance.now()
            val since = nowMs - drawnAt

            // NaN-guard: a colour of no number is never drawn (off is then NaN, and every comparison false)
            if (!(off > 0.0) || since < ROOM_EVERY_MS || (off < ROOM_STEP && since < ROOM_SETTLE_MS)) {
                return false
            }

            shown = rgb
            drawnAt = nowMs
            sunStrip.setRGB(rgb.first, rgb.second, rgb.third)

            draw(into = target)

            return true
        }

        fun dispose() {
            cube.dispose()
            target.dispose()
            generator.dispose()
            scene.disposeTree()
        }

        /**
         * Draws the room, [ROOM_PIXELS] pixels a side (the room is soft strips, a finer one shows nothing more), and
         * filters it [into] the picture given, or into a new one where there is none yet; gives that picture.
         */
        private fun draw(into: WebGLRenderTarget?): WebGLRenderTarget {
            eye.update(renderer, scene)

            return generator.fromCubemap(cube.texture, into).unsafeCast<WebGLRenderTarget>()
        }
    }

    /**
     * An instrument's [material] polished while the lacquer is worn: to [color] (linear red, green, blue; null keeps
     * it), [roughness] and [metalness] (null keeps them), mirroring the room. [off] puts back what it was.
     */
    private class Polish(
        val material: MeshStandardMaterial,
        val color: Triple<Double, Double, Double>? = null,
        val roughness: Double? = null,
        val metalness: Double? = null,
    ) {
        private val wasColor: Color = material.color.copied()
        private val wasRoughness: Double = material.roughness
        private val wasMetalness: Double = material.metalness

        fun on(room: Texture) {
            color?.let { (r, g, b) -> material.color.setRGB(r, g, b) }
            roughness?.let { material.roughness = it }
            metalness?.let { material.metalness = it }
            material.asDynamic().envMap = room
            material.asDynamic().envMapIntensity = ROOM
            // the room is a new part of the material's program
            material.needsUpdate = true
        }

        /** The room drawn anew ([Room.sun]): the same kind of picture, the program stays. */
        fun room(room: Texture) {
            material.asDynamic().envMap = room
        }

        fun light(level: Double) {
            material.asDynamic().envMapIntensity = level
        }

        fun off() {
            material.color.setTo(wasColor)
            material.roughness = wasRoughness
            material.metalness = wasMetalness
            material.asDynamic().envMap = null
            material.asDynamic().envMapIntensity = 1.0
            material.needsUpdate = true
        }
    }

    companion object {
        /** How bright the room is mirrored with the house fully up. */
        private const val ROOM = 1.0

        /** The lacquer's colour under its coat, linear: nearly black, a breath of blue. */
        private val LACQUER = Triple(0.0045, 0.0045, 0.006)

        /** The roughness under the coat: a broad, faint sheen beneath the coat's sharp one. */
        private const val UNDER = 0.38

        /** How much the paint under the coat reflects, of what a plain dielectric would: black paint shows little. */
        private const val UNDER_SPECULAR = 0.5

        /** The clear coat's roughness: almost a mirror. */
        private const val COAT = 0.05

        /**
         * The gold's colour, linear, as made: a little below what polished gold reflects, so it stays gold under a
         * strong lamp. A uniform of the shader (`uKlGold`): the time of day changes it ([WorldLight.gold]).
         */
        private val GOLD = WorldLight.gold(WorldTime.OFF)

        /** The room's warm strip where the sun stands, linear, as made ([WorldLight.roomSun]). */
        private val ROOM_SUN = WorldLight.roomSun(WorldTime.OFF)

        /** The room is drawn anew where its sun moved by more than this (linear, in any channel) ... */
        private const val ROOM_STEP = 0.04

        /** ... or by less, for longer than this, in milliseconds: so it comes to rest exactly where the sun does. */
        private const val ROOM_SETTLE_MS = 500.0

        /** Never more often than this, in milliseconds. */
        private const val ROOM_EVERY_MS = 120.0

        /** A uniform of the gold's colour as made. */
        internal fun goldUniform(): Uniform<Vector3> = uniform(vec(GOLD.first, GOLD.second, GOLD.third))

        /** The gold's roughness: polished, a little softer than the coat, so a thin line catches light over more angles. */
        private const val GOLD_ROUGH = 0.18

        /** How much of the clear coat lies over the gold: less, so the gold's own colour leads its highlight. */
        private const val GOLD_COAT = 0.25

        /** The instruments' lacquer, linear, and its roughness (no clear coat there: the instruments keep their material). */
        private val CASE = Triple(0.004, 0.004, 0.005)
        private const val CASE_ROUGH = 0.12

        /** The brass, polished: linear colour and roughness. */
        private val BRASS = Triple(0.8, 0.5, 0.15)
        private const val BRASS_ROUGH = 0.22

        /**
         * The band in lacquer: [bodies] (the band's dressed parts) and [brass] (the trumpet's) re-dressed, the
         * instruments' shared materials polished, the room made with [renderer].
         */
        fun of(renderer: WebGLRenderer, bodies: List<DressedBody>, brass: List<MeshStandardMaterial>): Lacquer {
            val polished = listOf(
                Polish(material = KitMaterials.shell, color = CASE, roughness = CASE_ROUGH, metalness = 0.0),
                Polish(material = StringMaterials.lacquer, color = CASE, roughness = CASE_ROUGH, metalness = 0.0),
                Polish(material = KitMaterials.brass, color = BRASS, roughness = BRASS_ROUGH, metalness = 1.0),
                Polish(material = KitMaterials.steel),
                Polish(material = StringMaterials.metal),
                Polish(material = StringMaterials.gold),
                Polish(material = MarimbaMaterials.brass),
            ) + brass.map { Polish(material = it, color = BRASS, roughness = BRASS_ROUGH, metalness = 1.0) }

            return Lacquer(bodies = bodies, polished = polished, room = Room(renderer))
        }

        /**
         * The band in lacquer without instruments ("Meet the Band"): [bodies] (the band's dressed parts) re-dressed,
         * the room made with [renderer]; no instrument's material is touched.
         */
        fun bodiesOnly(renderer: WebGLRenderer, bodies: List<DressedBody>): Lacquer =
            Lacquer(bodies = bodies, polished = emptyList(), room = Room(renderer))

        /**
         * The lacquer a body wears whose suit was [name] (`sirene_dark`): its pattern [cloth] as gold in the colour
         * [gold] holds, its hair combed back where [hair] says, mirroring [room] (none: nothing is mirrored).
         */
        internal fun lacquerFor(
            name: String,
            cloth: ClothPattern,
            hair: HairPart?,
            room: Texture?,
            gold: Uniform<Vector3> = goldUniform(),
        ): MeshStandardMaterial {
            val material = Three.addon.create<MeshStandardMaterial>(
                "MeshPhysicalMaterial",
                jsObject<MaterialParameters> {
                    roughness = UNDER
                    metalness = 0.0
                },
            )
            val m = material.asDynamic()

            material.color.setRGB(LACQUER.first, LACQUER.second, LACQUER.third)
            m.name = name + "_lacquer"
            m.specularIntensity = UNDER_SPECULAR
            m.clearcoat = 1.0
            m.clearcoatRoughness = COAT
            m.envMap = room
            m.envMapIntensity = ROOM

            // Skalpell's hair, combed back: lacquered strands, glossier and duller by turns
            val combed = hair?.let { combedBack(hair = it, matt = "0.30", gloss = "0.06") } ?: ""
            val cacheKey = "lacquer-" + cloth.key + (if (hair != null) "-hair" else "")

            material.onBeforeCompile(cacheKey) { shader: CompiledShader ->
                shader.uniforms["uKlGold"] = gold
                shader.vertexShader = shader.vertexShader
                    .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vRest;")
                    .replaceFirst("#include <begin_vertex>", "#include <begin_vertex>\nvRest = position;")

                shader.fragmentShader = shader.fragmentShader
                    .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vRest;\nuniform vec3 uKlGold;\n$INLAY_GLSL")
                    .replaceFirst(
                        "#include <color_fragment>",
                        "#include <color_fragment>\nfloat klGold; { vec3 p = vRest; ${cloth.before} klGold = clamp(${cloth.inlay}, 0.0, 1.0); }\n" +
                            "diffuseColor.rgb = mix(diffuseColor.rgb, uKlGold, klGold);",
                    )
                    .replaceFirst(
                        "#include <roughnessmap_fragment>",
                        "#include <roughnessmap_fragment>$combed\nroughnessFactor = mix(roughnessFactor, ${GOLD_ROUGH.toFixed(3)}, klGold);",
                    )
                    .replaceFirst(
                        "#include <metalnessmap_fragment>",
                        "#include <metalnessmap_fragment>\nmetalnessFactor = mix(metalnessFactor, 1.0, klGold);",
                    )
                    .replaceFirst(
                        "#include <lights_physical_fragment>",
                        "#include <lights_physical_fragment>\nmaterial.clearcoat *= mix(1.0, ${GOLD_COAT.toFixed(3)}, klGold);",
                    )
            }

            return material
        }

        /** The height the room is seen from: a figure's chest. */
        private const val EYE = 1.2

        /** The room's picture, in pixels a side of each of its six ways. */
        private const val ROOM_PIXELS = 128
    }
}
