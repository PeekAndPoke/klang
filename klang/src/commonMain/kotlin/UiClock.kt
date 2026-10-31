/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

/**
 * The clock the UI times what it shows on, in milliseconds: `Date.now()` in the browser, where every highlight, the
 * ADSR curves and the voice rain compare a signal's times with it. The playback signals are put on this clock
 * (KLANG-00399). Not `KlangTime`: that one counts `performance.now()`, which some browsers pause while the machine
 * sleeps, so after a sleep it lags `Date.now()` by the sleep; times on it would be that much too early for a consumer
 * on `Date.now()`, shown at once or dropped as gone.
 */
internal expect fun uiClockMs(): Double
