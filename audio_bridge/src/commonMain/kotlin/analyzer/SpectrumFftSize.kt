/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.analyzer

/**
 * The spectrum analyser's FFT size, shared by the backend's analyser and the display that reads it. 8192 points give
 * 4096 bins of 5.9 Hz at 48 kHz, so the display's lowest columns (8 Hz wide at 30 Hz) read mostly bins of their own; at
 * 2048 points (23 Hz bins) the columns below 150 Hz shared one or two bins and showed the same value. The price is
 * the window: 170 ms instead of 43, so the spectrum follows a hit about 65 ms later than the scope (still 2048).
 */
const val SPECTRUM_FFT_SIZE = 8192
