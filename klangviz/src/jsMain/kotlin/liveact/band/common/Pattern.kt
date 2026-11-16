/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.common

import io.peekandpoke.klang.workshop.threejs.CompiledShader
import io.peekandpoke.klang.workshop.threejs.onBeforeCompile
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
private val dress: dynamic = uniform(0)

/** The value of the shared `dress` uniform (see [dress]): the stage sets it every frame. */
var dressLevel: Double
    get() = dress.value.unsafeCast<Double>()
    set(value) {
        dress.value = value
    }

/** A pattern a body can wear: [key] names it (in the program's cache key), [glsl] is its value at the place `p`. */
enum class ClothPattern(val key: String, val glsl: String) {
    /** Broad bands round the body, waving like a tiger's. */
    Tiger(
        key = "tiger",
        glsl = "smoothstep(0.42, 0.58, fract(p.y * 6.5 + 0.16 * sin(p.x * 13.0) + 0.12 * sin(p.z * 17.0)))",
    ),

    /** Fine upright pinstripes. */
    Pinstripe(
        key = "pinstripe",
        glsl = "smoothstep(0.30, 0.45, abs(fract(atan(p.z, p.x) * 4.14) - 0.5) * 2.0)",
    ),

    /** One stripe winding up the body. */
    Spiral(
        key = "spiral",
        glsl = "smoothstep(0.40, 0.60, fract(p.y * 4.5 + atan(p.z, p.x) * 0.955))",
    ),

    /** Round spots, row by row offset. */
    Spots(
        key = "spots",
        glsl = "1.0 - smoothstep(0.26, 0.34, length(fract(vec3(p.x * 11.0 + 0.5 * floor(p.y * 11.0), p.y * 11.0, p.z * 11.0)) - 0.5))",
    ),

    /** A harlequin's diamonds. */
    Diamonds(
        key = "diamonds",
        glsl = "step(0.5, fract((floor(p.y * 9.0 + atan(p.z, p.x) * 1.91) + floor(p.y * 9.0 - atan(p.z, p.x) * 1.91)) * 0.5))",
    ),

    /**
     * Fine lines woven round the body, crossing each other in a braid between thin hoops: the engraving on a
     * banknote or a watch's dial.
     */
    Braid(
        key = "braid",
        glsl = "1.0 - smoothstep(0.10, 0.24, min(min(abs(sin(atan(p.z, p.x) * 5.0 + 1.6 * sin(p.y * 14.0))), " +
            "abs(sin(atan(p.z, p.x) * 5.0 - 1.6 * sin(p.y * 14.0)))), 1.6 * abs(sin(p.y * 7.0))))",
    ),

    /** Long hair hanging: fine strands down its length, a little wavy. */
    Strands(
        key = "strands",
        glsl = "smoothstep(0.25, 0.5, fract(p.x * 75.0 + 0.5 * sin(p.y * 11.0))) * " +
            "(1.0 - smoothstep(0.62, 0.9, fract(p.x * 75.0 + 0.5 * sin(p.y * 11.0))))",
    ),
}

/**
 * The braid laid flat, for a wall (x across, y up, in metres): the same woven lines between hoops, a pace wide. Not
 * for a body, so not a [ClothPattern].
 */
