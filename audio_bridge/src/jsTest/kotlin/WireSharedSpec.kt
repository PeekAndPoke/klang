/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.wire.decode_IgnitorDsl
import io.peekandpoke.klang.audio_bridge.wire.decode_KatalystDsl
import io.peekandpoke.klang.audio_bridge.wire.decode_KlangCommLink_Cmd
import io.peekandpoke.klang.audio_bridge.wire.encode_IgnitorDsl
import io.peekandpoke.klang.audio_bridge.wire.encode_KatalystDsl
import io.peekandpoke.klang.audio_bridge.wire.encode_KlangCommLink_Cmd

/**
 * [WireShared] on [IgnitorDsl]: a node referenced twice in one message crosses the worklet wire as ONE object
 * (`docs/tasks/in-progress/parallel-serial-bands.md`, 2026-10-10). The backend builds a tree through an identity-keyed
 * cache, so without this the browser built a shared `let` twice while the JVM built it once.
 *
 * The hop between the two ends is `postMessage`'s structured clone, which keeps shared references within one message;
 * [hop] runs the same algorithm (`structuredClone`), so every row crosses the wire the way the worklet's messages do.
 */
class WireSharedSpec : StringSpec({

    fun hop(encoded: dynamic): dynamic = js("structuredClone")(encoded)

    fun same(a: Any?, b: Any?): Boolean = a === b

    /** A node with state worth sharing, and a sum that uses it twice: `let s = ...; s + s.lowpass(800)`. */
    val s = IgnitorDsl.Saw().lowpass(800.0)
    val twice = IgnitorDsl.Plus(left = s, right = IgnitorDsl.Times(left = s, right = IgnitorDsl.Constant(0.5)))

    "a node used twice is encoded as one JS object" {
        val encoded = encode_IgnitorDsl(twice)

        same(encoded.left, encoded.right.left) shouldBe true
    }

    "a node used twice arrives as one Kotlin object, and the tree is still equal" {
        val decoded = decode_IgnitorDsl(hop(encode_IgnitorDsl(twice))) as IgnitorDsl.Plus

        decoded shouldBe twice
        same(decoded.left, (decoded.right as IgnitorDsl.Times).left) shouldBe true
    }

    "inside a command, as the frontend sends an instrument" {
        val cmd = KlangCommLink.Cmd.RegisterIgnitor(playbackId = "pb", name = "inst", dsl = twice)
        val decoded = decode_KlangCommLink_Cmd(hop(encode_KlangCommLink_Cmd(cmd))) as KlangCommLink.Cmd.RegisterIgnitor
        val dsl = decoded.dsl as IgnitorDsl.Plus

        decoded shouldBe cmd
        same(dsl.left, (dsl.right as IgnitorDsl.Times).left) shouldBe true
    }

    "inside a chain: a knob shared by two stages stays one object" {
        val knob = IgnitorDsl.Saw().lowpass(5.0)
        val chain = KatalystDsl.of(KatalystStageDsl.Gain(knob), KatalystStageDsl.Gain(knob))
        val decoded = decode_KatalystDsl(hop(encode_KatalystDsl(chain)))

        decoded shouldBe chain
        same((decoded.stages[0] as KatalystStageDsl.Gain).gain, (decoded.stages[1] as KatalystStageDsl.Gain).gain) shouldBe true
    }

    "a parallel's branches still read ONE input after the hop" {
        val dsl = s.parallel({ it }, { it.mul(0.5) }) as IgnitorDsl.Parallel
        val decoded = decode_IgnitorDsl(hop(encode_IgnitorDsl(dsl))) as IgnitorDsl.Parallel

        decoded shouldBe dsl
        same(decoded.branches[0], (decoded.branches[1] as IgnitorDsl.Times).left) shouldBe true
    }

    "two EQUAL but distinct nodes stay two: identity is kept, nothing is interned" {
        // Two oscillators written twice are two oscillators (`IgnitorDslOptimizer`'s KDoc); folding them would change
        // the sound of every instrument that layers two equal voices.
        val apart = IgnitorDsl.Plus(left = IgnitorDsl.Saw().lowpass(800.0), right = IgnitorDsl.Saw().lowpass(800.0))
        val decoded = decode_IgnitorDsl(hop(encode_IgnitorDsl(apart))) as IgnitorDsl.Plus

        decoded shouldBe apart
        same(decoded.left, decoded.right) shouldBe false
    }

    "a decode that fails half-way leaves nothing behind: the next message starts clean" {
        // A valid left branch is decoded into the table, then the right branch's unknown tag throws.
        val broken = encode_IgnitorDsl(IgnitorDsl.Plus(left = s, right = IgnitorDsl.Constant(1.0)))
        broken.right = js("({'#t': 'no-such-node'})")

        var threw = false

        try {
            decode_IgnitorDsl(broken)
        } catch (e: Throwable) {
            threw = true
        }

        threw shouldBe true

        // Were the scope left open, the next two root calls would share one table and hand out one object.
        same(encode_IgnitorDsl(s), encode_IgnitorDsl(s)) shouldBe false
    }

        "nothing is remembered between messages: the same node in two messages is two objects" {
        val first = encode_IgnitorDsl(s)
        val second = encode_IgnitorDsl(s)

        same(first, second) shouldBe false

        val encoded = encode_IgnitorDsl(s)
        same(decode_IgnitorDsl(encoded), decode_IgnitorDsl(encoded)) shouldBe false
    }
})
