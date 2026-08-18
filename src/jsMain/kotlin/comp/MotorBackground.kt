/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.common.math.PerlinNoise2D
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
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.style
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

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
 * Industrial vinyl-groove metal background with a centred spotlight whose
 * cone width breathes and whose filament subtly flickers.
 *
 * Pure Kotlin port of the former `motor-background.js` built on the kraft-threejs
 * addon. Exposes [powerOn], [startScan], [stopScan] as a lighting state machine.
 */
class MotorBackground(ctx: NoProps) : PureComponent(ctx) {

    companion object {
        // Picked once per session so sibling components (e.g. the start-page
        // power button) can match the eventual background light tint.
        // laf.bronze (#362820) is a warm overlay-background tone, too dark to
        // read as a light — substitute a brighter shade in the same hue.
        val lightColorHex: String by lazy {
            val brightBronze = "#cd9b6a"
            val palette = listOf(
                KlangTheme.Hex.excellent, KlangTheme.Hex.good, KlangTheme.Hex.moderate, KlangTheme.Hex.warning,
                KlangTheme.Hex.critical, KlangTheme.Hex.gold, brightBronze,
            )
            palette.random()
        }
    }

    ////  ADDON  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val threeAddon: ThreeJsAddon? by subscribingTo(addons.threeJs)
    private val laf by subscribingTo(KlangTheme)

    /** 2D Perlin source for the brushed-metal grain — fixed seed keeps the texture reproducible. */
    private val grainNoise = PerlinNoise2D()

    private fun pickLightColor(): Int = lightColorHex.removePrefix("#").toInt(16)

    ////  SCENE STATE  ////////////////////////////////////////////////////////////////////////////////////////////

    private var renderer: WebGLRenderer? = null
    private var camera: Camera? = null
    private var plane: Mesh? = null
    private var material: MeshStandardMaterial? = null
    private var titlePlane: Mesh? = null
    private var titleMaterial: MeshStandardMaterial? = null
    private var baseMap: CanvasTexture? = null
    private var mainLight: PointLight? = null
    private var fillLight: DirectionalLight? = null

    private var aspect: Double = 1.0
    private val texHeight = 2048

    ////  LIGHTING STATE MACHINE  /////////////////////////////////////////////////////////////////////////////////

    private var poweredOn = false
    private var scanning = false

    private val defaultIntensity = 1.62
    private val hoverIntensity = 3.0
    private var targetIntensity = 0.0

    // Ceiling for the plate's blend over the menu-colored backdrop — keeps the
    // background graphic dim enough to not compete with the page content.
    private val maxPlateOpacity = 0.55

    // Light "radius" — pulling the light further from the plate widens the
    // illuminated area, so hover reads as the light growing bigger.
    private val defaultLightZ = 3.2
    private val hoverLightZ = 3.6
    private var targetLightZ = defaultLightZ
    private var targetFillIntensity = 0.0

    ////  CONE BREATHING  /////////////////////////////////////////////////////////////////////////////////////////

    // The light sits fixed over the vinyl centre; instead of wandering around,
    // its cone width breathes by oscillating the Z distance. The eased base Z
    // (hover pulls it up) carries a sine breath on top.
    private var lightZ = defaultLightZ
    private var breathePhase = 0.0
    private var breatheAmp = 0.0
    private var breatheRate = 0.0

    // Eased intensity lives here (not on the light) so the flicker riding on
    // top never pollutes the easing state.
    private var currentIntensity = 0.0
    private var flickerTime = 0.0

    ////  DEFERRED TEXTURE BUILD  /////////////////////////////////////////////////////////////////////////////////

    private var sceneCtx: ThreeJsContext? = null
    private var prepared = false
    private var preparing = false
    private var prepareRequested = false
    private val onPrepared = mutableListOf<() -> Unit>()

    /** Rows per macrotask for the chunked per-pixel texture loops. */
    private val chunkRows = 128

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
        prepareRequested = true
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

