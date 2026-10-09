/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl

/**
 * The pitch mod above an `fm` node, as its MODULATOR reads it (pitch pipeline step 3b, decision D1: a pitch node means
 * what it wraps, and one above an fm moves the whole operator, so the ratio stays exact).
 *
 * The `Fm` arm of the build hands [mod] (the accumulated mod above the fm) to the modulator through this wrapper, so
 * every pitched source of the modulator applies it the way the carrier's sources do, through its own
 * [ModApplyingIgnitor]. The wrapper asks [mod] at the CARRIER's frequency, which the fm pins here ([pin]) every block
 * before it renders the modulator, and ignores the frequency it is called with. Why: the modulator runs at
 * `freq x ratio`, and a mod whose knobs read `Freq` (a vibrato rate on the note, an audio-rate `pitchMod`, an outer
 * fm, whose `freq` is the note) keeps its memo's freq key. Asked at the modulator's frequency it would render a second
 * time in the block, at another rate, and advance its state twice: the carrier's own modulation breaks with it, and
 * a chain of N fms renders 2^N times. Pinned, the call hits the memo the carrier filled and reads its samples bit for
 * bit, so the mod above the fm renders once per block for every carrier pitch, and a chain of N fms costs N + 1
 * oscillator renders.
 *
 * Because it ignores the caller's frequency, the wrapper is freq-invariant FOR ONE PIN (one carrier pitch): a pitch
 * node INSIDE the modulator combines it into a memo that drops its freq key (`combineMods`), so that memo too renders
 * once per block when the modulator holds two pitches (a forking `detune` inside the modulator). The one exception is
 * part of the author rule's residue (step 3b review round 2, MINOR 1): when the fm's CARRIER holds two pitches
 * (`(x + x.detune(7)).fm(m)`), the fm re-pins the wrapper within one block, once per carrier pitch; if the mod above
 * the fm keeps its freq key (it reads the note: a vibrato rate on the note, an audio-rate `pitchMod`, an outer fm) and
 * the modulator holds a pitch node whose knobs read no `Freq`, that pitch node's memo is filled under the first pin,
 * and the second pitch's modulator reads the first pitch's outer mod.
 *
 * When [mod] is itself freq-invariant (every sprudel door, every vibrato with constant knobs, a nested wrapper), the
 * frequency does not matter, so the wrapper hands on the caller's `freqHz` instead of the pinned field: the same
 * samples, and on V8 no heap number per block (a double loaded from a field and handed to a call V8 does not inline is
 * boxed; `docs/tasks/engine-follow-ups.md` item 10c).
 *
 * Its readers are the modulator's own pitched sources and the pitch-mod memos and nested wrappers inside the
 * modulator, all rendered by the fm right after [pin], so the pinned frequency is always the current block's. One
 * wrapper per fm node and outer mod ([IgnitorBuildCache.carrierFreqMod]): the modulator is keyed by it in the build
 * cache, so `let f = x.fm(m); f + f` keeps `m` one instance. One field write per fm per block; nothing allocates on
 * the JVM.
 */
internal class CarrierFreqMod(
    /** The accumulated pitch mod above the fm. */
    val mod: Ignitor,
    /** The fm node this wrapper serves: with [mod], the build's lookup key ([IgnitorBuildCache.carrierFreqMod]). */
    val fmNode: IgnitorDsl,
) : Ignitor {

    /** [mod] renders the same at any frequency, so the caller's frequency serves as well as the pinned one. */
    private val modIsFreqInvariant: Boolean = mod is CarrierFreqMod || (mod is MemoizingIgnitor && mod.freqInvariant)

    /** The frequency the fm's carrier asks the mod at, this block. */
    private var carrierHz: Double = 0.0

    /** Called by the fm every block, before it renders the modulator. */
    fun pin(carrierHz: Double) {
        this.carrierHz = carrierHz
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        if (modIsFreqInvariant) {
            mod.generate(buffer, freqHz, ctx)

            return
        }

        mod.generate(buffer, carrierHz, ctx)
    }
}
