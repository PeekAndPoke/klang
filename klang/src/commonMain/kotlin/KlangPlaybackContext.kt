/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred

/**
 * Context object bundling all dependencies needed by playback implementations.
 * Owned and managed by [KlangPlayer].
 *
 * This reduces constructor parameter lists and makes it easier to add new dependencies
 * without changing all playback constructors.
 */
class KlangPlaybackContext internal constructor(
    /** Player configuration options */
    val playerOptions: KlangPlayer.Options,

    /** Centralized sample preloader (shared across all playbacks) */
    val samplePreloader: SamplePreloader,

    /** Function to send control commands to the audio backend */
    val sendControl: (KlangCommLink.Cmd) -> Unit,

    /** Coroutine scope for playback operations */
    val scope: CoroutineScope,

    /** Dispatcher for event fetching operations */
    val fetcherDispatcher: CoroutineDispatcher,

    /** Dispatcher for frontend callbacks and signals */
    val callbackDispatcher: CoroutineDispatcher,

    /**
     * Completes when the backend emits [KlangCommLink.Feedback.BackendReady].
     * Playback should await this before scheduling the first voice so that
     * cold-start JIT/cache warmup is already done.
     */
    val backendReady: Deferred<Unit>,

    /** The single FE↔BE clock offset — GLOBAL, owned by [KlangPlayer]; controllers read it. */
    internal val clockSync: BackendClockSync,
)
