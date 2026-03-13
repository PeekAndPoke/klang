package io.peekandpoke.klang.ui

import de.peekandpoke.kraft.vdom.custom
import kotlinx.html.FlowContent
import kotlinx.html.svg

/**
 * SVG DSL helpers in the spirit of kotlinx.html.
 *
 * These wrap Kraft's [custom] tag builder to give named, type-safe constructors
 * for the SVG elements used in Klang's staff and notation components.
 *
 * All numeric parameters accept [Number] (works with both [Int] and [Double]).
 * Use [svgG] for container groups; use the leaf functions for self-closing elements.
 *
 * Example:
 * ```kotlin
 * svgRoot(width = 200, height = 100) {
 *     svgLine(0, 50, 200, 50, stroke = "#333")
 *     svgG(key = "note-1", style = "cursor:default") {
 *         attributes["data-note-id"] = "1"
 *         svgEllipse(40, 50, 5.0, 3.5, fill = "#222")
 *     }
 * }
 * ```
 */

// ── Root ──────────────────────────────────────────────────────────────────────

/**
 * Renders an `<svg>` element with fixed pixel [width] and [height].
 */
fun FlowContent.svgRoot(
    width: Int,
    height: Int,
    style: String? = "display:block;",
    block: FlowContent.() -> Unit,
) {
    svg {
        attributes["width"] = width.toString()
        attributes["height"] = height.toString()
        if (style != null) attributes["style"] = style
        block()
    }
}

/**
 * Renders an `<svg>` element using a [viewBox] for responsive scaling.
 * Defaults to `width="100%"` so the SVG fills its container.
 */
fun FlowContent.svgRoot(
    viewBox: String,
    width: String = "100%",
    style: String? = "display:block;",
    block: FlowContent.() -> Unit,
) {
    svg {
        attributes["viewBox"] = viewBox
        attributes["width"] = width
        if (style != null) attributes["style"] = style
        block()
    }
}

// ── Container ─────────────────────────────────────────────────────────────────

/**
 * Renders a `<g>` group element.
 *
 * The [block] runs with the `<g>` as the receiver, so you can set extra
 * attributes (e.g. `data-*`) with `attributes["data-foo"] = "bar"` and add
 * child SVG elements by calling other DSL functions inside.
 */
fun FlowContent.svgG(
    key: String? = null,
    style: String? = null,
    block: FlowContent.() -> Unit,
) {
    custom("g") {
        if (key != null) attributes["key"] = key
        if (style != null) attributes["style"] = style
        block()
    }
}

// ── Leaf elements ─────────────────────────────────────────────────────────────

/** Renders a `<line>` element. */
fun FlowContent.svgLine(
    x1: Number, y1: Number,
    x2: Number, y2: Number,
    stroke: String = "#333",
    strokeWidth: String = "1.2",
    key: String? = null,
) {
    custom("line") {
        if (key != null) attributes["key"] = key
        attributes["x1"] = x1.toString()
        attributes["y1"] = y1.toString()
        attributes["x2"] = x2.toString()
        attributes["y2"] = y2.toString()
        attributes["stroke"] = stroke
        attributes["stroke-width"] = strokeWidth
    }
}

/** Renders an `<ellipse>` element. */
fun FlowContent.svgEllipse(
    cx: Number, cy: Number,
    rx: Number, ry: Number,
    fill: String,
    stroke: String? = null,
    strokeWidth: String? = null,
    opacity: String? = null,
    style: String? = null,
    key: String? = null,
) {
    custom("ellipse") {
        if (key != null) attributes["key"] = key
        attributes["cx"] = cx.toString()
        attributes["cy"] = cy.toString()
        attributes["rx"] = rx.toString()
        attributes["ry"] = ry.toString()
        attributes["fill"] = fill
        if (stroke != null) attributes["stroke"] = stroke
        if (strokeWidth != null) attributes["stroke-width"] = strokeWidth
        if (opacity != null) attributes["opacity"] = opacity
        if (style != null) attributes["style"] = style
    }
}

/** Renders a `<rect>` element. */
fun FlowContent.svgRect(
    x: Number, y: Number,
    width: Number, height: Number,
    fill: String,
    rx: String? = null,
    stroke: String? = null,
    strokeWidth: String? = null,
    key: String? = null,
) {
    custom("rect") {
        if (key != null) attributes["key"] = key
        attributes["x"] = x.toString()
        attributes["y"] = y.toString()
        attributes["width"] = width.toString()
        attributes["height"] = height.toString()
        attributes["fill"] = fill
        if (rx != null) attributes["rx"] = rx
        if (stroke != null) attributes["stroke"] = stroke
        if (strokeWidth != null) attributes["stroke-width"] = strokeWidth
    }
}

/**
 * Renders a `<text>` element with [text] as its content.
 *
 * For more complex text (e.g. `<tspan>` children), use [svgG] with a raw
 * `custom("text") { }` block instead.
 */
/** Renders a `<path>` element. */
fun FlowContent.svgPath(
    d: String,
    fill: String = "none",
    stroke: String? = null,
    strokeWidth: String? = null,
    opacity: String? = null,
    key: String? = null,
) {
    custom("path") {
        if (key != null) attributes["key"] = key
        attributes["d"] = d
        attributes["fill"] = fill
        if (stroke != null) attributes["stroke"] = stroke
        if (strokeWidth != null) attributes["stroke-width"] = strokeWidth
        if (opacity != null) attributes["opacity"] = opacity
    }
}

fun FlowContent.svgText(
    x: Number, y: Number,
    text: String,
    fill: String = "#333",
    fontSize: String = "12",
    textAnchor: String? = null,
    fontWeight: String? = null,
    transform: String? = null,
    style: String? = null,
    key: String? = null,
) {
    custom("text") {
        if (key != null) attributes["key"] = key
        attributes["x"] = x.toString()
        attributes["y"] = y.toString()
        attributes["font-size"] = fontSize
        attributes["fill"] = fill
        if (textAnchor != null) attributes["text-anchor"] = textAnchor
        if (fontWeight != null) attributes["font-weight"] = fontWeight
        if (transform != null) attributes["transform"] = transform
        if (style != null) attributes["style"] = style
        +text
    }
}
