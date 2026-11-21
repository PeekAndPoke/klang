/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.toFixed
import io.peekandpoke.klang.workshop.threejs.Blending
import io.peekandpoke.klang.workshop.threejs.CompiledShader
import io.peekandpoke.klang.workshop.threejs.createCylinderGeometry
import io.peekandpoke.klang.workshop.threejs.depthWrite
import io.peekandpoke.klang.workshop.threejs.onBeforeCompile
import io.peekandpoke.klang.workshop.threejs.scale
import io.peekandpoke.klang.workshop.threejs.set
import io.peekandpoke.klang.workshop.threejs.toneMapped
import io.peekandpoke.klang.workshop.threejs.uniform
import io.peekandpoke.kraft.addons.threejs.js.Group
import io.peekandpoke.kraft.addons.threejs.js.MeshBasicMaterial
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.utils.jsObject
import kotlinx.browser.document
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The drummer's riser, built into [scene] (KLANG-00478; the maintainer, 2026-11-21: "The drum pedestal now looks
 * pretty 'generic' compared with the rest of the scene", and of the idea built here: "i like version A"): a low
 * six-sided plinth of satin black stone, a flat side toward the audience, its top edge cut off, a few bold gold lines
 * on its sides in the manner of the lines on the band, and under it, in a narrow gap, a warm glow that also lies on
 * the floor around it and beats with the bass drum ([light]).
 *
 * It mirrors nothing itself: the stone only catches the lamps' highlights. The mirror floor shows it as it shows
 * everything. The gold lines are worked out on the sides and go as they get thinner than a pixel, so from afar the
 * plinth is its shape and its glow, and nothing flickers.
 *
 * Its top is [Stage.RISER] high and holds the kit as the box before it did. Everything it makes is in the scene and is
 * freed with it.
 */
class Riser(scene: Scene) {
    /** The plinth and its glow: what stands on the floor (the stage darkens the floor under it). */
    val body: Group = Three.addon.createGroup()

    /** The glow in the gap under the plinth, and the same light where it lies on the floor around it. */
    private val gap: MeshBasicMaterial
    private val pool: MeshBasicMaterial

    /** How bright the gold lines' own faint light is: it goes with the stage's light. */
    private val lines = uniform(1.0)

    init {
        val stone = Three.addon.createMeshStandardMaterial(jsObject {
            color = 0x08090c
            roughness = STONE_ROUGH
            metalness = 0.0
        })
        // six flat sides, each shaded as one plane
        stone.asDynamic().flatShading = true

        val sides = Three.addon.createMeshStandardMaterial(jsObject {
            color = 0x08090c
            roughness = STONE_ROUGH
            metalness = 0.0
        })
        sides.asDynamic().flatShading = true
        sides.onBeforeCompile("riser-gold-lines") { shader: CompiledShader ->
            shader.uniforms["uRiserLines"] = lines
            shader.vertexShader = shader.vertexShader
                .replaceFirst("#include <common>", "#include <common>\nvarying vec2 vRiser;")
                .replaceFirst("#include <begin_vertex>", "#include <begin_vertex>\nvRiser = uv;")
            shader.fragmentShader = shader.fragmentShader
                .replaceFirst("#include <common>", "#include <common>\nvarying vec2 vRiser;\nuniform float uRiserLines;\n$GOLD_LINES")
                .replaceFirst("#include <color_fragment>", "#include <color_fragment>\nfloat riserLine = riserLineAt(vRiser);\n" +
                    "diffuseColor.rgb = mix(diffuseColor.rgb, vec3($GOLD), riserLine);")
                .replaceFirst("#include <roughnessmap_fragment>", "#include <roughnessmap_fragment>\nroughnessFactor = mix(roughnessFactor, 0.55, riserLine);")
                .replaceFirst("#include <metalnessmap_fragment>", "#include <metalnessmap_fragment>\nmetalnessFactor = mix(metalnessFactor, 0.6, riserLine);")
                .replaceFirst(
                    "#include <emissivemap_fragment>",
                    "#include <emissivemap_fragment>\ntotalEmissiveRadiance += vec3($GOLD) * ${LINE_LIGHT.toFixed(3)} * riserLine * uRiserLines;",
                )
        }

        // the sides, from the gap up to where the top edge is cut off
        val wall = Three.addon.createMesh(side(top = RADIUS, bottom = RADIUS, height = SIDE_HIGH, open = true), sides)
        wall.position.y = GAP + SIDE_HIGH / 2

        // the cut-off edge and the top
        val edge = Three.addon.createMesh(side(top = RADIUS - CHAMFER, bottom = RADIUS, height = CHAMFER, open = false), stone)
        edge.position.y = GAP + SIDE_HIGH + CHAMFER / 2

        // the foot the plinth stands on, set in: the gap around it glows
        gap = Three.addon.createMeshBasicMaterial(jsObject { color = GLOW_COLOR })
        gap.toneMapped = false

        val foot = Three.addon.createMesh(side(top = RADIUS - SET_IN, bottom = RADIUS - SET_IN, height = GAP, open = true), gap)
        foot.position.y = GAP / 2

        for (part in listOf(wall, edge)) {
            part.castShadow = true
            part.receiveShadow = true
        }

        body.add(wall)
        body.add(edge)
        body.add(foot)
        // six sides of one length would be as deep as wide: it is pressed flatter, as the kit needs it
        body.scale.set(1.0, 1.0, DEPTH / (RADIUS * sqrt(3.0)))
        body.position.set(0.0, 0.0, MIDDLE_Z)
        scene.add(body)

        // the glow on the floor around the plinth: a soft warm ring, added to what is there
        pool = Three.addon.createMeshBasicMaterial(jsObject {
            color = GLOW_COLOR
            transparent = true
        })
        pool.asDynamic().map = ring()
        pool.asDynamic().blending = Blending.AdditiveBlending
        pool.depthWrite = false
        pool.toneMapped = false

        val onFloor = Three.addon.createMesh(Three.addon.createPlaneGeometry(1.0, 1.0), pool)
        onFloor.rotation.x = -PI / 2
        onFloor.scale.set(2 * RADIUS + 2 * REACH, DEPTH + 2 * REACH, 1.0)
        onFloor.position.set(0.0, POOL_Y, MIDDLE_Z)
        scene.add(onFloor)
    }

