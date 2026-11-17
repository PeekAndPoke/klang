package io.peekandpoke.klang.workshop.threejs

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.kraft.addons.threejs.js.BufferGeometry
import io.peekandpoke.kraft.addons.threejs.js.Camera
import io.peekandpoke.kraft.addons.threejs.js.Material
import io.peekandpoke.kraft.addons.threejs.js.Texture
import io.peekandpoke.kraft.utils.jsObject
import org.khronos.webgl.Uint8Array

// What the geometry, the instanced mesh, the render target and the casts declare, against the real three.js: that
// each argument reaches the place three.js has for it (two of a kind are easily swapped), and that each cast says no
// to what it is not. No WebGL: these rows touch only what three.js builds without a renderer. A renderer's own
// members (its pixel ratio, its counts, its extensions, its render target) cannot be met here: the test browser
// has no graphics card; the stage's pictures are their check. Everything else declared for three.js has a case
// here or in ThreeMoreSpec.

/** What three.js keeps of the arguments a geometry was made with. */
private fun BufferGeometry.made(): dynamic = asDynamic().parameters

/** The box a geometry fills, worked out anew: its least and its greatest corner as six numbers. */
private fun BufferGeometry.box(): List<Double> {
    asDynamic().computeBoundingBox()

    val box: dynamic = asDynamic().boundingBox

    return listOf(box.min.x, box.min.y, box.min.z, box.max.x, box.max.y, box.max.z).map { typed<Double>(it) }
}

