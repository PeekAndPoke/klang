# Type inference for stored lambdas in the editor

Status: FUTURE, NICE. Found 2026-09-26 while fixing the param-tool icons (the maintainer's report on
`guitar3_shape`).

## The problem

A lambda written inline as an argument is typed: `AnalyzedAst` gives its parameters the types the called
function declares (`expectedArgumentTypes`, through `ArgAlignment`), so `.apply(x => x.body(...))` knows `x` is a
pattern. A lambda STORED first is not:

```
export guitar3_shape = x => x.pregain(...).sound(guitar).body(material = "oak", wet = 0.3)
export guitar3 = n(guitar3_pat).apply(guitar3_shape)
```

Here `x` is untyped (`ArgAlignment`'s KDoc says so: "a lambda passed via a variable ... is untyped in the editor").
Everything that depends on the receiver type then guesses: the param-tool badges, the whole-call tool, completion
after `x.`, hover docs. Since 2026-09-26 the param tools guess well (`KlangSymbol.callableForArgument`: the variant
whose param has a tool wins, and the whole-call rewrite is withheld when the variants disagree on the param name),
but it is still a guess, and completion and hover still show every variant.

## The idea

Infer a stored lambda's parameter types from where it is USED:

- a first pass collects, for every `let` / `const` / `export` whose value is a lambda, the expected function type
  at each call site that passes it as an argument (the same `expectedArgumentTypes` / `ArgAlignment` rule);
- when all uses agree, revisit the lambda body with those parameter types;
- when they disagree, or the lambda is never passed, leave it untyped (today's behaviour).

## Where it lives

`klangscript/src/commonMain/kotlin/intel/AnalyzedAst.kt` (the scope bindings and the lambda-body visit), perhaps with
a helper file next to it (analysis utilities live in `klangscript`, never in UI modules). Rough size: a day with
tests (the implementer's estimate).

## Difficulties

- A use can come after the definition: two passes or a deferred visit.
- Disagreeing uses (a lambda applied to a pattern in one place and passed elsewhere).
- Uses in OTHER files that import the export are not visible.
- `ArgAlignment`'s KDoc sentence on untyped variables is updated when this lands.