    /**
     * The riser's own light for this frame: at [level] of the stage's own light (0 to 1: a show's light coming up, a
     * dark world), beating with the bass drum, last hit [sinceKick] seconds ago (null: not hit yet).
     */
    fun light(level: Double, sinceKick: Double?) {
        val beat = if (sinceKick == null || sinceKick < 0.0) 0.0 else exp(-sinceKick / BEAT_FADE)
        val share = level.coerceIn(0.0, 1.0)

        gap.color.setHex(GLOW_COLOR).scale(share * (GAP_REST + (1.0 - GAP_REST) * beat))
        pool.color.setHex(GLOW_COLOR).scale(share * (POOL_REST + (POOL_BEAT - POOL_REST) * beat))
        lines.value = share
    }

    /** One six-sided ring of the plinth: [top] and [bottom] its radii, a flat side toward the audience. */
    private fun side(top: Double, bottom: Double, height: Double, open: Boolean) = Three.addon.createCylinderGeometry(
        radiusTop = top,
        radiusBottom = bottom,
        height = height,
        radialSegments = 6,
        heightSegments = 1,
        openEnded = open,
        thetaStart = PI / 6,
        thetaLength = 2 * PI,
    )

    /** The picture of the glow on the floor: nothing in the middle (the plinth stands there), a soft ring, nothing far out. */
    private fun ring() = Three.addon.createCanvasTexture(
        (document.createElement("canvas") as HTMLCanvasElement).also { canvas ->
            canvas.width = 128
            canvas.height = 128

            val g = canvas.getContext("2d") as CanvasRenderingContext2D
            val fill = g.createRadialGradient(64.0, 64.0, 30.0, 64.0, 64.0, 64.0)

            fill.addColorStop(0.0, "rgba(255,255,255,0)")
            fill.addColorStop(0.42, "rgba(255,255,255,1)")
            fill.addColorStop(1.0, "rgba(255,255,255,0)")
            g.fillStyle = fill
            g.fillRect(0.0, 0.0, 128.0, 128.0)
        },
    )

    companion object {
        /** Where the plinth's middle stands along z, and how deep it is front to back, in metres (as the box was). */
        const val MIDDLE_Z = -1.75
        const val DEPTH = 2.3

        /** From the middle to a corner, in metres: its corners point left and right. */
        const val RADIUS = 1.62

        /** The gap under the plinth that glows, how far its foot is set in, and how much of the top edge is cut off. */
        const val GAP = 0.045
        const val SET_IN = 0.14
        const val CHAMFER = 0.05

        /** How high the sides are between the gap and the cut-off edge: the top is [Stage.RISER] high. */
        const val SIDE_HIGH = Stage.RISER - GAP - CHAMFER

        /** The glow's colour; how bright it is in the gap between two hits, against 1 at a hit. */
        const val GLOW_COLOR = 0xff9a3c
        const val GAP_REST = 0.3

        /**
         * How rough the stone is: matt, so a lamp that shines on a side straight on (the low front lamp) does not
         * turn the whole side into one bright plate (the maintainer, 2026-11-21: "It looks as if the riser is
         * self-illuminating ... we have to make sure that it does not fight Sirene's dress when she walks around").
         */
        const val STONE_ROUGH = 0.9

        /** The glow on the floor: how far it reaches around the plinth (metres), how bright at rest and at a hit. */
        const val REACH = 0.9
        const val POOL_REST = 0.04
        const val POOL_BEAT = 0.14
        const val POOL_Y = 0.006

        /** How fast a hit's light goes, in seconds to 1/e. */
        const val BEAT_FADE = 0.14

        /**
         * The gold of the lines (linear red, green, blue): an old, dull gold, quieter than the lines on the band, which
         * stand right before it; and how bright their own light is: none, they show only where a lamp finds them.
         */
        private const val GOLD = "0.42, 0.29, 0.10"
        private const val LINE_LIGHT = 0.0

        /**
         * The gold lines on a side, 0 to 1, from the side's own coordinates (x: round the plinth, 0 to 1 over its six
         * sides; y: up the side, 0 to 1): a line near the foot, one near the top, and between them a zigzag, two
         * peaks to a side. The lines are some 1.4 cm wide; where one gets thinner than a pixel it goes, so nothing
         * flickers from afar.
         */
        private val GOLD_LINES = """
float riserLineAt(vec2 at) {
  float wide = ${RADIUS.toFixed(3)};
  float high = ${SIDE_HIGH.toFixed(3)};
  float f = fract(at.x * 6.0);
  float y = at.y * high;
  float zig = 0.07 + (high - 0.14) * abs(2.0 * fract(f * 2.0) - 1.0);
  float slope = (high - 0.14) * 4.0 / wide;
  float d = min(min(abs(y - 0.035), abs(y - (high - 0.035))), abs(y - zig) / sqrt(1.0 + slope * slope));
  float w = 0.007;
  float aa = max(fwidth(d), 1e-5);
  return (1.0 - smoothstep(w - aa, w + aa, d)) * clamp(w / aa, 0.0, 1.0);
}
"""
    }
}
