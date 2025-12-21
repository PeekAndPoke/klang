// Polyfill
import * as performance from '@perf-tools/performance';
// Debug
import {format as prettyFormat} from 'pretty-format'
// Strudel
import * as core from "@strudel/core";
import {evalScope} from "@strudel/core";

import * as mini from "@strudel/mini";
import { miniAllStrings } from '@strudel/mini';

import { transpiler } from "@strudel/transpiler";

await evalScope(
    import('@strudel/core'),
    import('@strudel/mini'),
    import('@strudel/tonal'),
    // import('@strudel/tonal'),
);

miniAllStrings(); // allows using single quotes for mini notation / skip transpilation

// noinspection JSUnusedGlobalSymbols
/**
 * Expose "compile like the REPL" in a headless-safe way:
 * - returns a Pattern object you can queryArc on.
 */
async function compile(strudelCode) {

    // const pat = transpiler('note("c3 [e3,g3]")', { wrapAsync: false, addReturn: false, simpleLocs: true });

    console.log("Evaluating strudel code\n", strudelCode)

    const strudelCodeEscaped = strudelCode.replaceAll(/`/g, "\\`");

    const res = await evaluate(`
${strudelCodeEscaped}    
    `)

    console.log("Result Pattern", res)

    return res.pattern
    // The goal is to evaluate `code` in an environment where core/mini functions exist,
    // and the last expression is a Pattern.
    //
    // We avoid `eval` directly by building a Function with explicit parameters.
    //
    // return (async () => {
    //     return await evaluate(`
    //         ${strudelCodeEscaped}
    //     `, transpiler);
    // })();
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
                    ...(typeof noteVal?.value === 'object' && noteVal.value?.note ? noteVal.value : {note: String(eh.value)}),
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
