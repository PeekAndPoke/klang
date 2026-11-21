/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.dress

import io.peekandpoke.klang.viz.liveact.toFixed
import io.peekandpoke.klang.workshop.threejs.CompiledShader
import io.peekandpoke.klang.workshop.threejs.onBeforeCompile
import io.peekandpoke.klang.workshop.threejs.set
import io.peekandpoke.klang.workshop.threejs.uniform
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.Vector3

// A pattern across a figure's whole body, in how it throws back the light and not in its colour: where the pattern
// is, the skin is glossy and gives sharp bright highlights; elsewhere it is matt and gives soft dim ones. In the dark
// the body stays black, and the pattern shows only where a lamp catches it. It is drawn from the body's own shape at
// rest (p, in metres: x to the figure's left, y up, z ahead), so it stays on the skin when the figure moves.

/**
 * How far the cloth's ground is darkened, 0 to 1: the stage sets it to how much the front lamp is on. Under the back
 * light alone the whole dress reflects (it looked best so in the dark); lit from the front its ground goes dark and
 * the pattern stays bright. One uniform, shared by every patterned material.
 */
private val dress = uniform(0.0)

/** The value of the shared `dress` uniform (see [dress]): the stage sets it every frame. */
var dressLevel: Double
    get() = dress.value
    set(value) {
        dress.value = value
    }

/**
 * How far the braid's hoops are lifted off the floor, in metres of the figure as modelled. Unlifted they lay at 0.45,
 * 0.90 and 1.35 m, and the one at 0.90 m read as a belt on Sirene's hips, where she is widest (the maintainer,
 * 2026-11-20: "the 'belt' on her dress painting is sitting a little too low, which is most apparent from her back
 * side view"). At her waist (a lift of 0.15) it was "way too far up"; it is a quarter of that way now, his measure:
 * the hoops lie at 0.04 m, 0.49 m (the knee), 0.94 m (the belt, on the upper hip) and 1.38 m (the collar).
 */
private const val BRAID_LIFT = 0.0375

/**
 * A pattern a body can wear: [key] names it (in the program's cache key), [glsl] is its value at the place `p`, an
 * expression; [before] are statements that work out what that expression needs, where one line is not enough.
 *
 * [inlay] is the same pattern drawn as thin lines, 0 to 1, for the gold in the lacquer (`?skin=lacquer`, [Lacquer]):
 * a broad figure (a band, a stripe, a diamond) becomes a thin line along its middle or round its edge, a figure that
 * is a line already stays itself. The same coordinates as [glsl], so the lines lie where the figures lie. It may use
 * what [before] works out, and the functions of [INLAY_GLSL].
 */
enum class ClothPattern(val key: String, val glsl: String, val inlay: String, val before: String = "") {
    /** Broad bands round the body, waving like a tiger's; inlaid, a wavy line along the middle of each band. */
    Tiger(
        key = "tiger",
        glsl = "smoothstep(0.42, 0.58, fract(p.y * 6.5 + 0.16 * sin(p.x * 13.0) + 0.12 * sin(p.z * 17.0)))",
        inlay = "klLine(klNear(p.y * 6.5 + 0.16 * sin(p.x * 13.0) + 0.12 * sin(p.z * 17.0) - 0.75), 0.03)",
    ),

    /** Fine upright pinstripes; inlaid, a hairline along the middle of each. */
    Pinstripe(
        key = "pinstripe",
        glsl = "smoothstep(0.30, 0.45, abs(fract(atan(p.z, p.x) * 4.14) - 0.5) * 2.0)",
        inlay = "klLine(klNear(atan(p.z, p.x) * 4.14), 0.03)",
    ),

    /** One stripe winding up the body; inlaid, a line along its middle. */
    Spiral(
        key = "spiral",
        glsl = "smoothstep(0.40, 0.60, fract(p.y * 4.5 + atan(p.z, p.x) * 0.955))",
        inlay = "klLine(klNear(p.y * 4.5 + atan(p.z, p.x) * 0.955 - 0.78), 0.03)",
    ),

    /** Round spots, row by row offset; inlaid, a ring round each. */
    Spots(
        key = "spots",
        glsl = "1.0 - smoothstep(0.26, 0.34, length(fract(vec3(p.x * 11.0 + 0.5 * floor(p.y * 11.0), p.y * 11.0, p.z * 11.0)) - 0.5))",
        inlay = "klLine(abs(length(fract(vec3(p.x * 11.0 + 0.5 * floor(p.y * 11.0), p.y * 11.0, p.z * 11.0)) - 0.5) - 0.30), 0.02)",
    ),

