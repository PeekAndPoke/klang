/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.notstrom

import io.peekandpoke.kraft.addons.threejs.js.Object3D

/**
 * Lets [prop] be seen through, as far as asked ([show]): every material in it is exchanged for a copy of its own that
 * can be seen through, so a material other instruments share stays as it is. [lift] gives the materials back and
 * frees the copies. For an instrument that goes or comes at a change of song (KLANG-00481).
 */
internal class Veil(prop: Object3D) {
    private class Worn(val mesh: dynamic, val own: dynamic, val copies: Array<dynamic>, val solid: List<Double>)

    private val worn = mutableListOf<Worn>()

    init {
        prop.traverse { node ->
            val o: dynamic = node
            val own: dynamic = o.material

            if (own != null) {
                val many = js("Array.isArray")(own).unsafeCast<Boolean>()
                val all: Array<dynamic> = if (many) own.unsafeCast<Array<dynamic>>() else arrayOf(own)
                val copies: Array<dynamic> = all.map { material ->
                    val copy: dynamic = material.clone()

                    copy.transparent = true
                    // (a copy does not take these along: without them a gilded body would lose its gold)
                    copy.onBeforeCompile = material.onBeforeCompile
                    copy.customProgramCacheKey = material.customProgramCacheKey

                    copy
                }.toTypedArray()

                o.material = if (many) copies else copies[0]
                worn.add(Worn(mesh = o, own = own, copies = copies, solid = all.map { (it.opacity as? Double) ?: 1.0 }))
            }
        }
    }

    /** Shows the prop [share] of how solid it is: 0 not at all, 1 as it was made. */
    fun show(share: Double) {
        // NaN-guard: what is no number shows it as it was made
        val by = if (share != share) 1.0 else share.coerceIn(0.0, 1.0)

        for (one in worn) {
            one.copies.forEachIndexed { index, copy -> copy.opacity = one.solid[index] * by }
        }
    }

    /** The prop has its own materials again, and the copies are freed. Call it once. */
    fun lift() {
        for (one in worn) {
            one.mesh.material = one.own
            one.copies.forEach { it.dispose() }
        }

        worn.clear()
    }
}
