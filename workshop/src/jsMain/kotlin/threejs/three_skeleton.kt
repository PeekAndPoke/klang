@file:Suppress("FunctionName", "unused")

package io.peekandpoke.klang.workshop.threejs

import io.peekandpoke.kraft.addons.threejs.ThreeJsAddon
import io.peekandpoke.kraft.addons.threejs.js.Group
import io.peekandpoke.kraft.addons.threejs.js.Material
import io.peekandpoke.kraft.addons.threejs.js.Object3D
import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * Figures with a skeleton, and the loader that reads them from glTF files. Types only, as in the addon.
 */

/** A bone of a skeleton: a node whose place and turn move the skin bound to it. */
external class Bone : Object3D

/** A mesh whose vertices follow the bones of a skeleton. */
external class SkinnedMesh : Object3D {
    /** One material, or an array of them. */
    var material: dynamic

    /**
     * The sphere its skinned points lie in, in its own space, or null. three.js works it out from the pose of the
     * moment when it is first needed and only then (while it is null): a figure that moves needs a sphere of its
     * own, large enough for every pose, or it is left out where it still shows. Not the geometry's sphere, which a
     * skinned mesh does not use.
     */
    var boundingSphere: Sphere?
}

/** The node as a bone, or null. */
val Object3D.asBone: Bone?
    get() = if (asDynamic().isBone == true) unsafeCast<Bone>() else null

/** The node as a skinned mesh, or null. */
val Object3D.asSkinnedMesh: SkinnedMesh?
    get() = if (asDynamic().isSkinnedMesh == true) unsafeCast<SkinnedMesh>() else null

/** Whether the node is a mesh of any kind (a skinned one too). */
val Object3D.isMesh: Boolean
    get() = asDynamic().isMesh == true

/** Whether the node is a light. */
val Object3D.isLight: Boolean
    get() = asDynamic().isLight == true

/** The skinned mesh's materials, one or many, as a list. */
val SkinnedMesh.materials: List<Material>
    get() {
        val m: dynamic = material

        return if (js("Array.isArray(m)").unsafeCast<Boolean>()) {
            m.unsafeCast<Array<Material>>().toList()
        } else {
            listOf(m.unsafeCast<Material>())
        }
    }

/** A material's name, as the file that brought it gave it. */
val Material.name: String
    get() = asDynamic().name.unsafeCast<String?>() ?: ""

/** What a glTF file holds, as far as it is used here: its scene. */
external interface Gltf {
    val scene: Group
}

/** Reads glTF files (`.gltf`, `.glb`). Made by [gltfLoader]. */
class GltfLoader internal constructor(private val loader: dynamic) {
    /** The file at [url], loaded. It throws where the file cannot be fetched or read. */
    suspend fun load(url: String): Gltf {
        return loader.loadAsync(url).unsafeCast<Promise<Gltf>>().await()
    }
}

/**
 * A loader for glTF files. It is a part of three.js that is not in its core, so it is fetched when first asked for.
 * [ThreeJsAddon] is taken to say: three.js itself is loaded.
 */
@Suppress("UnusedReceiverParameter")
suspend fun ThreeJsAddon.gltfLoader(): GltfLoader {
    val module: dynamic = js("import('three/addons/loaders/GLTFLoader.js')").unsafeCast<Promise<dynamic>>().await()
    val ctor: dynamic = module.GLTFLoader

    return GltfLoader(loader = js("new ctor()"))
}
