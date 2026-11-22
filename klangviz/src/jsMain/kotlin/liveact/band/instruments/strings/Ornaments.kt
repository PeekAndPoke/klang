/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.strings

import io.peekandpoke.klang.viz.liveact.band.dress.INLAY_GLSL
import io.peekandpoke.klang.viz.liveact.toFixed
import io.peekandpoke.klang.workshop.threejs.CompiledShader
import io.peekandpoke.klang.workshop.threejs.onBeforeCompile
import io.peekandpoke.klang.workshop.threejs.set
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import kotlin.math.floor
import kotlin.math.pow

// An instrument's gold (KLANG-00478; the maintainer, 2026-11-21: "would also be nice if they have ornaments or
// similar"): thin lines drawn by the shader in the instrument's own measure, never modelled, in the manner of the
// lines on the band and on the drummer's riser. A dull old gold, a little quieter than the band's lines, with no light
// of its own: it shows where a lamp or the lacquer's room finds it. Where a line gets thinner than a pixel it fades
// (`klLine`, see INLAY_GLSL), so from afar the instrument is its shape and nothing flickers.

/**
 * A quake's trace across an instrument's body (Beben's: his name means quake), in the body's own measure: from
 * [from] to [to] along the body (x), about the line [y] across it, which bends by [bow] toward the strings at its two
 * ends. A straight line that breaks into a few big peaks at [at] (it rises within some [rise] before and dies away
 * over some [fall] after) and lies straight again: the peaks are [period] apart along it and at most [height] high,
 * the calm ones [calm] of that.
 *
 * Kept simple on purpose: a fine seismograph's trace with a peak every 8 mm, trembling as he played, flickered as the
 * camera moved (the maintainer, 2026-11-21: "it aliases too much when the camera moves, so we need something more
 * simple"). A few long strokes and a bolder line hold still; the trace does not move.
 */
class QuakeTrace(
    val from: Double,
    val to: Double,
    val y: Double,
    val bow: Double,
    val at: Double,
    val rise: Double,
    val fall: Double,
    val height: Double,
    val period: Double,
    val calm: Double,
)

/**
 * A scratch of a claw across an instrument's body (Klaue's: his name means claw), in the body's own measure: from
 * [from] to [to] (x and y), bent by [bow] to its left on the way. It begins broad, where the claw dug in, and runs
 * out to a point.
 */
class ClawMark(val from: Pair<Double, Double>, val to: Pair<Double, Double>, val bow: Double)

/**
 * The gold an instrument wears: a thin line along the body's [edge] (on its rounded edge, just off the face), dots
 * on the fretboard ([markers], with its frets as fine lines), a [quake]'s trace across the body, the scratches of
 * [claws] across it, and [goldHardware] (the bridge, the knobs and the tuners) in place of the steel.
 */
class Ornaments(
    val edge: Boolean = false,
    val markers: Boolean = false,
    val quake: QuakeTrace? = null,
    val claws: List<ClawMark> = emptyList(),
    val goldHardware: Boolean = false,
) {
    /** Whether the body is drawn with gold: it then wears a material of its own (see `StrungInstrument`). */
    val onBody: Boolean get() = edge || quake != null || claws.isNotEmpty()

    companion object {
        /** No gold at all: today's instruments. */
        val NONE = Ornaments()
    }
}

/**
 * The gold, linear red, green, blue: an old, dull gold, a little quieter than the band's lines (`WorldLight.gold`);
 * how rough and how metallic it is. Never emissive.
 */
internal val ORNAMENT_GOLD_RGB = Triple(0.50, 0.35, 0.12)
private val ORNAMENT_GOLD = ORNAMENT_GOLD_RGB.toList().joinToString(", ") { it.toFixed(3) }
internal const val ORNAMENT_GOLD_ROUGH = 0.45
internal const val ORNAMENT_GOLD_METAL = 0.7

/** How far below the face the line along the edge lies on the rounded edge, and its half width there (in depth). */
private const val EDGE_DROP = 0.0013
private const val EDGE_HALF = 0.00035

/** The quake's trace: its half width. */
private const val QUAKE_HALF = 0.0016

/** A claw's scratch: its half width where it is broadest. Bold, as the quake's trace is: a fine line would flicker. */
private const val CLAW_HALF = 0.0034

/**
 * Draws the body's gold ([ornaments]) onto [material], a body's (its outline cut from a slab, in the body's own
 * measure: the face at [top] along z). [key] names the program (the design's name: its gold lies where its outline
 * does).
 */
