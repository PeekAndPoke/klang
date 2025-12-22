// Polyfill
import * as performance from '@perf-tools/performance';
// Debug
import {format as prettyFormat} from 'pretty-format'
// Strudel
import * as core from "@strudel/core";
import {evalScope, isNote, register} from "@strudel/core";

import * as mini from "@strudel/mini";
import {miniAllStrings} from "@strudel/mini";

import * as tonal from "@strudel/tonal";

await evalScope(
    import('@strudel/tonal'),
    import('@strudel/core'),
    import('@strudel/mini'),
    // import('@strudel/midi'),
    // import('@strudel/hydra'),
    // import('@strudel/soundfonts'),
);

// import * as tonaljs from "@tonaljs/tonal"
// import {Scale} from "@tonaljs/tonal";
//
// console.log("-----------------------------------------------------------------------------------")
// const cMinor = Scale.get("c minor")
// console.log("cMinor", prettyFormat(cMinor))
//
// console.log("-----------------------------------------------------------------------------------")
// const s = "C4:minor";
// console.log(s.split(" ", ":").filter(Boolean)); // should be ["C4", "minor"]
//
// console.log("-----------------------------------------------------------------------------------")
// const r = /^([a-gA-G])([#bsf]*)(-?[0-9]*)$/;
// for (const x of ["C-1", "Eb-1", "C4", "Bb2", "F#3", "c"]) {
//     const m = r.exec(x);
//     console.log(x, "=>", m ? m.slice(1) : null);
// }
//
// console.log("-----------------------------------------------------------------------------------")
// console.log("RegExp sanity:", /^C4:minor$/.test("C4:minor"));
// console.log("strudel tonal keys:", Object.keys(tonal).slice(0, 20));
//
// console.log("-----------------------------------------------------------------------------------")
// console.log("isNote()", isNote("C-1"))


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
    const strudelCodeEscaped = strudelCode.replaceAll(/`/g, "\\`");

    miniAllStrings(); // allows using single quotes for mini notation / skip transpilation

    const evalRes = await evaluate(`
${strudelCodeEscaped}    
    `)

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
    // Helper libs
    prettyFormat,
    // Strudel
    core,
    mini,
    tonal,
    // tonaljs,
    // transpiler,
    // Strudel interop
    compile,
    queryPattern,
};
