/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

/**
 * A single processing stage in the voice pipeline.
 *
 * The voice signal flows through a chain of BlockRenderers:
 * **Ignite → (teardown fade) → Send**
 *
 * - the **Ignite** stage ([IgniteRenderer]) writes [BlockContext.audioBuffer]: the Ignitor tree, the whole instrument
 * - the **teardown fade** ([TeardownFadeRenderer]) reads/writes [BlockContext.audioBuffer], when the tree does not
 *   end in its own envelope
 * - the **Send** stage ([SendRenderer]) reads [BlockContext.audioBuffer] and routes it to the cylinder mixer
 */
fun interface BlockRenderer {
    fun render(ctx: BlockContext)
}