internal fun ornamentBody(material: MeshStandardMaterial, ornaments: Ornaments, top: Double, key: String) {
    val trace = ornaments.quake
    val lines = buildList {
        if (ornaments.edge) {
            add("klLine(abs(p.z - ${(top - EDGE_DROP).toFixed(5)}), ${EDGE_HALF.toFixed(5)})")
        }

        if (trace != null) {
            add("step(${(top - 0.0005).toFixed(5)}, p.z) * klLine(klQuakeDistance(p.xy), ${QUAKE_HALF.toFixed(5)})")
        }

        for (claw in ornaments.claws) {
            val (ax, ay) = claw.from
            val (bx, by) = claw.to

            add(
                "step(${(top - 0.0005).toFixed(5)}, p.z) * klClaw(p.xy, vec2(${ax.toFixed(5)}, ${ay.toFixed(5)}), " +
                    "vec2(${bx.toFixed(5)}, ${by.toFixed(5)}), ${claw.bow.toFixed(5)})"
            )
        }
    }
    val gold = lines.fold("0.0") { all, line -> "max($all, $line)" }
    val functions = INLAY_GLSL + "\n" + (trace?.let { quakeGlsl(it) } ?: "") + (if (ornaments.claws.isEmpty()) "" else CLAW_GLSL) +
        "\nfloat klOrnament(vec3 p) { return $gold; }"

    material.onBeforeCompile("strung-body-$key") { shader: CompiledShader ->
        shader.vertexShader = shader.vertexShader
            .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vKlBody;")
            .replaceFirst("#include <begin_vertex>", "#include <begin_vertex>\nvKlBody = position;")
        shader.fragmentShader = goldOver(
            fragment = shader.fragmentShader
                .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vKlBody;\n$functions"),
            share = "klOrnament(vKlBody)",
        )
    }
}

/**
 * A claw's scratch as GLSL: `klClaw(p, a, b, bow)`, how much of the place `p` (the body's x and y) is the scratch from
 * `a` to `b`: a bent stroke, broad soon after its start and running out to a point.
 */
private val CLAW_GLSL = """
float klClaw(vec2 p, vec2 a, vec2 b, float bow) {
  vec2 ab = b - a;
  float t = clamp(dot(p - a, ab) / dot(ab, ab), 0.0, 1.0);
  vec2 n = normalize(vec2(-ab.y, ab.x));
  vec2 c = a + ab * t + n * bow * 4.0 * t * (1.0 - t);
  float wide = ${CLAW_HALF.toFixed(5)} * smoothstep(0.0, 0.12, t) * pow(1.0 - t, 0.7);
  return wide < 0.0002 ? 0.0 : klLine(length(p - c), wide);
}
"""

/**
 * The quake's trace as GLSL: `klQuakeDistance(p)`, how far the place `p` (the body's x and y) is from the trace. The
 * trace is a line through its peaks, every one of them [QuakeTrace.period] along, the next one on the other side,
 * each as high as the burst there is strong, a little more or less by chance.
 */
private fun quakeGlsl(q: QuakeTrace): String {
    val last = floor((q.to - q.from) / q.period)

    return """
float klQuakeHash(float i) { return fract(sin(i * 12.9898 + 4.1) * 43758.5453); }
vec2 klQuakePeak(float i) {
  float x = ${q.from.toFixed(5)} + i * ${q.period.toFixed(5)};
  float a = x - ${q.at.toFixed(5)};
  float burst = a < 0.0 ? exp(-(a * a) / ${(q.rise * q.rise).toFixed(7)}) : exp(-a / ${q.fall.toFixed(5)});
  float strong = ${q.calm.toFixed(4)} + ${(1 - q.calm).toFixed(4)} * burst;
  float u = (x - ${q.from.toFixed(5)}) / ${(q.to - q.from).toFixed(5)} - 0.5;
  float side = mod(i, 2.0) < 0.5 ? 1.0 : -1.0;
  float h = klQuakeHash(i);
  float lift = ${q.height.toFixed(5)} * strong * (0.55 + 0.45 * h);
  return vec2(x, ${q.y.toFixed(5)} + ${q.bow.toFixed(5)} * 4.0 * u * u + side * lift);
}
float klQuakeDistance(vec2 p) {
  if (p.x < ${(q.from - 0.01).toFixed(5)} || p.x > ${(q.to + 0.01).toFixed(5)}) { return 1.0; }
  float i = floor((p.x - ${q.from.toFixed(5)}) / ${q.period.toFixed(5)});
  float d = 1.0;
  for (int k = -1; k <= 1; k++) {
    float j = clamp(i + float(k), 0.0, ${(last - 1).toFixed(1)});
    vec2 a = klQuakePeak(j);
    vec2 ab = klQuakePeak(j + 1.0) - a;
    float t = clamp(dot(p - a, ab) / dot(ab, ab), 0.0, 1.0);
    d = min(d, length(p - a - ab * t));
  }
  return d;
}
"""
}