    /**
     * The marks of a claw: four scratches side by side, slanting down the body, a little bent, thin at both ends;
     * one such mark in most of the fields the body is cut into (six round it, a scratch some 25 cm long), a quarter of
     * them left bare. The fields meet where the angle round the body starts again, so no mark is cut there.
     */
    Claws(
        key = "claws",
        before = "float ca = atan(p.z, p.x); " +
            "vec2 cq = vec2(ca * (2.0 / PI) + p.y * 1.9, p.y * 2.5 - ca * RECIPROCAL_PI); " +
            "vec2 ci = floor(cq); vec2 cf = cq - ci - 0.5; " +
            "float ch = fract(sin(dot(vec2(ci.x + 2.0 * ci.y, mod(ci.x, 4.0)), vec2(12.9898, 78.233))) * 43758.5453); " +
            "float cx = cf.x + 0.35 * cf.y * cf.y - 0.04; " +
            "float cd = abs(abs(abs(cx) - 0.14) - 0.07); " +
            "float cw = 0.040 * (1.0 - smoothstep(0.18, 0.44, abs(cf.y)));",
        glsl = "(1.0 - smoothstep(cw * 0.45, cw + 0.0005, cd)) * step(abs(cx), 0.27) * step(abs(cf.y), 0.44) * step(0.25, ch)",
        inlay = "(1.0 - smoothstep(cw * 0.45, cw + 0.0005, cd)) * step(abs(cx), 0.27) * step(abs(cf.y), 0.44) * step(0.25, ch)",
    ),

    /** A harlequin's diamonds; inlaid, the lattice round them. */
    Diamonds(
        key = "diamonds",
        glsl = "step(0.5, fract((floor(p.y * 9.0 + atan(p.z, p.x) * 1.91) + floor(p.y * 9.0 - atan(p.z, p.x) * 1.91)) * 0.5))",
        inlay = "klLine(min(klNear(p.y * 9.0 + atan(p.z, p.x) * 1.91), klNear(p.y * 9.0 - atan(p.z, p.x) * 1.91)), 0.022)",
    ),

    /**
     * Fine lines woven round the body, crossing each other in a braid between thin hoops: the engraving on a
     * banknote or a watch's dial. The hoops lie every 0.449 m, lifted by [BRAID_LIFT] so that one is the belt on the
     * wearer's upper hip.
     */
    Braid(
        key = "braid",
        before = "p.y -= $BRAID_LIFT;",
        glsl = "1.0 - smoothstep(0.10, 0.24, min(min(abs(sin(atan(p.z, p.x) * 5.0 + 1.6 * sin(p.y * 14.0))), " +
            "abs(sin(atan(p.z, p.x) * 5.0 - 1.6 * sin(p.y * 14.0)))), 1.6 * abs(sin(p.y * 7.0))))",
        inlay = "klLine(min(min(abs(sin(atan(p.z, p.x) * 5.0 + 1.6 * sin(p.y * 14.0))), " +
            "abs(sin(atan(p.z, p.x) * 5.0 - 1.6 * sin(p.y * 14.0)))), 1.6 * abs(sin(p.y * 7.0))), 0.08)",
    ),

    /** Long hair hanging: fine strands down its length, a little wavy; inlaid, every strand a hairline. */
    Strands(
        key = "strands",
        glsl = "smoothstep(0.25, 0.5, fract(p.x * 75.0 + 0.5 * sin(p.y * 11.0))) * " +
            "(1.0 - smoothstep(0.62, 0.9, fract(p.x * 75.0 + 0.5 * sin(p.y * 11.0))))",
        inlay = "klLine(klNear(p.x * 75.0 + 0.5 * sin(p.y * 11.0) - 0.44), 0.08)",
    ),
}

/**
 * The functions a [ClothPattern.inlay] draws its lines with, in GLSL:
 * - `klNear(x)`: how far `x` is from the nearest whole number, 0 to 0.5.
 * - `klLine(d, w)`: a line where the distance `d` is below `w`, 1 inside and 0 outside, its edge one pixel soft. Where
 *   the line is thinner than a pixel it is drawn a pixel wide and that much fainter, so a figure seen from afar shows
 *   a calm, darker gold and no line that flickers from pixel to pixel.
 */
const val INLAY_GLSL: String = "float klNear(float x) { return abs(fract(x + 0.5) - 0.5); }\n" +
    "float klLine(float d, float w) { float fw = max(fwidth(d), 1e-5); " +
    "return clamp(0.5 + (w - d) / fw, 0.0, 1.0) * min(1.0, 2.0 * w / fw); }"

/** Where hair is worn as a pattern: the head's [centre] (in the body's rest space) and its [radius]. */
class HairPart(val centre: Vector3, val radius: Double)

/**
 * Gives [material] (a body's) the pattern [kind]; [gloss] and [matt] are the two roughnesses.
 * [glitter]: the share of the skin's grains that are spangles (see below).
 * [hair]: on the head, behind a hairline that runs from the brow back over the ears, the pattern is hair combed
 * straight back: fine glossy strands that run from the front over the skull to the nape, a little uneven, as wet or
 * oiled hair catches the light.
 */
