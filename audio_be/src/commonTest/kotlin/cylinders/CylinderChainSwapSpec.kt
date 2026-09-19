/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl

/**
 * The per-cylinder chain swap: how a `katalyst(…)` name becomes the chain an orbit runs
 * (Katalyst step 3a, `docs/tasks/katalyst-dsl.md` §6 and the plan's §7).
 *
 * The semantics of a request, the bounded cache behind them, and the lifecycle
 * (`retire` / `adopt`). What a DECLARED chain's knobs resolve to is `KatalystSlotResolverSpec`'s
 * subject; what the swap of a SOUNDING orbit sounds like, and the drain behind it, is
 * `CylinderChainCrossfadeSpec`'s (step 3b).
 */
class CylinderChainSwapSpec : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    /** A chain with a delay and nothing else: its `delay` stage is there, its `reverb` is not. */
    fun delayChain(time: Double) = KatalystDsl.of(
        KatalystStageDsl.Delay(time = IgnitorDsl.Constant(time))
    )

    /** A chain with neither a delay nor a reverb, the "everything the classic chain has is gone" case. */
    fun gainChain(gain: Double) = KatalystDsl.of(
        KatalystStageDsl.Gain(gain = IgnitorDsl.Constant(gain))
    )

    /** A ring shelf that records every allocation it is asked for, and can be told to refuse. */
    // Holds the allocator lambda rather than implementing the function type (forbidden on Kotlin/JS).
    class Recording(var failing: Boolean = false) {
        val asked = mutableListOf<Int>()
        val allocate: (Int) -> StereoBuffer? = { frames ->
            asked += frames
            if (failing) null else StereoBuffer(frames)
        }
    }

    class Rig(val registry: KatalystRegistry = KatalystRegistry(), val alloc: Recording = Recording()) {
        val cylinder = Cylinder(
            id = 0,
            blockFrames = blockFrames,
            sampleRate = sampleRate,
            // 0 = one silent tryDeactivate is enough, so a row reads as "it went quiet".
            silentBlocksBeforeTailCheck = 0,
            rings = SizedBuffers.forRings(sampleRate, allocate = alloc.allocate),
            reverbs = ReverbUnits(sampleRate),
            katalysts = registry,
        )

        /** A voice claims the orbit: the chain's stages are written and the orbit sounds. */
        fun sound(voice: Voice = VoiceTestHelpers.createSynthVoice()) {
            cylinder.updateFromVoice(voice, blockStart = 0.0)
        }

        /** The orbit falls silent and the round-robin cleanup reaches it. */
        fun goQuiet() {
            cylinder.mixBuffer.clear()
            cylinder.tryDeactivate()
        }
    }

    /** True while the cylinder runs the classic chain, the only one with all seven classic effects. */
    fun Cylinder.runsClassic(): Boolean = reverb != null && delay != null && body != null && duck != null

    // ── The semantics of a request ───────────────────────────────────────────────────────────────

    "an idle cylinder installs a declared chain right away" {
        val rig = Rig()
        rig.registry.register("gain", gainChain(1.5))

        rig.cylinder.runsClassic() shouldBe true

        rig.cylinder.requestChain("gain")

        rig.cylinder.runsClassic() shouldBe false
        rig.cylinder.reverb.shouldBeNull()
        rig.cylinder.duck.shouldBeNull()
        rig.cylinder.pipeline.size shouldBe 1
    }

    "the same name again is a no-op: the running chain is not rebuilt" {
        val rig = Rig()
        rig.registry.register("gain", gainChain(1.5))

        rig.cylinder.requestChain("gain")
        val installed = rig.cylinder.pipeline
        rig.cylinder.cachedChainCount shouldBe 1

        rig.cylinder.requestChain("gain")

        // Same list instance means the same chain object: a rebuild would hand out a new one and
        // silently drop the orbit's DSP state with it.
        rig.cylinder.pipeline shouldBeSameInstanceAs installed
        rig.cylinder.cachedChainCount shouldBe 1
    }

    "a name the registry does not know is remembered, never resolved to classic, and retried" {
        val rig = Rig()

        rig.cylinder.requestChain("late")

        withClue("an unknown name must not install anything at all") {
            rig.cylinder.runsClassic() shouldBe true
        }

        // The registration arrives (a dropped or late `RegisterKatalyst`), and the next request
        // for the same name lands.
        rig.registry.register("late", gainChain(2.0))
        rig.cylinder.requestChain("late")

        rig.cylinder.runsClassic() shouldBe false
    }

    "a name that never resolves costs NO allocation per polled block" {
        val rig = Rig()

        // The stuck path: a dropped `RegisterKatalyst` leaves this name pending forever, and the
        // poll retries it on every block of every orbit. `KatalystRegistry.namesNormalized`
        // counts the lookups that build a lowercased string, which is the only allocation the
        // registry can make; the render path must take the key door instead and leave it at zero.
        rig.cylinder.requestChain("Never-Registered")

        for (block in 0 until 64) {
            rig.cylinder.pollPendingChain()
        }

        withClue("the ONE normalization happens in the cylinder, at request time, not per block") {
            rig.registry.namesNormalized shouldBe 0
        }

        // Positive control: the poll was live the whole time, so a zero above means "no string",
        // not "no poll".
        rig.registry.register("never-registered", gainChain(2.0))
        rig.cylinder.pollPendingChain()

        rig.cylinder.runsClassic() shouldBe false
        rig.registry.namesNormalized shouldBe 0
    }

    "a pending name on an ALREADY silent orbit lands on the polled block" {
        val rig = Rig()

        // Nothing sounds and nothing else is coming: a one-shot `.katalyst(…)` emits its
        // reference once, so the poll is the only thing that can still install it.
        rig.cylinder.requestChain("late")
        rig.cylinder.pollPendingChain()

        rig.cylinder.runsClassic() shouldBe true

        rig.registry.register("late", gainChain(2.0))
        rig.cylinder.pollPendingChain()

        rig.cylinder.runsClassic() shouldBe false
    }

    "a request on a SOUNDING orbit installs the chain at once, through a crossfade" {
        val rig = Rig()
        rig.registry.register("gain", gainChain(1.5))
        rig.sound()

        rig.cylinder.requestChain("gain")

        withClue("step 3b: the declared chain is in service immediately") {
            rig.cylinder.runsClassic() shouldBe false
        }

        withClue("and the chain it replaced is still audible, fading out") {
            rig.cylinder.isFading shouldBe true
        }
    }

    "a pending name also lands at the next idle check, without a second request" {
        val rig = Rig()
        rig.sound()

        rig.cylinder.requestChain("late")
        rig.registry.register("late", gainChain(2.0))

        withClue("while the orbit sounds, nothing is swapped") {
            rig.cylinder.runsClassic() shouldBe true
        }

        rig.goQuiet()

        rig.cylinder.isActive shouldBe false
        rig.cylinder.runsClassic() shouldBe false
    }

    "the LAST request wins: an earlier queued name never lands after it" {
        val rig = Rig()
        rig.sound()

        // Neither name is registered yet, so each request can only be queued, and the queue holds
        // exactly one. (The mid-FADE queue, which is the other way in, is
        // `CylinderChainCrossfadeSpec`'s.)
        rig.cylinder.requestChain("first")
        rig.cylinder.requestChain("second")

        rig.registry.register("first", gainChain(1.1))
        rig.registry.register("second", delayChain(0.25))

        rig.goQuiet()

        // "second" has a delay stage and "first" has not, so the stage list says which one landed.
        rig.cylinder.delay.shouldNotBeNull()
        rig.cylinder.reverb.shouldBeNull()
    }

    "the KEY is the identity, not the content: a second name for one stage list is a second chain" {
        // The no-op of a re-emitted declaration compares the requested key against the CURRENT
        // chain's key, never the stage lists, so a chain re-registered under a second name rebuilds
        // (and crossfades, on a sounding orbit). `KatalystDsl.uniqueId()` is content-derived, so one
        // content normally has one name and the price is not paid.
        //
        // ONE content is compared, and it is the exception this row does not cover: a declaration
        // equal to `KatalystDsl.classic` resolves to the chain the cylinder was born with (step
        // 5b-1, `Cylinder.chainFor`), which is what the "no build, no swap" row above is about.
        val rig = Rig()
        rig.registry.register("gain", gainChain(1.5))
        rig.registry.register("gain-again", gainChain(1.5))

        rig.cylinder.requestChain("gain")
        val installed = rig.cylinder.pipeline

        rig.cylinder.requestChain("gain-again")

        rig.cylinder.pipeline shouldNotBeSameInstanceAs installed
        rig.cylinder.cachedChainCount shouldBe 2

        withClue("the same name again is still the free path") {
            val second = rig.cylinder.pipeline

            rig.cylinder.requestChain("gain-again")

            rig.cylinder.pipeline shouldBeSameInstanceAs second
            rig.cylinder.cachedChainCount shouldBe 2
        }
    }

    "an install drops a stale queued request: the older name never lands afterwards" {
        val rig = Rig()
        // "early" is asked for before it is known, so it is queued.
        rig.cylinder.requestChain("early")
        rig.registry.register("early", delayChain(0.25))
        rig.registry.register("now", gainChain(1.5))

        // A later request that CAN be served is the newer intent, and it is installed at once.
        rig.cylinder.requestChain("now")

        rig.cylinder.delay.shouldBeNull()

        // The orbit sounds and falls silent again: the idle check must find nothing to do.
        rig.sound()
        rig.goQuiet()

        withClue("the superseded 'early' must not overwrite the chain that was installed after it") {
            rig.cylinder.delay.shouldBeNull()
            rig.cylinder.reverb.shouldBeNull()
        }
    }

    "a chain whose CONTENT is classic resolves to the born-with instance: no build, no swap" {
        // Decided 2026-09-19 with step 5b-1, and it reverses the decision of 2026-09-18 (review
        // round 2) together with its reason. That round forbade the shortcut because the born-with
        // chain was VOICE-driven, so handing it back made `Katalyst(k => k.classic())` inert and
        // every `katp` on the orbit went nowhere. Both chains read the orbit's param state now, so
        // the two are the same chain in every observable way, and building a second one would buy
        // a crossfade that is not bit-transparent for a declaration that changes nothing.
        val rig = Rig()
        rig.registry.register("some-classic-name", KatalystDsl.classic)

        val bornWith = rig.cylinder.pipeline

        rig.cylinder.requestChain("some-classic-name")

        rig.cylinder.pipeline shouldBeSameInstanceAs bornWith
        withClue("nothing was built, so nothing is cached") {
            rig.cylinder.cachedChainCount shouldBe 0
        }

        withClue("and the name was adopted, so a re-emission takes the raw fast path") {
            rig.cylinder.requestChain("some-classic-name")

            rig.cylinder.pipeline shouldBeSameInstanceAs bornWith
            rig.cylinder.cachedChainCount shouldBe 0
        }

        // The engagement control: a chain with DIFFERENT content is still built and installed, so
        // the row above is about the CONTENT and not about a `requestChain` that stopped working.
        rig.registry.register("not-classic", gainChain(1.5))
        rig.cylinder.requestChain("not-classic")

        rig.cylinder.pipeline shouldNotBeSameInstanceAs bornWith
        rig.cylinder.cachedChainCount shouldBe 1
    }

    "a QUEUED content-classic key still lets the deactivation reach its clean slate" {
        // Review round 1, m1. `install` returns FALSE when the requested chain is the one already
        // in service, so `installPending` reports "nothing installed" and `tryDeactivate` falls
        // through to its own reset. Without that the orbit went inactive with its DSP state and its
        // lease intact, and the next life's first owner inherited this life's settings, which the
        // reset exists to prevent.
        val rig = Rig()

        // A room the deactivation has to clear, written as a SLOT so the born-with chain reads it.
        rig.sound(
            VoiceTestHelpers.createSynthVoice(
                katalystParams = mapOf("reverb.wet" to 0.5, "reverb.size" to 6.0),
            )
        )

        rig.cylinder.reverb.shouldNotBeNull().reverb.shouldNotBeNull()

        // The request arrives BEFORE its `RegisterKatalyst`, which is what puts the key in the
        // queue rather than installing it: a request on a sounding orbit whose name IS known goes
        // through `beginFade` and never reaches `installPending` at all.
        //
        // This is the cheapest way to reach the guarded path, NOT the production scenario, and a
        // future reader should not go looking for a late registration. In production
        // `Cylinders.processAndMix` polls every cylinder's pending chain BEFORE `tryDeactivate`,
        // so a key that is merely late is installed by the poll. What reaches `tryDeactivate` with
        // a key still queued is one that was refused by that block's poll and freed afterwards:
        // a key waiting behind a fade whose `outgoing` slot is released inside `processEffects` of
        // the same block.
        rig.cylinder.requestChain("classic")
        rig.registry.register("classic", KatalystDsl.classic)

        rig.goQuiet()

        rig.cylinder.isActive shouldBe false

        // The observable is the LEASE, which `tryDeactivate`'s reset frees along with the orbit's
        // param state. A second voice offering itself in the SAME block as the first is inside the
        // lease's grace: if the lease still stood, its claim would be refused and the orbit would
        // keep the dead owner's 6. Granted, it writes its own 2.
        rig.sound(
            VoiceTestHelpers.createSynthVoice(
                katalystParams = mapOf("reverb.wet" to 0.5, "reverb.size" to 2.0),
            )
        )

        withClue("the lease was freed, so the next owner configures the orbit from scratch") {
            rig.cylinder.reverb.shouldNotBeNull().reverb.shouldNotBeNull().size shouldBe
                    Reverb.normalizeSize(2.0)
        }
    }

    "the classic chain by name from a declared chain comes back to the born-with instance" {
        val rig = Rig()
        rig.registry.register("gain", gainChain(1.5))
        rig.registry.register("classic", KatalystDsl.classic)

        val bornWith = rig.cylinder.pipeline

        rig.cylinder.requestChain("gain")
        rig.cylinder.runsClassic() shouldBe false

        rig.cylinder.requestChain("classic")

        rig.cylinder.pipeline shouldBeSameInstanceAs bornWith
        rig.cylinder.runsClassic() shouldBe true
        withClue("only the gain chain was ever built") { rig.cylinder.cachedChainCount shouldBe 1 }
    }

    // ── The bounded cache ────────────────────────────────────────────────────────────────────────

    "the cache is bounded: nine declared chains, eight kept" {
        val rig = Rig()

        for (i in 0 until Cylinder.MAX_CACHED_CHAINS + 1) {
            val name = "chain-$i"
            rig.registry.register(name, gainChain(1.0 + i))
            rig.cylinder.requestChain(name)
        }

        rig.cylinder.cachedChainCount shouldBe Cylinder.MAX_CACHED_CHAINS
    }

    "a swap hands the outgoing chain's ring back to the SHELF, where the next one finds it" {
        val rig = Rig()
        // Two different chains, both with a delay of the same ring class: the second one's stage
        // is a fresh instance, so the only way it can avoid an allocation is the shelf.
        rig.registry.register("echo", delayChain(0.25))
        // The feedback is deliberately NOT the DELAY_FEEDBACK default, or the two chains would be
        // content-equal and the second request would be the no-op of the row above.
        rig.registry.register("echo-slower", KatalystDsl.of(
            KatalystStageDsl.Delay(time = IgnitorDsl.Constant(0.25), feedback = IgnitorDsl.Constant(0.45))
        ))

        rig.cylinder.requestChain("echo")
        rig.sound()

        withClue("the declared delay rents exactly one ring") {
            rig.alloc.asked.size shouldBe 1
        }

        rig.goQuiet()
        rig.cylinder.requestChain("echo-slower")
        rig.sound()

        rig.cylinder.delay.shouldNotBeNull().delayLine.shouldNotBeNull()
        withClue("the outgoing chain was retired, so this rent is a shelf hit, not an allocation") {
            rig.alloc.asked.size shouldBe 1
        }
        rig.cylinder.cachedChainCount shouldBe 2
    }

    "a cached chain goes straight back into service, ring and all" {
        val rig = Rig()
        rig.registry.register("echo", delayChain(0.25))
        rig.registry.register("dry", gainChain(1.0))

        rig.cylinder.requestChain("echo")
        rig.sound()
        val echoStages = rig.cylinder.pipeline

        rig.goQuiet()
        rig.cylinder.requestChain("dry")
        rig.cylinder.delay.shouldBeNull()

        rig.cylinder.requestChain("echo")
        rig.sound()

        // The same chain object, not a rebuild: a cache miss here would drop the orbit's DSP state
        // and pay the build again on the audio thread.
        rig.cylinder.pipeline shouldBeSameInstanceAs echoStages
        rig.cylinder.delay.shouldNotBeNull().delayLine.shouldNotBeNull()
        rig.alloc.asked.size shouldBe 1
        rig.cylinder.cachedChainCount shouldBe 2
    }

    "two spellings of one name share one cache entry" {
        val rig = Rig()
        // The registry stores under the lowercased name, so the cache has to as well or a song
        // that writes `Bus` and `BUS` would build the same chain twice.
        rig.registry.register("Bus", gainChain(1.5))
        rig.registry.register("echo", delayChain(0.25))

        rig.cylinder.requestChain("Bus")
        val installed = rig.cylinder.pipeline

        rig.cylinder.requestChain("echo")
        rig.cylinder.requestChain("BUS")

        rig.cylinder.pipeline shouldBeSameInstanceAs installed
        rig.cylinder.cachedChainCount shouldBe 2
    }

    "eviction never takes the chain in service, even when it is the OLDEST entry" {
        val rig = Rig()

        for (i in 0 until Cylinder.MAX_CACHED_CHAINS) {
            val name = "chain-$i"
            rig.registry.register(name, gainChain(1.0 + i))
            rig.cylinder.requestChain(name)
        }

        // Back to the first one: the cache is full and its OLDEST entry is now the chain in
        // service, which is exactly the case insertion-order eviction would get wrong.
        rig.cylinder.requestChain("chain-0")
        val inService = rig.cylinder.pipeline
        rig.cylinder.cachedChainCount shouldBe Cylinder.MAX_CACHED_CHAINS

        rig.registry.register("chain-8", gainChain(9.0))
        rig.cylinder.requestChain("chain-8")

        rig.cylinder.cachedChainCount shouldBe Cylinder.MAX_CACHED_CHAINS

        // chain-0 must still be the cached chain it was: evicting the chain that was in service
        // would have retired it under its own feet, and coming back would build a new one.
        rig.cylinder.requestChain("chain-0")

        rig.cylinder.pipeline shouldBeSameInstanceAs inService
    }

    "deniedRents is per cylinder LIFE: two swaps under a denying shelf keep the sum" {
        val rig = Rig(alloc = Recording(failing = true))
        rig.registry.register("echo", delayChain(0.25))
        rig.registry.register("echo-2", KatalystDsl.of(
            KatalystStageDsl.Delay(time = IgnitorDsl.Constant(0.25), feedback = IgnitorDsl.Constant(0.45))
        ))
        rig.registry.register("dry", gainChain(1.0))

        rig.cylinder.requestChain("echo")
        rig.sound()
        rig.cylinder.deniedRents shouldBe 1

        rig.goQuiet()
        rig.cylinder.requestChain("echo-2")
        rig.sound()

        withClue("the second refusal adds to the first, it does not replace it") {
            rig.cylinder.deniedRents shouldBe 2
        }

        rig.goQuiet()
        rig.cylinder.requestChain("dry")

        withClue("a chain with nothing to rent still reports what this cylinder's life was refused") {
            rig.cylinder.deniedRents shouldBe 2
        }

        rig.cylinder.retire()

        withClue("the count is per LIFE: a shelved cylinder carries no number into the next engine") {
            rig.cylinder.deniedRents shouldBe 0
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────────────────────

    "retire clears the cache, drops the pending name and puts the classic chain back" {
        val rig = Rig()
        rig.registry.register("gain", gainChain(1.5))

        rig.cylinder.requestChain("gain")
        rig.sound()
        // Still unknown when it is asked for, so it can only be QUEUED, which is what retire has
        // to drop. (A name that resolves would start a crossfade instead, see step 3b.)
        rig.cylinder.requestChain("echo")
        rig.registry.register("echo", delayChain(0.25))

        rig.cylinder.retire()

        rig.cylinder.cachedChainCount shouldBe 0
        rig.cylinder.runsClassic() shouldBe true
        rig.cylinder.isActive shouldBe false

        // The queued "echo" is gone with the cylinder's life: a new engine's orbit must not
        // inherit a chain the previous one declared.
        rig.sound()
        rig.goQuiet()

        rig.cylinder.runsClassic() shouldBe true
    }

    "adopt strands no unit: the ring goes back even without a return to the shelf" {
        val rig = Rig()
        rig.registry.register("echo", delayChain(0.25))

        rig.cylinder.requestChain("echo")
        rig.sound()
        rig.alloc.asked.size shouldBe 1

        // Re-labelled for the next engine WITHOUT going through `CylinderUnits.giveBack` first.
        val next = KatalystRegistry()
        next.register("echo-next", KatalystDsl.of(
            KatalystStageDsl.Delay(time = IgnitorDsl.Constant(0.25), feedback = IgnitorDsl.Constant(0.45))
        ))
        rig.cylinder.adopt(id = 5, silentBlocksBeforeTailCheck = 0, katalysts = next)

        rig.cylinder.requestChain("echo-next")
        rig.sound()

        rig.cylinder.delay.shouldNotBeNull().delayLine.shouldNotBeNull()
        withClue("adopt retired the previous chain, so the shelf had the ring for the new one") {
            rig.alloc.asked.size shouldBe 1
        }
    }

    "adopt starts the next engine's orbit from classic, on the next engine's registry" {
        val rig = Rig()
        rig.registry.register("gain", gainChain(1.5))

        rig.cylinder.requestChain("gain")
        rig.cylinder.runsClassic() shouldBe false

        val next = KatalystRegistry()
        next.register("other", delayChain(0.5))
        rig.cylinder.adopt(id = 5, silentBlocksBeforeTailCheck = 0, katalysts = next)

        rig.cylinder.id shouldBe 5
        rig.cylinder.runsClassic() shouldBe true

        withClue("the previous engine's chains are unreachable now") {
            rig.cylinder.requestChain("gain")
            rig.cylinder.runsClassic() shouldBe true
        }

        rig.cylinder.requestChain("other")

        rig.cylinder.delay.shouldNotBeNull()
        rig.cylinder.reverb.shouldBeNull()
    }
})