/** The fret cells that carry a dot (the twelfth carries two), and how far the two lie from the middle, in scales. */
private val MARKED = listOf(3, 5, 7, 9, 15, 17, 19, 21)
private const val TWELFTH_APART = 0.016

/** A dot's radius, and a fret's half width, in scales (the nut to the bridge); how many frets are drawn. */
private const val DOT = 0.0065
private const val FRET_HALF = 0.0007
private const val FRETS = 22

/** The frets' nickel, linear, how rough and how metallic. */
private const val FRET_COLOR = "0.30, 0.30, 0.32"
private const val FRET_ROUGH = 0.35
private const val FRET_METAL = 0.8

/**
 * Draws gold dots and fine frets on a neck's fretboard onto [material]. The neck's `uv` says where on it a place is,
 * in scales (the nut to the bridge is 1): x from the nut toward the body, y across from the middle; anywhere but on
 * the fretboard y is far out (see `fretboardUv`). So one material serves every neck.
 */
internal fun markNeck(material: MeshStandardMaterial) {
    /** Where the middle of the fret cell [n] lies from the nut, in scales. */
    fun cell(n: Int): Double = 1 - 2.0.pow(-(n - 0.5) / 12)

    val twelfth = cell(12).toFixed(5)
    // the nearest single dot: GLSL's min takes two at a time
    val one = MARKED.map { "length(f - vec2(${cell(it).toFixed(5)}, 0.0))" }.reduce { all, dot -> "min($all, $dot)" }
    val functions = INLAY_GLSL + "\n" + """
float klDots(vec2 f) {
  float one = $one;
  float two = min(length(f - vec2($twelfth, ${TWELFTH_APART.toFixed(4)})), length(f - vec2($twelfth, -${TWELFTH_APART.toFixed(4)})));
  return klLine(min(one, two), ${DOT.toFixed(5)});
}
float klFrets(vec2 f) {
  float n = floor(-12.0 * log2(max(1.0 - f.x, 1e-4)) + 0.5);
  float at = 1.0 - exp2(-n / 12.0);
  return klLine(abs(f.x - at), ${FRET_HALF.toFixed(5)}) * step(0.5, n) * step(n, ${FRETS}.5) * step(abs(f.y), 0.5);
}
"""

    material.onBeforeCompile("strung-neck-marked") { shader: CompiledShader ->
        shader.vertexShader = shader.vertexShader
            .replaceFirst("#include <common>", "#include <common>\nvarying vec2 vKlFret;")
            .replaceFirst("#include <begin_vertex>", "#include <begin_vertex>\nvKlFret = uv;")
        shader.fragmentShader = shader.fragmentShader
            .replaceFirst("#include <common>", "#include <common>\nvarying vec2 vKlFret;\n$functions")
            .replaceFirst(
                "#include <color_fragment>",
                "#include <color_fragment>\nfloat klDot = klDots(vKlFret);\nfloat klFret = klFrets(vKlFret) * (1.0 - klDot);\n" +
                    "diffuseColor.rgb = mix(mix(diffuseColor.rgb, vec3($FRET_COLOR), klFret), vec3($ORNAMENT_GOLD), klDot);",
            )
            .replaceFirst(
                "#include <roughnessmap_fragment>",
                "#include <roughnessmap_fragment>\nroughnessFactor = mix(mix(roughnessFactor, ${FRET_ROUGH.toFixed(3)}, klFret), " +
                    "${ORNAMENT_GOLD_ROUGH.toFixed(3)}, klDot);",
            )
            .replaceFirst(
                "#include <metalnessmap_fragment>",
                "#include <metalnessmap_fragment>\nmetalnessFactor = mix(mix(metalnessFactor, ${FRET_METAL.toFixed(3)}, klFret), " +
                    "${ORNAMENT_GOLD_METAL.toFixed(3)}, klDot);",
            )
    }
}

/**
 * [fragment] (a standard material's fragment shader) with the gold laid over its colour, roughness and metalness
 * where [share] (a GLSL expression, 0 to 1) says.
 */
private fun goldOver(fragment: String, share: String): String = fragment
    .replaceFirst(
        "#include <color_fragment>",
        "#include <color_fragment>\nfloat klGold = $share;\ndiffuseColor.rgb = mix(diffuseColor.rgb, vec3($ORNAMENT_GOLD), klGold);",
    )
    .replaceFirst(
        "#include <roughnessmap_fragment>",
        "#include <roughnessmap_fragment>\nroughnessFactor = mix(roughnessFactor, ${ORNAMENT_GOLD_ROUGH.toFixed(3)}, klGold);",
    )
    .replaceFirst(
        "#include <metalnessmap_fragment>",
        "#include <metalnessmap_fragment>\nmetalnessFactor = mix(metalnessFactor, ${ORNAMENT_GOLD_METAL.toFixed(3)}, klGold);",
    )
