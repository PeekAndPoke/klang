package io.peekandpoke.klang.ui

import kotlinx.browser.window
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Detects a double-click on the same subject within a configurable timeout.
 *
 * Usage:
 * ```
 * val dblClick = DblClickCtrl()
 * dblClick(atom, 0.5.seconds) { doSomething() }
 * ```
 *
 * Call [invoke] on each "click". If the same subject is passed again within [timeout],
 * the [onDoubleClick] callback fires (and the state is reset so a third call starts fresh).
 */
class DblClickCtrl {

    private var lastSubject: Any? = null
    private var lastTimeMs: Double = 0.0

    operator fun <T : Any> invoke(
        subject: T,
        timeout: Duration = 500.milliseconds,
        onDoubleClick: () -> Unit,
    ) {
        val now = window.performance?.now() ?: 0.0
        val elapsed = (now - lastTimeMs).milliseconds

        if (subject == lastSubject && elapsed < timeout) {
            lastSubject = null
            lastTimeMs = 0.0
            onDoubleClick()
        } else {
            lastSubject = subject
            lastTimeMs = now
        }
    }

    /** Resets internal state, clearing any pending first-click. */
    fun reset() {
        lastSubject = null
        lastTimeMs = 0.0
    }
}