fun pattern(
    material: MeshStandardMaterial,
    kind: ClothPattern,
    gloss: Double = 0.12,
    matt: Double = 0.85,
    hair: HairPart? = null,
    glitter: Double = 0.14,
) {
    material.metalness = 0.0

    val combed = if (hair != null) combedBack(hair = hair, matt = "0.62", gloss = "0.07") else ""

    // The cloth: less of a mirror, and it glitters. The skin is cut into grains of about 4 mm (in its rest shape, so
    // they stay on it); each grain has its own roughness and faces a little its own way, and a share of them
    // ([glitter], about one in seven) is a spangle: smooth, and tilted more. So a highlight is no streak but a
    // scatter of points that come and go as the figure moves. The spangles are in the cloth's ground, hardly on its
    // pattern (a tenth of them there): the pattern stays a calm gloss and the glitter lies between its figures.
    val grain = "vec3 cell = floor(vRest * 250.0); float grain = fract(sin(dot(cell, vec3(12.9898, 78.233, 37.719))) * 43758.5453); " +
        "float spangle = step(${(1 - glitter).toFixed(3)}, grain) * (1.0 - 0.9 * klOn);"
    val sparkle = "\n{ $grain roughnessFactor = clamp(roughnessFactor + 0.20 + 0.30 * (grain - 0.5), 0.05, 1.0); " +
        "roughnessFactor = mix(roughnessFactor, 0.20, spangle); }"
    val cacheKey = "pattern-" + kind.key + (if (hair != null) "-hair" else "") + "-glitter" + glitter.jsText()

    material.onBeforeCompile(cacheKey) { shader: CompiledShader ->
        shader.uniforms["uDress"] = dress

        shader.vertexShader = shader.vertexShader
            .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vRest;")
            .replaceFirst("#include <begin_vertex>", "#include <begin_vertex>\nvRest = position;")

        shader.fragmentShader = shader.fragmentShader
            .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vRest;\nuniform float uDress;")
            .replaceFirst(
                "#include <roughnessmap_fragment>",
                "#include <roughnessmap_fragment>\nfloat klOn; { vec3 p = vRest; ${kind.before} klOn = ${kind.glsl}; " +
                    "roughnessFactor = mix(${matt.toFixed(3)}, ${gloss.toFixed(3)}, klOn); }$sparkle$combed",
            )
            .replaceFirst(
                "#include <color_fragment>",
                "#include <color_fragment>\n{ vec3 p = vRest; ${kind.before} float figure = ${kind.glsl}; " +
                    "diffuseColor.rgb *= mix(mix(1.0, 0.15, uDress), 1.0, figure); }",
            )
            // lit from the front ([dress]) the cloth's ground goes dark: it throws back an eighth of the light the
            // pattern does, so the figure stays dark and its pattern (and the spangles in the ground) stand out
            .replaceFirst(
                "#include <lights_physical_fragment>",
                "#include <lights_physical_fragment>\n{ $grain float lit = mix(mix(1.0, 0.12, uDress), 1.0, max(klOn, spangle)); " +
                    "material.specularColor *= lit; material.specularF90 *= lit; }",
            )
            .replaceFirst(
                "#include <normal_fragment_maps>",
                "#include <normal_fragment_maps>\n{ $grain normal = normalize(normal + " +
                    "(vec3(grain, fract(grain * 17.3), fract(grain * 113.7)) - 0.5) * (0.12 + 0.50 * spangle)); }",
            )
    }
}

/**
 * The hair combed straight back on the head of [hair] (see [pattern]), as GLSL statements that change
 * `roughnessFactor`: behind the hairline, strands between [matt] and [gloss] (GLSL numbers, as written).
 */
internal fun combedBack(hair: HairPart, matt: String, gloss: String): String = """
    { vec3 h = (vRest - vec3(${hair.centre.x.toFixed(4)}, ${hair.centre.y.toFixed(4)}, ${hair.centre.z.toFixed(4)})) / ${hair.radius.toFixed(4)};
      float line = h.z - 0.50 - 0.75 * h.y;                              // the hairline: further forward the higher up
      float in_hair = (1.0 - smoothstep(-0.06, 0.06, line)) * smoothstep(-0.95, -0.75, h.y) * (1.0 - smoothstep(1.25, 1.45, length(h)));
      float around = atan(h.x, h.y + 0.25);                              // which strand: round the axis from front to back
      float strand = fract(around * 9.5 + 0.35 * sin(h.z * 5.0 + around * 3.0));
      float shine = smoothstep(0.25, 0.5, strand) * (1.0 - smoothstep(0.62, 0.9, strand));
      roughnessFactor = mix(roughnessFactor, mix($matt, $gloss, shine), in_hair); }"""

/** The number as JavaScript writes it in a string (`1` for 1.0, `0.55`): as the prototype's shader text has it. */
internal fun Double.jsText(): String = js("String")(this).unsafeCast<String>()