        try {
            val texW = ceil(texHeight * aspect).toInt()
            generateMotorNormalMap(addon, texW, texHeight) { normalMap ->
                val plateMap = buildPlateBaseMap(addon, texW, texHeight)
                buildTitleOverlayMaps(addon, texW, texHeight) { titleAlbedo, titleNormal ->
                    buildMeshes(ctx, addon, normalMap, plateMap, titleAlbedo, titleNormal)
                    prepared = true
                    preparing = false
                    onPrepared.forEach { it() }
                    onPrepared.clear()
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
            color = 0xffffff            // let the map provide the base color
            roughness = 0.28
            metalness = 0.97
            val d = this.asDynamic()
            d.map = plateMap
            d.normalMap = normalMap
            d.normalScale = addon.createVector2(0.9, 0.9)
            d.transparent = true
            d.opacity = 0.0             // fades in with the main light (see animate)
        })
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
            d.normalScale = addon.createVector2(0.9, 0.9)
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

    fun powerOn() {
        // Self-heal for flows that never called prepare() — idempotent, and the
        // plate simply fades in whenever the build finishes.
        prepare()
        poweredOn = true
        targetIntensity = defaultIntensity
        targetFillIntensity = 0.12
    }

    fun startScan() {
        scanning = true
    }

    fun stopScan() {
        scanning = false
    }

    /** Boosts the light and widens the cone. Call from an element's mouse-enter. */
    fun hoverStart() {
        targetIntensity = hoverIntensity
        targetLightZ = hoverLightZ
        scanning = false
    }

    /** Reverts the hover effect. Call from the element's mouse-leave. */
    fun hoverEnd() {
        targetIntensity = if (poweredOn) defaultIntensity else 0.0
        targetLightZ = defaultLightZ
    }

    ////  LIFECYCLE  //////////////////////////////////////////////////////////////////////////////////////////////

    private var failed: Boolean by value(false)

    /**
     * Central fail-soft: ANY 3D-background error drops to the flat
     * menu-colored fallback and — crucially — releases every [prepare]
     * waiter, so an unsupported browser can never wedge the start flow.
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
                    "z-index:-1;pointer-events:none;background-color:${laf.menuBackground};"

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

        // No scene background — the canvas stays transparent (alpha = true) so the
        // menu-colored wrapper div shows through until the plate fades in.

        // The plate + title textures are NOT built here — their per-pixel loops
        // block the main thread for a noticeable moment, which froze the start
        // page's power button right after mount. They are built by [prepare]
        // (row-chunked, UI-friendly) during the start page's loading sequence.
        sceneCtx = ctx
        if (prepareRequested) {
            startPrepareIfPossible()
        }

        // ── Lighting ──

        val lightColor = pickLightColor()

        ctx.scene.add(addon.createAmbientLight(color = lightColor, intensity = 0.5))

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

    @Suppress("unused")
    private fun createColor(addon: ThreeJsAddon, hex: Int): dynamic {
        @Suppress("unused", "UNUSED_VARIABLE")
        val ctor = addon.raw.Color
        return js("new ctor(hex)")
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
                // Nothing to rebuild before prepare() has run — it will pick up
                // the current aspect when it does.
                if (prepared) {
                    val newTexW = ceil(texHeight * aspect).toInt()
                    generateMotorNormalMap(addon, newTexW, texHeight) { newNormal ->
                        val newBase = buildPlateBaseMap(addon, newTexW, texHeight)
                        buildTitleOverlayMaps(addon, newTexW, texHeight) { newTitleAlbedo, newTitleNormal ->
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
                        }
                    }
                }
            }, 300)
        }
        window.addEventListener("resize", handler)
        resizeListener = handler
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

        // Cone breathing — scanning breathes deep and fast (the dyno-test
        // "rev"), idle is a slow calm pulse. Amp and rate ease between the two,
        // and the phase runs continuously, so mode switches never jump.
        val targetAmp = if (scanning) 1.3 else 0.35
        val targetRate = if (scanning) 0.0022 else 0.0007   // rad per ms
        breatheAmp += (targetAmp - breatheAmp) * 0.01
        breatheRate += (targetRate - breatheRate) * 0.01
        breathePhase += delta * breatheRate

        // The light stays centred over the vinyl; only its distance moves —
        // base Z eases toward the hover/default target, the breath rides on top.
        mainLight?.position?.let { pos ->
            lightZ += (targetLightZ - lightZ) * 0.04
            pos.z = lightZ + sin(breathePhase) * breatheAmp
        }

        // Smooth intensity transitions
        currentIntensity += (targetIntensity - currentIntensity) * 0.01
        fillLight?.let { it.intensity += (targetFillIntensity - it.intensity) * 0.02 }

        // Subtle lamp flicker — a slow drift plus a faster shimmer, both smooth
        // noise. Stays within a few percent so it reads as a living filament,
        // not a faulty one.
        flickerTime += delta
        val flicker = 1.0 +
                grainNoise.noise(flickerTime * 0.0035, 7.7) * 0.05 +
                grainNoise.noise(flickerTime * 0.021, 3.3) * 0.025
        mainLight?.let { it.intensity = currentIntensity * flicker }

        // The plate fades in with the main light — while the light is off the
        // canvas is transparent and the menu-colored backdrop shows through.
        // The title overlay is exempt from the dimming cap and stays bright.
        // Driven by the unflickered intensity so the fade itself stays steady.
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
    }

    ////  PROCEDURAL NORMAL MAP  //////////////////////////////////////////////////////////////////////////////////

    private fun hashCell(col: Int, row: Int): Double {
        var h = (col * 73856093) xor (row * 19349663)
        h = ((h ushr 16) xor h) * 0x45d9f3b
        h = ((h ushr 16) xor h) * 0x45d9f3b
        h = (h ushr 16) xor h
        return (h and 0xffff).toDouble() / 0xffff.toDouble()
    }

    private fun n2c(n: Double): Int = max(0.0, min(255.0, (n + 1.0) * 127.5)).toInt()

    /** Configures font, alignment and letter-spacing consistently for both text canvases. */
    private fun applyTitleTextStyle(tctx: CanvasRenderingContext2D, height: Int) {
        val fontSize = (height * 0.135).coerceAtLeast(48.0)
        tctx.font = "bold ${fontSize}px monospace"
        tctx.asDynamic().textAlign = "center"
        tctx.asDynamic().textBaseline = "middle"
        tctx.asDynamic().letterSpacing = "-0.03em"
    }

    /**
     * Renders "KLANGMOTÖR" flat (no perspective) at the title position.
     *
     * Caller is responsible for clip / blur / fillStyle setup on `tctx`.
     */
    private fun drawTitleText(
        tctx: CanvasRenderingContext2D,
        width: Int,
        height: Int,
        fillStyle: String,
    ) {
        val textCy = height * 0.18
        tctx.fillStyle = fillStyle
        applyTitleTextStyle(tctx, height)
        tctx.fillText("KLANGMOTÖR", width / 2.0, textCy)
    }

    /**
     * Albedo map for the plate — uniform dark metal in the menu background tone.
     * The title lives on its own overlay plane (see [buildTitleOverlayMaps]) so
     * the plate can be dimmed without dimming the title.
     */
    private fun buildPlateBaseMap(addon: ThreeJsAddon, width: Int, height: Int): CanvasTexture {
        val cnv = document.createElement("canvas") as HTMLCanvasElement
        cnv.width = width
        cnv.height = height
        val tctx = cnv.getContext("2d") as CanvasRenderingContext2D
        // Plate base tone follows the menu / chrome background color
        tctx.fillStyle = laf.menuBackground
        tctx.fillRect(0.0, 0.0, width.toDouble(), height.toDouble())

        val tex = addon.createCanvasTexture(cnv)
        tex.wrapS = TextureWrapping.ClampToEdgeWrapping
        tex.wrapT = TextureWrapping.ClampToEdgeWrapping
        return tex
    }

    /**
     * Albedo + normal map for the title overlay plane.
     *
     * The albedo is "KLANGMOTÖR" on a TRANSPARENT background — the material's
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
        val fontSize = (height * 0.135).coerceAtLeast(48.0)
        val textCy = height * 0.18
        val textBottomY = textCy + fontSize / 2.0
        tctx.save()
        tctx.beginPath()
        tctx.rect(0.0, 0.0, width.toDouble(), textBottomY)
        tctx.clip()
        // Dark halo first — matches the plate tone, so the bevel ring around the
        // letters has pixels to render on (its width matches the engrave mask blur).
        tctx.asDynamic().filter = "blur(3px)"
        drawTitleText(tctx, width, height, laf.menuBackground)
        // Off-white text — with the noise normals and metallic reflection this reads
        // as a faceted glass/crystal inlay; slightly dimmed so highlights don't blow out.
        tctx.asDynamic().filter = "blur(1px)"
        drawTitleText(tctx, width, height, "#b8b8b8")
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
                    // Two-octave stepped surface: fine per-pixel grain + coarser patches.
                    // Combined they give a finely granulated, high-variation metal texture.
                    val fineBlock = 1
                    val coarseBlock = 8
                    val fbc = px / fineBlock
                    val fbr = py / fineBlock
                    val cbc = px / coarseBlock
                    val cbr = py / coarseBlock
                    val f1 = hashCell(fbc + 17, fbr * 3 + 5)
                    val f2 = hashCell(fbr * 5 + 11, fbc + 23)
                    val c1 = hashCell(cbc + 97, cbr * 7 + 41)
                    val c2 = hashCell(cbr * 11 + 53, cbc + 79)
                    nx = (f1 - 0.5) * 0.34 + (c1 - 0.5) * 0.22
                    ny = (f2 - 0.5) * 0.34 + (c2 - 0.5) * 0.22
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
     * Pre-renders "KLANGMOTÖR" into a soft-edged alpha mask the size of the
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
        val fontSize = (height * 0.135).coerceAtLeast(48.0)
        val textCy = height * 0.18
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
     * Generates a vinyl-record normal map.
     *
     * Concentric grooves circle the plate centre. Each groove tilts the normal
     * radially with a sine profile across the groove pitch, a per-angle noise
     * wobble presses the "music" into the groove, and a slow radial depth
     * envelope fades passages louder and quieter — the near-silent stretches
     * read as the dead wax between tracks.
     *
     * The "KLANGMOTÖR" engraving lives on the separate title overlay plane —
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

        // Vinyl grooves — concentric around the plate centre.
        val cx = width / 2.0
        val cy = height / 2.0
        // Pitch well above the screen sampling limit — fine pitches (~6px) moiré
        // when the 2048px texture is minified to viewport size.
        val groovePitch = 24.0  // px between neighbouring grooves
        val grooveAmp = 0.35    // normal tilt at the groove walls

        runChunked(height, { pyFrom, pyTo ->
        for (py in pyFrom until pyTo) {
            for (px in 0 until width) {
                val idx = (py * width + px) * 4

                val dx = px - cx
                val dy = py - cy
                val r = sqrt(dx * dx + dy * dy)
                // Radial unit vector — groove walls tilt along it.
                val dirX = if (r > 1e-6) dx / r else 0.0
                val dirY = if (r > 1e-6) dy / r else 0.0
                val angle = atan2(dy, dx)

                // Groove coordinate: integer part = which groove, fraction = position
                // across the groove profile.
                val band = r / groovePitch

                // The "music" pressed into the groove — a phase wobble that must stay
                // continuous everywhere, or it leaves visible ring seams. Sampling the
                // noise on a circle keeps it seamless where the angle wraps; the small
                // r-term drifts it slowly from groove to groove like a real signal.
                val wobble = grainNoise.noise(
                    cos(angle) * 2.5 + r * 0.02,
                    sin(angle) * 2.5,
                ) * 0.35

                // Loud and quiet passages over the radius; the deep dips read as the
                // dead wax between tracks. Two octaves, both smooth in r.
                val depthMod = (
                        0.55 +
                                0.45 * grainNoise.noise(r * 0.01, 400.2) +
                                0.15 * grainNoise.noise(r * 0.06, 77.7)
                        ).coerceIn(0.08, 1.0)

                val tilt = sin((band + wobble) * 2.0 * PI) * grooveAmp * depthMod

                // Patchy grain — a low-frequency hash modulates the noise amplitude so
                // the surface has calm and busier regions instead of uniform static.
                val patchAmp = hashCell(px / 24 + 101, py / 24 + 53)
                val grainScale = 0.006 + patchAmp * 0.045
                // Smooth Perlin grain instead of per-pixel TV static. Scale
                // ≈ 0.18 → ~5.5px per noise cell; two uncorrelated samples
                // drive the X and Y normal offsets. Final * 0.05 = the prior
                // ±0.5 range scaled down to ~10% of its previous strength.
                val grainX = grainNoise.noise(px * 0.18, py * 0.18) * grainScale * 0.05
                val grainY = grainNoise.noise(px * 0.18 + 113.7, py * 0.18 - 91.3) * grainScale * 0.05

                // Tilt the normal radially across the groove walls.
                val nx = dirX * tilt + grainX
                val ny = dirY * tilt + grainY
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
