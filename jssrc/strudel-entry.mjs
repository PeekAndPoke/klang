// Polyfill
import * as performance from '@perf-tools/performance';

import * as core from "@strudel/core";
import * as mini from "@strudel/mini";
import * as transpiler from "@strudel/transpiler";


// noinspection JSUnusedGlobalSymbols
/**
 * Headless adapter:
 * - Stores one "current" pattern
 * - Allows querying events in an interval
 *
 * Note: We intentionally avoid WebAudio and any "play" functions.
 */
function createEngine() {
    let pattern = null;

    // noinspection JSUnusedGlobalSymbols
    return {
        // Accept either an already-built Pattern, or a function producing one.
        // For now, we keep it simple: caller passes a function name and args, or you can wire a parser later.
        setPatternFromFn(fnName, ...args) {
            const fn = core[fnName];
            if (typeof fn !== "function") {
                throw new Error(`No such core function: ${fnName}`);
            }
            return fn(...args);
        },

        // For a first test, allow installing a Pattern directly (from JS evaluation)
        setPattern(p) {
            pattern = p;
            return true;
        },

        /**
         * Query events between fromSec and toSec.
         * Returns plain JSON-ish objects to cross the polyglot boundary cleanly.
         */
        query(fromSec, toSec) {
            if (!pattern) return [];

            const out = [];
            // Many Strudel Pattern objects support query-like semantics that yield Haps/values.
            // We normalize everything into {t, dur, value}.
            pattern.query(fromSec, toSec, (ev) => {
                out.push({
                    t: ev.time ?? ev.t ?? ev.begin ?? ev.start ?? fromSec,
                    dur: ev.duration ?? ev.dur ?? (ev.span?.duration ?? 0),
                    value: ev.value ?? ev.val ?? ev
                });
            });
            return out;
        },
    };
}

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

export {
    performance,
    core,
    mini,
    transpiler,
    createEngine,
    compile,
};
