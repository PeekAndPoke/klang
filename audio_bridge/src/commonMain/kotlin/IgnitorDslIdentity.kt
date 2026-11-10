/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.common.infra.KlangSnapshotMap

/**
 * Process-wide identity map for [IgnitorDsl] trees.
 *
 * Identity = what the engine builds from the tree: its structure (the data classes' equality) AND its sharing, which
 * node instances it holds more than once ([IgnitorDslNameKey]). Two trees with both in common collapse to one entry and
 * share one synthetic name like `"ignitor-7"`. The counter is monotonic and never resets: names stay stable across the
 * lifetime of the process regardless of which player or playback first encountered the tree.
 *
 * A player-scoped IgnitorRegistry uses this map to look up the name and keeps its own
 * bookkeeping of which DSLs it has already announced to its backend.
 *
 * Reads (the common case during voice scheduling) are lock-free via [KlangSnapshotMap].
 * The counter mutation rides inside the map's lock through the `getOrPut` callback.
 */
private val globalIgnitorNames = KlangSnapshotMap<IgnitorDslNameKey, String>()
private var nextGlobalIgnitorId: Int = 0

/**
 * Return the process-wide unique name for this [IgnitorDsl] tree.
 *
 * On first sighting, allocates a fresh monotonic name like `"ignitor-N"`. Subsequent calls
 * with trees of the same structure and the same sharing return the same name without further allocation.
 *
 * Note: this only allocates a *name*; it does not announce the DSL to any audio backend.
 * That side of the round-trip is the player's IgnitorRegistry responsibility.
 */
fun IgnitorDsl.uniqueId(): String = globalIgnitorNames.getOrPut(IgnitorDslNameKey.of(this)) {
    "ignitor-${nextGlobalIgnitorId++}"
}

/**
 * The key an inline instrument is named by: the tree as structure, and where it holds one node INSTANCE twice.
 *
 * The engine builds a tree by identity (its build cache keys by `===`, [WireShared] keeps the sharing across the
 * worklet wire), so `let p = pluck(); p + p.lowpass(800)` is one string read twice and `pluck() + pluck().lowpass(800)`
 * is two strings, while the two trees are EQUAL as data. A name keyed by equality alone gave both one name, and the
 * announce-once gate then kept the first one heard for the playback's life: an edit that changed only the sharing was
 * not heard (audit L5-02). So the key holds [sharing] beside the tree.
 *
 * [sharing] is read in one fixed order, depth first, children in [childNodes] order: every visit of a node that is not
 * a leaf gets a number, and a node met a second time adds two numbers, this visit's and the first visit's, and is not
 * entered again. A tree without a repeated instance has an empty list. The five leaves (`Param`, `Constant`, `Freq`,
 * `Progress`, `Elapsed`) are not counted: the build makes a leaf anew at every read, so whether a leaf instance repeats
 * changes nothing the engine builds. Counting every other node is on the safe side: a repeated instance the build forks
 * anyway (across a detune, under two pitch nodes) only gets a name of its own, never another tree's.
 *
 * [tree] has every noise node's `uid` set to 0 (audit L5-10): the uid made two calls of `Ignitor.whitenoise()` two
 * names before the key knew sharing; with [sharing] it says nothing more, and it made every evaluation of a script with
 * a noise instrument a new name, a new announcement and a new optimizer run on the audio thread. The tree announced to
 * the engine keeps its uids; only the key drops them.
 */
internal data class IgnitorDslNameKey(val tree: IgnitorDsl, val sharing: List<Int>) {

    companion object {
        /** The key of [root]. */
        fun of(root: IgnitorDsl): IgnitorDslNameKey {
            val walk = SharingWalk()
            walk.visit(root)

            return IgnitorDslNameKey(tree = if (walk.sawNoise) root.withNoiseUidsZero(IdentityRewrites()) else root, sharing = walk.sharing)
        }

        private fun IgnitorDsl.isLeaf(): Boolean =
            this is IgnitorDsl.Param || this is IgnitorDsl.Constant || this is IgnitorDsl.Freq ||
                this is IgnitorDsl.Progress || this is IgnitorDsl.Elapsed

        private fun IgnitorDsl.isNoise(): Boolean =
            this is IgnitorDsl.WhiteNoise || this is IgnitorDsl.BrownNoise || this is IgnitorDsl.PinkNoise

        /** The depth-first walk that numbers the visits and records the repeated instances (see [sharing]). */
        private class SharingWalk {
            /** Every non-leaf instance met, by first visit; looked up by identity, a linear scan as the optimizer's. */
            private val seen = ArrayList<IgnitorDsl>()
            val sharing = ArrayList<Int>()
            var sawNoise = false
            private var visits = 0

            fun visit(node: IgnitorDsl) {
                if (node.isLeaf()) {
                    return
                }

                val visit = visits++

                for (k in seen.indices) {
                    if (seen[k] === node) {
                        sharing.add(visit)
                        sharing.add(k)

                        return
                    }
                }

                seen.add(node)
                sawNoise = sawNoise || node.isNoise()

                for (child in node.childNodes()) {
                    visit(child)
                }
            }
        }

        /** An identity memo, so a shared subtree is rewritten once and a node equal to its rewrite stays itself. */
        private class IdentityRewrites {
            val from = ArrayList<IgnitorDsl>()
            val to = ArrayList<IgnitorDsl>()
        }

        /** This tree with every noise node's `uid` at 0; an untouched subtree is returned as it is. */
        private fun IgnitorDsl.withNoiseUidsZero(memo: IdentityRewrites): IgnitorDsl {
            for (i in memo.from.indices) {
                if (memo.from[i] === this) {
                    return memo.to[i]
                }
            }

            val children = childNodes()
            val rewritten = children.map { it.withNoiseUidsZero(memo) }
            val unchanged = children.indices.all { children[it] === rewritten[it] }
            val rebuilt = if (unchanged) this else withChildNodes(rewritten)
            val result = when (rebuilt) {
                is IgnitorDsl.WhiteNoise -> if (rebuilt.uid == 0) rebuilt else rebuilt.copy(uid = 0)
                is IgnitorDsl.BrownNoise -> if (rebuilt.uid == 0) rebuilt else rebuilt.copy(uid = 0)
                is IgnitorDsl.PinkNoise -> if (rebuilt.uid == 0) rebuilt else rebuilt.copy(uid = 0)
                else -> rebuilt
            }

            memo.from.add(this)
            memo.to.add(result)

            return result
        }
    }
}
