/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.disposeTree
import io.peekandpoke.klang.viz.liveact.lights.AroundLight
import io.peekandpoke.klang.viz.liveact.lights.WorldTime
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.BlendFactor
import io.peekandpoke.klang.workshop.threejs.Blending
import io.peekandpoke.klang.workshop.threejs.ShaderMaterial
import io.peekandpoke.klang.workshop.threejs.ShaderMaterialParameters
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.createCylinderGeometry
import io.peekandpoke.klang.workshop.threejs.createShaderMaterial
import io.peekandpoke.klang.workshop.threejs.uniform
import io.peekandpoke.klang.workshop.threejs.uniformsOf
import io.peekandpoke.kraft.addons.threejs.js.CanvasTexture
import io.peekandpoke.kraft.addons.threejs.js.Group
import io.peekandpoke.kraft.addons.threejs.js.MaterialSide
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.utils.jsObject
import org.w3c.dom.HTMLCanvasElement

/**
 * The surroundings of the stage and the audience (KLANG-00478, [Surrounding]), built into [scene]: large dark shapes
 * lit from below, in three see-through shapes of their own, as a group.
 *
 * - **Beside the stage:** two upright planes, [SIDE_FROM] to [SIDE_TO] m out to either side of the stage's middle and
 *   [SIDE_HIGH] m high, just before the dark surface behind the fence ([Fence.BACK_Z]), behind its haze; where the fence
 *   runs on past the band, the forest (or the ruins, or the stones) presses against the mesh. The middle behind the
 *   drummer stays free for the sun and the band's shadows. Only while the fence stands ([sides]): the wall hides them.
 * - **Round the audience:** a band of a ring [FAR_RADIUS] m round the stage's middle, behind the audience's last row,
 *   from [FAR_BASE] to [FAR_BASE] + [FAR_HIGH] m up, [FAR_WRAP] radians to either side of straight ahead: silhouettes
 *   against the night sky, just over the audience's last lights in the middle of the reverse total and rising toward
 *   its edges. They hide the stars and the moon behind them.
 *
 * What they show is drawn once, as the stage is built ([paintSides], [paintFar]): two pictures, each surrounding in
 * its own colour channel, so a turn from one to another is a uniform ([light]). The beside picture is
 * [SIDES_PIXELS_WIDE] by [SIDES_PIXELS_HIGH] (the audience's left in its left half), the round one [FAR_PIXELS_WIDE] by
 * [FAR_PIXELS_HIGH]; with their smaller copies some 21 MB on the graphics card. Three draws a frame, two more in the mirror
 * floor where it sees the sides (the round band is hidden while the mirror draws, [hideFar]: it is never seen there);
 * no shadows, no light of their own on anything else.
 *
 * Their light: dim, from below, fading upward into the dark ([AroundLight]), with a lit edge where a shape has nothing
 * below it or toward the stage.
 *
 * Hidden and shown again with [show] (the look turned off and on again: kept). [dispose] takes it out of the scene and
 * frees what it holds.
 */
class Around(private val scene: Scene) {
    private val group: Group = Three.addon.createGroup()

    /** The two pictures. */
    private val sidesPicture: CanvasTexture = picture(paintSides())
    private val farPicture: CanvasTexture = picture(paintFar())

    /** Which surrounding is shown: one of the channels, 1. */
    private val pick = vec(1.0, 0.0, 0.0)

    /** How much of the shapes is there, 0 to 1. */
    private val shown = uniform(1.0)

    /** The light at their feet on the audience's left, and on its right, linear, at its strength. */
    private val cold = vec()
    private val warm = vec()

    private val color = Three.addon.createColor(0xffffff)

    private val left: Mesh
    private val right: Mesh
    private val far: Mesh

    init {
        left = side(way = -1)
        right = side(way = 1)
        far = horizon()

        group.add(left)
        group.add(right)
        group.add(far)
        scene.add(group)
    }

    /**
     * Sets them for this frame: the surrounding of [moment] as far as it is there; the light at the time of day
     * [world]; [level] (0 to 1) how bright what gives light of its own is (the stage's light coming up in a show that
     * opens, from pitch black; 1 otherwise).
     */
    fun light(moment: AroundMoment, world: WorldTime, level: Double) {
        when (moment.surrounding) {
            Surrounding.Forest -> pick.set(1.0, 0.0, 0.0)
            Surrounding.Ruins -> pick.set(0.0, 1.0, 0.0)
            Surrounding.Stones -> pick.set(0.0, 0.0, 1.0)
            null -> pick.set(0.0, 0.0, 0.0)
        }

        val there = moment.presence * AroundLight.shown(world) * level

        // NaN-guard: what is no number is not there
        shown.value = if (there != there) 0.0 else there.coerceIn(0.0, 1.0)

        val strength = AroundLight.level(world)

        color.setHex(AroundLight.coldSide(world))
        cold.set(color.r * strength, color.g * strength, color.b * strength)
        color.setHex(AroundLight.warmSide(world))
        warm.set(color.r * strength, color.g * strength, color.b * strength)
    }