private const val BRAID_WALL = "1.0 - smoothstep(0.06, 0.16, min(min(abs(sin(p.x * 3.0 + 1.6 * sin(p.y * 4.2))), " +
    "abs(sin(p.x * 3.0 - 1.6 * sin(p.y * 4.2)))), 1.6 * abs(sin(p.y * 2.1))))"

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

    val combed = if (hair != null) {
        """
    { vec3 h = (vRest - vec3(${hair.centre.x.toFixed(4)}, ${hair.centre.y.toFixed(4)}, ${hair.centre.z.toFixed(4)})) / ${hair.radius.toFixed(4)};
      float line = h.z - 0.50 - 0.75 * h.y;                              // the hairline: further forward the higher up
      float in_hair = (1.0 - smoothstep(-0.06, 0.06, line)) * smoothstep(-0.95, -0.75, h.y) * (1.0 - smoothstep(1.25, 1.45, length(h)));
      float around = atan(h.x, h.y + 0.25);                              // which strand: round the axis from front to back
      float strand = fract(around * 9.5 + 0.35 * sin(h.z * 5.0 + around * 3.0));
      float shine = smoothstep(0.25, 0.5, strand) * (1.0 - smoothstep(0.62, 0.9, strand));
      roughnessFactor = mix(roughnessFactor, mix(0.62, 0.07, shine), in_hair); }"""
    } else {
        ""
    }

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
        shader.uniforms.uDress = dress

        shader.vertexShader = shader.vertexShader
            .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vRest;")
            .replaceFirst("#include <begin_vertex>", "#include <begin_vertex>\nvRest = position;")

        shader.fragmentShader = shader.fragmentShader
            .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vRest;\nuniform float uDress;")
            .replaceFirst(
                "#include <roughnessmap_fragment>",
                "#include <roughnessmap_fragment>\nfloat klOn; { vec3 p = vRest; klOn = ${kind.glsl}; " +
                    "roughnessFactor = mix(${matt.toFixed(3)}, ${gloss.toFixed(3)}, klOn); }$sparkle$combed",
            )
            .replaceFirst(
                "#include <color_fragment>",
                "#include <color_fragment>\n{ vec3 p = vRest; float figure = ${kind.glsl}; " +
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
 * The wall behind the stage (a plane, x across and y up in metres) has two faces (the maintainer, 2026-11-16: "first
 * wealthy ... then prison"):
 * - by itself, under the back light: an ornament, a damask of round medallions with a flower in each, ringed, small
 *   studs between them, seen ever so slightly in a faint glow of its own that fades to the sides;
 * - under the front lamp (front 0 to 1, set every frame through the returned function): a wire mesh, the braid
 *   laid flat (the maintainer: "like perfect like wire mesh"), dark on the lit wall. The ornament's glow goes as the
 *   lamp comes.
 *
 * [amount]: how bright the ornament's own glow is; [width]: how far it reaches to the sides before it fades, in
 * metres; [color]: its colour, as red, green and blue.
 */
fun wall(
    material: MeshStandardMaterial,
    amount: Double = 0.017,
    width: Double = 5.5,
    color: Triple<Double, Double, Double> = Triple(0.55, 0.62, 1.0),
): (front: Double) -> Unit {
    val uFront: dynamic = uniform(0)
    val ornament = """
    float u = p.x * 2.4, v = p.y * 2.4;
    float field = cos(u) * cos(v);
    float ring = 1.0 - smoothstep(0.0, 0.07, abs(abs(field) - 0.38));
    float second = 1.0 - smoothstep(0.0, 0.035, abs(abs(field) - 0.62));
    float flower = smoothstep(0.70, 0.86, abs(field)) * (0.45 + 0.55 * cos(6.0 * atan(sin(u), sin(v))));
    float stud = smoothstep(0.90, 0.97, abs(cos(u + 1.5708) * cos(v + 1.5708)));
    float ornament = clamp(max(max(ring, 0.6 * second), max(flower, stud)), 0.0, 1.0);
    // the glow: the same lines again, wide and soft, so the light seems to spill from them onto the wall
    float halo = max(max(exp(-9.0 * abs(abs(field) - 0.38)), 0.6 * exp(-12.0 * abs(abs(field) - 0.62))),
                     max(0.7 * smoothstep(0.45, 0.86, abs(field)), smoothstep(0.60, 0.97, abs(cos(u + 1.5708) * cos(v + 1.5708)))));"""
    // the mesh is the braid laid flat ([BRAID_WALL]): woven wires between hoops, as wire fencing is woven
    val mesh = "float mesh = $BRAID_WALL;"
    val rgb = listOf(color.first, color.second, color.third).joinToString(", ") { it.jsText() }

    material.onBeforeCompile("wall-two-faces-braid-glow") { shader: CompiledShader ->
        shader.uniforms.uFront = uFront

        shader.vertexShader = shader.vertexShader
            .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vRest;")
            .replaceFirst("#include <begin_vertex>", "#include <begin_vertex>\nvRest = position;")

        shader.fragmentShader = shader.fragmentShader
            .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vRest;\nuniform float uFront;")
            .replaceFirst(
                "#include <color_fragment>",
                "#include <color_fragment>\n{ vec3 p = vRest; $mesh diffuseColor.rgb *= 1.0 - 0.55 * mesh; }",
            )
            .replaceFirst(
                "#include <emissivemap_fragment>",
                "#include <emissivemap_fragment>\n{ vec3 p = vRest; $ornament totalEmissiveRadiance += vec3($rgb) * " +
                    "${amount.toFixed(4)} * (0.28 + 1.5 * ornament + 0.75 * halo) * " +
                    "exp(-(p.x * p.x) / ${(width * width).toFixed(2)}) * (1.0 - uFront); }",
            )
    }

    return { front -> uFront.value = front }
}

/** The number with [digits] digits after the point, exactly as JavaScript's `toFixed` writes it. */
private fun Double.toFixed(digits: Int): String = asDynamic().toFixed(digits).unsafeCast<String>()

/** The number as JavaScript writes it in a string (`1` for 1.0, `0.55`): as the prototype's shader text has it. */
private fun Double.jsText(): String = js("String")(this).unsafeCast<String>()
