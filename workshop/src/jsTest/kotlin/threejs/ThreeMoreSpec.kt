package io.peekandpoke.klang.workshop.threejs

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.peekandpoke.kraft.addons.registry.AddonRegistry.Companion.addons
import io.peekandpoke.kraft.addons.registry.addons
import io.peekandpoke.kraft.addons.threejs.ThreeJsAddon
import io.peekandpoke.kraft.addons.threejs.js.Material
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.Object3D
import io.peekandpoke.kraft.addons.threejs.threeJs
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.utils.jsObject
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.math.PI
import kotlin.time.Duration.Companion.seconds

private var loaded: ThreeJsAddon? = null

@Suppress("TestFunctionName")
private fun Tag.ThreeCatcher() = comp { ThreeCatcher(it) }

private class ThreeCatcher(ctx: NoProps) : PureComponent(ctx) {
    private val three by subscribingTo(addons.threeJs)

    override fun VDom.render() {
        loaded = three
        div { }
    }
}

/** Runs [block] with the loaded addon. No WebGL: these rows touch only what three.js builds without a renderer. */
private suspend fun withThree(block: suspend (ThreeJsAddon) -> Unit) {
    TestBed.preact(appSetup = { addons { threeJs() } }, view = { ThreeCatcher() }) {
        block(eventually(timeout = 5.seconds) { loaded.shouldNotBeNull() })
    }
}

private fun isInstance(obj: Any?, ctor: dynamic): Boolean = js("obj instanceof ctor").unsafeCast<Boolean>()

/**
 * An untyped JavaScript value as a typed one. An assertion written straight on an untyped value would be taken for a
 * call of a method of that value.
 */
private fun <T> typed(value: dynamic): T = value.unsafeCast<T>()

private const val EPS = 1e-9

/**
 * The types this folder adds to Kraft's three.js addon, against the real library: a wrong name in a declaration
 * or in an `asDynamic()` extension compiles and fails only when it runs.
 */
