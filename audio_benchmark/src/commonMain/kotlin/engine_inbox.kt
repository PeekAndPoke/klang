/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_benchmark

import io.peekandpoke.klang.audio_bridge.ScheduledVoice

/**
 * [voice] as the platform's engine receives it: on Kotlin/JS through the generated wire codec, the path every voice
 * takes into the worklet (so its slot maps are the decoder's); on the JVM as it is, since the JVM engine takes
 * sprudel's voice data in-process. The allocation check's `C-remap` case builds its voices through this, so a
 * change of the decoder's map type shows in its bytes.
 */
expect fun receivedByEngine(voice: ScheduledVoice): ScheduledVoice

/**
 * [voice] as the frontend hands it on to the worklet: on Kotlin/JS the generated wire encode (the browser's next step
 * after sprudel's `toVoiceData`, which walks both slot maps); on the JVM the voice itself, since the JVM engine takes
 * sprudel's voice data in-process and nothing is encoded. The allocation check's `S-doors` case sends through this.
 */
expect fun sentToWorklet(voice: ScheduledVoice): Any?
