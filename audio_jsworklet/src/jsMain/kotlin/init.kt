/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

import io.peekandpoke.klang.audio_bridge.registerProcessor

@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("main")
fun main() {
    console.log("[WORKLET] Registering KlangAudioWorklet")

    registerProcessor(
        "klang-audio-processor",
        KlangAudioWorklet::class.js,
    )

    console.log("[WORKLET] KlangAudioWorklet registered")
}
