/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.cylinders.Cylinder

// A chain's stages by kind, for the specs that ask about ONE of them (the warehouse specs about the rented ring and
// network, the resonator specs about a body and a vowel). Main code has no typed accessor for a serial stage (only
// the duck, which runs outside the pipeline, keeps its own): it walks `KatalystChain.pipeline` and asks the chain
// `declaresTail` and `latencyFrames`, which test kinds once at build. These lived on `KatalystChain` and
// `Cylinder` until engine tidy-up step 13 (A2.1), where the chain built six lists for them at every build.
//
// Null when the chain declares no such stage, which `KatalystDsl.classic` never does. A chain that declares a kind
// twice reports the LAST one here, the rule the duck follows, while BOTH run. The cylinder's are the chain IN SERVICE.

/** The last body stage: the resonator of the body kind. Body and vowel are one class, so this finds it by kind. */
internal val KatalystChain.body: KatalystResonatorEffect?
    get() = pipeline.filterIsInstance<KatalystResonatorEffect>().lastOrNull { it.kind == ResonatorKind.BODY }

/** The last vowel stage: the resonator of the vowel kind, found by kind like [body]. */
internal val KatalystChain.vowel: KatalystResonatorEffect?
    get() = pipeline.filterIsInstance<KatalystResonatorEffect>().lastOrNull { it.kind == ResonatorKind.VOWEL }

/** The last delay stage. */
internal val KatalystChain.delay: KatalystDelayEffect?
    get() = pipeline.filterIsInstance<KatalystDelayEffect>().lastOrNull()

/** The last reverb stage. */
internal val KatalystChain.reverb: KatalystReverbEffect?
    get() = pipeline.filterIsInstance<KatalystReverbEffect>().lastOrNull()

/** The last phaser stage. */
internal val KatalystChain.phaser: KatalystPhaserEffect?
    get() = pipeline.filterIsInstance<KatalystPhaserEffect>().lastOrNull()

/** The last compressor stage. */
internal val KatalystChain.compressor: KatalystCompressorEffect?
    get() = pipeline.filterIsInstance<KatalystCompressorEffect>().lastOrNull()

/** The body stage of the chain in service. */
internal val Cylinder.body: KatalystResonatorEffect? get() = currentChain.body

/** The vowel stage of the chain in service. */
internal val Cylinder.vowel: KatalystResonatorEffect? get() = currentChain.vowel

/** The delay stage of the chain in service. */
internal val Cylinder.delay: KatalystDelayEffect? get() = currentChain.delay

/** The reverb stage of the chain in service. */
internal val Cylinder.reverb: KatalystReverbEffect? get() = currentChain.reverb

/** The phaser stage of the chain in service. */
internal val Cylinder.phaser: KatalystPhaserEffect? get() = currentChain.phaser

/** The compressor stage of the chain in service. */
internal val Cylinder.compressor: KatalystCompressorEffect? get() = currentChain.compressor

/** The bus pipeline of the chain in service, in the order it declares its stages (the duck is not in it). */
internal val Cylinder.pipeline: List<KatalystEffect> get() = currentChain.pipeline
