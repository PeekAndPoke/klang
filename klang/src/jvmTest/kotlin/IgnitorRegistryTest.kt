package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.uniqueId
import io.peekandpoke.klang.common.infra.KlangLock
import io.peekandpoke.klang.common.infra.withLock

/**
 * Behavioural tests for [IgnitorRegistry] — the per-player tracker that announces inline
 * [IgnitorDsl] trees to the audio backend once each. Synthetic names come from the
 * process-wide [IgnitorDsl.uniqueId] map; this class only tracks which DSLs it has
 * already forwarded a [KlangCommLink.Cmd.RegisterIgnitor] command for.
 */
class IgnitorRegistryTest : StringSpec({

    fun newRegistry(): Pair<IgnitorRegistry, MutableList<KlangCommLink.Cmd>> {
        // The concurrent test exercises this from many threads; the production code path
        // serialises sendControl through the player, but the bare MutableList here would
        // lose adds without explicit synchronisation. KlangLock is KMP-portable.
        val sendLock = KlangLock()
        val sent = mutableListOf<KlangCommLink.Cmd>()
        return IgnitorRegistry(sendControl = { cmd -> sendLock.withLock { sent.add(cmd) } }) to sent
    }

    "registerOrLookup returns the same name for the same DSL instance" {
        val (reg, sent) = newRegistry()
        val dsl = IgnitorDsl.Sine()

        val name1 = reg.registerOrLookup(dsl)
        val name2 = reg.registerOrLookup(dsl)

        name1 shouldBe name2
        sent.size shouldBe 1 // only one RegisterIgnitor command goes to the backend
    }

    "structurally-equal but distinct DSL instances collapse to the same name" {
        val (reg, sent) = newRegistry()

        val name1 = reg.registerOrLookup(IgnitorDsl.Sine())
        val name2 = reg.registerOrLookup(IgnitorDsl.Sine())

        name1 shouldBe name2
        sent.size shouldBe 1
    }

    "different DSL trees get distinct names" {
        val (reg, sent) = newRegistry()

        val name1 = reg.registerOrLookup(IgnitorDsl.Sine())
        val name2 = reg.registerOrLookup(IgnitorDsl.Sawtooth())
        val name3 = reg.registerOrLookup(IgnitorDsl.Square())

        name1 shouldNotBe name2
        name2 shouldNotBe name3
        name1 shouldNotBe name3
        sent.size shouldBe 3
    }

    "register emits a RegisterIgnitor command with the synthetic name and original DSL" {
        val (reg, sent) = newRegistry()
        val dsl = IgnitorDsl.Lowpass(inner = IgnitorDsl.Sine(), cutoffHz = IgnitorDsl.Constant(2000.0))

        val name = reg.registerOrLookup(dsl)

        sent.size shouldBe 1
        val cmd = sent.single() as KlangCommLink.Cmd.RegisterIgnitor
        cmd.name shouldBe name
        cmd.dsl shouldBe dsl
        cmd.playbackId shouldBe KlangCommLink.SYSTEM_PLAYBACK_ID
    }

    "synthetic name matches IgnitorDsl.uniqueId()" {
        val (reg, _) = newRegistry()
        val dsl = IgnitorDsl.Triangle()
        reg.registerOrLookup(dsl) shouldBe dsl.uniqueId()
    }

    "two registries (two players) share names but each announces independently" {
        val (regA, sentA) = newRegistry()
        val (regB, sentB) = newRegistry()
        // A fresh, distinct DSL ensures this test is order-independent re: global counter.
        val dsl = IgnitorDsl.Lowpass(inner = IgnitorDsl.Ramp(), cutoffHz = IgnitorDsl.Constant(7777.7))

        val nameA = regA.registerOrLookup(dsl)
        val nameB = regB.registerOrLookup(dsl)

        nameA shouldBe nameB // global uniqueId = same name everywhere
        sentA.size shouldBe 1 // each player announces to its own backend exactly once
        sentB.size shouldBe 1
    }

    "clear() empties the per-player sent set (without resetting global names)" {
        val (reg, sent) = newRegistry()
        val dsl = IgnitorDsl.Sine()
        val nameBefore = reg.registerOrLookup(dsl)
        reg.size shouldBe 1
        sent.size shouldBe 1

        reg.clear()
        reg.size shouldBe 0

        // Same DSL still resolves to the same global name — but the player re-announces
        // because its sent-set was cleared.
        val nameAfter = reg.registerOrLookup(dsl)
        nameAfter shouldBe nameBefore
        sent.size shouldBe 2
    }

    "concurrent registerOrLookup never collides on synthetic names" {
        val (reg, sent) = newRegistry()
        val threads = 8
        val perThread = 200
        val results = Array(threads) { Array<String?>(perThread) { null } }

        val tasks = (0 until threads).map { t ->
            Thread {
                repeat(perThread) { i ->
                    // Mix shared + unique DSLs so the cache sees both
                    // collisions (good — same name) and new entries.
                    val dsl = if (i % 3 == 0) {
                        IgnitorDsl.Sine()
                    } else {
                        IgnitorDsl.Sawtooth(freq = IgnitorDsl.Constant(440.0 + t * 1000 + i))
                    }
                    results[t][i] = reg.registerOrLookup(dsl)
                }
            }.also { it.start() }
        }
        tasks.forEach { it.join() }

        results.flatten().forEach { it shouldNotBe null }
        val distinctNames = sent.filterIsInstance<KlangCommLink.Cmd.RegisterIgnitor>().map { it.name }
        distinctNames.toSet().size shouldBe distinctNames.size
        reg.size shouldBe distinctNames.size
    }
})