    /** Shows the shapes beside the stage ([visible]: the fence stands) or hides them (the wall stands before them). */
    fun sides(visible: Boolean) {
        left.visible = visible
        right.visible = visible
    }

    /** Hides the horizon round the audience ([hidden]) or shows it again: for the mirror floor, which never sees it. */
    fun hideFar(hidden: Boolean) {
        far.visible = !hidden
    }

    /** Shows the surroundings ([visible]) or hides them, with all they hold kept. */
    fun show(visible: Boolean) {
        group.visible = visible
    }

    /** Takes them out of the scene and frees their shapes, materials and pictures. Call it once. */
    fun dispose() {
        scene.remove(group)
        group.disposeTree()
        // the pictures are the materials' uniforms, which the tree does not see
        sidesPicture.dispose()
        farPicture.dispose()
    }

    /** The plane on the audience's left ([way] -1) or right (1). */
    private fun side(way: Int): Mesh {
        val wide = SIDE_TO - SIDE_FROM
        val mesh = Three.addon.createMesh(
            Three.addon.createPlaneGeometry(wide, SIDE_HIGH),
            material(
                picture = sidesPicture,
                from = if (way < 0) 0.0 else 0.5,
                across = 0.5,
                below = EDGE / SIDE_HIGH,
                // toward the stage: on the left plane further along its picture, on the right back
                inward = -way * EDGE / wide * 0.5,
                foot = 0.0,
                fall = SIDE_FALL,
                gain = SIDE_GAIN,
                hides = SIDE_HIDES,
                // each plane is all on one side
                spread = 1.0,
                depthWrite = false,
                side = MaterialSide.FrontSide,
            ),
        )

        mesh.position.set(way * (SIDE_FROM + wide / 2), SIDE_HIGH / 2, SIDE_Z)
        mesh.renderOrder = SIDE_ORDER

        return mesh
    }

    /** The band of the ring round the audience. */
    private fun horizon(): Mesh {
        val geometry = Three.addon.createCylinderGeometry(
            radiusTop = FAR_RADIUS,
            radiusBottom = FAR_RADIUS,
            height = FAR_HIGH,
            radialSegments = 160,
            heightSegments = 1,
            openEnded = true,
            thetaStart = -FAR_WRAP,
            thetaLength = 2 * FAR_WRAP,
        )
        val mesh = Three.addon.createMesh(
            geometry,
            material(
                picture = farPicture,
                from = 0.0,
                across = 1.0,
                below = FAR_EDGE / FAR_HIGH,
                inward = 0.0,
                foot = FAR_FOOT,
                fall = FAR_FALL,
                gain = FAR_GAIN,
                hides = 1.0,
                spread = FAR_SPREAD,
                // it hides the stars and the moon behind it, which are drawn after it
                depthWrite = true,
                // seen from inside the ring
                side = MaterialSide.BackSide,
            ),
        )

        mesh.position.y = FAR_BASE + FAR_HIGH / 2
        mesh.renderOrder = FAR_ORDER

        return mesh
    }

    /**
     * The material of a plane or the band: it reads [picture] from [from] [across] of its width; a shape's edge is lit
     * where the picture is empty [below] of its height under it and [inward] of its width toward the stage; the light
     * is strongest at [foot] metres up and falls to 1/e over [fall]; [gain] how strong it is; [hides] how much a shape
     * hides what is behind it (0 to 1); [spread] over how many metres to either side of the stage's middle the light
     * turns from the cold side to the warm one.
     */
    private fun material(
        picture: CanvasTexture,
        from: Double,
        across: Double,
        below: Double,
        inward: Double,
        foot: Double,
        fall: Double,
        gain: Double,
        hides: Double,
        spread: Double,
        depthWrite: Boolean,
        side: Int,
    ): ShaderMaterial {
        val uniforms = uniformsOf(
            "uMap" to uniform(picture),
            "uSpan" to uniform(Three.addon.createVector2(x = from, y = across)),
            "uBelow" to uniform(below),
            "uInward" to uniform(inward),
            "uPick" to uniform(pick),
            "uShow" to shown,
            "uCold" to uniform(cold),
            "uWarm" to uniform(warm),
            "uFoot" to uniform(foot),
            "uFall" to uniform(fall),
            "uGain" to uniform(gain),
            "uHides" to uniform(hides),
            "uSpread" to uniform(spread),
        )

        return Three.addon.createShaderMaterial(jsObject<ShaderMaterialParameters> {
            this.uniforms = uniforms
            vertexShader = VERTEX
            fragmentShader = FRAGMENT
            transparent = true
            this.depthWrite = depthWrite
            this.side = side
            // the light added, what is behind hidden as far as the shape hides it
            blending = Blending.CustomBlending
            blendSrc = BlendFactor.OneFactor
            blendDst = BlendFactor.OneMinusSrcAlphaFactor
        })
    }

