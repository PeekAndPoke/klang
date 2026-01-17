package io.peekandpoke.klang.audio_fe

import kotlinx.browser.window

/**
 * JS implementation using performance.now() for high precision
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object KlangTime {
    actual fun nowMs(): Double {
        return window.performance.now()
    }
}
