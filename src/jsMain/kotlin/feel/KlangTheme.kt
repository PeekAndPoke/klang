package io.peekandpoke.klang.feel

import de.peekandpoke.kraft.addons.styling.StyleSheets
import de.peekandpoke.ultra.streams.Stream
import de.peekandpoke.ultra.streams.StreamSource
import de.peekandpoke.ultra.streams.Unsubscribe
import io.peekandpoke.klang.feel.KlangTheme.current
import io.peekandpoke.klang.feel.KlangTheme.initialize
import io.peekandpoke.klang.feel.KlangTheme.update
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Single entry-point for the active Klang look-and-feel.
 *
 * Subscribe to [current] in any component to receive the active [KlangLookAndFeel] and
 * re-render automatically whenever it changes:
 *
 * ```kotlin
 * private val laf by subscribingTo(KlangTheme.current)
 * // in render:
 * css { backgroundColor = Color(laf.appBackground) }
 * ```
 *
 * Call [initialize] once in `main()` before mounting the app.
 * Call [update] to apply a new look-and-feel at runtime (future user theming).
 */
object KlangTheme : Stream<KlangLookAndFeel> {

    /** Stream of the active look-and-feel.  Default is the hardcoded fallback. */
    private val current: StreamSource<KlangLookAndFeel> = StreamSource(KlangLookAndFeel())

    private var mountedStyles: KlangLookAndFeel.Styles? = null

    override fun invoke(): KlangLookAndFeel = current()

    override fun subscribeToStream(sub: (KlangLookAndFeel) -> Unit): Unsubscribe = current.subscribeToStream(sub)

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Reads CSS custom properties from `:root`, applies the resulting theme, and emits it. */
    fun initialize() {
        update(KlangLookAndFeel.fromCss())
    }

    /** Replaces the active theme: syncs CSS vars, swaps the stylesheet, and emits the new value. */
    fun update(laf: KlangLookAndFeel) {
        swapStyles(laf)
        syncCssVars(laf)
        current(laf)
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun swapStyles(laf: KlangLookAndFeel) {
        mountedStyles?.let { StyleSheets.unmount(it) }
        mountedStyles = laf.styles
        StyleSheets.mount(laf.styles)
    }

    /**
     * Writes all tokens as inline style properties on `<html>`.
     * Inline styles take precedence over the stylesheet declarations in `index.css`,
     * so all `var(--klang-*)` references in CSS (Fomantic overrides etc.) update automatically.
     */
    private fun syncCssVars(laf: KlangLookAndFeel) {
        val style = (document.documentElement as? HTMLElement)?.style ?: return
        style.setProperty("--klang-bg-app", laf.appBackground)
        style.setProperty("--klang-bg-menu", laf.menuBackground)
        style.setProperty("--klang-bg-panel", laf.panelBackground)
        style.setProperty("--klang-bg-card", laf.cardBackground)
        style.setProperty("--klang-bg-overlay", laf.overlayBackground)
        style.setProperty("--klang-text-primary", laf.textPrimary)
        style.setProperty("--klang-text-secondary", laf.textSecondary)
        style.setProperty("--klang-text-tertiary", laf.textTertiary)
        style.setProperty("--klang-accent", laf.accent)
        style.setProperty("--klang-excellent", laf.excellent)
        style.setProperty("--klang-good", laf.good)
        style.setProperty("--klang-moderate", laf.moderate)
        style.setProperty("--klang-warning", laf.warning)
        style.setProperty("--klang-critical", laf.critical)
    }
}
