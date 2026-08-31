/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlinx.coroutines.CoroutineScope

interface AudioBackend {
    class Config(
        val commLink: KlangCommLink.BackendEndpoint,
        val sampleRate: Int,
        val blockSize: Int,
    )

    /**
     * Access to visualization data.
     * Returns null if backend doesn't support visualization.
     */
    val analyzer: AudioAnalyzer? get() = null

    /**
     * Drain the control link **now** instead of at the backend's next scheduled poll.
     *
     * Called by `KlangPlayer.sendControl` immediately after a command is queued, so a command does
     * not sit in the ring waiting for a timer. That wait is invisible for scheduled voices (they
     * carry their own start time) but it is pure latency for the realtime path, where the command
     * IS the note-on.
     *
     * Draining is always the whole ring, in order, so this cannot reorder CONTROL commands: a
     * realtime voice can't arrive ahead of the `RegisterIgnitor` that names its sound.
     *
     * It does NOT make sample PCM ordered against control commands, and never did: a
     * `Cmd.Sample.Complete` is exploded into an upload buffer that the host drips out slowly, so a
     * voice queued after it still reaches the engine first and is dropped if its sample has not
     * landed. That is pre-existing behaviour the frontend already works around by awaiting
     * `Feedback.SampleReceived` before playing.
     *
     * **Implementations must not throw**, cancellation excepted. The caller queues first and pumps
     * second, so a failure here has already consumed the command; propagating it would break
     * `sendControl`'s no-throw contract, which callers depend on. The call site catches anyway,
     * and rethrows only `CancellationException`.
     *
     * Default no-op: a backend whose render loop already drains every block (the JVM one) has
     * nothing to gain, and must not be poked from a foreign thread.
     */
    fun pump() {}

    suspend fun run(scope: CoroutineScope)
}
