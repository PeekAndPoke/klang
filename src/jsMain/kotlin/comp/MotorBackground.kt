/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.common.math.PerlinNoise2D
import io.peekandpoke.klang.spine.SwingingLamp
import io.peekandpoke.klang.spine.VisualState
import io.peekandpoke.klang.storage.SeedStorage
import io.peekandpoke.klang.ui.feel.KlangLook
import io.peekandpoke.klang.ui.feel.KlangRole
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.addons.registry.AddonRegistry.Companion.addons
import io.peekandpoke.kraft.addons.threejs.ThreeJs
import io.peekandpoke.kraft.addons.threejs.ThreeJsAddon
import io.peekandpoke.kraft.addons.threejs.ThreeJsContext
import io.peekandpoke.kraft.addons.threejs.ThreeJsFrame
import io.peekandpoke.kraft.addons.threejs.createCanvasTexture
import io.peekandpoke.kraft.addons.threejs.createVector2
import io.peekandpoke.kraft.addons.threejs.js.Camera
import io.peekandpoke.kraft.addons.threejs.js.CanvasTexture
import io.peekandpoke.kraft.addons.threejs.js.DirectionalLight
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.PointLight
import io.peekandpoke.kraft.addons.threejs.js.TextureWrapping
import io.peekandpoke.kraft.addons.threejs.js.WebGLRenderer
import io.peekandpoke.kraft.addons.threejs.threeJs
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.utils.jsObject
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.streams.Unsubscribe
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.style
import org.khronos.webgl.Uint8ClampedArray
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event
import kotlin.js.Date
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Shared ref to the mounted [MotorBackground] so sibling components
 * (e.g. [io.peekandpoke.klang.pages.StartPage]) can drive the lighting
 * state machine without threading the ref through every layout.
 */
val motorBackgroundRef: ComponentRef.Tracker<MotorBackground> = ComponentRef.Tracker()

@Suppress("FunctionName")
fun Tag.MotorBackground(
    ref: ComponentRef.Tracker<MotorBackground>? = motorBackgroundRef,
    composition: PlateComposition = PlateComposition.start,
): ComponentRef<MotorBackground> {
    val created = comp(MotorBackground.Props(composition = composition)) { MotorBackground(it) }
    return if (ref != null) created.track(ref) else created
}

/**
 * Machined panel-grid background in the code page's surface language, with a
 * spotlight that stands on the centre of the screen (the maintainer, 2026-10-26: "Stop the light from moving ... just
 * point it at the center of the screen") and whose filament subtly flickers; now and then it stutters, a bulb about
 * to break ([BulbStutter]).
 *
 * Pure Kotlin port of the former `motor-background.js` built on the kraft-threejs
 * addon. While the Motor waits for its key the screen stays black; [warmUp] fades in a dim standby light, quickly,
 * while the Motor warms up (the maintainer, 2026-10-26: "In the very beginning leave it full black"); [powerOn]
 * brings the light up, [hoverStart] and [hoverEnd] swell it under an element the mouse is over.
 */
class MotorBackground(ctx: Ctx<Props>) : Component<MotorBackground.Props>(ctx) {

    data class Props(
        /**
         * The tiles laid on the grid: the title on its plate, and whatever plates a screen places for its parts (the
         * maintainer, 2026-10-29: "implement the tiles individually and compose the screen as needed").
         */
        val composition: PlateComposition,
    )

