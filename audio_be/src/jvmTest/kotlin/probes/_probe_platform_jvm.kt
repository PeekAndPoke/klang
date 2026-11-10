/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import java.lang.management.ManagementFactory
import java.util.Locale
import com.sun.management.ThreadMXBean as HotSpotThreadMXBean

// The JVM's half of `_probe_platform.kt` (KLANG-00420).

actual fun nanoNow(): Double = System.nanoTime().toDouble()

private val hotSpotThreads: HotSpotThreadMXBean? = (ManagementFactory.getThreadMXBean() as? HotSpotThreadMXBean)
    ?.takeIf { it.isThreadAllocatedMemorySupported && it.isThreadAllocatedMemoryEnabled }

/**
 * HotSpot's per-thread counter, which counts every TLAB and outside allocation, as a Double at once; NaN on a JVM
 * without the counter.
 */
actual fun allocatedBytesNow(): Double = hotSpotThreads?.currentThreadAllocatedBytes?.toDouble() ?: Double.NaN

actual fun formatFixed(x: Double, digits: Int): String = String.format(Locale.ROOT, "%.${digits}f", x)
