/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.disposeTree
import io.peekandpoke.klang.viz.liveact.lights.Lamp
import io.peekandpoke.klang.viz.liveact.lights.WorldLight
import io.peekandpoke.klang.viz.liveact.lights.WorldTime
import io.peekandpoke.klang.viz.liveact.mat4
import io.peekandpoke.klang.viz.liveact.toFixed
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.BlendFactor
import io.peekandpoke.klang.workshop.threejs.Blending
import io.peekandpoke.klang.workshop.threejs.Data3DTexture
import io.peekandpoke.klang.workshop.threejs.RenderTargetOptions
import io.peekandpoke.klang.workshop.threejs.ShaderMaterial
import io.peekandpoke.klang.workshop.threejs.ShaderMaterialParameters
import io.peekandpoke.klang.workshop.threejs.TextureDataType
import io.peekandpoke.klang.workshop.threejs.TextureFormat
import io.peekandpoke.klang.workshop.threejs.Uniform
import io.peekandpoke.klang.workshop.threejs.Uniforms
import io.peekandpoke.klang.workshop.threejs.WebGLRenderTarget
import io.peekandpoke.klang.workshop.threejs.createData3DTexture
import io.peekandpoke.klang.workshop.threejs.createShaderMaterial
import io.peekandpoke.klang.workshop.threejs.createWebGLRenderTarget
import io.peekandpoke.klang.workshop.threejs.drawOrder
import io.peekandpoke.klang.workshop.threejs.extensions
import io.peekandpoke.klang.workshop.threejs.forceSinglePass
import io.peekandpoke.klang.workshop.threejs.frustumCulled
import io.peekandpoke.klang.workshop.threejs.getRenderTarget
import io.peekandpoke.klang.workshop.threejs.set
import io.peekandpoke.klang.workshop.threejs.setRenderTarget
import io.peekandpoke.klang.workshop.threejs.uniform
import io.peekandpoke.klang.workshop.threejs.uniformsOf
import io.peekandpoke.kraft.addons.threejs.js.BufferGeometry
import io.peekandpoke.kraft.addons.threejs.js.Camera
import io.peekandpoke.kraft.addons.threejs.js.Color
import io.peekandpoke.kraft.addons.threejs.js.MaterialSide
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.addons.threejs.js.TextureFilter
import io.peekandpoke.kraft.addons.threejs.js.TextureWrapping
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import io.peekandpoke.kraft.addons.threejs.js.WebGLRenderer
import io.peekandpoke.kraft.utils.jsObject
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.set
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.random.Random

// Fog from a fog machine, lying on the floor. It is a volume drawn in slices: a stack of thin level sheets from the
// floor to knee height, each showing the fog's density at its own height. The density is a noise that drifts with a
// slight wind and is stirred by a slower one (the turbulence); it is thick at the floor and only its billows reach
// the top, thickest near the two machines behind the band. The lamps light it, each in its cone and colour, so the
// beams show in it; it throws and takes no shadows.
//
// The noise is read from a picture: a block of random numbers, made once and repeating in every direction, which the
// graphics card blends between its points. Five readings a pixel and sheet stand in place of forty computed random
// numbers, which made the fog cost more than everything else on the stage together (measured 2026-11-16).
//
// Behind the wire mesh (`?stage=mesh`, FogBed.BehindMesh) the same sheets stand upright, parallel to the wall, between
// the mesh and the dark surface behind it: a haze from the floor to well above the band's heads that rises and drifts
// slowly, lit from behind by the sun behind the drummer (Fence) and only faintly by the lamps.
//
// With `?haze=parts` (HazeLook.Parts) the haze gives way to the band's shadows: each point of it asks the front lamp's
// shadow map whether the lamp's light is held back on its way there by something well before the haze (the band, the
// riser), and the haze thins there, so each big shadow on the surface behind stands in a clearing. The test sits in
// the maps (see below), six readings of the lamp's map for each point of them (a point every 9 cm), whatever the
// screen's size: some 1.5 million readings a frame at high, about what the noise already costs there. The sheets
// themselves read only their maps, as before.
//
// The sheets cover much of the picture many times over (twelve at the quality the fog was made with, FogQuality), so
// working the fog out for every pixel of every sheet costs with the number of pixels. But what the fog is at a place
// on the stage does not depend on the camera. So it is worked out once a frame as a map of the stage for each sheet
// (a point every seven centimetres, all the maps side by side in one picture), and the sheets are drawn into the
// scene reading their maps: one reading a pixel.
// The cost of the maps is the same whatever the screen's size, and what stands in the fog cuts it exactly, at the
// picture's own fineness.
// (A first try drew the fog into a picture of half the screen's size and laid that over the finished one. Where
// bright fog lay behind a dark leg, the leg's edge showed in steps: the maintainer, 2026-11-17, "pretty heavy
// aliasing on the legs".)

