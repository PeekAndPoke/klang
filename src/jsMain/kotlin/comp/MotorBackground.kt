/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.common.math.PerlinNoise2D
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
import io.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.utils.jsObject
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.maths.Smoothing
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.Unsubscribe
import io.peekandpoke.ultra.streams.ops.smooth
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.style
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
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
): ComponentRef<MotorBackground> {
    val created = comp { MotorBackground(it) }
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
class MotorBackground(ctx: NoProps) : PureComponent(ctx) {

    companion object {
        /**
         * The lamp sways as if it hung on a cord, left and right of the centre, very slightly and very slowly (the
         * maintainer, 2026-10-28): this far at the most, in the scene's units (the plate is 2 high, the lamp 3 in
         * front of it), and one swing there and back in [LIGHT_SWAY_PERIOD_MS].
         */
        private const val LIGHT_SWAY = 0.12
        private const val LIGHT_SWAY_PERIOD_MS = 16_000.0

        /**
         * The line the lamp sways along turns slowly round, as a Foucault pendulum's (the maintainer, 2026-10-28: "make
         * the light pendulum direction rotate slowly"): once round in this time.
         */
        private const val LIGHT_SWAY_TURN_MS = 90_000.0

        /** The texture's height in px; its width follows the screen's aspect. */
        private const val TEX_HEIGHT = 2048

        /** Panel-grid cell size in texture px: shared by the normal map, the albedo seams and the title's plate. */
        private const val PANEL_CELL = 330.0

        /**
         * The start page's result panel, in screen px, under the gauges' row, which is one tile row high: the title,
         * the gauges and the panel are placed as one.
         */
        private const val PANEL_UNDER_GAUGES_PX = 250.0

        /**
         * The share of the free height above the title; the rest is under the block. Half: the title and the block
         * stand in the middle of the screen, under the light, which stands there too (the maintainer, 2026-10-26: "When
         * i am in fullscreen everything is too far up", and at a third above: "Still too far up").
         */
        private const val FREE_ABOVE = 0.5

        /** The plate's reach above and below the title's middle, as shares of the texture's height. */
        private val plateReach: Pair<Double, Double> by lazy {
            val plate = titlePlate(width = TEX_HEIGHT, height = TEX_HEIGHT, centerShare = 0.0)

            (-plate.top / TEX_HEIGHT) to (plate.bottom / TEX_HEIGHT)
        }

        /** Where KLANGMOTOR's middle sits in a screen [screenHeight] px high, as a share of it from the top. */
        fun titleCenterShare(screenHeight: Double): Double {
            val (above, below) = plateReach
            val composition = (above + below) * screenHeight + tileWidthPx(screenHeight) + PANEL_UNDER_GAUGES_PX
            val free = (screenHeight - composition).coerceAtLeast(0.0)

            return ((free * FREE_ABOVE + above * screenHeight) / screenHeight).coerceIn(0.12, 0.4)
        }

        /**
         * Where the title's plate ends in a screen [screenHeight] px high, as a share of it from the top: the texture
         * spans the screen, so the start page sets its gauges under it at the same gap as the panel under the gauges
         * (the maintainer, 2026-10-26).
         */
        fun titlePlateBottomShare(screenHeight: Double): Double = titleCenterShare(screenHeight) + plateReach.second

        /**
         * A tile's width on a screen [screenHeight] px high, in screen px: the texture spans the screen's height. The
         * columns are centred on the screen, a tile's middle on the screen's, so the start page sets its side gauges a
         * tile's width out (the maintainer, 2026-10-27: each gauge centred in its tile).
         */
        fun tileWidthPx(screenHeight: Double): Double = PANEL_CELL / TEX_HEIGHT * screenHeight

        /**
         * The plate under KLANGMOTOR (the maintainer, 2026-10-26: "one nice tile underneith the text so that no lines
         * are crossing it"; then smaller, "NOT be alligned with the rest of the grid", and "a bit wider and raise it
         * instead of carving it"): the title's box and a margin, a plate standing on the tiles wherever the grid
         * runs; no seam crosses it. [left], [top], [right] and [bottom] are in texture px.
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

        /**
         * The plate under the title in a texture [width] by [height]: one tile row high round the letters' own outline
         * (not the font's box, whose middle sits above the capitals' middle), so the title sits in its middle, and an
         * even number of tiles wide.
         */
        private fun titlePlate(width: Int, height: Int, centerShare: Double): TitlePlate {
            val cnv = document.createElement("canvas") as HTMLCanvasElement
            val tctx = cnv.getContext("2d") as CanvasRenderingContext2D

            applyTitleTextStyle(tctx, height)

            val metrics = tctx.measureText("KLANGMOTOR")
            val cx = width / 2.0
            val cy = height * centerShare
            val ascent = metrics.actualBoundingBoxAscent
            val descent = metrics.actualBoundingBoxDescent
            // Exactly one tile row high, so the rows can run along its edges (the maintainer, 2026-10-26: the tiles
            // aligned with the title, "but only vertically")
            val margin = ((PANEL_CELL - ascent - descent) / 2.0).coerceAtLeast((ascent + descent) * 0.2)

            // An even number of tiles wide, round the screen's middle, where a tile's middle sits: so its sides end
            // half way across a tile, as far from the grid's lines as can be, on purpose (the maintainer, 2026-10-26:
            // "Try half"). The least that leaves the letters some air
            val textWidth = metrics.actualBoundingBoxLeft + metrics.actualBoundingBoxRight
            val leastWidth = textWidth + 2 * (ascent + descent) * 0.3
            val tiles = ceil(leastWidth / PANEL_CELL).toInt().let { if (it % 2 == 0) it else it + 1 }
            val halfWidth = tiles * PANEL_CELL / 2.0

            return TitlePlate(
                left = cx - halfWidth,
                top = cy - ascent - margin,
                right = cx + halfWidth,
                bottom = cy + descent + margin,
            )
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

    private var poweredOn = false

    /** The light at rest (the maintainer, 2026-10-27: 1.62 was "a bit too bright"). */
    private val defaultIntensity = 1.35
    /**
     * Over "Make Music Now" (the maintainer, 2026-10-26 and 27: twice the first swell was too bright, then 4.5 "a bit
     * too bright" too).
     */
    private val hoverIntensity = 3.6

    /** The light while the Motor warms up ([warmUp]): the plate and the title show, dim (the maintainer, 2026-10-26). */
    private val standbyIntensity = defaultIntensity * 0.18
    private var targetIntensity = 0.0

    /** Whether the Motor warms up: the standby light is on from then. */
    private var warming = false

    /** How far the light moves toward its target each frame: calm, but quick while it warms up to the standby. */
    private val intensityEase = 0.01
    private val warmUpEase = 0.05
    private var currentEase = intensityEase

    // Ceiling for the plate's blend over the black backdrop: the light, not the backdrop, decides how bright it is
    private val maxPlateOpacity = 0.85

    // Light "radius": pulling the light further from the plate widens the illuminated area, so hover reads as the light
    // growing bigger (the maintainer, 2026-10-26, once the plate was as bright as before: "the circle of light is too
    // big")
    private val defaultLightZ = 3.0
    /** Over "Make Music Now" the cone opens wider, "like opening your eyes" (the maintainer, 2026-10-26: wider still). */
    private val hoverLightZ = 5.2
    private var targetLightZ = defaultLightZ
    private var targetFillIntensity = 0.0

    ////  LIGHT MOTION  ///////////////////////////////////////////////////////////////////////////////////////////

    // Fixed cone size: Z only moves toward the hover/default target ([moveCone]).
    private var lightZ = defaultLightZ

    /**
     * The cone's move to [targetLightZ]: from [coneFrom], over [coneMs], easing out hard, so the eye opens quickly and
     * settles slowly (the maintainer, 2026-10-26: "Stronger ease out and longer eye opening").
     */
    private var coneFrom = defaultLightZ
    private var coneElapsedMs = 0.0
    private var coneMs = 1.0

    /**
     * The eye opening over "Make Music Now", and closing again (the maintainer: "longer please", then rather than
     * twice as long, "just stronger ease out": [easeOutExpo]).
     */
    private val coneOpenMs = 5000.0
    private val coneCloseMs = 2000.0

    private fun moveCone(to: Double, ms: Double) {
        coneFrom = lightZ
        targetLightZ = to
        coneElapsedMs = 0.0
        coneMs = ms
    }

    /**
     * Ease out, exponential: most of the way at first, then a long, very soft arrival (the maintainer: "much stronger
     * ease out" than a quintic).
     */
    private fun easeOutExpo(t: Double): Double = if (t >= 1.0) 1.0 else 1.0 - 2.0.pow(-10.0 * t)

    // Eased intensity lives here (not on the light) so the flicker riding on
    // top never pollutes the easing state.
    private var currentIntensity = 0.0
    private var flickerTime = 0.0

    /** How long the lamp has been swaying, for its pendulum. */
    private var swayElapsedMs = 0.0

    /**
     * The bulb about to break ([BulbStutter]): its goals go into [bulbGoal], and the light reads them smoothed, each
     * stutter by a law of its own (the maintainer, 2026-10-28: "build a stream with .smooth() for how bright the light
     * is and throw values in it"). One smoothed stream for the lamp's life: a stutter swaps the law ([bulbLaw]), the
     * motion goes on where it is ("when you recreate the bulbFollow you lose the history").
     */
    private val bulbGoal = StreamSource<Double?>(1.0)

    /** The law the bulb's light moves by now; a stutter puts its own. */
    private val bulbLaw = SwappableSmoothing(Smoothing.Exponential(keepPer10Ms = 0.5))

    /** The bulb's share of the light as smoothed, read every frame. */
    private var bulbShown = 1.0

    private var bulbFollow: Unsubscribe? = bulbGoal.smooth(smoothing = bulbLaw, rest = 1.0, near = 0.002)
        .subscribeToStream { bulbShown = it ?: 1.0 }

    /** Clean from the start: no stutter, no shimmer, until [settle] (the maintainer, 2026-10-28). */
    private val bulb = BulbStutter(onStutter = { bulbLaw.law = it }).apply { steady = true }

    /** How much of the filament's shimmer shows: none while the bulb is clean, easing in once it [settle]s. */
    private var shimmer = 0.0

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
     * Where the panel grid's rows start: on the title's [plate], one row high, so the rows run along its top and its
     * bottom (the maintainer, 2026-10-26: aligned with the title, "but only vertically"; the columns stay centred).
     */
    private fun gridOriginY(plate: TitlePlate): Double = (plate.top % PANEL_CELL + PANEL_CELL) % PANEL_CELL

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

    /** Where the title's middle sits, as a share of the window's height, for the build that runs: taken as it starts. */
    private var buildCenter = 0.25

    /** A rebuild runs; one asked for meanwhile waits for it ([rebuildPending]). */
    private var rebuilding = false
    private var rebuildPending = false

    /** Where the title's middle sits in the textures the running build makes. */
    private fun titleCenter(): Double = buildCenter

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
            val length = 140.0 + 260.0 * hashCell(row, 31 + layer)
            val along = (px + hashCell(row, 47 + layer) * length) / length
            val stroke = floor(along).toInt()
            val fade = sin(PI * (along - stroke))
            val depth = hashCell(row * 7 + stroke, 59 + layer) - 0.5

            sum += depth * 0.11 * fade
        }

        val pressure = 0.55 + 0.45 * grainNoise.noise(px * 0.006 + 3.1, py * 0.02 + 8.7)

        return sum * pressure
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
        buildCenter = titleCenterShare(window.innerHeight.toDouble())

        try {
            val texW = ceil(TEX_HEIGHT * aspect).toInt()
            generateMotorNormalMap(addon, texW, TEX_HEIGHT) { normalMap ->
                val plateMap = buildPlateBaseMap(addon, texW, TEX_HEIGHT)
                buildTitleOverlayMaps(addon, texW, TEX_HEIGHT) { titleAlbedo, titleNormal ->
                    buildMeshes(ctx, addon, normalMap, plateMap, titleAlbedo, titleNormal)
                    prepared = true
                    preparing = false

                    // The plate shows once the Motor warms up: dim until it is powered on
                    if (warming && !poweredOn) {
                        targetIntensity = standbyIntensity
                    }

                    onPrepared.forEach { it() }
                    onPrepared.clear()

                    rebuildIfPending(addon)
                }
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

    /**
     * The Motor warms up: the standby light fades in from black, quickly; once the plate is built if it is not yet.
     * Call when the key turns to ON.
     */
    fun warmUp() {
        prepare()
        warming = true
        currentEase = warmUpEase

        if (prepared && !poweredOn) {
            targetIntensity = standbyIntensity
        }
    }

    fun powerOn() {
        // Self-heal for flows that never called prepare() — idempotent, and the
        // plate simply fades in whenever the build finishes.
        prepare()
        warming = true
        poweredOn = true
        currentEase = intensityEase
        targetIntensity = defaultIntensity
        targetFillIntensity = 0.12
    }

    /**
     * The light comes on clean and stays so, no stutter, no shimmer, until this (the maintainer, 2026-10-28: "The light
     * must come clean on and stay clean until 5 seconds after the dynamo test"): then the shimmer eases in and the
     * bulb stutters now and then, calmly ([BulbStutter.SETTLED]).
     */
    fun settle() {
        bulb.temper = BulbStutter.SETTLED
        bulb.steady = false
    }

    /** Boosts the light and widens the cone. Call from an element's mouse-enter. */
    fun hoverStart() {
        targetIntensity = hoverIntensity
        moveCone(to = hoverLightZ, ms = coneOpenMs)
    }

    /** Reverts the hover effect. Call from the element's mouse-leave. */
    fun hoverEnd() {
        targetIntensity = when {
            poweredOn -> defaultIntensity
            warming -> standbyIntensity
            else -> 0.0
        }
        moveCone(to = defaultLightZ, ms = coneCloseMs)
    }

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

    init {
        lifecycle {
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
        main.position.set(0.0, 0.0, defaultLightZ)
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
        buildCenter = titleCenterShare(window.innerHeight.toDouble())

        val newTexW = ceil(TEX_HEIGHT * aspect).toInt()
        generateMotorNormalMap(addon, newTexW, TEX_HEIGHT) { newNormal ->
            val newBase = buildPlateBaseMap(addon, newTexW, TEX_HEIGHT)
            buildTitleOverlayMaps(addon, newTexW, TEX_HEIGHT) { newTitleAlbedo, newTitleNormal ->
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
                rebuilding = false

                rebuildIfPending(addon)
            }
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

    private fun animateUnsafe(frame: ThreeJsFrame) {
        if (failed) return
        val delta = min(frame.deltaMs, 100.0)

        // The light hangs over the centre of the screen and sways a little, as a pendulum. The cone size stays FIXED:
        // Z only moves toward the hover/default target ([moveCone]), no breathing
        swayElapsedMs += delta

        mainLight?.position?.let { pos ->
            val swing = LIGHT_SWAY * sin(2.0 * PI * swayElapsedMs / LIGHT_SWAY_PERIOD_MS)
            val direction = 2.0 * PI * swayElapsedMs / LIGHT_SWAY_TURN_MS

            pos.x = swing * cos(direction)
            pos.y = swing * sin(direction)
            coneElapsedMs += delta
            lightZ = coneFrom + (targetLightZ - coneFrom) * easeOutExpo(min(1.0, coneElapsedMs / coneMs))
            pos.z = lightZ
        }

        // Smooth intensity transitions
        currentIntensity += (targetIntensity - currentIntensity) * currentEase
        fillLight?.let { it.intensity += (targetFillIntensity - it.intensity) * 0.02 }

        // Subtle lamp flicker — a slow drift plus a faster shimmer, both smooth
        // noise. Stays within a few percent so it reads as a living filament,
        // not a faulty one.
        flickerTime += delta
        // None while the bulb is clean; once it settles, the shimmer eases in over a couple of seconds
        shimmer += ((if (bulb.steady) 0.0 else 1.0) - shimmer) * 0.02
        val flicker = 1.0 + shimmer * (
                // Three times slower than at first (the maintainer, 2026-10-26: "it flickers too often")
                // A bit livelier between the stutters (the maintainer, 2026-10-27: "a bit less stable in between")
                grainNoise.noise(flickerTime * 0.0012, 7.7) * 0.07 +
                        grainNoise.noise(flickerTime * 0.007, 3.3) * 0.035
                )
        val bulbHeads = bulb.next(delta)

        if (bulbHeads != bulbGoal()) {
            bulbGoal(bulbHeads)
        }

        mainLight?.let { it.intensity = currentIntensity * flicker * bulbShown }

        // The plate fades in with the main light: while the light is off the canvas is transparent and the black
        // backdrop shows through. The title overlay is exempt from the dimming cap and stays bright. Driven by the
        // unflickered intensity so the fade itself stays steady.
        val lit = min(1.0, currentIntensity / defaultIntensity)
        material?.asDynamic()?.opacity = maxPlateOpacity * lit
        titleMaterial?.asDynamic()?.opacity = lit
    }

    ////  TEARDOWN  ///////////////////////////////////////////////////////////////////////////////////////////////

    private fun teardown() {
        resizeListener?.let { window.removeEventListener("resize", it) }
        resizeTimer?.let { window.clearTimeout(it) }
        resizeListener = null
        resizeTimer = null
        bulbFollow?.invoke()
        bulbFollow = null
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
     * Renders "KLANGMOTOR" flat (no perspective) at the title position.
     *
     * Caller is responsible for clip / blur / fillStyle setup on `tctx`.
     */
    private fun drawTitleText(
        tctx: CanvasRenderingContext2D,
        width: Int,
        height: Int,
        fillStyle: String,
    ) {
        val textCy = height * titleCenter()
        tctx.fillStyle = fillStyle
        applyTitleTextStyle(tctx, height)
        tctx.fillText("KLANGMOTOR", width / 2.0, textCy)
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

        val plate = titlePlate(width = width, height = height, centerShare = titleCenter())

        // Dark seam lines on the panel grid: albedo crispness on top of the normal-map bevels, about half the face's
        // brightness, as they were. None crosses the title's plate: a line that would stops at its edges.
        tctx.strokeStyle = "#858585"
        tctx.lineWidth = 2.0

        fun line(x0: Double, y0: Double, x1: Double, y1: Double) {
            tctx.beginPath()
            tctx.moveTo(x0, y0)
            tctx.lineTo(x1, y1)
            tctx.stroke()
        }

        // The columns, above and below the plate's row
        var gx = gridOrigin(width)

        while (gx <= width) {
            line(gx, 0.0, gx, plate.top)
            line(gx, plate.bottom, gx, height.toDouble())

            gx += PANEL_CELL
        }

        // In the plate's row the tiles lie half a tile over, so their seams meet the plate's sides; none on the plate,
        // and none along its sides, which are its own edges
        gx = gridOriginInPlateRow(width)

        while (gx <= width) {
            if (gx < plate.left - 1.0 || gx > plate.right + 1.0) {
                line(gx, plate.top, gx, plate.bottom)
            }

            gx += PANEL_CELL
        }

        var gy = gridOriginY(plate)

        while (gy <= height) {
            // The rows along the plate's top and bottom stop at its sides: no line round the plate
            if (gy >= plate.top - 1.0 && gy <= plate.bottom + 1.0) {
                line(0.0, gy, plate.left, gy)
                line(plate.right, gy, width.toDouble(), gy)
            } else {
                line(0.0, gy, width.toDouble(), gy)
            }

            gy += PANEL_CELL
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
        val textCy = height * titleCenter()
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

        runChunked(height, { pyFrom, pyTo ->
        for (py in pyFrom until pyTo) {
            for (px in 0 until width) {
                val idx = (py * width + px) * 4
                val i1D = py * width + px
                val maskVal = engraveMask[i1D]

                var nx = 0.0
                var ny = 0.0
                var nz = 1.0
                if (maskVal > 0.5) {
                    // Two-octave stepped surface: fine per-pixel grain + coarser facets.
                    // The facets stay hard-edged (block-quantized), but their tilt is
                    // drawn from 2D Perlin sampled once per block — so neighbouring
                    // facets lean in related directions and the letters read as one
                    // flowing surface instead of uncorrelated sparkle. The amplitudes
                    // set how far a normal swings off-axis; lower means the letters
                    // face the light more uniformly.
                    val fineBlock = 1
                    val coarseBlock = 6
                    val fineAmp = 0.34
                    val coarseAmp = 0.15
                    // Perlin lattice units per facet — how fast the direction field
                    // turns. Higher means neighbouring facets diverge sooner.
                    val coarseFreq = 0.0625
                    // Layers stacked on that base flow, each half the amplitude at
                    // twice the frequency.
                    val coarseOctaves = 4
                    val fbc = px / fineBlock
                    val fbr = py / fineBlock
                    val cbc = (px / coarseBlock) * coarseFreq
                    val cbr = (py / coarseBlock) * coarseFreq
                    val f1 = hashCell(fbc + 17, fbr * 3 + 5)
                    val f2 = hashCell(fbr * 5 + 11, fbc + 23)
                    // Two far-apart slices of the same field drive x and y independently.
                    // The fractional offsets keep every sample off the lattice nodes,
                    // where Perlin is 0 by construction — without them a coarseFreq that
                    // divides 1.0 would stamp a regular grid of dead, flat facets.
                    // Halved: Perlin is centred on 0 and spans ±1, the hash spans ±0.5.
                    val c1 = fbm(cbc + 0.37, cbr + 0.61, coarseOctaves) * 0.5
                    val c2 = fbm(cbc + 41.7, cbr - 23.3, coarseOctaves) * 0.5
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
        val textCy = height * titleCenter()
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
     * Generates the machined panel-grid normal map — near-black panels with
     * sharp V-groove seams and per-panel micro-tilts that react crisply to
     * the light.
     *
     * The "KLANGMOTOR" engraving lives on the separate title overlay plane —
     * see [buildTitleOverlayMaps].
     */
    private fun generateMotorNormalMap(
        addon: ThreeJsAddon,
        width: Int,
        height: Int,
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
        val plate = titlePlate(width = width, height = height, centerShare = titleCenter())
        val originY = gridOriginY(plate)
        val plateRowOriginX = gridOriginInPlateRow(width)
        // Whether a tile lies turned, worked out when the loop enters another tile, not for every pixel
        var turnedCol = Int.MIN_VALUE
        var turnedRow = Int.MIN_VALUE
        var tileTurned = false

        runChunked(height, { pyFrom, pyTo ->
        for (py in pyFrom until pyTo) {
            for (px in 0 until width) {
                val idx = (py * width + px) * 4

                // The panels before the grid's origin are numbered from -1 down: floor, not truncation
                // The plate's row lies half a tile over ([gridOriginInPlateRow])
                val rowOriginX = if (py >= plate.top && py < plate.bottom) plateRowOriginX else originX
                val col = floor((px - rowOriginX) / PANEL_CELL).toInt()
                val row = floor((py - originY) / PANEL_CELL).toInt()
                val u = px - rowOriginX - col * PANEL_CELL
                val v = py - originY - row * PANEL_CELL

                // On the title's plate: a flat face, no seams, and its edges stepping up from the tiles
                val onPlate = plate.contains(px.toDouble(), py.toDouble())

                // Per-panel constant tilt; the plate's face lies flat
                val tiltX = if (onPlate) 0.0 else (hashCell(col * 3 + 11, row * 7 + 29) - 0.5) * 0.08
                val tiltY = if (onPlate) 0.0 else (hashCell(col * 5 + 43, row * 3 + 71) - 0.5) * 0.08

                // V-groove seams along the grid lines; the plate's edges as a tile's, so it meets its neighbours as tiles
                // meet (the maintainer, 2026-10-26)
                val seamNx = when {
                    onPlate && px - plate.left < seamHalf -> -bevel * (1.0 - (px - plate.left) / seamHalf)
                    onPlate && plate.right - px <= seamHalf -> bevel * (1.0 - (plate.right - px) / seamHalf)
                    onPlate -> 0.0
                    u < seamHalf -> -bevel * (1.0 - u / seamHalf)
                    u > PANEL_CELL - seamHalf -> bevel * (1.0 - (PANEL_CELL - u) / seamHalf)
                    else -> 0.0
                }
                val seamNy = when {
                    onPlate && py - plate.top < seamHalf -> -bevel * (1.0 - (py - plate.top) / seamHalf)
                    onPlate && plate.bottom - py <= seamHalf -> bevel * (1.0 - (plate.bottom - py) / seamHalf)
                    onPlate -> 0.0
                    v < seamHalf -> -bevel * (1.0 - v / seamHalf)
                    v > PANEL_CELL - seamHalf -> bevel * (1.0 - (PANEL_CELL - v) / seamHalf)
                    else -> 0.0
                }

                // Brushed steel, the plate and every tile: hairlines along the brushing tilt the face across them. A
                // tile laid turned by 90 degrees, as if fitted the wrong way round, is brushed up and down (the
                // maintainer, 2026-10-26); the title's plate lies straight
                if (col != turnedCol || row != turnedRow) {
                    turnedCol = col
                    turnedRow = row
                    tileTurned = isTurned(col, row)
                }

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
        }
        }, onDone = {
            g2d.putImageData(imageData, 0.0, 0.0)

            val tex = addon.createCanvasTexture(cnv)
            tex.wrapS = TextureWrapping.ClampToEdgeWrapping
            tex.wrapT = TextureWrapping.ClampToEdgeWrapping
            onDone(tex)
        })
    }
}
