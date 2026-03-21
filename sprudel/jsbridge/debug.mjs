import {compile, queryPattern} from "../build/strudel-bundle.mjs";

console.log("is ESM:", typeof require === "undefined"); // should be true
// also valid only in ESM:
console.log("import.meta.url:", import.meta.url);

function printEv(e) {
    console.log(`${e.value.note}  ${e.context.scale}`)
}

// (async function() {
//     const compiled = await compile(`
//     note("0 1 2 3 4 5 6 7").scale("C4:minor")
// `)
//
//     console.log(compiled)
//
//     const events = compiled.queryArc(0.0, 1.0)
//
//     console.log(events)
//
//     events.forEach(e => printEv(e))
//
// })();

async function ex1() {
    const compiled = await compile(`
    n("0 1 2 3 4 5 6 7").debug("C4:minor").scale("C4:minor")
`)

    console.log("compiled", compiled)

    const events = queryPattern(compiled, 0.0, 1.0)

    console.log(events)

    events.forEach(e => printEv(e))

}

await ex1()
