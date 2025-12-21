// Polyfill
import * as performance from '@perf-tools/performance';

import * as core from "@strudel/core";
import * as mini from "@strudel/mini";
import * as transpiler from "@strudel/transpiler";

import {format as prettyFormat} from 'pretty-format'

// noinspection JSUnusedGlobalSymbols
/**
 * Expose "compile like the REPL" in a headless-safe way:
 * - returns a Pattern object you can queryArc on.
 */
function compile(code) {
    // The goal is to evaluate `code` in an environment where core/mini functions exist,
    // and the last expression is a Pattern.
    //
    // We avoid `eval` directly by building a Function with explicit parameters.
    const env = {...core, ...mini, ...transpiler};

    const names = Object.keys(env);
    const values = names.map(k => env[k]);

    // Important: wrap in parentheses so expressions like `s("bd*4")` work as expression.
    // Also allow multi-line code by returning the last expression.
    const fn = new Function(...names, `
"use strict";
return (function() {
  return ${code};
})();
`);

    return fn(...values);
}

// noinspection JSUnusedGlobalSymbols
/**
 * Query events between fromSec and toSec.
 * Returns plain JSON-ish objects to cross the polyglot boundary cleanly.
 */
function queryPattern(pattern, from, to) {
    if (!pattern) return [];

    // Many Strudel Pattern objects support query-like semantics that yield Haps/values.
    // We normalize everything into {t, dur, value}.
    const haps = pattern.queryArc(from, to)

    return haps.map(h => {
        const base = {
            t: Number(h.part?.begin?.valueOf?.() ?? 0),
            dur: Number((h.part?.end?.valueOf?.() ?? 0) - (h.part?.begin?.valueOf?.() ?? 0)),
            value: h.value
        };

        console.log("HAP", prettyFormat(h));

        if (!h?.value || h.value.note == null) return {...base, notesExpanded: []};

        // 1) Expand mini-notation within the same time window
        const expanded = mini.mini(h.value.note).queryArc(base.t, base.t + base.dur);

        // 2) Map each token to a note-value event
        const notesExpanded = expanded.map(eh => {
            const noteVal = core.note(eh.value); // eh.value is like "a" or "c3"

            return {
                t: Number(eh.part?.begin?.valueOf?.() ?? 0),
                dur: Number((eh.part?.end?.valueOf?.() ?? 0) - (eh.part?.begin?.valueOf?.() ?? 0)),
                // carry a uniform shape: { note: string }
                value: {
                    // Inherit from parent hap
                    ...(h.value || {}),
                    // Get note from noteVal or inherit it from eh.value
                    ...(typeof noteVal?.value === 'object' && noteVal.value?.note ? noteVal.value : { note: String(eh.value) }),
                },
            };
        });

        notesExpanded.forEach(ne => console.log(JSON.stringify(ne)));

        return {...base, notesExpanded};
    });
}

export {
    // Polyfills
    performance,
    // Helper libs
    prettyFormat,
    // Strudel
    core,
    mini,
    transpiler,
    // Strudel interop
    compile,
    queryPattern,
};
