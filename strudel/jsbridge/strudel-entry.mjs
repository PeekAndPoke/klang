// Polyfill
import * as performance from '@perf-tools/performance';
// Debug
import {format as prettyFormat} from 'pretty-format'
// Strudel
import * as core from "@strudel/core";
import {evalScope, register} from "@strudel/core";

import * as mini from "@strudel/mini";
import {miniAllStrings} from "@strudel/mini";

import * as tonal from "@strudel/tonal";

await evalScope(
    import('@strudel/core'),
    import('@strudel/mini'),
    import('@strudel/tonal'),
);

register(
    'debug',
    function (input, pat) {
        // console.log("debug:", input, pat)
        return pat
    },
    true,
    true,
)

// noinspection JSUnusedGlobalSymbols
/**
 * Expose "compile like the REPL" in a headless-safe way:
 * - returns a Pattern object you can queryArc on.
 */
const compile = async (strudelCode) => {
    miniAllStrings(); // allows using single quotes for mini notation / skip transpilation

    const evalRes = await evaluate(strudelCode)

    // console.log(res)
    // console.log("===================================================================")
    // const arcs = evalRes.pattern.queryArc(0.0, 1.0)
    // arcs.forEach(e => console.log(`${e.value.note}  ${e.context.scale}`))
    // console.log("===================================================================")

    return evalRes.pattern;
}

// noinspection JSUnusedGlobalSymbols
const queryPattern = (pattern, from, to) => {
    return pattern.queryArc(from, to)
}

export {
    // Polyfills
    performance,
    // helpers
    prettyFormat,
    // Strudel
    core,
    mini,
    tonal,
    // Strudel interop
    compile,
    queryPattern,
};
