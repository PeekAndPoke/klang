/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.peekandpoke.klang.workshop.threejs.Matrix4
import io.peekandpoke.klang.workshop.threejs.Quaternion
import io.peekandpoke.klang.workshop.threejs.createMatrix4
import io.peekandpoke.klang.workshop.threejs.createQuaternion
import io.peekandpoke.kraft.addons.threejs.ThreeJsAddon
import io.peekandpoke.kraft.addons.threejs.js.Object3D
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import org.w3c.dom.events.Event

/**
 * three.js, as the live act reaches it. The library is one for the whole page, loaded by Kraft's addon; the live
 * act keeps it here once it is there ([LiveAct] sets it before anything is built), so a vector can be made without
 * handing the addon down through every call.
 */
internal object Three {
    lateinit var addon: ThreeJsAddon
}

internal fun vec(x: Double = 0.0, y: Double = 0.0, z: Double = 0.0): Vector3 = Three.addon.createVector3(x, y, z)

internal fun vec(xyz: Triple<Double, Double, Double>): Vector3 = vec(xyz.first, xyz.second, xyz.third)

internal fun quat(): Quaternion = Three.addon.createQuaternion()

internal fun mat4(): Matrix4 = Three.addon.createMatrix4()

/**
 * Whether a key was pressed while the user types somewhere (a field, a text area, an editor): the live act's keys
 * are single letters and the space bar, and must not take them away from a text.
 */
internal fun Event.fromTyping(): Boolean {
    // where the key was really pressed: inside a web component the plain target is only its host
    val path: dynamic = asDynamic().composedPath?.call(this)
    val target: dynamic = (if (path != null && path.length > 0) path[0] else null) ?: this.target ?: return false
    val tag = (target.tagName as? String)?.lowercase()

    return tag == "input" || tag == "textarea" || tag == "select" || target.isContentEditable == true
}

/**
 * Frees what the nodes under this one hold on the graphics card: every geometry, every material and the textures
 * a material carries, a lamp's shadow map and a skeleton's bones. The nodes stay where they are; call it when the scene is given up.
 */
internal fun Object3D.disposeTree() {
    traverse { node ->
        val o: dynamic = node

        o.geometry?.dispose()

        val materials: Array<dynamic> = when {
            o.material == null -> emptyArray()
            js("Array.isArray")(o.material).unsafeCast<Boolean>() -> o.material.unsafeCast<Array<dynamic>>()
            else -> arrayOf(o.material)
        }

        for (material in materials) {
            for (key in js("Object.keys")(material).unsafeCast<Array<String>>()) {
                val value: dynamic = material[key]

                if (value != null && value.isTexture == true) {
                    value.dispose()
                }
            }

            material.dispose()
        }

        // a lamp's shadow map
        o.shadow?.dispose()
        // a figure's bones, as a picture on the card
        o.skeleton?.dispose()
    }
}

/** The three axes, and straight up. Never change them: copy first. */
internal val X_AXIS: Vector3 by lazy { vec(1.0, 0.0, 0.0) }
internal val Y_AXIS: Vector3 by lazy { vec(0.0, 1.0, 0.0) }
internal val Z_AXIS: Vector3 by lazy { vec(0.0, 0.0, 1.0) }