class ThreeGeometrySpec : StringSpec({

    "a cylinder made with all eight choices has each of them where three.js keeps it" {
        withThree { three ->
            val made = three.createCylinderGeometry(
                radiusTop = 1.0,
                radiusBottom = 2.0,
                height = 3.0,
                radialSegments = 5,
                heightSegments = 4,
                openEnded = true,
                thetaStart = 0.25,
                thetaLength = 1.5,
            ).made()

            typed<Double>(made.radiusTop) shouldBe 1.0
            typed<Double>(made.radiusBottom) shouldBe 2.0
            typed<Double>(made.height) shouldBe 3.0
            typed<Int>(made.radialSegments) shouldBe 5
            typed<Int>(made.heightSegments) shouldBe 4
            typed<Boolean>(made.openEnded) shouldBe true
            typed<Double>(made.thetaStart) shouldBe 0.25
            typed<Double>(made.thetaLength) shouldBe 1.5
        }
    }

    "a cone and a lathe keep what they were made with" {
        withThree { three ->
            val cone = three.createConeGeometry(radius = 1.5, height = 4.0, radialSegments = 7, heightSegments = 3, openEnded = true).made()

            typed<Double>(cone.radius) shouldBe 1.5
            typed<Double>(cone.height) shouldBe 4.0
            typed<Int>(cone.radialSegments) shouldBe 7
            typed<Int>(cone.heightSegments) shouldBe 3
            typed<Boolean>(cone.openEnded) shouldBe true

            val outline = arrayOf(three.createVector2(0.5, 0.0), three.createVector2(1.0, 1.0), three.createVector2(0.25, 2.0))
            val lathe = three.createLatheGeometry(points = outline, segments = 9)

            typed<Int>(lathe.made().segments) shouldBe 9
            typed<Int>(lathe.made().points.length) shouldBe 3
            // turned round the upright: as far out as its widest point (the turn begins toward +z), as high as its
            // outline
            lathe.box()[5] shouldBe (1.0 plusOrMinus 1e-6)
            lathe.box()[1] shouldBe (0.0 plusOrMinus 1e-9)
            lathe.box()[4] shouldBe (2.0 plusOrMinus 1e-9)
        }
    }

    "a shape drawn through points and cut from a slab is as deep as asked, and as wide as its outline" {
        withThree { three ->
            val shape = three.createShape()

            shape.moveTo(0.0, 0.0)
            shape.splineThru(arrayOf(three.createVector2(2.0, 0.0), three.createVector2(2.0, 1.0), three.createVector2(0.0, 1.0)))
            shape.closePath()

            val slab = three.createExtrudeGeometry(
                shape = shape,
                // (a bevel is named and switched off: the slab is as deep as asked only if the switch arrives)
                options = jsObject {
                    depth = 0.5
                    bevelEnabled = false
                    bevelSize = 0.05
                    bevelThickness = 0.1
                    bevelSegments = 2
                    curveSegments = 6
                },
            )

            isInstance(slab, three.raw.ExtrudeGeometry) shouldBe true

            val box = slab.box()

            // from the slab's face to its back
            box[2] shouldBe (0.0 plusOrMinus 1e-9)
            box[5] shouldBe (0.5 plusOrMinus 1e-9)
            // the outline's points are on it
            (box[3] >= 2.0 - 1e-9) shouldBe true
            (box[4] >= 1.0 - 1e-9) shouldBe true

            // with a bevel it is deeper by the bevel's thickness on both faces
            val rounded = three.createExtrudeGeometry(
                shape = shape,
                options = jsObject {
                    depth = 0.5
                    bevelEnabled = true
                    bevelSize = 0.05
                    bevelThickness = 0.1
                    bevelSegments = 2
                    curveSegments = 6
                },
            ).box()

            rounded[2] shouldBe (-0.1 plusOrMinus 1e-6)
            rounded[5] shouldBe (0.6 plusOrMinus 1e-6)
            // and wider by the bevel's size
            (rounded[0] < box[0] - 0.04) shouldBe true

            // more steps along the curve, and more along the bevel, make more of it
            fun points(curve: Int, bevel: Int): Int = typed(
                three.createExtrudeGeometry(
                    shape = shape,
                    options = jsObject {
                        depth = 0.5
                        bevelEnabled = true
                        bevelSize = 0.05
                        bevelThickness = 0.1
                        bevelSegments = bevel
                        curveSegments = curve
                    },
                ).asDynamic().attributes.position.count
            )

            (points(curve = 12, bevel = 2) > points(curve = 6, bevel = 2)) shouldBe true
            (points(curve = 6, bevel = 4) > points(curve = 6, bevel = 2)) shouldBe true
        }
    }

    "a mesh drawn many times over keeps its geometry, its material and its count, and each one's place" {
        withThree { three ->
            val geometry = three.createBoxGeometry()
            val material = three.createMeshBasicMaterial(jsObject { color = 0xffffff })
            val many = three.createInstancedMesh(geometry = geometry, material = material, count = 3)

            many.count shouldBe 3
            many.instanceMatrix.count shouldBe 3
            many.instanceMatrix.itemSize shouldBe 16
            typed<Any?>(many.asDynamic().geometry) shouldBeSameInstanceAs geometry
            typed<Any?>(many.asDynamic().material) shouldBeSameInstanceAs material
            many.isInstancedMesh shouldBe true
            three.createMesh(geometry, material).isInstancedMesh shouldBe false

            // the second one is moved to (4, 5, 6) and made (2, 3, 7) large: its sixteen numbers say so, the others'
            // stay as they were
            val place = three.createMatrix4().compose(
                position = three.createVector3(4.0, 5.0, 6.0),
                quaternion = three.createQuaternion(),
                scale = three.createVector3(2.0, 3.0, 7.0),
            )

            many.setMatrixAt(1, place)

            val numbers: dynamic = many.instanceMatrix.asDynamic().array

            listOf(16, 21, 26, 28, 29, 30).map { typed<Double>(numbers[it]) } shouldBe listOf(2.0, 3.0, 7.0, 4.0, 5.0, 6.0)
            listOf(0, 5, 10, 12, 13, 14).map { typed<Double>(numbers[it]) } shouldBe listOf(1.0, 1.0, 1.0, 0.0, 0.0, 0.0)
        }
    }

    "a matrix put together from a place, a turn and a size holds each where it belongs" {
        withThree { three ->
            // a quarter turn round the upright: what pointed along x points along -z
            val turn = three.createQuaternion().setFromAxisAngle(three.createVector3(0.0, 1.0, 0.0), kotlin.math.PI / 2)
            val matrix = three.createMatrix4().compose(
                position = three.createVector3(1.0, 2.0, 3.0),
                quaternion = turn,
                scale = three.createVector3(2.0, 2.0, 2.0),
            )
            val moved = three.createVector3(1.0, 0.0, 0.0).applyMatrix4(matrix)

            moved.x shouldBe (1.0 plusOrMinus 1e-9)
            moved.y shouldBe (2.0 plusOrMinus 1e-9)
            moved.z shouldBe (1.0 plusOrMinus 1e-9)
        }
    }

    "a point between two others: so much of the way from the first to the second, and they stay" {
        withThree { three ->
            val from = three.createVector3(1.0, 1.0, 1.0)
            val to = three.createVector3(11.0, 21.0, 31.0)
            val between = three.createVector3().lerpVectors(from = from, to = to, share = 0.25)

            listOf(between.x, between.y, between.z) shouldBe listOf(3.5, 6.0, 8.5)
            listOf(from.x, to.x) shouldBe listOf(1.0, 11.0)
        }
    }

    "a geometry moved is where it was moved to" {
        withThree { three ->
            val box = three.createBoxGeometry(2.0, 2.0, 2.0).translate(x = 1.0, y = 2.0, z = 3.0).box()

            box shouldBe listOf(0.0, 1.0, 2.0, 2.0, 3.0, 4.0)
        }
    }

    "a sphere is about the very point it was given, as far as it was given" {
        withThree { three ->
            val middle = three.createVector3(1.0, 2.0, 3.0)
            val sphere = three.createSphere(center = middle, radius = 4.5)

            sphere.center shouldBeSameInstanceAs middle
            sphere.radius shouldBe 4.5
        }
    }

    "a block of numbers as a picture in three directions is as wide, as high and as deep as said" {
        withThree { three ->
            val block = three.createData3DTexture(data = Uint8Array(2 * 3 * 4), width = 2, height = 3, depth = 4)
            val image: dynamic = block.asDynamic().image

            typed<Int>(image.width) shouldBe 2
            typed<Int>(image.height) shouldBe 3
            typed<Int>(image.depth) shouldBe 4
            // a block's rows are read byte by byte unless told otherwise
            block.unpackAlignment shouldBe 1

            block.format = TextureFormat.RedFormat
            block.wrapR = 1000
            typed<Int>(block.asDynamic().format) shouldBe typed<Int>(three.raw.RedFormat)
            typed<Int>(block.asDynamic().wrapR) shouldBe typed<Int>(three.raw.RepeatWrapping)
        }
    }

    "a render target is as wide and as high as asked and holds the kind of numbers asked for" {
        withThree { three ->
            val target = three.createWebGLRenderTarget(
                width = 64,
                height = 32,
                options = jsObject {
                    type = TextureDataType.HalfFloatType
                    depthBuffer = false
                    stencilBuffer = true
                },
            )

            target.width shouldBe 64
            target.height shouldBe 32
            typed<Int>(target.texture.asDynamic().type) shouldBe typed<Int>(three.raw.HalfFloatType)
            // (three.js gives a target a depth buffer and no stencil buffer unless told otherwise)
            typed<Boolean>(target.asDynamic().depthBuffer) shouldBe false
            typed<Boolean>(target.asDynamic().stencilBuffer) shouldBe true

            var freed = 0
            target.asDynamic().addEventListener("dispose") { freed++ }
            target.dispose()
            freed shouldBe 1
        }
    }

    "the kinds of numbers and of pictures are three.js's own" {
        withThree { three ->
            TextureDataType.UnsignedByteType shouldBe typed<Int>(three.raw.UnsignedByteType)
            TextureDataType.HalfFloatType shouldBe typed<Int>(three.raw.HalfFloatType)
            TextureFormat.RedFormat shouldBe typed<Int>(three.raw.RedFormat)
        }
    }

    "what a material, a node, a picture and a shadow let be set is three.js's own member of that name" {
        withThree { three ->
            // each starts as three.js has it, is set through the typed member and read back by three.js's own name
            val material = three.createMeshBasicMaterial(jsObject { color = 0xffffff })

            listOf(material.colorWrite, material.forceSinglePass, material.toneMapped) shouldBe listOf(true, false, true)
            material.colorWrite = false
            material.forceSinglePass = true
            material.toneMapped = false
            typed<Boolean>(material.asDynamic().colorWrite) shouldBe false
            typed<Boolean>(material.asDynamic().forceSinglePass) shouldBe true
            typed<Boolean>(material.asDynamic().toneMapped) shouldBe false

            val node = three.createGroup()

            node.drawOrder shouldBe 0.0
            node.drawOrder = 12.5
            typed<Double>(node.asDynamic().renderOrder) shouldBe 12.5

            val picture = three.create<Texture>("Texture")

            picture.colorSpace shouldBe ""
            picture.colorSpace = "srgb"
            typed<String>(picture.asDynamic().colorSpace) shouldBe "srgb"

            val shadow = three.createSpotLight().shadow

            shadow.autoUpdate shouldBe true
            shadow.autoUpdate = false
            typed<Boolean>(shadow.asDynamic().autoUpdate) shouldBe false

            val skin = three.create<SkinnedMesh>("SkinnedMesh", three.createBoxGeometry(), material)
            val sphere = three.createSphere(center = three.createVector3(0.0, 1.0, 0.0), radius = 2.0)

            skin.boundingSphere shouldBe null
            skin.boundingSphere = sphere
            typed<Any?>(skin.asDynamic().boundingSphere) shouldBeSameInstanceAs sphere
        }
    }

    "a lit material made with a glow and a picture has them, and its copy is another one with the same picture" {
        withThree { three ->
            val picture = three.create<Texture>("Texture")
            val material = three.createMeshStandardMaterial(jsObject {
                color = 0x000000
                emissive = three.createColor(0x102030)
                emissiveIntensity = 2.5
                map = picture
            })

            typed<Int>(material.asDynamic().emissive.getHex()) shouldBe 0x102030
            typed<Double>(material.asDynamic().emissiveIntensity) shouldBe 2.5
            typed<Any?>(material.asDynamic().map) shouldBeSameInstanceAs picture
            material.map shouldBeSameInstanceAs picture

            val copy = material.clone()

            (copy === material) shouldBe false
            copy.map shouldBeSameInstanceAs picture
            typed<Double>(copy.asDynamic().emissiveIntensity) shouldBe 2.5

            val other = three.create<Texture>("Texture")

            copy.map = other
            typed<Any?>(copy.asDynamic().map) shouldBeSameInstanceAs other
            material.map shouldBeSameInstanceAs picture
        }
    }

    "a material, a node and a camera are taken for what they are, and for nothing else" {
        withThree { three ->
            val standard = three.createMeshStandardMaterial(jsObject { color = 0xffffff })
            val physical = three.create<Material>("MeshPhysicalMaterial")
            val basic = three.createMeshBasicMaterial(jsObject { color = 0xffffff })

            standard.asMeshStandardMaterial shouldBeSameInstanceAs standard
            physical.asMeshStandardMaterial.shouldNotBeNull()
            basic.asMeshStandardMaterial shouldBe null

            val lamp = three.createSpotLight()

            lamp.asLight shouldBeSameInstanceAs lamp
            three.createGroup().asLight shouldBe null

            val lens = three.createPerspectiveCamera(fov = 40.0, aspect = 1.5, near = 0.1, far = 100.0)
            val flat = three.create<Camera>("OrthographicCamera", -1.0, 1.0, 1.0, -1.0, 0.1, 100.0)

            lens.asPerspectiveCamera shouldBeSameInstanceAs lens
            flat.asPerspectiveCamera shouldBe null
        }
    }
})
