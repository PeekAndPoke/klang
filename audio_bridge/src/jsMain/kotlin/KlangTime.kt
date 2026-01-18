package io.peekandpoke.klang.audio_bridge

import kotlin.js.Date

/**
 * JS implementation
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object KlangTime {
    actual fun nowMs(): Double {
        return Date.now()
        // Try performance.now() (main thread and some worker contexts)
        // Fall back to Date.now() (AudioWorklet and other contexts)
//        return js("""
//            (function() {
//                if (typeof window !== 'undefined' && window.performance) {
//                    return window.performance.now();
//                } else if (typeof performance !== 'undefined') {
//                    return performance.now();
//                } else {
//                    return Date.now();
//                }
//            })()
//        """) as Double
    }
}
