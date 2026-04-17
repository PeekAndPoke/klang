package io.peekandpoke.klang.script.ksp

/**
 * Decides whether an extracted default-value expression is a stand-alone
 * Kotlin literal that can be pasted verbatim into the generated registration
 * file without risk of compile failure.
 *
 * Used by [KlangScriptProcessor.safeDefaultThunk] to gate which Kotlin defaults
 * become runtime thunks (enabling named-arg calls to omit the slot) vs which
 * stay as docs-only metadata. The conservative-by-default rule: if we can't
 * prove the text needs no enclosing-scope symbols to compile, return false and
 * let the runtime fall back to Kotlin's own arity dispatch.
 *
 * Recognised literal shapes:
 *  - `null`, `true`, `false`
 *  - Number literals (signed, optional decimal, optional exponent, optional
 *    Kotlin suffix `f`/`F`/`l`/`L`/`d`/`D`)
 *  - String literals — `"..."` and `"""..."""` — with no embedded splice/concat
 *  - Char literals — `'.'` or `'\.'`
 *
 * Anything else (qualified references, function calls, expressions) returns
 * false. Splitting this out as a top-level `object` keeps the gating logic
 * unit-testable without spinning up a KSP environment.
 */
object SafeDefaultLiteral {

    // Covers decimal, hex (0x…), binary (0b…), underscore-separated (1_000), and
    // optional sign + suffix. Permissive on underscores — Kotlin allows them anywhere
    // between digits but rejects double-underscore or trailing; the Kotlin compiler
    // catches those, so we don't over-validate here.
    private val NUMBER_LITERAL = Regex(
        "^[+-]?(" +
                "0[xX][0-9a-fA-F_]+" +           // hex
                "|0[bB][01_]+" +                  // binary
                "|[\\d_]+(\\.[\\d_]+)?([eE][+-]?[\\d_]+)?" +  // decimal (optional fractional + exponent)
                "|\\.\\d[\\d_]*([eE][+-]?[\\d_]+)?" +         // leading-dot decimal
                ")[fFlLdDuU]?\$"
    )

    fun isSafe(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        if (trimmed == "null" || trimmed == "true" || trimmed == "false") return true

        if (NUMBER_LITERAL.matches(trimmed)) return true

        // Triple-quoted raw string — no nested """ inside (would be two adjacent strings).
        if (trimmed.startsWith("\"\"\"") && trimmed.endsWith("\"\"\"") && trimmed.length >= 6) {
            val inner = trimmed.substring(3, trimmed.length - 3)
            if (!inner.contains("\"\"\"")) return true
        }

        // Regular string literal — closing quote must be the trailing char and
        // no other unescaped " can appear inside (which would mean concatenation).
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
            var i = 1
            while (i < trimmed.length - 1) {
                when (trimmed[i]) {
                    '\\' -> i += 2
                    '"' -> return false
                    else -> i++
                }
            }
            return true
        }

        // Char literal — '.' or '\.'
        if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
            val inner = trimmed.substring(1, trimmed.length - 1)
            if (inner.length == 1 || (inner.length == 2 && inner.startsWith("\\"))) return true
        }

        return false
    }
}
