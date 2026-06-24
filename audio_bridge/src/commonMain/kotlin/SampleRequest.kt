/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge


/**
 * Used to request a sample from the Sample Index
 */
@WireFormat
data class SampleRequest(
    /** Name of the requested bank ... null means default sounds */
    val bank: String?,
    /** Name of the requested sound */
    val sound: String?,
    /** Index of the requested variant (if any) */
    val index: Int?,
    /** Note at which the sample would be played. Helps to find the best sample. */
    val note: String?,
)
