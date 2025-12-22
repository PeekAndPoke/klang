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
);

miniAllStrings(); // allows using single quotes for mini notation / skip transpilation

// noinspection JSUnusedGlobalSymbols
/**
 * Expose "compile like the REPL" in a headless-safe way:
 * - returns a Pattern object you can queryArc on.
 */
async function compile(strudelCode) {
    const strudelCodeEscaped = strudelCode.replaceAll(/`/g, "\\`");

    const res = await evaluate(`
${strudelCodeEscaped}    
    `)

    return res.pattern
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
};
