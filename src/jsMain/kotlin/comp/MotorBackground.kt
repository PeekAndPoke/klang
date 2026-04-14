package io.peekandpoke.klang.comp

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
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
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
): ComponentRef<MotorBackground> {
    val created = comp { MotorBackground(it) }
    return if (ref != null) created.track(ref) else created
}

/**
 * Industrial metallic grid background with autonomous wandering spotlight.
 *
 * Pure Kotlin port of the former `motor-background.js` built on the kraft-threejs
 * addon. Exposes [powerOn], [startScan], [stopScan] as a lighting state machine.
 */
class MotorBackground(ctx: NoProps) : PureComponent(ctx) {

    ////  ADDON  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val threeAddon: ThreeJsAddon? by subscribingTo(addons.threeJs)

    ////  SCENE STATE  ////////////////////////////////////////////////////////////////////////////////////////////

    private var renderer: WebGLRenderer? = null
    private var camera: Camera? = null
    private var plane: Mesh? = null
    private var material: MeshStandardMaterial? = null
    private var baseMap: CanvasTexture? = null
    private var mainLight: PointLight? = null
    private var fillLight: DirectionalLight? = null

    private var aspect: Double = 1.0
    private val texHeight = 2048

    ////  LIGHTING STATE MACHINE  /////////////////////////////////////////////////////////////////////////////////

    private var poweredOn = false
    private var scanning = false
    private var returning = false
    private var hoverWandering = false
    private var hoverEnvelope = 0.0

    private val defaultIntensity = 1.62
    private val hoverIntensity = 3.0
    private var targetIntensity = 0.0

    // Light "radius" — pulling the light further from the plate widens the
    // illuminated area, so hover reads as the light growing bigger.
    private val defaultLightZ = 2.5
    private val hoverLightZ = 2.8
    private var targetLightZ = defaultLightZ
    private var targetFillIntensity = 0.0

    ////  WANDER PHYSICS  /////////////////////////////////////////////////////////////////////////////////////////

    private var targetX = 0.0
    private var targetY = 0.0
    private var mouseX = 0.0
    private var mouseY = 0.0
    private var velX = 0.0
    private var velY = 0.0
    private var attractX = 0.0
    private var attractY = 0.0

    ////  LISTENERS (for cleanup)  ////////////////////////////////////////////////////////////////////////////////

    private var resizeListener: ((Event) -> Unit)? = null
    private var resizeTimer: Int? = null

    ////  PUBLIC API (call via motorBackgroundRef)  ///////////////////////////////////////////////////////////////

    fun powerOn() {
        poweredOn = true
        targetIntensity = defaultIntensity
        targetFillIntensity = 0.12
    }

    fun startScan() {
        scanning = true
    }

    fun stopScan() {
        returning = true
        attractX = 0.0
        attractY = 0.0
    }

    /** Pulls the light up and starts a slow wander. Call from an element's mouse-enter. */
    fun hoverStart() {
        targetIntensity = hoverIntensity
        targetLightZ = hoverLightZ
        hoverWandering = true
    }

    /** Reverts the hover effect. Call from the element's mouse-leave. */
    fun hoverEnd() {
        targetIntensity = if (poweredOn) defaultIntensity else 0.0
        targetLightZ = defaultLightZ
        hoverWandering = false
    }

    ////  LIFECYCLE  //////////////////////////////////////////////////////////////////////////////////////////////

    private var failed: Boolean by value(false)

    init {
        lifecycle {
            onUnmount { teardown() }
            onError { e ->
                console.error("MotorBackground error — falling back to flat bg", e)
                runCatching { teardown() }
                failed = true
            }
        }
    }