class ThreeMoreSpec : StringSpec({

    "each maker gives an instance of the three.js class" {
        withThree { three ->
            val raw = three.raw

            isInstance(three.createFog(0x020308, 9.0, 22.0), raw.Fog) shouldBe true
            isInstance(three.createColor(0xff8420), raw.Color) shouldBe true
            isInstance(three.createQuaternion(), raw.Quaternion) shouldBe true
            isInstance(three.createMatrix4(), raw.Matrix4) shouldBe true
            isInstance(three.createBufferGeometry(), raw.BufferGeometry) shouldBe true
            isInstance(three.createCircleGeometry(1.0), raw.CircleGeometry) shouldBe true
            isInstance(three.createFloat32Attribute(arrayOf(1.0, 2.0, 3.0), 3), raw.Float32BufferAttribute) shouldBe true

            val material = three.createShaderMaterial(jsObject {
                vertexShader = "void main() { gl_Position = vec4(0.0); }"
                fragmentShader = "void main() { gl_FragColor = vec4(1.0); }"
            })

            isInstance(material, raw.ShaderMaterial) shouldBe true
            isInstance(three.createPoints(three.createBufferGeometry(), material), raw.Points) shouldBe true
        }
    }

    "the constants are three.js's own values" {
        withThree { three ->
            val raw = three.raw

            Blending.NoBlending shouldBe raw.NoBlending
            Blending.NormalBlending shouldBe raw.NormalBlending
            Blending.AdditiveBlending shouldBe raw.AdditiveBlending
            Blending.CustomBlending shouldBe raw.CustomBlending
            BlendFactor.ZeroFactor shouldBe raw.ZeroFactor
            BlendFactor.OneFactor shouldBe raw.OneFactor
            BlendFactor.SrcAlphaFactor shouldBe raw.SrcAlphaFactor
            BlendFactor.OneMinusSrcAlphaFactor shouldBe raw.OneMinusSrcAlphaFactor
            ToneMapping.NoToneMapping shouldBe raw.NoToneMapping
            ToneMapping.LinearToneMapping shouldBe raw.LinearToneMapping
            ToneMapping.ACESFilmicToneMapping shouldBe raw.ACESFilmicToneMapping
            ShadowMapType.BasicShadowMap shouldBe raw.BasicShadowMap
            ShadowMapType.PCFShadowMap shouldBe raw.PCFShadowMap
            ShadowMapType.VSMShadowMap shouldBe raw.VSMShadowMap
        }
    }

    "a fog keeps its colour and its two distances, and a scene takes it" {
        withThree { three ->
            val fog = three.createFog(0xff0000, 9.0, 22.0)
            val scene = three.createScene()

            fog.near shouldBe 9.0
            fog.far shouldBe 22.0
            fog.color.r shouldBe 1.0
            scene.fog shouldBe null

            scene.fog = fog
            scene.fog shouldBeSameInstanceAs fog
        }
    }

    "a shader material keeps the uniforms it was given, each an object with a value" {
        withThree { three ->
            val time = uniform(2.5)
            val uniforms: dynamic = js("({})")
            uniforms.uTime = time

            val material = three.createShaderMaterial(jsObject {
                this.uniforms = uniforms
                vertexShader = "void main() { gl_Position = vec4(0.0); }"
                fragmentShader = "void main() { gl_FragColor = vec4(1.0); }"
                transparent = true
                depthWrite = false
                blending = Blending.AdditiveBlending
            })

            typed<Any?>(material.uniforms.uTime.value) shouldBe 2.5
            time.value = 4.0
            typed<Any?>(material.uniforms.uTime.value) shouldBe 4.0
            material.transparent shouldBe true
            material.depthWrite shouldBe false
            typed<Any?>(material.asDynamic().blending) shouldBe three.raw.AdditiveBlending
        }
    }

    "a geometry takes an attribute, of as many values as it has numbers by item size" {
        withThree { three ->
            val geometry = three.createBufferGeometry()
            val places = three.createFloat32Attribute(arrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0), 3)

            places.count shouldBe 2
            places.itemSize shouldBe 3
            geometry.setAttribute("position", places) shouldBeSameInstanceAs geometry
            typed<Any?>(geometry.asDynamic().getAttribute("position")) shouldBeSameInstanceAs places
        }
    }

    "a material's members: depth writing, fog, its name, and the hook into its shader" {
        withThree { three ->
            val material = three.createMeshStandardMaterial(jsObject { color = 0xffffff })

            material.depthWrite shouldBe true
            material.depthWrite = false
            typed<Any?>(material.asDynamic().depthWrite) shouldBe false

            material.fog shouldBe true
            material.fog = false
            typed<Any?>(material.asDynamic().fog) shouldBe false

            material.name shouldBe ""
            material.asDynamic().name = "drums_eyes"
            material.name shouldBe "drums_eyes"

            var seen: CompiledShader? = null
            val version = material.asDynamic().version.unsafeCast<Int>()

            material.onBeforeCompile(cacheKey = "mine") { seen = it }

            typed<Any?>(material.asDynamic().customProgramCacheKey()) shouldBe "mine"
            // needsUpdate counts the material's version up: three.js builds its program anew
            typed<Any?>(material.asDynamic().version) shouldBe version + 1

            val shader: dynamic = js("({ uniforms: {}, vertexShader: 'v', fragmentShader: 'f' })")
            material.asDynamic().onBeforeCompile(shader)
            seen.shouldNotBeNull().vertexShader shouldBe "v"
        }
    }

    "a standard material's own light: its colour and how strong" {
        withThree { three ->
            val material: MeshStandardMaterial = three.createMeshStandardMaterial(jsObject { color = 0x000000 })

            material.emissive.r shouldBe 0.0
            material.emissive.setHex(0xff0000)
            typed<Any?>(material.asDynamic().emissive.r) shouldBe 1.0

            material.emissiveIntensity shouldBe 1.0
            material.emissiveIntensity = 3.4
            typed<Any?>(material.asDynamic().emissiveIntensity) shouldBe 3.4
        }
    }

    "a colour goes a share of the way to another, and is scaled" {
        withThree { three ->
            val a = three.createColor(0x000000)
            val b = three.createColor(0xffffff)

            a.lerp(b, 0.25) shouldBeSameInstanceAs a
            a.r shouldBe (0.25 plusOrMinus EPS)
            a.scale(2.0) shouldBeSameInstanceAs a
            a.g shouldBe (0.5 plusOrMinus EPS)
            b.r shouldBe 1.0
        }
    }

    "a spot light's shadow is tuned: the map's size, the bias, what its camera sees" {
        withThree { three ->
            val light = three.createSpotLight()

            light.tuneShadow(mapSize = 2048, bias = -0.0005, near = 1.0, far = 30.0)

            val shadow: dynamic = light.asDynamic().shadow
            typed<Any?>(shadow.mapSize.x) shouldBe 2048
            typed<Any?>(shadow.mapSize.y) shouldBe 2048
            typed<Any?>(shadow.bias) shouldBe -0.0005
            typed<Any?>(shadow.camera.near) shouldBe 1.0
            typed<Any?>(shadow.camera.far) shouldBe 30.0

            // a light without a shadow is left alone
            three.createAmbientLight().tuneShadow(mapSize = 512)
        }
    }

    "a vector's methods change it and give it back; the operators give a new one" {
        withThree { three ->
            fun v(x: Double, y: Double, z: Double) = three.createVector3(x, y, z)

            val a = v(1.0, 0.0, 0.0)

            a.addScaledVector(v(0.0, 1.0, 0.0), 2.0) shouldBeSameInstanceAs a
            a.y shouldBe 2.0

            v(0.0, 0.0, 0.0).lerp(v(4.0, 0.0, 0.0), 0.25).x shouldBe 1.0
            v(1.0, 0.0, 0.0).cross(v(0.0, 1.0, 0.0)).z shouldBe 1.0
            v(9.0, 9.0, 9.0).crossVectors(v(0.0, 1.0, 0.0), v(0.0, 0.0, 1.0)).x shouldBe 1.0
            v(9.0, 9.0, 9.0).subVectors(v(5.0, 0.0, 0.0), v(2.0, 0.0, 0.0)).x shouldBe 3.0
            v(1.0, 2.0, 3.0).setY(7.0).y shouldBe 7.0
            v(1.0, -2.0, 3.0).negate().y shouldBe 2.0
            v(3.0, 4.0, 0.0).lengthSq() shouldBe 25.0
            v(3.0, 4.0, 0.0).distanceTo(v(0.0, 0.0, 0.0)) shouldBe 5.0
            v(1.0, 2.0, 3.0).dot(v(4.0, 5.0, 6.0)) shouldBe 32.0
            v(1.0, 0.0, 0.0).angleTo(v(0.0, 1.0, 0.0)) shouldBe (PI / 2 plusOrMinus EPS)

            // a quarter turn about y takes x to -z
            val turned = v(1.0, 0.0, 0.0).applyAxisAngle(v(0.0, 1.0, 0.0), PI / 2)
            turned.z shouldBe (-1.0 plusOrMinus EPS)

            val sum = a + v(1.0, 1.0, 1.0)
            sum shouldNotBeSameInstanceAs a
            sum.x shouldBe 2.0
            a.x shouldBe 1.0
            (a - v(1.0, 0.0, 0.0)).x shouldBe 0.0
            (a * 3.0).y shouldBe 6.0
            (-a).x shouldBe -1.0
            a.y shouldBe 2.0
        }
    }

    "a quaternion: from an axis and an angle, from two directions, part of the way, undone" {
        withThree { three ->
            fun v(x: Double, y: Double, z: Double) = three.createVector3(x, y, z)

            val quarter = three.createQuaternion().setFromAxisAngle(v(0.0, 1.0, 0.0), PI / 2)

            v(1.0, 0.0, 0.0).applyQuaternion(quarter).z shouldBe (-1.0 plusOrMinus EPS)

            val fromTo = three.createQuaternion().setFromUnitVectors(v(1.0, 0.0, 0.0), v(0.0, 1.0, 0.0))
            v(1.0, 0.0, 0.0).applyQuaternion(fromTo).y shouldBe (1.0 plusOrMinus EPS)

            val none = three.createQuaternion()
            none.w shouldBe 1.0
            none.angleTo(quarter) shouldBe (PI / 2 plusOrMinus EPS)

            val half = none.clone().slerp(quarter, 0.5)
            half.angleTo(quarter) shouldBe (PI / 4 plusOrMinus EPS)
            none.w shouldBe 1.0

            val back = quarter.clone().invert()
            v(1.0, 0.0, 0.0).applyQuaternion(quarter).applyQuaternion(back).x shouldBe (1.0 plusOrMinus EPS)

            // multiply: this rotation after the other one's argument, as three.js has it
            val twice = quarter.clone().multiply(quarter)
            v(1.0, 0.0, 0.0).applyQuaternion(twice).x shouldBe (-1.0 plusOrMinus EPS)
            quarter.clone().premultiply(back).angleTo(none) shouldBe (0.0 plusOrMinus 1e-6)

            three.createQuaternion().copy(quarter).y shouldBe quarter.y
            three.createQuaternion().set(0.0, 0.0, 0.0, 1.0).identity().w shouldBe 1.0
        }
    }

    "a matrix from three axes is the rotation onto them" {
        withThree { three ->
            fun v(x: Double, y: Double, z: Double) = three.createVector3(x, y, z)

            // x onto y, y onto z, z onto x
            val basis = three.createMatrix4().makeBasis(v(0.0, 1.0, 0.0), v(0.0, 0.0, 1.0), v(1.0, 0.0, 0.0))
            val q = three.createQuaternion().setFromRotationMatrix(basis)

            v(1.0, 0.0, 0.0).applyQuaternion(q).y shouldBe (1.0 plusOrMinus EPS)
            v(1.0, 0.0, 0.0).applyMatrix4(basis).y shouldBe (1.0 plusOrMinus EPS)
            v(2.0, 0.0, 0.0).transformDirection(basis).y shouldBe (1.0 plusOrMinus EPS)
            basis.clone().invert().multiply(basis).elements[0] shouldBe (1.0 plusOrMinus EPS)
            three.createMatrix4().copy(basis).elements[1] shouldBe 1.0
            three.createMatrix4().identity().elements[0] shouldBe 1.0
        }
    }

    "a node's place and turn in the world, and a world point in its own space" {
        withThree { three ->
            val parent = three.createObject3D()
            val child = three.createObject3D()

            parent.position.set(10.0, 0.0, 0.0)
            parent.quaternion.setFromAxisAngle(three.createVector3(0.0, 1.0, 0.0), PI / 2)
            child.position.set(1.0, 0.0, 0.0)
            parent.add(child)

            // the parent's quarter turn takes the child's x offset to -z
            val at = three.createVector3()
            child.getWorldPosition(at) shouldBeSameInstanceAs at
            at.x shouldBe (10.0 plusOrMinus EPS)
            at.z shouldBe (-1.0 plusOrMinus EPS)

            val turn = three.createQuaternion()
            child.getWorldQuaternion(turn) shouldBeSameInstanceAs turn
            turn.angleTo(parent.quaternion) shouldBe (0.0 plusOrMinus 1e-6)

            val point = three.createVector3(10.0, 0.0, -1.0)
            parent.worldToLocal(point) shouldBeSameInstanceAs point
            point.x shouldBe (1.0 plusOrMinus EPS)
            point.z shouldBe (0.0 plusOrMinus EPS)

            parent.updateWorldMatrix(updateParents = true, updateChildren = true)
            three.createVector3(0.0, 0.0, 0.0).applyMatrix4(child.matrixWorld).x shouldBe (10.0 plusOrMinus EPS)

            child.frustumCulled shouldBe true
            child.frustumCulled = false
            typed<Any?>(child.asDynamic().frustumCulled) shouldBe false
        }
    }

    "what a node is: a bone, a skinned mesh, a mesh, a light" {
        withThree { three ->
            val bone = three.create<Object3D>("Bone")
            val skinned = three.create<Object3D>("SkinnedMesh")
            val mesh = three.createMesh(three.createBoxGeometry(), three.createMeshStandardMaterial(jsObject { color = 0xffffff }))
            val plain = three.createObject3D()

            bone.asBone shouldBeSameInstanceAs bone
            plain.asBone shouldBe null
            skinned.asSkinnedMesh shouldBeSameInstanceAs skinned
            mesh.asSkinnedMesh shouldBe null
            mesh.isMesh shouldBe true
            skinned.isMesh shouldBe true
            plain.isMesh shouldBe false
            three.createSpotLight().isLight shouldBe true
            plain.isLight shouldBe false
        }
    }

    "a skinned mesh's materials are a list, of one or of many" {
        withThree { three ->
            val one: Material = three.createMeshStandardMaterial(jsObject { color = 0xffffff })
            val other: Material = three.createMeshStandardMaterial(jsObject { color = 0x000000 })
            val skinned = three.create<SkinnedMesh>("SkinnedMesh")

            skinned.material = one
            skinned.materials shouldBe listOf(one)

            skinned.material = arrayOf(one, other)
            skinned.materials shouldBe listOf(one, other)
        }
    }

    "the glTF loader reads a file's scene" {
        withThree { three ->
            val loader = three.gltfLoader()

            // the smallest glTF there is: one scene with one named node
            val file = """{"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"name":"figure"}]}"""
            val gltf = loader.load("data:application/json," + js("encodeURIComponent")(file).unsafeCast<String>())

            isInstance(gltf.scene, three.raw.Group) shouldBe true
            gltf.scene.children.size shouldBe 1
            gltf.scene.children[0].name shouldBe "figure"

            // a file that is not there: the loader answers, with a failure
            val failed = try {
                loader.load("no-such-figure.glb")
                false
            } catch (_: Throwable) {
                true
            }

            failed shouldBe true
        }
    }
})