    companion object {
        /** The texture's height in px; its width follows the screen's aspect. */
        private const val TEX_HEIGHT = 2048

        /**
         * While tiles come in one by one, the texture goes up to the GPU this often at the most: the whole of it each
         * time, about 30 MB on a large screen.
         */
        private const val UPLOAD_EVERY_MS = 250.0

        /** The brush's pressure is sampled this far apart, along a stroke and across ([pressure]). */
        private const val PRESSURE_STEP_X = 8.0
        private const val PRESSURE_STEP_Y = 4.0

        /** The last plate surface any background built ([SurfaceCache]); null before the first. */
        private var surfaceCache: SurfaceCache? = null

        /**
         * A plate surface built (the normal map's pixels) and what it was built for. A tile's surface depends only on
         * where it lies and on the metal's seed, so a build at the same size and seed takes from it every tile that no
         * plate covers, then or now, and that lies in a row laid the same way. One at a time, each build replacing it: it never grows.
         */
        private class SurfaceCache(
            val seed: Int,
            val width: Int,
            val height: Int,
            val originY: Double,
            val offset: BooleanArray,
            val plates: List<TitlePlate>,
            val pixels: Uint8ClampedArray,
        ) {
            companion object {
                fun of(
                    seed: Int,
                    width: Int,
                    height: Int,
                    originY: Double,
                    offset: BooleanArray,
                    plates: List<TitlePlate>,
                    data: Uint8ClampedArray,
                ): SurfaceCache {
                    val copy = Uint8ClampedArray(data.length)

                    copy.asDynamic().set(data)

                    return SurfaceCache(seed, width, height, originY, offset.copyOf(), plates, copy)
                }
            }

            /** Whether the [cell] ([x0, y0, x1, y1, row]) is the same in a build with [offset] and [plates]. */
            fun holds(cell: IntArray, offset: BooleanArray, plates: List<TitlePlate>): Boolean {
                val (x0, y0, x1, y1) = cell
                val row = cell[4]

                fun clear(of: List<TitlePlate>) =
                    of.none { it.left < x1 && it.right > x0 && it.top < y1 && it.bottom > y0 }

                return this.offset.getOrElse(row + 1) { false } == offset.getOrElse(row + 1) { false } &&
                    clear(this.plates) && clear(plates)
            }

            /** Copies the [cell]'s pixels into [target], an image's pixels of the same size. */
            fun copyInto(target: dynamic, cell: IntArray) {
                val (x0, y0, x1, y1) = cell

                for (py in y0 until y1) {
                    val from = (py * width + x0) * 4
                    val to = (py * width + x1) * 4

                    target.set(pixels.asDynamic().subarray(from, to), from)
                }
            }
        }

        /** Panel-grid cell size in texture px: shared by the normal map, the albedo seams and the title's plate. */
        private const val PANEL_CELL = 330.0

        /**
         * The share of the free height above a composition; the rest is under it. Half: the composition stands in the
         * middle of the screen, under the light, which stands there too (the maintainer, 2026-10-26: "When i am in
         * fullscreen everything is too far up", and at a third above: "Still too far up").
         */
        private const val FREE_ABOVE = 0.5

        /** How far KLANGMOTOR's middle lies under its plate's top, as a share of the texture's height. */
        private val titleAbove: Double by lazy {
            val (ascent, descent) = letterReach("KLANGMOTOR")

            (PANEL_CELL + ascent - descent) / 2.0 / TEX_HEIGHT
        }

        /**
         * Where a [composition]'s top row starts on a screen [screenHeight] px high, as a share of it from the top: the
         * composition and the room it asks below in the middle; the title's middle never higher than 12 % nor lower
         * than 40 % of the screen, as it always stood.
         */
        fun topShare(screenHeight: Double, composition: PlateComposition): Double {
            val tall = composition.rows * tileWidthPx(screenHeight) + composition.roomBelowPx
            val free = (screenHeight - tall).coerceAtLeast(0.0)

            return (free * FREE_ABOVE / screenHeight).coerceIn(0.12 - titleAbove, 0.4 - titleAbove)
        }

        /**
         * Where the start page's title plate ends in a screen [screenHeight] px high, as a share of it from the top: the
         * start page sets its gauges under it (the maintainer, 2026-10-26).
         */
        fun titlePlateBottomShare(screenHeight: Double): Double =
            topShare(screenHeight, PlateComposition.start) + PANEL_CELL / TEX_HEIGHT

        /**
         * A tile's width on a screen [screenHeight] px high, in screen px: the texture spans the screen's height. The
         * columns are centred on the screen, a tile's middle on the screen's, so the start page sets its side gauges a
         * tile's width out (the maintainer, 2026-10-27: each gauge centred in its tile).
         */
        fun tileWidthPx(screenHeight: Double): Double = PANEL_CELL / TEX_HEIGHT * screenHeight

        /** A [composition]'s tiles in a texture [width] px wide whose top row starts at [topShare] of its height. */
        private fun tilesIn(width: Int, topShare: Double, composition: PlateComposition): List<Pair<PlacedTile, TitlePlate>> {
            val cx = width / 2.0
            val top = topShare * TEX_HEIGHT

            return composition.tiles.map { tile ->
                tile to TitlePlate(
                    left = cx + tile.left * PANEL_CELL,
                    top = top + tile.row * PANEL_CELL,
                    right = cx + (tile.left + tile.columns) * PANEL_CELL,
                    bottom = top + (tile.row + tile.rows) * PANEL_CELL,
                )
            }
        }

        /**
         * Where a [composition]'s tiles lie on a screen [screenWidth] by [screenHeight] px, in screen px, by their ids:
         * the plates the texture draws, worked out as the texture works them out (its width follows the screen's aspect,
         * its height spans the screen), so a screen sets each tile's content on its plate.
         */
        fun tilesOnScreen(screenWidth: Double, screenHeight: Double, composition: PlateComposition): Map<String, ScreenRect> {
            val texWidth = ceil(TEX_HEIGHT * screenWidth / screenHeight).toInt()
            val sx = screenWidth / texWidth
            val sy = screenHeight / TEX_HEIGHT

            return tilesIn(width = texWidth, topShare = topShare(screenHeight, composition), composition = composition)
                .associate { (tile, plate) ->
                    tile.id to ScreenRect(
                        left = plate.left * sx,
                        top = plate.top * sy,
                        right = plate.right * sx,
                        bottom = plate.bottom * sy,
                    )
                }
        }

        /**
         * A plate's corners in texture px: the title's and every other tile a composition raises ([PlacedTile]); no seam
         * crosses it.
         */
        private data class TitlePlate(
            val left: Double,
            val top: Double,
            val right: Double,
            val bottom: Double,
        ) {
            fun contains(x: Double, y: Double): Boolean = x >= left && x < right && y >= top && y < bottom
        }

        /** The title's font size in a texture [height] pixels high. */
        private fun titleFontSize(height: Int): Double = (height * 0.135).coerceAtLeast(48.0)

        /** Configures font, alignment and letter-spacing consistently for both text canvases. */
        private fun applyTitleTextStyle(tctx: CanvasRenderingContext2D, height: Int) {
            val fontSize = titleFontSize(height)
            tctx.font = "bold ${fontSize}px monospace"
            tctx.asDynamic().textAlign = "center"
            tctx.asDynamic().textBaseline = "middle"
            tctx.asDynamic().letterSpacing = "-0.03em"
        }

        /** How far [text]'s letters reach above and below their middle line, in texture px: their own outline. */
        private fun letterReach(text: String): Pair<Double, Double> {
            val cnv = document.createElement("canvas") as HTMLCanvasElement
            val tctx = cnv.getContext("2d") as CanvasRenderingContext2D

            applyTitleTextStyle(tctx, TEX_HEIGHT)

            val metrics = tctx.measureText(text)

            return metrics.actualBoundingBoxAscent to metrics.actualBoundingBoxDescent
        }

        /**
         * How many tiles a title's plate is wide (the maintainer, 2026-10-26: "one nice tile underneith the text so that
         * no lines are crossing it"): an even number, round the screen's middle, where a tile's middle sits, so its sides
         * end half way across a tile, as far from the grid's lines as can be, on purpose ("Try half"); the least that
         * leaves the letters some air.
         */
        fun titleColumns(text: String): Int {
            val cnv = document.createElement("canvas") as HTMLCanvasElement
            val tctx = cnv.getContext("2d") as CanvasRenderingContext2D

            applyTitleTextStyle(tctx, TEX_HEIGHT)

            val metrics = tctx.measureText(text)
            val tall = metrics.actualBoundingBoxAscent + metrics.actualBoundingBoxDescent
            val textWidth = metrics.actualBoundingBoxLeft + metrics.actualBoundingBoxRight
            val leastWidth = textWidth + 2 * tall * 0.3
            val tiles = ceil(leastWidth / PANEL_CELL).toInt()

            return if (tiles % 2 == 0) tiles else tiles + 1
        }

        /** A role's colour as a number for three.js; a value that is not `#rrggbb` takes the role's default. */
        private fun KlangLook.hexOf(role: KlangRole): Int =
            parseHex(this[role]) ?: parseHex(role.default) ?: 0x808080

        private fun parseHex(value: String): Int? =
            value.trim().removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)
    }

    ////  ADDON  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val threeAddon: ThreeJsAddon? by subscribingTo(addons.threeJs)

    /**
     * The look's colours (the maintainer, 2026-10-26: "The background and Klangmotor color needs to fit the theme
     * colors"): the light shines in the look's metal, so KLANGMOTOR glitters like the gauges' rims; the plate takes the
     * look's ground. A changed look repaints them at once.
     */
    private val look by subscribingTo(KlangTheme.look) { applyLook(it) }

    /**
     * The user's seed ([SeedStorage.metal]): the title's flakes, the grain, the strokes and the turned tiles are the
     * user's own sheet of metal, the same on every visit (the maintainer, 2026-10-28: "The noise on Klangmotor looks
     * the same everytime ... seed it randomly", then "every user should get a ui-seed in the storage").
     */
    private val seed = SeedStorage.metal

    /** 2D Perlin source for the brushed-metal grain and the lamp's flicker, from this visit's [seed]. */
    private val grainNoise = PerlinNoise2D(random = Random(seed))

    /** The light's colour: the look's metal. */
    private fun lightColor(look: KlangLook): Int = look.hexOf(KlangRole.MetalMid)

    /** The plate's colour: the look's ground; its texture is neutral and only shapes it (seams, grain). */
    private fun plateColor(look: KlangLook): Int = look.hexOf(KlangRole.GroundApp)

    private fun applyLook(look: KlangLook) {
        val light = lightColor(look)

        listOfNotNull(ambientLight, mainLight, fillLight).forEach { it.asDynamic().color.setHex(light) }
        // Raw, as the canvas textures are taken (three would decode a hex int as sRGB, ten times darker here)
        material?.asDynamic()?.color?.setHex(plateColor(look), "srgb-linear")
    }

    ////  SCENE STATE  ////////////////////////////////////////////////////////////////////////////////////////////

    private var renderer: WebGLRenderer? = null
    private var camera: Camera? = null
    private var plane: Mesh? = null
    private var material: MeshStandardMaterial? = null
    private var titlePlane: Mesh? = null
    private var titleMaterial: MeshStandardMaterial? = null
    private var baseMap: CanvasTexture? = null
    private var mainLight: PointLight? = null
    private var ambientLight: Any? = null
    private var fillLight: DirectionalLight? = null

    private var aspect: Double = 1.0

    ////  LIGHTING STATE MACHINE  /////////////////////////////////////////////////////////////////////////////////

    /**
     * The lamp's light, from the Spine's one lamp ([VisualState.light], KLANG-00389): this plate only shows it. Followed
     * while mounted; the lamp works only while someone looks.
     */
    private var light = SwingingLamp.Light.dark
    private var lightFollow: Unsubscribe? = null

    // Ceiling for the plate's blend over the black backdrop: the light, not the backdrop, decides how bright it is
    private val maxPlateOpacity = 0.85

    ////  DEFERRED TEXTURE BUILD  /////////////////////////////////////////////////////////////////////////////////

    private var sceneCtx: ThreeJsContext? = null
    private var prepared = false
    private var preparing = false
    private val onPrepared = mutableListOf<() -> Unit>()

    /** Rows per macrotask for the chunked per-pixel texture loops. */
    private val chunkRows = 128

    /**
     * Where the panel grid's columns start in a texture [size] pixels wide, so that one panel sits exactly in the
     * middle of the screen (the maintainer, 2026-10-26): the texture spans the screen, so its middle is the screen's.
     * The rows start at [gridOriginY].
     */
    private fun gridOrigin(size: Int): Double = ((size / 2.0 - PANEL_CELL / 2.0) % PANEL_CELL + PANEL_CELL) % PANEL_CELL

    /**
     * Where the panel grid's rows start: on the composition's top, so the rows run along its tiles' tops and bottoms
     * (the maintainer, 2026-10-26: aligned with the title, "but only vertically"; the columns stay centred).
     */
    private fun gridOriginY(height: Int): Double {
        val top = buildTopShare * height

        return (top % PANEL_CELL + PANEL_CELL) % PANEL_CELL
    }

    /** The build's tiles in a texture [width] px wide. */
    private fun placedTiles(width: Int): List<Pair<PlacedTile, TitlePlate>> =
        tilesIn(width = width, topShare = buildTopShare, composition = props.composition)

    /** The raised ones among them: plates standing on the tiles. */
    private fun raisedPlates(width: Int): List<TitlePlate> = placedTiles(width).filter { it.first.raised }.map { it.second }

    /**
     * Which grid rows lie half a tile over, by row index from -1 ([rowIndexOf]): those a tile with its edges half way
     * across a tile lies in ([PlacedTile.offGrid]), the title's row as ever.
     */
    private fun offsetRows(width: Int, height: Int): BooleanArray {
        val originY = gridOriginY(height)
        val rows = BooleanArray((height / PANEL_CELL).toInt() + 3)

        for ((tile, plate) in placedTiles(width)) {
            if (tile.raised && tile.offGrid) {
                val first = rowIndexOf(plate.top + 1.0, originY)

                for (r in first until first + tile.rows) {
                    if (r + 1 in rows.indices) {
                        rows[r + 1] = true
                    }
                }
            }
        }

        return rows
    }

    /** The grid row a texture row [y] lies in, numbered from the grid's [originY]; -1 above it. */
    private fun rowIndexOf(y: Double, originY: Double): Int = floor((y - originY) / PANEL_CELL).toInt()

    /**
     * Where the columns start in the title plate's row: half a tile over from the others, like a course of bricks, so
     * the seams meet the plate's sides, which end half way across a tile (the maintainer, 2026-10-26: the tiles
     * aligned left and right to the plate, "only on the row").
     */
    private fun gridOriginInPlateRow(width: Int): Double = (gridOrigin(width) + PANEL_CELL / 2.0) % PANEL_CELL

    /** About the share of the tiles laid turned by 90 degrees, brushed up and down instead of across. */
    private val turnedTiles = 0.25

    /** A tile's lot for being turned: below [turnedTiles] it may be. */
    private fun turnLot(col: Int, row: Int): Double = hashCell(col * 11 + 3, row * 13 + 5)

    /**
     * Whether the tile at [col], [row] lies turned: its lot is low, and lower than that of every neighbour whose lot
     * is low too, so two turned tiles never touch, not at an edge and not at a corner (the maintainer, 2026-10-26).
     */
    private fun isTurned(col: Int, row: Int): Boolean {
        val lot = turnLot(col, row)

        if (lot >= turnedTiles) {
            return false
        }

        for (dc in -1..1) {
            for (dr in -1..1) {
                val other = turnLot(col + dc, row + dr)

                if ((dc != 0 || dr != 0) && other < turnedTiles && other < lot) {
                    return false
                }
            }
        }

        return true
    }

    /** Where the composition's top row starts, as a share of the window's height, for the build that runs: taken as it starts. */
    private var buildTopShare = 0.2

    /** A rebuild runs; one asked for meanwhile waits for it ([rebuildPending]). */
    private var rebuilding = false
    private var rebuildPending = false

    /** The engraving the running build cuts: its text and its middle in texture px; null for none. */
    private fun engraving(width: Int, height: Int): Triple<String, Double, Double>? {
        val (tile, plate) = placedTiles(width).firstOrNull { it.first.engraving != null } ?: return null
        val text = tile.engraving ?: return null
        val (ascent, descent) = letterReach(text)
        // The letters' own outline in the middle of the plate's row, not the font's box, whose middle sits above the
        // capitals' middle
        val cy = plate.top * height / TEX_HEIGHT + (PANEL_CELL + ascent - descent) / 2.0

        return Triple(text, (plate.left + plate.right) / 2.0, cy)
    }

    /**
     * Brushed steel on the plate and the tiles (the maintainer, 2026-10-26: "a little like brushed steel... very fine
     * carvings"; soft, wavy streaks looked "a bit like wood", even rows "too even": "make it look hand brushed"):
     * strokes of a hand, not machine rows. Each texture row is cut into strokes of their own length and depth, fading
     * in and out at their ends; a second layer crosses the first at a slightly other angle; and some patches are
     * brushed harder than others. For a tile brushed up and down, pass the coordinates swapped.
     */
    private fun brushed(px: Int, py: Int): Double {
        var sum = 0.0

        for (layer in 0..1) {
            // The stroke's slight angle: each layer its own
            val y = py + px * if (layer == 0) 0.004 else -0.007
            val row = floor(y).toInt()

            // A row's stroke length and shift, and a stroke's depth, kept while the pixels stay in them: the same
            // numbers, worked out once a stroke instead of once a pixel
            if (row != strokeRow[layer]) {
                strokeRow[layer] = row
                strokeLength[layer] = 140.0 + 260.0 * hashCell(row, 31 + layer)
                strokeShift[layer] = hashCell(row, 47 + layer) * strokeLength[layer]
                strokeIndex[layer] = Int.MIN_VALUE
            }

            val along = (px + strokeShift[layer]) / strokeLength[layer]
            val stroke = floor(along).toInt()

            if (stroke != strokeIndex[layer]) {
                strokeIndex[layer] = stroke
                strokeDepth[layer] = hashCell(row * 7 + stroke, 59 + layer) - 0.5
            }

            val fade = sin(PI * (along - stroke))

            sum += strokeDepth[layer] * 0.11 * fade
        }

        return sum * pressure(px, py)
    }

    /** The strokes [brushed] is in, a layer each: their row, length, shift, and the stroke and its depth. */
    private val strokeRow = IntArray(2) { Int.MIN_VALUE }
    private val strokeLength = DoubleArray(2)
    private val strokeShift = DoubleArray(2)
    private val strokeIndex = IntArray(2) { Int.MIN_VALUE }
    private val strokeDepth = DoubleArray(2)

    /** The pressure's samples, every [PRESSURE_STEP_X] by [PRESSURE_STEP_Y] px; made for a [pressureSpan] square. */
    private var pressureGrid = DoubleArray(0)
    private var pressureCols = 0
    private var pressureSpan = -1

    /**
     * How hard the brush pressed at [px], [py]: harder in some patches than in others. The noise moves slowly, so it is
     * sampled on a coarse grid ([preparePressure]) and blended between the samples, once per build instead of once a
     * pixel; the difference does not show.
     */
    private fun pressure(px: Int, py: Int): Double {
        val fx = px / PRESSURE_STEP_X
        val fy = py / PRESSURE_STEP_Y
        val c = floor(fx).toInt()
        val r = floor(fy).toInt()
        val tx = fx - c
        val ty = fy - r
        val i = r * pressureCols + c
        val top = pressureGrid[i] + (pressureGrid[i + 1] - pressureGrid[i]) * tx
        val bottom = pressureGrid[i + pressureCols] + (pressureGrid[i + pressureCols + 1] - pressureGrid[i + pressureCols]) * tx

        return top + (bottom - top) * ty
    }

    /** The pressure's samples for coordinates up to [span] either way: a turned tile's are swapped. */
    private fun preparePressure(span: Int) {
        if (span == pressureSpan) {
            return
        }

        val cols = (span / PRESSURE_STEP_X).toInt() + 2
        val rows = (span / PRESSURE_STEP_Y).toInt() + 2
        val grid = DoubleArray(cols * rows)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                grid[r * cols + c] =
                    0.55 + 0.45 * grainNoise.noise(c * PRESSURE_STEP_X * 0.006 + 3.1, r * PRESSURE_STEP_Y * 0.02 + 8.7)
            }
        }

        pressureGrid = grid
        pressureCols = cols
        pressureSpan = span
    }

    /**
     * Builds the plate + title textures and meshes. The heavy per-pixel loops
     * run in row batches on macrotasks so the UI stays responsive — call this
     * during the start page's loading sequence. [onReady] fires once the
     * background is fully built; immediately if it already is, and also on a
     * failed WebGL setup, so callers never wait forever.
     */
    fun prepare(onReady: () -> Unit = {}) {
        if (prepared || failed) {
            onReady()
            return
        }
        onPrepared.add(onReady)
        // Watchdog: if the 3D pipeline never comes up (no WebGL, three.js
        // failed to load), fail soft instead of leaving the boot screen stuck.
        // Only fires when the build has not even STARTED — an in-progress
        // chunked build has its own error guards.
        window.setTimeout({
            if (!prepared && !preparing && !failed) {
                markFailed("3D background never became ready (WebGL unsupported?)")
            }
        }, 5000)
        startPrepareIfPossible()
    }

    private fun startPrepareIfPossible() {
        if (preparing || prepared) return
        // Not mounted / addon not ready yet — setupScene calls again when it is.
        val ctx = sceneCtx ?: return
        val addon = threeAddon ?: return
        preparing = true
        buildTopShare = topShare(window.innerHeight.toDouble(), props.composition)

        try {
            val texW = ceil(TEX_HEIGHT * aspect).toInt()
            val plateMap = buildPlateBaseMap(addon, texW, TEX_HEIGHT)

            buildTitleOverlayMaps(addon, texW, TEX_HEIGHT) { titleAlbedo, titleNormal ->
                // The meshes as soon as the surface starts: its tiles come in one by one; ready once all are in
                generateMotorNormalMap(
                    addon = addon,
                    width = texW,
                    height = TEX_HEIGHT,
                    onStart = { normalMap -> buildMeshes(ctx, addon, normalMap, plateMap, titleAlbedo, titleNormal) },
                    onDone = {
                        prepared = true
                        preparing = false

                        // The plate shows once the Motor warms up: dim until it is powered on
                        // The standby light may show now, while the Motor warms up
                        VisualState.lamp.plateReady()

                        onPrepared.forEach { it() }
                        onPrepared.clear()

                        rebuildIfPending(addon)
                    },
                )
            }
        } catch (e: Throwable) {
            markFailed(e)
        }
    }

    private fun buildMeshes(
        ctx: ThreeJsContext,
        addon: ThreeJsAddon,
        normalMap: CanvasTexture,
        plateMap: CanvasTexture,
        titleAlbedo: CanvasTexture,
        titleNormal: CanvasTexture,
    ) {
        val geometry = addon.createPlaneGeometry(2.0 * aspect, 2.0)
        val mat = addon.createMeshStandardMaterial(jsObject {
            color = 0xffffff            // set to the look's ground below; the map, neutral, shapes it
            roughness = 0.28
            metalness = 0.97
            val d = this.asDynamic()
            d.map = plateMap
            d.normalMap = normalMap
            d.normalScale = addon.createVector2(0.9, 0.9)
            d.transparent = true
            d.opacity = 0.0             // fades in with the main light (see animate)
        })
        // The look's ground, taken raw as the halo around the title is (see applyLook)
        mat.asDynamic().color.setHex(plateColor(look), "srgb-linear")
        baseMap = plateMap
        val pl = addon.createMesh(geometry, mat)
        ctx.scene.add(pl)

        plane = pl
        material = mat

        // Title overlay — its own plane a hair in front of the plate, so the
        // title can stay at full brightness while the plate is dimmed.
        val titleMat = addon.createMeshStandardMaterial(jsObject {
            color = 0xffffff
            roughness = 0.28
            metalness = 0.97
            val d = this.asDynamic()
            d.map = titleAlbedo
            d.normalMap = titleNormal
            // Stronger than the plate's: the facets need to bend the reflection far
            // enough that its structure reads, not just shimmer.
            d.normalScale = addon.createVector2(1.4, 1.4)
            d.transparent = true
            d.opacity = 0.0             // fades in with the main light (see animate)
        })
        val titlePl = addon.createMesh(addon.createPlaneGeometry(2.0 * aspect, 2.0), titleMat)
        titlePl.asDynamic().position.z = 0.01
        ctx.scene.add(titlePl)

        titlePlane = titlePl
        titleMaterial = titleMat
    }

    /**
     * Runs [rows] rows of per-pixel work in [chunkRows]-row batches, yielding
     * to the event loop between batches so the UI can breathe.
     */
    private fun runChunked(rows: Int, block: (from: Int, toExclusive: Int) -> Unit, onDone: () -> Unit) {
        fun step(from: Int) {
            // The steps run as detached macrotasks — an escaping exception would
            // silently strand prepare() waiters, so every step fails soft.
            try {
                if (from >= rows) {
                    onDone()
                    return
                }
                val to = min(rows, from + chunkRows)
                block(from, to)
                window.setTimeout({ step(to) }, 0)
            } catch (e: Throwable) {
                markFailed(e)
            }
        }
        step(0)
    }

    ////  LISTENERS (for cleanup)  ////////////////////////////////////////////////////////////////////////////////

    private var resizeListener: ((Event) -> Unit)? = null
    private var resizeTimer: Int? = null

    ////  PUBLIC API (call via motorBackgroundRef)  ///////////////////////////////////////////////////////////////

    // The lamp's commands (warm up, power on, settle, hover) are the Spine's: VisualState.lamp. This plate only shows its
    // light, and builds itself: [prepare]

    ////  LIFECYCLE  //////////////////////////////////////////////////////////////////////////////////////////////

    private var failed: Boolean by value(false)

    /**
     * Central fail-soft: ANY 3D-background error drops to the flat black backdrop and, crucially, releases every
     * [prepare] waiter, so an unsupported browser can never wedge the start flow.
     */
    private fun markFailed(e: Any?) {
        if (failed) return
        console.error("MotorBackground error — falling back to flat bg", e)
        runCatching { teardown() }
        failed = true
        preparing = false
        onPrepared.forEach { runCatching { it() } }
        onPrepared.clear()
    }

    /** The composition the textures were last asked for: another one rebuilds them. */
    private var builtFor: PlateComposition = props.composition

    init {
        lifecycle {
            onMount {
                // The lamp works while a plate shows it
                lightFollow = VisualState.light.subscribeToStream { light = it }
            }
            onUpdate {
                if (props.composition != builtFor) {
                    builtFor = props.composition
                    // As a resize does: a build that runs rebuilds when it is done
                    threeAddon?.let { addon ->
                        when {
                            prepared -> rebuildTextures(addon)
                            preparing -> rebuildPending = true
                        }
                    }
                }
            }
            onUnmount { teardown() }
            onError { e -> markFailed(e) }
        }
    }

    override fun VDom.render() {
        div {
            key = "motor-bg"
            style = "position:fixed;top:0;left:0;width:100%;height:100%;" +
                    // Black under the plate: it fades in as the light comes on, and over black it rises as itself, with no
                    // other colour mixed in (the maintainer, 2026-10-26: "the initial background color must match")
                    "z-index:-1;pointer-events:none;background-color:#000;"

            if (!failed) {
                // The lamp rests while the window has no attention (VisualState.light), but Kraft's three.js loop draws
                // every frame regardless: a pause for it, and a clock to join the shared frame, is ULTRA-00270
                ThreeJs(
                    onReady = { ctx -> setupScene(ctx) },
                    onFrame = { f -> animate(f) },
                    createCamera = { a -> buildCamera(a) },
                    alpha = true,
                    antialias = false,
                )
            }
        }
    }

    ////  CAMERA  /////////////////////////////////////////////////////////////////////////////////////////////////

    private fun buildCamera(initialAspect: Double): Camera {
        aspect = initialAspect
        val camSize = 1.0
        val cam = threeAddon!!.createOrthographicCamera(
            left = -camSize * initialAspect,
            right = camSize * initialAspect,
            top = camSize,
            bottom = -camSize,
            near = 0.1,
            far = 10.0,
        )
        cam.position.z = 1.0
        return cam
    }

    ////  SCENE SETUP  ////////////////////////////////////////////////////////////////////////////////////////////

    private fun setupScene(ctx: ThreeJsContext) = try {
        setupSceneUnsafe(ctx)
    } catch (e: Throwable) {
        markFailed(e)
    }

    private fun setupSceneUnsafe(ctx: ThreeJsContext) {
        val addon = threeAddon ?: return
        renderer = ctx.renderer
        camera = ctx.camera

        val w = window.innerWidth
        val h = window.innerHeight
        aspect = w.toDouble() / h.toDouble()

        renderer?.setPixelRatio(min(window.devicePixelRatio, 2.0))
        renderer?.setSize(w, h)

        // No scene background: the canvas stays transparent (alpha = true), so the black wrapper div shows
        // through until the plate fades in.

        // The plate and title textures are built at once, row-chunked by [prepare] so the page stays responsive;
        // they show once the Motor warms up ([warmUp])
        sceneCtx = ctx
        prepare()

        // ── Lighting ──

        val lightColor = lightColor(look)
        val ambient = addon.createAmbientLight(color = lightColor, intensity = 0.5)

        ctx.scene.add(ambient)
        ambientLight = ambient

        // Start dark — light turns on when powerOn() is called
        val main = addon.createPointLight(color = lightColor, intensity = 0.0, distance = 0.0, decay = 1.0)
        main.position.set(0.0, 0.0, SwingingLamp.DEFAULT_Z)
        ctx.scene.add(main)
        mainLight = main

        val fill = addon.createDirectionalLight(color = lightColor, intensity = 0.0)
        fill.position.set(-1.0, -0.5, 0.5)
        ctx.scene.add(fill)
        fillLight = fill

        // Hover reactivity is driven externally via hoverStart() / hoverEnd()
        // — call them from the hosting component's mouse-enter / mouse-leave.

        // ── Resize ──
        registerResizeListener(addon)
    }

    ////  LISTENERS  //////////////////////////////////////////////////////////////////////////////////////////////

    private fun registerResizeListener(addon: ThreeJsAddon) {
        val handler: (Event) -> Unit = { _ ->
            val w = window.innerWidth
            val h = window.innerHeight
            aspect = w.toDouble() / h.toDouble()
            renderer?.setSize(w, h)
            camera?.let { cam ->
                val cd = cam.asDynamic()
                cd.left = -aspect
                cd.right = aspect
                cd.updateProjectionMatrix()
            }
            plane?.let { pl ->
                pl.geometry.dispose()
                pl.geometry = addon.createPlaneGeometry(2.0 * aspect, 2.0)
            }
            titlePlane?.let { pl ->
                pl.geometry.dispose()
                pl.geometry = addon.createPlaneGeometry(2.0 * aspect, 2.0)
            }
            resizeTimer?.let { window.clearTimeout(it) }
            resizeTimer = window.setTimeout({
                // Nothing to rebuild before the first build has started; one that runs rebuilds when it is done
                when {
                    prepared -> rebuildTextures(addon)
                    preparing -> rebuildPending = true
                }
            }, 300)
        }
        window.addEventListener("resize", handler)
        resizeListener = handler
    }

    /**
     * Builds the textures again, for a new window size. One build at a time: a resize while one runs asks for one more
     * when it is done, so a build never mixes two window heights (the title's place is taken once, as it starts).
     */
    private fun rebuildTextures(addon: ThreeJsAddon) {
        if (rebuilding) {
            rebuildPending = true
            return
        }

        rebuilding = true
        buildTopShare = topShare(window.innerHeight.toDouble(), props.composition)

        val newTexW = ceil(TEX_HEIGHT * aspect).toInt()
        val newBase = buildPlateBaseMap(addon, newTexW, TEX_HEIGHT)

        buildTitleOverlayMaps(addon, newTexW, TEX_HEIGHT) { newTitleAlbedo, newTitleNormal ->
            // The surface goes in as it starts, its tiles coming in one by one; the next build waits for it
            generateMotorNormalMap(
                addon = addon,
                width = newTexW,
                height = TEX_HEIGHT,
                onStart = { newNormal ->
                    material?.let { mat ->
                        val mDyn = mat.asDynamic()
                        val existingNormal = mDyn.normalMap
                        if (existingNormal != null && existingNormal != undefined) existingNormal.dispose()
                        mDyn.normalMap = newNormal
                        val existingBase = mDyn.map
                        if (existingBase != null && existingBase != undefined) existingBase.dispose()
                        mDyn.map = newBase
                        mDyn.needsUpdate = true
                    }
                    titleMaterial?.let { mat ->
                        val mDyn = mat.asDynamic()
                        val existingNormal = mDyn.normalMap
                        if (existingNormal != null && existingNormal != undefined) existingNormal.dispose()
                        mDyn.normalMap = newTitleNormal
                        val existingBase = mDyn.map
                        if (existingBase != null && existingBase != undefined) existingBase.dispose()
                        mDyn.map = newTitleAlbedo
                        mDyn.needsUpdate = true
                    }
                    baseMap = newBase
                },
                onDone = {
                    rebuilding = false

                    rebuildIfPending(addon)
                },
            )
        }
    }

    /** After a build: the rebuild a resize asked for while it ran. */
    private fun rebuildIfPending(addon: ThreeJsAddon) {
        if (rebuildPending) {
            rebuildPending = false
            rebuildTextures(addon)
        }
    }

    ////  RENDER LOOP  ////////////////////////////////////////////////////////////////////////////////////////////

    private fun animate(frame: ThreeJsFrame) = try {
        animateUnsafe(frame)
    } catch (e: Throwable) {
        markFailed(e)
    }

    private fun animateUnsafe(@Suppress("UNUSED_PARAMETER") frame: ThreeJsFrame) {
        if (failed) return

        // The lamp's light as the Spine works it out ([VisualState.light]): where it hangs, how bright it shines
        val lamp = light

        mainLight?.let { main ->
            main.position.x = lamp.x
            main.position.y = lamp.y
            main.position.z = lamp.z
            main.intensity = lamp.intensity
        }
        fillLight?.let { it.intensity = lamp.fill }

        // The plate fades in with the main light: while the light is off the canvas is transparent and the black
        // backdrop shows through. The title overlay is exempt from the dimming cap and stays bright. Driven by the
        // unflickered level so the fade itself stays steady.
        val lit = min(1.0, lamp.level)
        material?.asDynamic()?.opacity = maxPlateOpacity * lit
        titleMaterial?.asDynamic()?.opacity = lit
    }

    ////  TEARDOWN  ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Taken down: a build that still runs stops. */
    private var tornDown = false

    private fun teardown() {
        tornDown = true
        resizeListener?.let { window.removeEventListener("resize", it) }
        resizeTimer?.let { window.clearTimeout(it) }
        resizeListener = null
        resizeTimer = null
        lightFollow?.invoke()
        lightFollow = null
    }

    ////  PROCEDURAL NORMAL MAP  //////////////////////////////////////////////////////////////////////////////////

    /** A number from 0 to 1 for a cell, the same for the same cell and [seed]. */
    private fun hashCell(col: Int, row: Int): Double {
        var h = (col * 73856093) xor (row * 19349663) xor seed
        h = ((h ushr 16) xor h) * 0x45d9f3b
        h = ((h ushr 16) xor h) * 0x45d9f3b
        h = (h ushr 16) xor h
        return (h and 0xffff).toDouble() / 0xffff.toDouble()
    }

    /**
     * Fractal Brownian motion over [grainNoise] — [octaves] Perlin layers at
     * doubling frequency and halving amplitude, normalized back to ~`[-1, 1]`.
     * Extra octaves add finer structure on top of the first layer's broad flow
     * without speeding that flow up, so it composes with the base frequency
     * instead of fighting it.
     */
    private fun fbm(x: Double, y: Double, octaves: Int): Double {
        var sum = 0.0
        var norm = 0.0
        var amp = 1.0
        var fx = x
        var fy = y
        repeat(octaves) {
            sum += grainNoise.noise(fx, fy) * amp
            norm += amp
            amp *= 0.5
            // Doubling the coordinates doubles the caller's fractional offset too,
            // so no octave can land on a lattice node.
            fx *= 2.0
            fy *= 2.0
        }
        return if (norm > 0.0) sum / norm else 0.0
    }

    private fun n2c(n: Double): Int = max(0.0, min(255.0, (n + 1.0) * 127.5)).toInt()

    /**
     * Renders the composition's engraving ("KLANGMOTOR", "KLANG AMP") flat (no perspective) in the middle of its plate;
     * nothing when it has none.
     *
     * Caller is responsible for clip / blur / fillStyle setup on `tctx`.
     */
    private fun drawTitleText(
        tctx: CanvasRenderingContext2D,
        width: Int,
        height: Int,
        fillStyle: String,
    ) {
        val (text, cx, cy) = engraving(width, height) ?: return
        tctx.fillStyle = fillStyle
        applyTitleTextStyle(tctx, height)
        tctx.fillText(text, cx, cy)
    }

    /**
     * Albedo map for the plate: a neutral white face with darker seams; the material's colour, the look's ground,
     * tints it.
     * The title lives on its own overlay plane (see [buildTitleOverlayMaps]) so
     * the plate can be dimmed without dimming the title.
     */
    private fun buildPlateBaseMap(addon: ThreeJsAddon, width: Int, height: Int): CanvasTexture {
        val cnv = document.createElement("canvas") as HTMLCanvasElement
        cnv.width = width
        cnv.height = height
        val tctx = cnv.getContext("2d") as CanvasRenderingContext2D
        // Neutral: the material's colour, the look's ground, tints it (a white face takes that colour as it is)
        tctx.fillStyle = "#ffffff"
        tctx.fillRect(0.0, 0.0, width.toDouble(), height.toDouble())

        val plates = raisedPlates(width)
        val originY = gridOriginY(height)
        val offset = offsetRows(width, height)

        // Dark seam lines on the panel grid: albedo crispness on top of the normal-map bevels, about half the face's
        // brightness, as they were. None crosses a plate, and none runs along its sides, which are its own edges.
        tctx.strokeStyle = "#858585"
        tctx.lineWidth = 2.0

        fun line(x0: Double, y0: Double, x1: Double, y1: Double) {
            tctx.beginPath()
            tctx.moveTo(x0, y0)
            tctx.lineTo(x1, y1)
            tctx.stroke()
        }

        // The columns, a row at a time: in a row a plate with its sides half way across a tile lies in, the tiles lie
        // half a tile over, so their seams meet its sides ([offsetRows])
        var row = rowIndexOf(0.0, originY)

        while (originY + row * PANEL_CELL < height) {
            val y0 = originY + row * PANEL_CELL
            val y1 = y0 + PANEL_CELL
            val inRow = plates.filter { it.top < y1 - 1.0 && it.bottom > y0 + 1.0 }
            var gx = if (offset.getOrElse(row + 1) { false }) gridOriginInPlateRow(width) else gridOrigin(width)

            while (gx <= width) {
                if (inRow.none { gx >= it.left - 1.0 && gx <= it.right + 1.0 }) {
                    line(gx, max(y0, 0.0), gx, min(y1, height.toDouble()))
                }

                gx += PANEL_CELL
            }

            row++
        }

        // The rows, across the screen but for the plates they meet: no line along a plate's top or bottom
        var gy = originY

        while (gy <= height) {
            var x = 0.0

            for (plate in plates.filter { gy >= it.top - 1.0 && gy <= it.bottom + 1.0 }.sortedBy { it.left }) {
                if (plate.left > x) {
                    line(x, gy, plate.left, gy)
                }

                x = max(x, plate.right)
            }

            if (x < width) {
                line(x, gy, width.toDouble(), gy)
            }

            gy += PANEL_CELL
        }

        // Only the composition's own tiles, the rest left black: the plate's face kept where a tile lies, clear outside,
        // the material's transparency shows the black backdrop there ([PlateComposition.bareOutside]); with its tiles
        // switched off, none ([PlateComposition.platesShown])
        if (props.composition.bareOutside) {
            val kept = plates.takeIf { props.composition.platesShown }.orEmpty()

            if (kept.isEmpty()) {
                // Nothing kept: all clear. An empty path filled draws nothing and so clears nothing
                tctx.clearRect(0.0, 0.0, width.toDouble(), height.toDouble())
            } else {
                // One path for all of them, filled once: a fill per plate would keep only what lies in every plate
                tctx.globalCompositeOperation = "destination-in"
                tctx.fillStyle = "#ffffff"
                tctx.beginPath()

                for (plate in kept) {
                    tctx.rect(plate.left, plate.top, plate.right - plate.left, plate.bottom - plate.top)
                }

                tctx.fill()
                tctx.globalCompositeOperation = "source-over"
            }
        }

        val tex = addon.createCanvasTexture(cnv)
        tex.wrapS = TextureWrapping.ClampToEdgeWrapping
        tex.wrapT = TextureWrapping.ClampToEdgeWrapping
        return tex
    }

    /**
     * Albedo + normal map for the title overlay plane.
     *
     * The albedo is "KLANGMOTOR" on a TRANSPARENT background — the material's
     * alpha masks the overlay down to the letters (plus a plate-colored halo
     * under the bevel ring), so the rest of this plane is invisible. The normal
     * map carries the hammered letter fill and the raised edge bevels that used
     * to be carved into the plate's normal map.
     */
    private fun buildTitleOverlayMaps(
        addon: ThreeJsAddon,
        width: Int,
        height: Int,
        onDone: (albedo: CanvasTexture, normal: CanvasTexture) -> Unit,
    ) {
        // ── Albedo: text only, transparent elsewhere ──
        val cnv = document.createElement("canvas") as HTMLCanvasElement
        cnv.width = width
        cnv.height = height
        val tctx = cnv.getContext("2d") as CanvasRenderingContext2D
        // Clip so the blur halo doesn't bleed below the letter baseline — the
        // bottom edge of the engraving stays sharp.
        val fontSize = titleFontSize(height)
        val textCy = engraving(width, height)?.third ?: 0.0
        val textBottomY = textCy + fontSize / 2.0
        tctx.save()
        tctx.beginPath()
        tctx.rect(0.0, 0.0, width.toDouble(), textBottomY)
        tctx.clip()
        // Dark halo first — matches the plate tone, so the bevel ring around the
        // letters has pixels to render on (its width matches the engrave mask blur).
        tctx.asDynamic().filter = "blur(3px)"
        drawTitleText(tctx, width, height, "#${plateColor(look).toString(16).padStart(6, '0')}")
        // Mid-grey text — with the noise normals and metallic reflection this reads
        // as a faceted glass/crystal inlay. On a metal the albedo IS the reflectance,
        // so this doubles as the brightness knob: dark enough that the facets keep
        // their light/dark structure instead of blowing out into one bright blob.
        tctx.asDynamic().filter = "blur(1px)"
        drawTitleText(tctx, width, height, "#7d7d7d")
        tctx.restore()

        val albedoTex = addon.createCanvasTexture(cnv)
        albedoTex.wrapS = TextureWrapping.ClampToEdgeWrapping
        albedoTex.wrapT = TextureWrapping.ClampToEdgeWrapping

        // ── Normal map: hammered fill inside the letters, bevels at the edges ──
        val ncnv = document.createElement("canvas") as HTMLCanvasElement
        ncnv.width = width
        ncnv.height = height
        val g2d = ncnv.getContext("2d") as CanvasRenderingContext2D
        val imageData = g2d.createImageData(width.toDouble(), height.toDouble())
        val d = imageData.data.asDynamic()

        // Soft-edged alpha mask — its gradient gives the bevel normals.
        val engraveMask = buildEngraveMask(width, height)
        val engraveStrength = 2.5

        // Only the rows round the letters are worked out, the rest is flat: the mask is nought there, so is its
        // gradient (the maintainer, 2026-10-29: "Generating the tiles takes quite a bit")
        val letters = engraving(width, height)?.third
        val reach = titleFontSize(height)
        val bandFrom = letters?.let { max(0.0, it - reach).toInt() } ?: 0
        val bandTo = letters?.let { min(height.toDouble(), it + reach).toInt() } ?: 0

        for (py in (0 until bandFrom) + (bandTo until height)) {
            for (px in 0 until width) {
                val idx = (py * width + px) * 4

                d[idx] = 127
                d[idx + 1] = 127
                d[idx + 2] = 255
                d[idx + 3] = 255
            }
        }

        // Two-octave stepped surface: fine per-pixel grain + coarser facets. The facets stay hard-edged, but their
        // tilt is drawn from 2D Perlin sampled once per facet, so neighbouring facets lean in related directions and
        // the letters read as one flowing surface instead of uncorrelated sparkle. The amplitudes set how far a normal
        // swings off-axis; lower means the letters face the light more uniformly. The facets lean a little steeper than
        // they began, so a moving lamp catches them (the maintainer, 2026-10-30: "just a little steeper edges in the
        // original coarsnes so it reacts more to the light").
        val fineAmp = 0.34
        val coarseAmp = 0.22
        // Perlin lattice units per facet: how fast the direction field turns. Higher means neighbouring facets
        // diverge sooner.
        val coarseFreq = 0.0625
        // Layers stacked on that base flow, each half the amplitude at twice the frequency.
        val coarseOctaves = 4
        // The facets are irregular: each [coarseBlock] square cell holds one point somewhere inside it, and a texel
        // belongs to the facet of the nearest point. Square facets laid on a grid showed as a grid on a fine display
        // (the maintainer, 2026-10-30: "On the Macbook the resolution is so fine that i can see "a grid" in the
        // headline").
        val coarseBlock = 6
        val cellCols = width / coarseBlock + 2
        val cellRowFrom = bandFrom / coarseBlock - 1
        val cellRows = bandTo / coarseBlock + 2 - cellRowFrom
        val facetX = DoubleArray(cellCols * cellRows)
        val facetY = DoubleArray(cellCols * cellRows)
        val facetTiltX = DoubleArray(cellCols * cellRows)
        val facetTiltY = DoubleArray(cellCols * cellRows)

        for (cellRow in 0 until cellRows) {
            for (cellCol in 0 until cellCols) {
                val row = cellRow + cellRowFrom
                val cell = cellRow * cellCols + cellCol
                val x = cellCol + hashCell(cellCol * 7 + 3, row * 13 + 1)
                val y = row + hashCell(row * 11 + 29, cellCol * 5 + 7)
                facetX[cell] = x * coarseBlock
                facetY[cell] = y * coarseBlock
                // Two far-apart slices of the same field drive x and y independently. The fractional offsets keep
                // every sample off the lattice nodes, where Perlin is 0 by construction. Halved: Perlin is centred on
                // 0 and spans ±1, the hash spans ±0.5.
                facetTiltX[cell] = fbm(x * coarseFreq + 0.37, y * coarseFreq + 0.61, coarseOctaves) * 0.5
                facetTiltY[cell] = fbm(x * coarseFreq + 41.7, y * coarseFreq - 23.3, coarseOctaves) * 0.5
            }
        }

        runChunked(bandTo - bandFrom, { fromInBand, toInBand ->
        for (py in bandFrom + fromInBand until bandFrom + toInBand) {
            for (px in 0 until width) {
                val idx = (py * width + px) * 4
                val i1D = py * width + px
                val maskVal = engraveMask[i1D]

                var nx = 0.0
                var ny = 0.0
                var nz = 1.0
                if (maskVal > 0.5) {
                    val f1 = hashCell(px + 17, py * 3 + 5)
                    val f2 = hashCell(py * 5 + 11, px + 23)
                    // The nearest facet point lies in this texel's cell or one of its eight neighbours
                    val ownCol = px / coarseBlock
                    val ownRow = py / coarseBlock - cellRowFrom
                    var nearest = 0
                    var nearestDistance = Double.MAX_VALUE

                    for (cellRow in max(0, ownRow - 1)..min(cellRows - 1, ownRow + 1)) {
                        for (cellCol in max(0, ownCol - 1)..min(cellCols - 1, ownCol + 1)) {
                            val cell = cellRow * cellCols + cellCol
                            val dx = px + 0.5 - facetX[cell]
                            val dy = py + 0.5 - facetY[cell]
                            val distance = dx * dx + dy * dy

                            if (distance < nearestDistance) {
                                nearestDistance = distance
                                nearest = cell
                            }
                        }
                    }

                    val c1 = facetTiltX[nearest]
                    val c2 = facetTiltY[nearest]
                    nx = (f1 - 0.5) * fineAmp + c1 * coarseAmp
                    ny = (f2 - 0.5) * fineAmp + c2 * coarseAmp
                    nz = sqrt(max(0.01, 1.0 - nx * nx - ny * ny))
                } else {
                    val left = engraveMask[if (px > 0) i1D - 1 else i1D]
                    val right = engraveMask[if (px < width - 1) i1D + 1 else i1D]
                    val up = engraveMask[if (py > 0) i1D - width else i1D]
                    val down = engraveMask[if (py < height - 1) i1D + width else i1D]
                    val gx = (right - left) * 0.5
                    val gy = (down - up) * 0.5
                    val gMag = sqrt(gx * gx + gy * gy)
                    if (gMag > 0.001) {
                        // Gradient points INTO the letter (mask goes 0→1 from plate→text).
                        // For a raised (embossed) letter, the bevel normal tilts OUTWARD —
                        // opposite to the gradient — so light catches the outer walls.
                        nx = -gx * engraveStrength
                        ny = -gy * engraveStrength
                        nz = sqrt(max(0.01, 1.0 - nx * nx - ny * ny))
                    }
                }

                d[idx] = n2c(nx)
                d[idx + 1] = n2c(-ny) // negate Y: canvas Y-down → WebGL Y-up
                d[idx + 2] = n2c(nz)
                d[idx + 3] = 255
            }
        }
        }, onDone = {
            g2d.putImageData(imageData, 0.0, 0.0)

            val normalTex = addon.createCanvasTexture(ncnv)
            normalTex.wrapS = TextureWrapping.ClampToEdgeWrapping
            normalTex.wrapT = TextureWrapping.ClampToEdgeWrapping

            onDone(albedoTex, normalTex)
        })
    }

    /**
     * Pre-renders "KLANGMOTOR" into a soft-edged alpha mask the size of the
     * normal map. Returns a DoubleArray where 1.0 = deep inside text, 0.0 = plain metal.
     * The gradient of this field is used to carve engraving bevels into the plate.
     */
    private fun buildEngraveMask(width: Int, height: Int): DoubleArray {
        val cnv = document.createElement("canvas") as HTMLCanvasElement
        cnv.width = width
        cnv.height = height
        val tctx = cnv.getContext("2d") as CanvasRenderingContext2D
        tctx.clearRect(0.0, 0.0, width.toDouble(), height.toDouble())
        // Narrow blur → sharp bevel walls → raised lettering with crisp edges.
        // Clip so the blur doesn't bleed below the letter baseline — the bottom
        // edge of the relief stays crisp instead of fading into the plate.
        val fontSize = titleFontSize(height)
        val textCy = engraving(width, height)?.third ?: 0.0
        val textBottomY = textCy + fontSize / 2.0
        tctx.save()
        tctx.beginPath()
        tctx.rect(0.0, 0.0, width.toDouble(), textBottomY)
        tctx.clip()
        tctx.asDynamic().filter = "blur(3px)"
        // Same flat draw as the albedo map so the engraved relief and the
        // painted text stay aligned.
        drawTitleText(tctx, width, height, "white")
        tctx.restore()

        val img = tctx.getImageData(0.0, 0.0, width.toDouble(), height.toDouble())
        val rgba = img.data.asDynamic()
        val size = width * height
        val mask = DoubleArray(size)
        for (i in 0 until size) {
            mask[i] = rgba[i * 4 + 3].unsafeCast<Int>() / 255.0
        }
        return mask
    }

    /**
     * Generates the machined panel-grid normal map: near-black panels with sharp V-groove seams and per-panel
     * micro-tilts that react crisply to the light. The "KLANGMOTOR" engraving lives on the separate title overlay
     * plane, see [buildTitleOverlayMaps].
     *
     * Tile by tile (the maintainer, 2026-10-29: "Generating the tiles takes quite a bit. Can we bring them in one by
     * one?", "Or can we cache the tiles?"): every tile the last build made that is still the same ([SurfaceCache]) is
     * copied at once; [onStart] then gets the texture, so it shows right away, smooth metal where a tile is still to
     * come; the rest is made one tile a macrotask, from the middle outward, each shown as it is done. [onDone] gets the
     * finished texture.
     */
    private fun generateMotorNormalMap(
        addon: ThreeJsAddon,
        width: Int,
        height: Int,
        onStart: (CanvasTexture) -> Unit,
        onDone: (CanvasTexture) -> Unit,
    ) {
        val cnv = document.createElement("canvas") as HTMLCanvasElement
        cnv.width = width
        cnv.height = height
        val g2d = cnv.getContext("2d") as CanvasRenderingContext2D
        val imageData = g2d.createImageData(width.toDouble(), height.toDouble())
        val data = imageData.data

        val d = data.asDynamic()

        // Machined panel grid: SHARP seams that catch the light.
        // Each seam is a V-groove: its two walls tilt in opposite directions,
        // so one wall flares up while the other darkens as the light passes.
        // Each panel additionally gets a tiny constant tilt of its own, so
        // adjacent faces take the light differently and even the flat faces
        // show a crisp step at the seam.
        val seamHalf = 3.0
        val bevel = 0.55
        val originX = gridOrigin(width)
        val plates = raisedPlates(width)
        val originY = gridOriginY(height)
        val offset = offsetRows(width, height)
        val plateRowOriginX = gridOriginInPlateRow(width)
        // Whether a tile lies turned, and its tilt, worked out when the loop enters another tile, not for every pixel
        var turnedCol = Int.MIN_VALUE
        var turnedRow = Int.MIN_VALUE
        var tileTurned = false
        var tileTiltX = 0.0
        var tileTiltY = 0.0
        // The plates the cell being made touches: most touch none
        var cellPlates: List<TitlePlate> = plates

        preparePressure(max(width, height))

        fun pixel(px: Int, py: Int) {
            val idx = (py * width + px) * 4

            // The panels before the grid's origin are numbered from -1 down: floor, not truncation
            // A row a plate with its sides half way across a tile lies in lies half a tile over ([offsetRows])
            val row = floor((py - originY) / PANEL_CELL).toInt()
            val rowOriginX = if (offset.getOrElse(row + 1) { false }) plateRowOriginX else originX
            val col = floor((px - rowOriginX) / PANEL_CELL).toInt()
            val u = px - rowOriginX - col * PANEL_CELL
            val v = py - originY - row * PANEL_CELL

            // On a plate: a flat face, no seams, and its edges stepping up from the tiles
            var on: TitlePlate? = null

            for (plate in cellPlates) {
                if (plate.contains(px.toDouble(), py.toDouble())) {
                    on = plate
                    break
                }
            }
            val onPlate = on != null

            if (col != turnedCol || row != turnedRow) {
                turnedCol = col
                turnedRow = row
                tileTurned = isTurned(col, row)
                tileTiltX = (hashCell(col * 3 + 11, row * 7 + 29) - 0.5) * 0.08
                tileTiltY = (hashCell(col * 5 + 43, row * 3 + 71) - 0.5) * 0.08
            }

            // Per-panel constant tilt; the plate's face lies flat
            val tiltX = if (onPlate) 0.0 else tileTiltX
            val tiltY = if (onPlate) 0.0 else tileTiltY

            // V-groove seams along the grid lines; the plate's edges as a tile's, so it meets its neighbours as tiles
            // meet (the maintainer, 2026-10-26)
            val seamNx = when {
                on != null && px - on.left < seamHalf -> -bevel * (1.0 - (px - on.left) / seamHalf)
                on != null && on.right - px <= seamHalf -> bevel * (1.0 - (on.right - px) / seamHalf)
                on != null -> 0.0
                u < seamHalf -> -bevel * (1.0 - u / seamHalf)
                u > PANEL_CELL - seamHalf -> bevel * (1.0 - (PANEL_CELL - u) / seamHalf)
                else -> 0.0
            }
            val seamNy = when {
                on != null && py - on.top < seamHalf -> -bevel * (1.0 - (py - on.top) / seamHalf)
                on != null && on.bottom - py <= seamHalf -> bevel * (1.0 - (on.bottom - py) / seamHalf)
                on != null -> 0.0
                v < seamHalf -> -bevel * (1.0 - v / seamHalf)
                v > PANEL_CELL - seamHalf -> bevel * (1.0 - (PANEL_CELL - v) / seamHalf)
                else -> 0.0
            }

            // Brushed steel, the plate and every tile: hairlines along the brushing tilt the face across them. A
            // tile laid turned by 90 degrees, as if fitted the wrong way round, is brushed up and down (the
            // maintainer, 2026-10-26); the title's plate lies straight
            val turned = !onPlate && tileTurned
            // The title's plate has no rivets (the maintainer, 2026-10-28: "remove the rivets on the Klangmotor
            // tile"; they came 2026-10-26)
            val nx = tiltX + seamNx + if (turned) brushed(px = py, py = px) else 0.0
            val ny = tiltY + seamNy + if (turned) 0.0 else brushed(px, py)

            val nz = sqrt(max(0.01, 1.0 - nx * nx - ny * ny))

            d[idx] = n2c(nx)
            d[idx + 1] = n2c(-ny) // negate Y: canvas Y-down → WebGL Y-up
            d[idx + 2] = n2c(nz)
            d[idx + 3] = 255
        }

        // The grid's cells, a tile each, in texture px [x0, y0, x1, y1): a pixel lies in the cell [pixel] numbers it by
        val cells = mutableListOf<IntArray>()
        var row = rowIndexOf(0.0, originY)

        while (originY + row * PANEL_CELL < height) {
            val y0 = max(0.0, ceil(originY + row * PANEL_CELL)).toInt()
            val y1 = min(height.toDouble(), ceil(originY + (row + 1) * PANEL_CELL)).toInt()
            val rowOriginX = if (offset.getOrElse(row + 1) { false }) plateRowOriginX else originX
            var col = floor(-rowOriginX / PANEL_CELL).toInt()

            while (rowOriginX + col * PANEL_CELL < width) {
                val x0 = max(0.0, ceil(rowOriginX + col * PANEL_CELL)).toInt()
                val x1 = min(width.toDouble(), ceil(rowOriginX + (col + 1) * PANEL_CELL)).toInt()

                if (x1 > x0 && y1 > y0) {
                    cells.add(intArrayOf(x0, y0, x1, y1, row))
                }

                col++
            }

            row++
        }

        // From the cache what is still the same; the rest to make, the middle first
        val cache = surfaceCache?.takeIf {
            it.seed == seed && it.width == width && it.height == height && it.originY == originY
        }
        val (cached, toMake) = cells.partition { cell -> cache != null && cache.holds(cell, offset, plates) }
        val cx = width / 2.0
        val cy = height / 2.0
        val queue = toMake.sortedBy { (x0, y0, x1, y1) -> hypot((x0 + x1) / 2.0 - cx, (y0 + y1) / 2.0 - cy) }

        for (cell in cached) {
            cache?.copyInto(d, cell)
        }

        // Smooth metal where a tile is still to come
        for (cell in queue) {
            for (py in cell[1] until cell[3]) {
                for (px in cell[0] until cell[2]) {
                    val idx = (py * width + px) * 4

                    d[idx] = 128
                    d[idx + 1] = 128
                    d[idx + 2] = 255
                    d[idx + 3] = 255
                }
            }
        }

        g2d.putImageData(imageData, 0.0, 0.0)

        val tex = addon.createCanvasTexture(cnv)
        tex.wrapS = TextureWrapping.ClampToEdgeWrapping
        tex.wrapT = TextureWrapping.ClampToEdgeWrapping
        onStart(tex)

        var uploadedMs = Date.now()

        fun step(i: Int) {
            // The steps run as detached macrotasks: an escaping exception would silently strand prepare() waiters,
            // so every step fails soft; a background taken down stops building
            try {
                if (tornDown) {
                    return
                }

                if (i >= queue.size) {
                    g2d.putImageData(imageData, 0.0, 0.0)
                    tex.asDynamic().needsUpdate = true
                    surfaceCache = SurfaceCache.of(seed, width, height, originY, offset, plates, data)
                    onDone(tex)
                    return
                }

                val (x0, y0, x1, y1) = queue[i]

                cellPlates = plates.filter { it.left < x1 && it.right > x0 && it.top < y1 && it.bottom > y0 }

                for (py in y0 until y1) {
                    for (px in x0 until x1) {
                        pixel(px, py)
                    }
                }

                g2d.putImageData(
                    imageData, 0.0, 0.0,
                    x0.toDouble(), y0.toDouble(), (x1 - x0).toDouble(), (y1 - y0).toDouble(),
                )

                // To the GPU now and then, not per tile: a whole texture goes up each time
                val now = Date.now()

                if (now - uploadedMs >= UPLOAD_EVERY_MS) {
                    uploadedMs = now
                    tex.asDynamic().needsUpdate = true
                }

                window.setTimeout({ step(i + 1) }, 0)
            } catch (e: Throwable) {
                markFailed(e)
            }
        }

        step(0)
    }
}
