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
 *   runs on past the band, the forest (or the ruins, or the stones) presses against the mesh. Each plane shows four
 *   rows of shapes behind one another, as far as 26 m behind it, each where the look through the plane meets it: they
 *   shift against one another as the camera moves, and none of it stands behind the surface, which would hide it. Their
 *   feet sink into the dark ([SIDE_SINK]). The middle behind the
 *   drummer stays free for the sun and the band's shadows. Only while the fence stands ([sides]): the wall hides them.
 * - **Round the audience:** a band of a ring [FAR_RADIUS] m round the stage's middle, behind the audience's last row,
 *   from [FAR_BASE] to [FAR_BASE] + [FAR_HIGH] m up, [FAR_WRAP] radians to either side of straight ahead: silhouettes
 *   against the night sky, just over the audience's last lights in the middle of the reverse total and rising toward
 *   its edges. They hide the stars and the moon behind them.
 *
 * What they show is drawn once, as the stage is built ([paintSides], [paintFar]): two pictures, each surrounding in
 * its own colour channel, so a turn from one to another is a uniform ([light]). The beside picture is
 * [SIDES_PIXELS_WIDE] by [SIDES_PIXELS_HIGH] (the audience's left in its left half), the round one [FAR_PIXELS_WIDE] by
 * [FAR_PIXELS_HIGH], the rows further back beside the stage [DEEP_PIXELS_WIDE] by [DEEP_PIXELS_HIGH]; with their smaller
 * copies, and the relief of the horizon's near shapes (as large as their picture), some 35 MB on the graphics card. Three draws a frame, two more in the mirror
 * floor where it sees the sides (the round band is hidden while the mirror draws, [hideFar]: it is never seen there);
 * no shadows, no light of their own on anything else.
 *
 * Their light: dim, from below, fading upward into the dark ([AroundLight]), with a lit edge where a shape has nothing
 * below it (beside the stage also where it has nothing toward the stage). The horizon's shapes have a relief: rounded
 * from their edge to their middle and rough with a grain (bark, courses of stone), lit from below and from either side
 * ([FAR_ROUND], [FAR_ROUGH]).
 *
 * Hidden and shown again with [show] (the look turned off and on again: kept). [dispose] takes it out of the scene and
 * frees what it holds.
 */
class Around(private val scene: Scene) {
    private val group: Group = Three.addon.createGroup()

    /** The pictures: beside the stage the near shapes and the ones further back, and the horizon. */
    private val sidesPicture: CanvasTexture = picture(paintSides(row = Row.Near, width = SIDES_PIXELS_WIDE, height = SIDES_PIXELS_HIGH))
    private val deepPicture: CanvasTexture = picture(paintSides(row = Row.Deep, width = DEEP_PIXELS_WIDE, height = DEEP_PIXELS_HIGH))
    private val farPicture: CanvasTexture = picture(paintFar(row = Row.Near, width = FAR_PIXELS_WIDE, height = FAR_PIXELS_HIGH))
    private val farDeepPicture: CanvasTexture = picture(paintFar(row = Row.Deep, width = FAR_DEEP_PIXELS_WIDE, height = FAR_DEEP_PIXELS_HIGH))

    /** The relief of the horizon's near shapes (see [paintFarRelief]). */
    private val farReliefPicture: CanvasTexture = picture(paintFarRelief(width = FAR_PIXELS_WIDE, height = FAR_PIXELS_HIGH))

    /** Which surrounding is shown: one of the channels, 1. */
    private val pick = vec(1.0, 0.0, 0.0)

    /** How much of the shapes is there, 0 to 1. */
    private val shown = uniform(1.0)

    /** The light at their feet on the audience's left, and on its right, linear, at its strength. */
    private val cold = vec()
    private val warm = vec()

    private val color = Three.addon.createColor(0xffffff)

    /** The grain of the horizon's surfaces: how many of its bumps a metre holds, along and up (see [FAR_GRAINS]). */
    private val grain = Three.addon.createVector2(x = 1.0, y = 1.0)

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

        moment.surrounding?.let { FAR_GRAINS.getValue(it) }?.let { (along, up) -> grain.set(along, up) }

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
        deepPicture.dispose()
        farPicture.dispose()
        farDeepPicture.dispose()
        farReliefPicture.dispose()
    }

    /** The plane on the audience's left ([way] -1) or right (1): the rows of shapes behind one another are seen in it. */
    private fun side(way: Int): Mesh {
        val wide = SIDE_TO - SIDE_FROM
        val uniforms = uniformsOf(
            "uNear" to uniform(sidesPicture),
            "uDeep" to uniform(deepPicture),
            "uWay" to uniform(way.toDouble()),
            "uReach" to uniform(Three.addon.createVector2(x = SIDE_FROM, y = wide)),
            "uHigh" to uniform(SIDE_HIGH),
            "uEdge" to uniform(EDGE),
            "uPick" to uniform(pick),
            "uShow" to shown,
            "uCold" to uniform(cold),
            "uWarm" to uniform(warm),
            "uFall" to uniform(SIDE_FALL),
            "uGain" to uniform(SIDE_GAIN),
            "uHides" to uniform(SIDE_HIDES),
            "uSink" to uniform(SIDE_SINK),
        )
        val mesh = Three.addon.createMesh(
            Three.addon.createPlaneGeometry(wide, SIDE_HIGH),
            Three.addon.createShaderMaterial(jsObject<ShaderMaterialParameters> {
                this.uniforms = uniforms
                vertexShader = VERTEX
                fragmentShader = SIDE_FRAGMENT
                transparent = true
                depthWrite = false
                side = MaterialSide.FrontSide
                // the light added, what is behind hidden as far as the shapes hide it
                blending = Blending.CustomBlending
                blendSrc = BlendFactor.OneFactor
                blendDst = BlendFactor.OneMinusSrcAlphaFactor
            }),
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
                deep = farDeepPicture,
                below = FAR_EDGE / FAR_HIGH,
                foot = FAR_FOOT,
                fall = FAR_FALL,
                gain = FAR_GAIN,
                spread = FAR_SPREAD,
                // laid over the stars and the moon, which are drawn before it: its soft edge stays soft against them
                depthWrite = false,
                // seen from inside the ring
                side = MaterialSide.BackSide,
            ),
        )

        mesh.position.y = FAR_BASE + FAR_HIGH / 2
        mesh.renderOrder = FAR_ORDER

        return mesh
    }

    /**
     * The material of the band: it reads [picture] (its near shapes) and [deep] (the ones behind them) across its
     * whole width; a shape's edge is lit where the picture is empty [below] of its height under it; the light is
     * strongest at [foot] metres up and falls to 1/e over [fall]; [gain] how strong it is; [spread] over how many
     * metres to either side of the stage's middle the light turns from the cold side to the warm one. A shape hides
     * what is behind it fully.
     */
    private fun material(
        picture: CanvasTexture,
        deep: CanvasTexture,
        below: Double,
        foot: Double,
        fall: Double,
        gain: Double,
        spread: Double,
        depthWrite: Boolean,
        side: Int,
    ): ShaderMaterial {
        val uniforms = uniformsOf(
            "uMap" to uniform(picture),
            "uDeep" to uniform(deep),
            "uBelow" to uniform(below),
            "uPick" to uniform(pick),
            "uShow" to shown,
            "uCold" to uniform(cold),
            "uWarm" to uniform(warm),
            "uFoot" to uniform(foot),
            "uFall" to uniform(fall),
            "uGain" to uniform(gain),
            "uSpread" to uniform(spread),
            "uMetres" to uniform(Three.addon.createVector2(x = 2 * FAR_WRAP * FAR_RADIUS, y = FAR_HIGH)),
            "uGrain" to uniform(grain),
            "uRound" to uniform(FAR_ROUND),
            "uRough" to uniform(FAR_ROUGH),
            "uRelief" to uniform(farReliefPicture),
            "uTexel" to uniform(Three.addon.createVector2(x = 1.0 / FAR_PIXELS_WIDE, y = 1.0 / FAR_PIXELS_HIGH)),
            "uCarve" to uniform(FAR_CARVE),
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
        // out of the total's frame (some 11 m to either side there at 16:9) and clear of the band's shadows on the
        // surface behind, which the shapes would hide (the outer players' reach some 10 m out; the maintainer,
        // 2026-11-22)
        const val SIDE_FROM = 12.0
        const val SIDE_TO = 30.0
        const val SIDE_HIGH = 18.0
        const val SIDE_Z = Fence.BACK_Z + 0.15

        /** Their place among what is drawn see-through: before the fence's wires and the haze, which stand before them. */
        const val SIDE_ORDER = Fence.ORDER - 10

        /** How far their light rises before it falls to 1/e, in metres, how strong it is, and how much they hide. */
        const val SIDE_FALL = 3.4
        const val SIDE_GAIN = 0.22
        const val SIDE_HIDES = 0.85

        /** Over how many metres from the ground they sink into the haze (the maintainer, 2026-11-22: no sharp foot). */
        const val SIDE_SINK = 4.5

        /** The beside picture's size, in pixels: the left half for the audience's left, the right half for its right. */
        const val SIDES_PIXELS_WIDE = 2048
        const val SIDES_PIXELS_HIGH = 1024

        /** The picture of the shapes further back beside the stage, laid out as the near one: they are dim and far. */
        const val DEEP_PIXELS_WIDE = 1024
        const val DEEP_PIXELS_HIGH = 512

        /** Round the audience: the ring's radius, how far it goes round to either side, from where and how high. */
        const val FAR_RADIUS = 85.0
        const val FAR_WRAP = 2.1
        const val FAR_BASE = 8.0
        const val FAR_HIGH = 30.0

        /** Its place among what is drawn see-through: after the sky (`NightSky.ORDER`), which it hides, before the crowd's lights. */
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

        /** The picture of the horizon's shapes further back, laid out as the near one. */
        const val FAR_DEEP_PIXELS_WIDE = 2048
        const val FAR_DEEP_PIXELS_HIGH = 256

        /**
         * The horizon's relief (the maintainer, 2026-11-22: "in order to make them not appear that flat we need to
         * apply some normal maps on the surfaces"). A shape's surface leans as its picture, read blurred, rises from
         * its edge to its middle (how far it bulges, in metres: [FAR_ROUND]). On that the near shapes wear a painted
         * relief ([FAR_CARVE]); the dim ones behind them only a grain of bumps ([FAR_ROUGH] metres deep, [FAR_GRAINS]
         * bumps to a metre along and up: bark runs upward, a wall is laid in courses, a stone is rough every way).
         */
        const val FAR_ROUND = 0.9
        const val FAR_ROUGH = 0.8

        /** How deep the near shapes' painted relief is from its lowest to its highest, in metres (see [paintFarRelief]). */
        const val FAR_CARVE = 0.7
        val FAR_GRAINS: Map<Surrounding, Pair<Double, Double>> = mapOf(
            Surrounding.Forest to (1.7 to 0.22),
            Surrounding.Ruins to (0.35 to 0.9),
            Surrounding.Stones to (0.45 to 0.4),
        )

        /**
         * How far an edge looks for the empty dark that lights it, in metres: beside the stage below and toward the
         * stage ([EDGE]), on the horizon below only ([FAR_EDGE]).
         */
        const val EDGE = 0.15
        const val FAR_EDGE = 0.35

        private val VERTEX = """
  varying vec2 vUv;
  varying vec3 vWorld;
  void main() {
    vUv = uv;
    vec4 world = modelMatrix * vec4(position, 1.0);
    vWorld = world.xyz;
    gl_Position = projectionMatrix * viewMatrix * world;
  }"""

        // The planes beside the stage: four rows of shapes behind one another, each seen where the look through the
        // plane meets it (so they shift against one another as the camera moves, as a deep forest does): the near
        // picture on the plane, the same picture mirrored some metres behind it, the deep picture further back, and
        // mirrored further still. A row goes on outward by its picture turned over and over. The further back, the
        // dimmer. Each row's light rises from the ground and fades upward; an edge with nothing below it or toward
        // the stage catches more; over its lowest metres a row sinks into the haze on the ground, light and shape
        // alike, so nothing ends in a hard foot. The colour leans cold on the audience's left, warm on its right.
        private val SIDE_FRAGMENT = """
  uniform sampler2D uNear, uDeep;
  uniform vec3 uPick, uCold, uWarm;
  uniform vec2 uReach;
  uniform float uWay, uHigh, uEdge, uShow, uFall, uGain, uHides, uSink;
  varying vec3 vWorld;
  float shapeAt(sampler2D map, float along, float up) {
    // turned over and over outward: 0 to 1, back to 0, ...
    float u = clamp(abs(mod(along + 1.0, 2.0) - 1.0), 0.002, 0.998);
    return dot(texture2D(map, vec2(uWay < 0.0 ? 0.5 - 0.5 * u : 0.5 + 0.5 * u, up)).rgb, uPick);
  }
  // A row [back] metres behind the plane, its picture slid [slide] of its width: its light and how much it hides.
  vec4 row(sampler2D map, vec3 ray, float back, float slide, float dim) {
    vec3 at = vWorld + ray * (back / max(-ray.z, 0.05));
    float out_ = uWay * at.x;
    float up = at.y / uHigh;
    if (out_ < uReach.x || up <= 0.0 || up >= 1.0) return vec4(0.0);
    float along = (out_ - uReach.x) / uReach.y + slide;
    float v = shapeAt(map, along, up);
    float below = shapeAt(map, along, up - uEdge / uHigh);
    float inward = shapeAt(map, along - uEdge / uReach.y, up);
    float glow = exp(-max(at.y, 0.0) / uFall);
    float rim = max(v - below, 0.0) + 0.6 * max(v - inward, 0.0);
    float stands = smoothstep(0.0, uSink, at.y);
    float lit = (v * v * (0.01 + glow) + rim * (0.02 + 0.9 * glow)) * dim * stands;
    return vec4(vec3(lit), clamp(v * 2.2, 0.0, 1.0) * uHides * stands);
  }
  void main() {
    if (uShow <= 0.0) discard;
    vec3 ray = normalize(vWorld - cameraPosition);
    vec4 seen = row(uNear, ray, 0.0, 0.0, 1.0);
    vec4 next = row(uNear, ray, 6.0, 0.37, 0.5);
    seen += (1.0 - seen.a) * next;
    next = row(uDeep, ray, 14.0, 0.0, 0.24);
    seen += (1.0 - seen.a) * next;
    next = row(uDeep, ray, 26.0, 0.61, 0.11);
    seen += (1.0 - seen.a) * next;
    if (seen.a < 0.003) discard;
    vec3 light = mix(uCold, uWarm, step(0.0, uWay));
    gl_FragColor = vec4(light * seen.rgb * uGain * uShow, seen.a * uShow);
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
  }"""

        // The horizon. A shape is its channel of its picture, the near shapes in one, those behind them in another
        // (dimmer, and a little see-through), so each has its whole soft edge: a picture read larger than it is drawn
        // then shows no steps (with both in one picture, the far shapes' half strength had to be doubled, which made
        // every edge hard; the maintainer, 2026-11-22: "pixellated outlines"). Its light rises from its foot and fades
        // upward; an edge with nothing below it catches more. The colour leans cold on the
        // audience's left, warm on its right.
        private val FRAGMENT = """
  uniform sampler2D uMap, uDeep, uRelief;
  uniform vec3 uPick, uCold, uWarm;
  uniform vec2 uMetres, uGrain, uTexel;
  uniform float uBelow, uShow, uFoot, uFall, uGain, uSpread, uRound, uRough, uCarve;
  varying vec2 vUv;
  varying vec3 vWorld;
  float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
  float bumps(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
  }
  // How far a shape of [map] bulges at [uv], 0 to 1: from its edge to its middle (the picture read blurred).
  float body(sampler2D map, vec2 uv) { return dot(texture2D(map, uv, 3.0).rgb, uPick); }
  // A grain of bumps, 0 to 1, for the shapes that have no painted relief.
  float rough(vec2 uv, float shift) {
    vec2 q = (uv * uMetres + shift) * uGrain;
    return 0.6 * bumps(q) + 0.4 * bumps(q * 2.7 + 5.3);
  }
  // The painted relief at [uv], 0 to 1.
  float carved(vec2 uv) { return dot(texture2D(uRelief, uv).rgb, uPick); }
  // A row's light from the cold side (x), from the warm side (y), and how much of it is there (z); [dim] how dark
  // it is against the near row; [painted]: it wears the painted relief (1), else a grain slid by [shift]; [side],
  // [up_], [face]: along the band, up, and out of it.
  vec3 row(sampler2D map, float dim, float glow, float painted, float shift, vec3 side, vec3 up_, vec3 face) {
    float v = dot(texture2D(map, vUv).rgb, uPick);
    if (v < 0.003) return vec3(0.0);
    float below = dot(texture2D(map, vUv - vec2(0.0, uBelow)).rgb, uPick);
    float rim = max(v - below, 0.0);
    // the surface's lean, from its height a little to either side and above and below
    vec2 e = vec2(0.35) / uMetres;
    vec2 ex = vec2(e.x, 0.0);
    vec2 ey = vec2(0.0, e.y);
    float along = uRound * (body(map, vUv + ex) - body(map, vUv - ex)) / 0.7;
    float rise = uRound * (body(map, vUv + ey) - body(map, vUv - ey)) / 0.7;
    if (painted > 0.5) {
      vec2 tx = vec2(uTexel.x, 0.0);
      vec2 ty = vec2(0.0, uTexel.y);
      along += uCarve * (carved(vUv + tx) - carved(vUv - tx)) / (2.0 * uTexel.x * uMetres.x);
      rise += uCarve * (carved(vUv + ty) - carved(vUv - ty)) / (2.0 * uTexel.y * uMetres.y);
    } else {
      along += uRough * (rough(vUv + ex, shift) - rough(vUv - ex, shift)) / 0.7;
      rise += uRough * (rough(vUv + ey, shift) - rough(vUv - ey, shift)) / 0.7;
    }
    vec3 n = normalize(face - along * side - rise * up_);
    // lit from below and from either side; a flat face is as bright as it was without relief
    float under = max(dot(n, normalize(face - 0.9 * up_)), 0.0) / 0.743;
    float cold = max(dot(n, normalize(face - vec3(1.3, 0.2, 0.0))), 0.0);
    float warm = max(dot(n, normalize(face + vec3(1.3, -0.2, 0.0))), 0.0);
    float body = dim * dim * v * v * (0.01 + glow) * (0.55 + 0.45 * under);
    float edge = dim * rim * (0.02 + 0.9 * glow);
    return vec3(body * (0.25 + cold * 1.5) + edge, body * (0.25 + warm * 1.5) + edge, v);
  }
  void main() {
    if (uShow <= 0.0) discard;
    float glow = exp(-max(vWorld.y - uFoot, 0.0) / uFall);
    // the band stands round the stage's middle: out of it is toward the middle, along it is across that
    vec3 face = normalize(vec3(-vWorld.x, 0.0, -vWorld.z));
    vec3 up_ = vec3(0.0, 1.0, 0.0);
    vec3 side = (dFdx(vWorld) * dFdy(vUv).y - dFdy(vWorld) * dFdx(vUv).y)
      * sign(dFdx(vUv).x * dFdy(vUv).y - dFdy(vUv).x * dFdx(vUv).y);
    side = normalize(side - dot(side, face) * face - dot(side, up_) * up_ + 1e-6 * cross(up_, face));
    vec3 near = row(uMap, 1.0, glow, 1.0, 0.0, side, up_, face);
    vec3 deep = row(uDeep, 0.42, glow, 0.0, 31.7, side, up_, face);
    float there = near.z + (1.0 - near.z) * 0.92 * deep.z;
    if (there < 0.003) discard;
    vec2 lit = near.xy + (1.0 - near.z) * deep.xy;
    // each side's light where it stands, a little of it everywhere
    float warmth = smoothstep(-uSpread, uSpread, vWorld.x);
    vec3 light = uCold * lit.x * mix(1.0, 0.3, warmth) + uWarm * lit.y * mix(0.3, 1.0, warmth);
    gl_FragColor = vec4(light * uGain * uShow, there * uShow);
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
  }"""
    }
}