    /** A picture from [canvas], its smaller copies read at a slant as well (the planes beside the stage, from the side). */
    private fun picture(canvas: HTMLCanvasElement): CanvasTexture {
        val texture = Three.addon.createCanvasTexture(canvas)

        texture.anisotropy = 4

        return texture
    }

    companion object {
        /** Beside the stage: from and to how far out from its middle, how high, and where along z. */
        const val SIDE_FROM = 4.0
        const val SIDE_TO = 30.0
        const val SIDE_HIGH = 18.0
        const val SIDE_Z = Fence.BACK_Z + 0.15

        /** Their place among what is drawn see-through: before the fence's wires and the haze, which stand before them. */
        const val SIDE_ORDER = Fence.ORDER - 10

        /** How far their light rises before it falls to 1/e, in metres, how strong it is, and how much they hide. */
        const val SIDE_FALL = 3.4
        const val SIDE_GAIN = 0.22
        const val SIDE_HIDES = 0.85

        /** The beside picture's size, in pixels: the left half for the audience's left, the right half for its right. */
        const val SIDES_PIXELS_WIDE = 2048
        const val SIDES_PIXELS_HIGH = 1024

        /** Round the audience: the ring's radius, how far it goes round to either side, from where and how high. */
        const val FAR_RADIUS = 85.0
        const val FAR_WRAP = 2.1
        const val FAR_BASE = 8.0
        const val FAR_HIGH = 30.0

        /** Its place among what is drawn see-through: before the crowd's lights and the sky, which it hides. */
        const val FAR_ORDER = SIDE_ORDER - 10

        /**
         * Where its light is strongest (at the audience's last row, which hides its foot), how far it rises before it
         * falls to 1/e, and how strong it is.
         */
        const val FAR_FOOT = 10.5
        const val FAR_FALL = 4.5
        const val FAR_GAIN = 0.24

        /** Over how many metres to either side of straight ahead its light turns from the cold side to the warm one. */
        const val FAR_SPREAD = 40.0

        /** The round picture's size, in pixels. */
        const val FAR_PIXELS_WIDE = 4096
        const val FAR_PIXELS_HIGH = 512

        /** How far below and toward the stage an edge looks for the empty dark that lights it, in metres. */
        const val EDGE = 0.15
        const val FAR_EDGE = 0.35

        private val VERTEX = """
  uniform vec2 uSpan;
  varying vec2 vUv;
  varying vec3 vWorld;
  void main() {
    vUv = vec2(uSpan.x + uv.x * uSpan.y, uv.y);
    vec4 world = modelMatrix * vec4(position, 1.0);
    vWorld = world.xyz;
    gl_Position = projectionMatrix * viewMatrix * world;
  }"""

        // A shape is its channel of the picture: 1 near, under a half further back. Its light rises from its foot and
        // fades upward; an edge with nothing below it or toward the stage catches more. The colour leans cold on the
        // audience's left, warm on its right.
        private val FRAGMENT = """
  uniform sampler2D uMap;
  uniform vec3 uPick, uCold, uWarm;
  uniform float uBelow, uInward, uShow, uFoot, uFall, uGain, uHides, uSpread;
  varying vec2 vUv;
  varying vec3 vWorld;
  float shapeAt(vec2 at) { return dot(texture2D(uMap, at).rgb, uPick); }
  void main() {
    float v = shapeAt(vUv);
    if (v < 0.003 || uShow <= 0.0) discard;
    float below = shapeAt(vUv - vec2(0.0, uBelow));
    float inward = shapeAt(vUv + vec2(uInward, 0.0));
    float glow = exp(-max(vWorld.y - uFoot, 0.0) / uFall);
    float rim = max(v - below, 0.0) + 0.6 * max(v - inward, 0.0);
    // a shape further back (under a half) is mostly dark: it hides more than it shows
    float lit = v * v * (0.01 + glow) + rim * (0.02 + 0.9 * glow);
    vec3 light = mix(uCold, uWarm, smoothstep(-uSpread, uSpread, vWorld.x));
    gl_FragColor = vec4(light * lit * uGain * uShow, clamp(v * 2.2, 0.0, 1.0) * uHides * uShow);
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
  }"""
    }
}