    override fun VDom.render() {
        div {
            key = "motor-bg"
            style = "position:fixed;top:0;left:0;width:100%;height:100%;" +
                    "z-index:-1;pointer-events:none;background-color:#000;"

            if (!failed) {
                ThreeJs(
                    onReady = { ctx -> setupScene(ctx) },
                    onFrame = { f -> animate(f) },
                    createCamera = { a -> buildCamera(a) },
                    alpha = false,
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

    private fun setupScene(ctx: ThreeJsContext) {
        val addon = threeAddon ?: return
        renderer = ctx.renderer
        camera = ctx.camera

        val w = window.innerWidth
        val h = window.innerHeight
        aspect = w.toDouble() / h.toDouble()

        renderer?.setPixelRatio(min(window.devicePixelRatio, 2.0))
        renderer?.setSize(w, h)

        ctx.scene.asDynamic().background = createColor(addon, 0x000000)

        // Normal map matching viewport aspect so panels stay square
        val texW = ceil(texHeight * aspect).toInt()
        val normalMap = generateMotorNormalMap(addon, texW, texHeight)
        val titleMap = buildTitleBaseMap(addon, texW, texHeight)

        val geometry = addon.createPlaneGeometry(2.0 * aspect, 2.0)
        val mat = addon.createMeshStandardMaterial(jsObject {
            color = 0xffffff            // let the map provide the base color
            roughness = 0.28
            metalness = 0.97
            val d = this.asDynamic()
            d.map = titleMap
            d.normalMap = normalMap
            d.normalScale = addon.createVector2(0.9, 0.9)
        })
        baseMap = titleMap
        val pl = addon.createMesh(geometry, mat)
        ctx.scene.add(pl)

        plane = pl
        material = mat

        // ── Lighting ──

        ctx.scene.add(addon.createAmbientLight(color = 0x191C22, intensity = 0.5))

        // Start dark — light turns on when powerOn() is called
        val main = addon.createPointLight(color = 0x56b6c2, intensity = 0.0, distance = 0.0, decay = 1.0)
        main.position.set(0.0, 0.0, 2.5)
        ctx.scene.add(main)
        mainLight = main

        val fill = addon.createDirectionalLight(color = 0x6677aa, intensity = 0.0)
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
            resizeTimer?.let { window.clearTimeout(it) }
            resizeTimer = window.setTimeout({
                val newTexW = ceil(texHeight * aspect).toInt()
                val newNormal = generateMotorNormalMap(addon, newTexW, texHeight)
                val newBase = buildTitleBaseMap(addon, newTexW, texHeight)
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
                baseMap = newBase
            }, 300)
        }
        window.addEventListener("resize", handler)
        resizeListener = handler
    }

    ////  RENDER LOOP  ////////////////////////////////////////////////////////////////////////////////////////////

    private val gravity = 0.25
    private val maxSpeed = 0.35
    private val damping = 0.98
    private val nearThreshold = 0.2

    private fun pickAttractor() {
        attractX = if (attractX <= 0.0) {
            0.4 + Random.nextDouble() * 0.35
        } else {
            -(0.4 + Random.nextDouble() * 0.35)
        }
        attractY = 0.0
    }

    private fun animate(frame: ThreeJsFrame) {
        val delta = min(frame.deltaMs, 100.0)

        if (scanning) {
            if (!returning && attractX == 0.0 && attractY == 0.0) pickAttractor()
            val dt = delta / 1000.0
            val dx = attractX - targetX
            val dy = attractY - targetY
            val dist = max(0.001, sqrt(dx * dx + dy * dy))
            velX += dx * gravity * dt
            velY += dy * gravity * dt
            velX *= damping
            velY *= damping
            val speed = sqrt(velX * velX + velY * velY)
            if (speed > maxSpeed) {
                velX = (velX / speed) * maxSpeed
                velY = (velY / speed) * maxSpeed
            }
            targetX += velX * dt
            targetY += velY * dt
            if (returning) {
                if (dist < 0.02 && speed < 0.01) {
                    scanning = false
                    returning = false
                    targetX = 0.0
                    targetY = 0.0
                }
            } else {
                if (dist < nearThreshold) pickAttractor()
            }
        } else if (hoverWandering) {
            // Slow lissajous-like wander while any button is hovered.
            // Envelope ramps up from 0 so the first move eases in instead of snapping.
            hoverEnvelope += (1.0 - hoverEnvelope) * 0.015
            val t = frame.elapsedMs * 0.001
            targetX = sin(t * 0.4) * 0.35 * hoverEnvelope
            targetY = cos(t * 0.33) * 0.15 * hoverEnvelope
        } else {
            // Not scanning, not hovering — envelope and target decay to center.
            hoverEnvelope *= 0.97
            targetX *= 0.97
            targetY *= 0.97
        }

        // Smooth position follow
        mouseX += (targetX - mouseX) * 0.18
        mouseY += (targetY - mouseY) * 0.18
        mainLight?.position?.x = mouseX * aspect
        mainLight?.position?.y = mouseY
        // Lerp the light's Z toward the current target — pulling it farther on
        // hover widens the illuminated area, so hover reads as a bigger light.
        mainLight?.position?.let { pos ->
            pos.z += (targetLightZ - pos.z) * 0.04
        }

        // Smooth intensity transitions
        mainLight?.let { it.intensity += (targetIntensity - it.intensity) * 0.01 }
        fillLight?.let { it.intensity += (targetFillIntensity - it.intensity) * 0.02 }
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
     * Albedo map for the plane — uniform dark metal grey with a dim teal title stamped in.
     * The title inherits the material's metalness so it reads as the same metal as the plate,
     * tinted just enough to be legible under the scene's cyan-leaning lighting.
     */
    private fun buildTitleBaseMap(addon: ThreeJsAddon, width: Int, height: Int): CanvasTexture {
        val cnv = document.createElement("canvas") as HTMLCanvasElement
        cnv.width = width
        cnv.height = height
        val tctx = cnv.getContext("2d") as CanvasRenderingContext2D
        tctx.fillStyle = "#13151a"
        tctx.fillRect(0.0, 0.0, width.toDouble(), height.toDouble())
        // Clip so the blur halo doesn't bleed below the letter baseline — the
        // bottom edge of the engraving stays sharp.
        val fontSize = (height * 0.135).coerceAtLeast(48.0)
        val textCy = height * 0.18
        val textBottomY = textCy + fontSize / 2.0
        tctx.save()
        tctx.beginPath()
        tctx.rect(0.0, 0.0, width.toDouble(), textBottomY)
        tctx.clip()
        tctx.asDynamic().filter = "blur(1px)"
        // Off-white text — with the noise normals and metallic reflection this reads
        // as a faceted glass/crystal inlay; slightly dimmed so highlights don't blow out.
        tctx.fillStyle = "#b8b8b8"
        applyTitleTextStyle(tctx, height)
        tctx.fillText("KLANG AUDIO MOTÖR", width / 2.0, textCy)
        tctx.restore()

        val tex = addon.createCanvasTexture(cnv)
        tex.wrapS = TextureWrapping.ClampToEdgeWrapping
        tex.wrapT = TextureWrapping.ClampToEdgeWrapping
        return tex
    }

    /**
     * Pre-renders "KLANG AUDIO MOTÖR" into a soft-edged alpha mask the size of the
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
        tctx.fillStyle = "white"
        applyTitleTextStyle(tctx, height)
        tctx.fillText("KLANG AUDIO MOTÖR", width / 2.0, textCy)
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
     * Generates a flat brushed-metal normal map.
     *
     * Horizontal brush lines come from a per-row tilt (shared across a whole scanline,
     * so lighting reveals long horizontal scratches), combined with per-pixel grain
     * and a slow lateral wobble so the brush isn't perfectly straight.
     *
     * The "KLANG AUDIO MOTÖR" text engraving still lives in this same normal map —
     * its alpha mask's gradient perturbs normals at the letter edges, and inside the
     * letter body the brushed pattern is replaced by a stepped hammered-grain noise.
     */
    private fun generateMotorNormalMap(addon: ThreeJsAddon, width: Int, height: Int): CanvasTexture {
        val cnv = document.createElement("canvas") as HTMLCanvasElement
        cnv.width = width
        cnv.height = height
        val g2d = cnv.getContext("2d") as CanvasRenderingContext2D
        val imageData = g2d.createImageData(width.toDouble(), height.toDouble())
        val data = imageData.data

        val d = data.asDynamic()

        // Pre-render text as a soft-edged alpha mask — gradient gives engraving normals.
        val engraveMask = buildEngraveMask(width, height)
        val engraveStrength = 1.6

        // Curtain-fold brushed metal — scratches radiate from a focal point far
        // above the canvas. At x = centre they're perfectly vertical, toward the
        // sides they fan out smoothly, giving a hanging-curtain look.
        val cx = width / 2.0
        val focalDist = height.toDouble() * 0.9   // focal point ~1× height above top → strong fan at bottom
        val scratchDensity = 400.0                 // angular density of scratches

        for (py in 0 until height) {
            val dy = py + focalDist
            for (px in 0 until width) {
                val idx = (py * width + px) * 4

                val dx = px - cx
                // Angle from focal point → identical for all pixels on the same scratch.
                val angle = atan2(dx, dy)
                val scratchIdx = (angle * scratchDensity).toInt()
                val h1 = hashCell(31, scratchIdx)
                val h2 = hashCell(67, scratchIdx * 2 + 7)
                val brushTilt = (h1 - 0.5) * 0.13 + (h2 - 0.5) * 0.06

                // Scratch direction = radial from focal point (unit vector).
                val len = sqrt(dx * dx + dy * dy)
                val dirX = dx / len
                val dirY = dy / len
                // Perpendicular to scratch = rotate 90° → (-dirY, dirX).
                val perpX = -dirY
                val perpY = dirX

                // Patchy grain — a low-frequency hash modulates the noise amplitude so
                // the surface has calm and busier regions instead of uniform static.
                val patchAmp = hashCell(px / 24 + 101, py / 24 + 53)
                val grainScale = 0.006 + patchAmp * 0.045
                val grainX = (Random.nextDouble() - 0.5) * grainScale
                val grainY = (Random.nextDouble() - 0.5) * grainScale
                val wobble = sin(px * 0.004 + py * 0.3) * 0.02

                // Tilt the normal perpendicular to the local scratch direction.
                var nx = perpX * brushTilt + grainX + wobble * 0.3
                var ny = perpY * brushTilt + grainY
                var nz = sqrt(max(0.01, 1.0 - nx * nx - ny * ny))

                // Engrave "KLANG AUDIO MOTÖR" into the plate using the text alpha gradient.
                // Inside the text body, keep a small fraction of the mosaic micro-variation
                // so light still plays across the letters; at the edge apron (blurred
                // transition band) push normals INTO the letter so the edges read as a
                // recessed wall catching light.
                val i1D = py * width + px
                val maskVal = engraveMask[i1D]
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
                        nx += -gx * engraveStrength
                        ny += -gy * engraveStrength
                        nz = sqrt(max(0.01, 1.0 - nx * nx - ny * ny))
                    }
                }

                d[idx] = n2c(nx)
                d[idx + 1] = n2c(-ny) // negate Y: canvas Y-down → WebGL Y-up
                d[idx + 2] = n2c(nz)
                d[idx + 3] = 255
            }
        }

        g2d.putImageData(imageData, 0.0, 0.0)

        val tex = addon.createCanvasTexture(cnv)
        tex.wrapS = TextureWrapping.ClampToEdgeWrapping
        tex.wrapT = TextureWrapping.ClampToEdgeWrapping
        return tex
    }
}
