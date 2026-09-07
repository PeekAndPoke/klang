# String slots have no readers

Recorded 2026-09-07 (maintainer decision, `docs/tasks/sprudel-field-accessors.md`): a compound
object exposes a reader child for every NUMERIC slot (`room.size`, `lpf.env`, `penv.curve`), and
none for a slot whose value is a name. "Apply a mapper to a name" has no use case, and reading a
name into another setter has none either; the maintainer expects it never will.

The slots without readers, as of batch G:

| Object       | Name slot(s)                       | Values                                        |
|--------------|------------------------------------|-----------------------------------------------|
| `distort`    | `shape`                            | `soft`, `hard`, `fold`, `tube`, ... (16 names) |
| `tremolo`    | `shape`                            | LFO waveform names                            |
| `adsrCurves` | `attack`, `decay`, `release`       | curve names; the object carries the setter only |
| `vowel`      | `vowel`                            | `a e i o u`, singer prefixes                  |
| `body`       | `material`                         | `wood cedar tube glass membrane brass`        |

The single-field string setters (`note`, `n`, `sound`, `bank`, `scale`, `unit`, `loop`) are not
accessors either, for the same reason.

If a use case ever appears (a `vowel` pattern read into a display, say), the shape is decided:
a `FieldAccessor` child whose read yields the name as a string value, and the mapper branch of
`_mapNumericField` generalised to text. Until then this is a won't-implement, not a gap.