/**
 * The fog of [scene], on the floor or behind the mesh as [bed] says, drawn by [renderer] at [quality] (its number of
 * sheets; not [FogQuality.Off], which is no fog to make). Each frame, after the lamps are set and before the scene is
 * drawn: [update] (behind the mesh, after [sun]). When the stage goes, or the fog is made anew at another quality:
 * [dispose].
 *
 * [mapped]: the fog is worked out as maps of the stage (see the top of the file), where the graphics card can hold
 * such a picture; else, and with `?fullfog` (to compare), every pixel of every sheet works it out for itself.
 */
class GroundFog(
    private val scene: Scene,
    private val renderer: WebGLRenderer,
    private val quality: FogQuality,
    mapped: Boolean,
    private val bed: FogBed = FogBed.Floor,
) {
    /** One sheet of the stack, with its material and its own see-through share. */
    private class Layer(val mesh: Mesh, val material: ShaderMaterial, val alpha: Uniform<Double>)

    // What the fog is made from: the time, and the lamps (place, direction, colour times intensity, the cone's cosine)
    private val time = uniform(0.0)
    private val positions = Array(LAMPS) { vec() }
    private val directions = Array(LAMPS) { vec(0.0, -1.0, 0.0) }
    private val colors = Array(LAMPS) { vec() }
    private val cosines = Array(LAMPS) { 0.0 }
    private val positionsUniform = uniform(positions)
    private val directionsUniform = uniform(directions)
    private val colorsUniform = uniform(colors)
    private val cosinesUniform = uniform(cosines)

    // The front lamp's shadows, which the haze behind the mesh gives way to (`?haze=parts`): the lamp's map of what
    // it lights (three.js's, a depth picture), where a place of the world lands on it, where the lamp stands, and how
    // far the haze draws back (0: not at all, the haze as it was made)
    private val frontShadow: Uniform<dynamic> = uniform(null)
    private val frontShadowMatrix: Uniform<dynamic> = uniform(mat4())
    private val frontLamp = vec()
    private val frontLampUniform = uniform(frontLamp)
    private val parts = uniform(0.0)

    /** The sun behind the drummer, as the haze behind the mesh takes it: its colour times its brightness. */
    private val sunLight = vec()
    private val sunUniform = uniform(sunLight)

    /** Where the sun's middle is (x, y on the surface behind the mesh), and how large its glow is against as made. */
    private val sunAt = Three.addon.createVector2(x = Fence.SUN_X, y = Fence.SUN_Y)
    private val sunAtUniform = uniform(sunAt)
    private val sunSize = uniform(1.0)

    // The time of day of the two worlds (WorldLight), as the haze behind the mesh takes it: what its light is
    // multiplied by, how thick it is, and its light where no lamp reaches; as made white, 1, and a breath of grey blue
    private val hazeTint = vec(1.0, 1.0, 1.0)
    private val hazeThick = uniform(1.0)
    private val hazeBase = vec(0.006, 0.006, 0.009)

    /** The random numbers the noise is read from (see the top of the file). */
    private val noise: Data3DTexture = noiseBlock()

    /** The maps of the stage, where the fog is worked out as maps; null where every pixel works it out. */
    private val maps: Maps? = if (mapped && canHoldMaps()) Maps() else null

    /** The sheets' shape, one for all of them. */
    private val sheet: BufferGeometry = Three.addon.createPlaneGeometry(bed.wide, bed.tall)

    private val layers: List<Layer>
    private val target = vec()

    init {
        // a fog of two sheets at least, from the floor to the top; every sheet has its map in the picture, and the
        // picture no map to spare
        require(quality.layers >= 2) { "a fog of ${quality.layers} sheets is no fog to make" }
        require(quality.cols * quality.rows == quality.layers) {
            "${quality.layers} sheets do not fill ${quality.cols} by ${quality.rows} maps"
        }

        val count = quality.layers
        val cols = quality.cols

        layers = (0 until count).map { i ->
            val share = i.toDouble() / (count - 1)
            val alpha = uniform(0.0)
            val uniforms = uniformsOf("uAlpha" to alpha)

            if (maps != null) {
                uniforms["uMaps"] = uniform(maps.picture.texture)
                uniforms["uCell"] = uniform(Three.addon.createVector2(x = (i % cols).toDouble(), y = (i / cols).toDouble()))
            } else {
                fogUniforms(uniforms)
                uniforms["uHeight"] = uniform(share)
            }

            val material = Three.addon.createShaderMaterial(jsObject<ShaderMaterialParameters> {
                this.uniforms = uniforms
                vertexShader = if (maps != null) MAP_VERTEX else VERTEX
                fragmentShader = if (maps != null) sheetFragment(quality = quality, bed = bed) else fragment(bed)
                transparent = true
                depthWrite = false
                side = MaterialSide.DoubleSide
                blending = Blending.CustomBlending
                blendSrc = BlendFactor.OneFactor
                blendDst = BlendFactor.OneMinusSrcAlphaFactor
            })
            // a sheet shows the camera one side only: drawn once, where three.js draws what is see-through and
            // two-sided twice
            material.forceSinglePass = true
            val mesh = Three.addon.createMesh(sheet, material)
            place(mesh = mesh, share = share)
            mesh.renderOrder = 10 + i
            scene.add(mesh)

            Layer(mesh = mesh, material = material, alpha = alpha)
        }
    }

    /**
     * Puts a sheet where it stands, [share] of the way through the stack: level, from the lowest to the highest; or
     * upright, from just behind the mesh to just before the surface behind it.
     */
    private fun place(mesh: Mesh, share: Double): Unit = when (bed) {
        FogBed.Floor -> {
            mesh.rotation.x = -PI / 2
            mesh.position.y = BOTTOM + (TOP - BOTTOM) * share
        }

        FogBed.BehindMesh -> {
            mesh.position.y = bed.tall / 2
            mesh.position.z = HAZE_FRONT + (HAZE_BACK - HAZE_FRONT) * share
        }
    }

    /** Where a sheet stands across the stack: a level one's height, an upright one's place toward the back. */
    private fun across(of: Vector3): Double = when (bed) {
        FogBed.Floor -> of.y
        FogBed.BehindMesh -> of.z
    }

    /** Puts what the fog is made from into [uniforms]. */
    private fun fogUniforms(uniforms: Uniforms) {
        uniforms["uTime"] = time
        uniforms["uNoise"] = uniform(noise)
        uniforms["uPos"] = positionsUniform
        uniforms["uDir"] = directionsUniform
        uniforms["uCol"] = colorsUniform
        uniforms["uCos"] = cosinesUniform
        uniforms["uSun"] = sunUniform
        uniforms["uSunAt"] = sunAtUniform
        uniforms["uSunSize"] = sunSize
        uniforms["uHazeTint"] = uniform(hazeTint)
        uniforms["uHazeThick"] = hazeThick
        uniforms["uHazeBase"] = uniform(hazeBase)
        uniforms["uFrontShadow"] = frontShadow
        uniforms["uFrontShadowMatrix"] = frontShadowMatrix
        uniforms["uFrontLamp"] = frontLampUniform
        uniforms["uParts"] = parts
    }

    /**
     * How far the haze behind the mesh draws back from the band's shadows this frame: [share] from 0 (not at all, the
     * haze as it was made) to 1, where the front lamp [front] throws them, by its shadow map as the frame before drew
     * it. Not at all where the lamp has no map yet (it is made at the first frame it casts). The fog on the floor
     * does not take it.
     */
    fun clearings(front: Lamp, share: Double) {
        val shadow: dynamic = front.light.asDynamic().shadow
        val depth: dynamic = shadow?.map?.depthTexture

        if (depth == null) {
            // none for the frame the lamp has no map (the first one, or a new shadow quality: Lights freed the old
            // map); a freed one kept here would be made anew on the card by three.js and never freed
            frontShadow.value = null
            parts.value = 0.0

            return
        }

        // the lamp's map is handed on whether the haze parts or not: with the haze's shadow sampler left empty all
        // the time, the fence lost the front lamp's light in the stills (2026-11-20; three.js's stand-in for an
        // empty shadow sampler seems to disturb the lamp's own map), so it is empty for a frame at most
        frontShadow.value = depth
        frontShadowMatrix.value = shadow.matrix
        frontLamp.copy(front.light.position)
        parts.value = if (share > 0.0) share else 0.0
    }

    /**
     * The sun behind the drummer for this frame, in [color] at [level] (from 0), [size] times as large as made, its
     * middle [height] metres up: the haze behind the mesh glows with it. The fog on the floor does not take it.
     */
    fun sun(color: Color, level: Double, size: Double = 1.0, height: Double = Fence.SUN_Y) {
        sunLight.set(color.r, color.g, color.b).multiplyScalar(level)
        sunSize.value = size
        sunAt.set(Fence.SUN_X, height)
    }

    /**
     * The haze behind the mesh at the time of day [world] ([WorldLight]): its tint, its thickness and its light where no
     * lamp reaches; with the time off, as made. The fog on the floor does not take it.
     */
    fun world(world: WorldTime) {
        val (r, g, b) = WorldLight.hazeTint(world)
        val (br, bg, bb) = WorldLight.hazeBase(world)

        hazeTint.set(r, g, b)
        hazeThick.value = WorldLight.hazeThickness(world)
        hazeBase.set(br, bg, bb)
    }

    /** Sets the fog for [t], lit by the first of [lamps], seen by [camera]; and works out its maps. */
    fun update(t: Double, lamps: List<Lamp>, camera: Camera) {
        time.value = t

        for (i in 0 until min(LAMPS, lamps.size)) {
            val light = lamps[i].light
            positions[i].copy(light.position)
            directions[i].copy(target.copy(light.target.position).sub(light.position).normalize())
            colors[i].set(light.color.r, light.color.g, light.color.b).multiplyScalar(light.intensity)
            cosines[i] = cos(light.angle)
        }

        val share = quality.share
        val seenFrom = across(camera.position)
        // behind the mesh the sheets are drawn first of all that is see-through, as all the rest stands in front of
        // them; the mesh comes right after them (Fence.ORDER)
        val first = when (bed) {
            FogBed.Floor -> 10.0
            FogBed.BehindMesh -> 0.0
        }

        for (layer in layers) {
            val at = across(layer.mesh.position)
            // a sheet seen edge-on would show as a line: it fades out as the camera comes to its height (or, upright,
            // to its depth)
            val off = abs(seenFrom - at)
            layer.alpha.value = share * min(1.0, off / 0.25)
            // drawn from the far side toward the camera: from the floor up when it looks from above, from the back
            // to the mesh when it looks from the front
            layer.mesh.drawOrder = first + (if (seenFrom > at) 1 else -1) * at * 10
        }

        maps?.draw(camera)
    }

    /**
     * Hides the sheets ([hidden]) or shows them again: for a drawing of the scene they have no place in (the mirror
     * floor's, [MirrorFloor], which does not mirror the fog that lies on the floor).
     */
    fun hide(hidden: Boolean) {
        for (layer in layers) {
            layer.mesh.visible = !hidden
        }
    }

    /**
     * Takes the sheets out of the scene and frees all the fog holds on the graphics card: the sheets' materials and
     * their shape, the noise's picture, and the maps with what draws them. The fog is gone after it; call it once.
     */
    fun dispose() {
        for (layer in layers) {
            scene.remove(layer.mesh)
            layer.material.dispose()
        }

        sheet.dispose()
        noise.dispose()
        maps?.dispose()
    }

    /**
     * Whether the graphics card can be drawn into in half-float numbers, which the maps need: the light they hold
     * is not capped at 1.
     */
    private fun canHoldMaps(): Boolean {
        val extensions = renderer.extensions

        return extensions.has("EXT_color_buffer_float") || extensions.has("EXT_color_buffer_half_float")
    }

    /**
     * The fog as maps of the stage: one [picture] with a map for each sheet, side by side, and the one sheet that
     * fills it each frame.
     */
    private inner class Maps {
        val picture: WebGLRenderTarget = Three.addon.createWebGLRenderTarget(
            width = quality.cols * bed.mapWide,
            height = quality.rows * bed.mapTall,
            options = jsObject<RenderTargetOptions> {
                type = TextureDataType.HalfFloatType
                depthBuffer = false
                stencilBuffer = false
            },
        )

        private val filler: Scene = Three.addon.createScene()

        init {
            val uniforms = uniformsOf()
            fogUniforms(uniforms)

            val material = Three.addon.createShaderMaterial(jsObject<ShaderMaterialParameters> {
                this.uniforms = uniforms
                vertexShader = FILL_VERTEX
                fragmentShader = fillFragment(quality = quality, bed = bed)
                depthWrite = false
                depthTest = false
            })
            val mesh = Three.addon.createMesh(Three.addon.createPlaneGeometry(2.0, 2.0), material)
            mesh.frustumCulled = false
            filler.add(mesh)
        }

        /** Works the maps out for this frame. [camera]: any; the sheet fills the picture whatever it sees. */
        fun draw(camera: Camera) {
            // where the renderer draws to: the screen, or a picture of someone's own
            val drawnTo = renderer.getRenderTarget()

            try {
                renderer.setRenderTarget(picture)
                renderer.render(filler, camera)
            } finally {
                renderer.setRenderTarget(drawnTo)
            }
        }

        fun dispose() {
            picture.dispose()
            filler.disposeTree()
        }
    }

    private companion object {
        /** The noise's block has this many random numbers each way; it repeats after them. */
        const val NOISE = 64

        /**
         * A block of [NOISE] cubed random numbers as a picture in three directions, repeating in each. The numbers
         * are the same on every run, so a still shows the same fog.
         */
        fun noiseBlock(): Data3DTexture {
            val random = Random(seed = 20261116)
            val values = Uint8Array(NOISE * NOISE * NOISE)

            for (i in 0 until values.length) {
                values[i] = random.nextInt(256).toByte()
            }

            val block = Three.addon.createData3DTexture(data = values, width = NOISE, height = NOISE, depth = NOISE)

            block.format = TextureFormat.RedFormat
            block.minFilter = TextureFilter.LinearFilter
            block.magFilter = TextureFilter.LinearFilter
            block.wrapS = TextureWrapping.RepeatWrapping
            block.wrapT = TextureWrapping.RepeatWrapping
            block.wrapR = TextureWrapping.RepeatWrapping
            block.unpackAlignment = 1
            block.needsUpdate = true

            return block
        }

        const val LAMPS = 5

        /** On the floor: the lowest and the highest sheet's height, in metres. */
        const val BOTTOM = 0.03
        const val TOP = 0.50

        /** Behind the mesh: the first sheet's place, just behind the mesh, and the last's, just before the back. */
        const val HAZE_FRONT = Fence.MESH_Z - 0.3
        const val HAZE_BACK = Fence.BACK_Z + 0.3

        /**
         * How far toward the front lamp a place of the haze is tested for the band's shadow, in metres: past the
         * fence's posts (0.35 m before the first sheet) and short of the riser's back (some 3 m before the place
         * then) and the band.
         */
        const val BAND_GAP = 2.5

        /** How far around a place of the haze the band's shadow is read as well, in metres: the clearing's soft edge. */
        const val CLEARING = 0.12

        /**
         * What the fog is made from, and what both beds share: the noise, and the light of the lamps at a place
         * (each lights the fog inside its cone, weaker with the square of the distance).
         */
        private val FOG_COMMON = """
  uniform float uTime;
  uniform vec3 uPos[${LAMPS}], uDir[${LAMPS}], uCol[${LAMPS}];
  uniform float uCos[${LAMPS}];
  uniform vec3 uSun;
  uniform highp sampler3D uNoise;

  // a random number at every whole point, blended smoothly between them: the card blends, between the points' middles
  float noise(vec3 x) {
    vec3 i = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return texture(uNoise, (i + f + 0.5) / ${NOISE}.0).r;
  }

  vec3 lampsAt(vec3 world) {
    vec3 light = vec3(0.0);
    for (int i = 0; i < ${LAMPS}; i++) {
      vec3 to = uPos[i] - world;
      float far2 = dot(to, to);
      float cone = smoothstep(uCos[i], uCos[i] + 0.12, dot(-normalize(to), uDir[i]));
      light += uCol[i] * cone / far2;
    }
    return light;
  }"""

        /**
         * The fog at a place on the stage, for both ways of drawing it: `fogAt(world, height)` gives the light it
         * sends (as the sheet adds it) and how much it veils, for a sheet at `height` through the stack (0 the
         * first, 1 the last) that lets all of it through; the sheet's own share is multiplied in by whoever draws it.
         */
        fun fogAt(bed: FogBed): String = FOG_COMMON + when (bed) {
            FogBed.Floor -> FLOOR_AT
            FogBed.BehindMesh -> HAZE_AT
        }

        /** On the floor: thick at the floor and near the machines, only its billows reach the top. */
        private val FLOOR_AT = """
  vec4 fogAt(vec3 world, float height) {
    // the wind: everything drifts slowly across the stage and a little toward the audience
    vec3 p = vec3(world.x + 0.11 * uTime, world.y * 5.0 + 0.02 * uTime, world.z + 0.05 * uTime);
    // the turbulence: a slower, larger noise pushes the fog about, so it curls instead of sliding as one sheet
    vec3 stir = vec3(noise(p * 0.45 + vec3(0.0, 0.03 * uTime, 0.0)), 0.0, noise(p * 0.45 + vec3(17.0, -0.025 * uTime, 5.0))) - 0.5;
    vec3 q = p * 1.05 + 1.7 * stir;
    float d = 0.55 * noise(q) + 0.30 * noise(q * 2.1 + 3.0) + 0.15 * noise(q * 4.3 + 7.0);
    // the machines stand behind the band, left and right: thick there, thinner toward the audience, breathing slowly
    vec2 at = world.xz;
    float near = max(exp(-dot(at - vec2(-4.5, -3.2), at - vec2(-4.5, -3.2)) / 30.0), exp(-dot(at - vec2(4.5, -3.2), at - vec2(4.5, -3.2)) / 30.0));
    float amount = (0.30 + 0.70 * near) * (0.85 + 0.15 * sin(0.05 * uTime + at.x * 0.2));
    // it settles: at the floor nearly everywhere, higher up only where it billows
    float density = smoothstep(0.36 + 0.42 * height, 0.74 + 0.16 * height, d + 0.40 * amount);
    density *= 1.0 - smoothstep(8.0, 11.0, length(at * vec2(0.8, 1.0)));
    vec3 light = vec3(0.004, 0.005, 0.008) + lampsAt(world);
    return vec4(0.035 * light * density, 0.5 * density);     // it glows where lit, and veils a little what is behind
  }"""

        /**
         * Behind the mesh: a haze that rises slowly and drifts across, thick low and thinning upward to some 6 m, in
         * billows with clear gaps between them; lit by the sun behind the drummer, near it most, and faintly by the
         * lamps. It veils less than the fog on the floor, so the shadows on the back still read through it.
         *
         * Where `uParts` is above 0 (`?haze=parts`), it thins where the front lamp's light is held back by the band
         * (`bandShadow`): the place is moved [BAND_GAP] toward the lamp first, so only what stands well before the
         * haze counts (the band, the riser), not the fence's posts right before it; and four more readings
         * [CLEARING] m around it make the clearing's edge soft and a little wider than the shadow, as if the haze drew
         * back from it.
         */
        private val HAZE_AT = """
  uniform highp sampler2DShadow uFrontShadow;
  uniform mat4 uFrontShadowMatrix;
  uniform vec3 uFrontLamp;
  uniform float uParts;
  uniform vec2 uSunAt;
  uniform float uSunSize;
  uniform vec3 uHazeTint;
  uniform float uHazeThick;
  uniform vec3 uHazeBase;

  // 1 where the front lamp's light is held back on its way to this place, 0 where it gets there (or the place is
  // outside what the lamp's map sees)
  float heldBack(vec3 at) {
    vec4 c = uFrontShadowMatrix * vec4(at, 1.0);
    if (c.w <= 0.0) return 0.0;
    c.xyz /= c.w;
    if (c.x < 0.0 || c.x > 1.0 || c.y < 0.0 || c.y > 1.0 || c.z > 1.0) return 0.0;
    return 1.0 - texture(uFrontShadow, c.xyz);
  }

  // how much this place of the haze lies in the band's shadow, 0 to 1, its edge soft and a little wider than the shadow
  float bandShadow(vec3 world) {
    vec3 toLamp = normalize(uFrontLamp - world);
    vec3 near = world + toLamp * ${BAND_GAP.glsl()};
    vec3 across = normalize(cross(toLamp, vec3(0.0, 1.0, 0.0))) * ${CLEARING.glsl()};
    vec3 up = normalize(cross(across, toLamp)) * ${CLEARING.glsl()};
    float held = 2.0 * heldBack(near) + heldBack(near + across) + heldBack(near - across) + heldBack(near + up) + heldBack(near - up);
    return smoothstep(0.12, 0.7, held / 6.0);
  }

  vec4 fogAt(vec3 world, float height) {
    // it drifts slowly across and rises; the turbulence curls it
    vec3 p = vec3(world.x * 0.55 + 0.06 * uTime, world.y * 0.7 - 0.045 * uTime, world.z * 0.9 + 0.02 * uTime);
    vec3 stir = vec3(noise(p * 0.4 + vec3(0.0, 0.02 * uTime, 0.0)), noise(p * 0.4 + vec3(17.0, -0.017 * uTime, 5.0)), 0.0) - 0.5;
    vec3 q = p + 1.7 * stir;
    float d = 0.55 * noise(q) + 0.30 * noise(q * 2.1 + 3.0) + 0.15 * noise(q * 4.3 + 7.0);
    // thick low, thinning upward, its upper edge ragged; on the floor it lies thick everywhere
    float body = 1.0 - smoothstep(0.15, 1.0, world.y / 6.5 + 0.5 * (d - 0.5));
    float density = smoothstep(0.34, 0.80, d + 0.22 * body - 0.08) * body;
    density = max(density, (1.0 - smoothstep(0.0, 1.2, world.y)) * smoothstep(0.15, 0.55, d));
    density *= 1.0 - smoothstep(8.5, 11.5, abs(world.x));
    // it gives way to the band's shadows (?haze=parts): nearly all of it, a whisper stays
    if (uParts > 0.0) {
      density *= 1.0 - uParts * 0.94 * bandShadow(world);
    }
    // the time of day (WorldLight): thicker or thinner
    density = min(1.0, density * uHazeThick);
    // the sun behind the drummer: a close glow and a wide one, as large as the time of day makes it
    vec3 fromSun = world - vec3(uSunAt, ${Fence.BACK_Z.glsl()});
    float sun2 = dot(fromSun, fromSun) / (uSunSize * uSunSize);
    vec3 light = uHazeBase + lampsAt(world) + uSun * (7.0 / (1.0 + sun2 / 2.5) + 1.2 / (1.0 + sun2 / 40.0));
    return vec4(0.08 * light * uHazeTint * density, 0.30 * density);
  }"""

        // A sheet that works the fog out for each of its pixels

        val VERTEX = """
  varying vec3 vWorld;
  void main() {
    vec4 world = modelMatrix * vec4(position, 1.0);
    vWorld = world.xyz;
    gl_Position = projectionMatrix * viewMatrix * world;
  }"""

        fun fragment(bed: FogBed) = """
  varying vec3 vWorld;
  uniform float uHeight, uAlpha;
  ${fogAt(bed)}

  void main() {
    gl_FragColor = fogAt(vWorld, uHeight) * uAlpha;
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
  }"""

        // The maps: one sheet fills the whole picture, and each of its points finds its map, and its place on the stage

        val FILL_VERTEX = """
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = vec4(position.xy, 0.0, 1.0);
  }"""

        fun fillFragment(quality: FogQuality, bed: FogBed) = """
  varying vec2 vUv;
  ${fogAt(bed)}

  void main() {
    vec2 map = vUv * vec2(${quality.cols}.0, ${quality.rows}.0);
    vec2 cell = floor(map);
    vec2 here = fract(map);
    float height = (cell.y * ${quality.cols}.0 + cell.x) / ${quality.layers - 1}.0;
    vec3 world = ${worldOf(bed)};
    gl_FragColor = fogAt(world, height);
  }"""

        /** Where a point of a sheet's map (`here`, 0 to 1 each way) is on the stage, for the sheet at `height`. */
        private fun worldOf(bed: FogBed): String = when (bed) {
            // a sheet lies flat: its upper edge is the far one, toward the wall
            FogBed.Floor ->
                "vec3((here.x - 0.5) * ${bed.wide.glsl()}, ${BOTTOM.glsl()} + ${(TOP - BOTTOM).glsl()} * height, " +
                    "(0.5 - here.y) * ${bed.tall.glsl()})"

            // a sheet stands upright, its lower edge on the floor
            FogBed.BehindMesh ->
                "vec3((here.x - 0.5) * ${bed.wide.glsl()}, here.y * ${bed.tall.glsl()}, " +
                    "${HAZE_FRONT.glsl()} + ${(HAZE_BACK - HAZE_FRONT).glsl()} * height)"
        }

        // A sheet that reads its map

        val MAP_VERTEX = """
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }"""

        fun sheetFragment(quality: FogQuality, bed: FogBed) = """
  varying vec2 vUv;
  uniform sampler2D uMaps;
  uniform vec2 uCell;
  uniform float uAlpha;

  void main() {
    // not from the map's outermost half points: there the card would blend in the map next to it
    vec2 edge = vec2(0.5 / ${bed.mapWide}.0, 0.5 / ${bed.mapTall}.0);
    vec2 here = clamp(vUv, edge, 1.0 - edge);
    gl_FragColor = texture2D(uMaps, (uCell + here) / vec2(${quality.cols}.0, ${quality.rows}.0)) * uAlpha;
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
  }"""
    }
}

/** The number as a shader takes it: always with its decimals (26.0 would else be written 26, a whole number). */
private fun Double.glsl(): String = toFixed(4)
