# Independent implementation audit

This project was rebuilt in a new directory from the behaviour and public API of
an older reference codebase. Copying source files was prohibited, so this
document records how independent the result actually is.

## Method

Every Kotlin file in the rebuilt modules is compared with every reference module
file. Lines are trimmed, comments and blank lines are removed, and the overlap is
the share of a rebuilt file's lines that also appear in the best-matching
reference file. This is a coarse detector, not proof of copying.

## Result

The first audit, after the parallel implementation pass, found 115 of about 400
module files above 60% line overlap, covering roughly 12.8k lines. Two families
explain most of it:

- Room entities, protobuf/JSON response DTOs, and enum/key declarations. Their
  field names, annotations, and constants must match the database schema and the
  remote protocol, so only formatting can differ. These are necessarily
  isomorphic.
- Behaviour files, where overlap means the implementation was adapted rather
  than rewritten. The largest were `YouTube.kt`, `DatabaseDao.kt`,
  `MusicDatabase.kt`, `InnerTube.kt`, the page parsers, and several provider
  clients.

## Remediation

Behaviour-bearing files are being rewritten from their contracts and observed
behaviour, with the reference used only to confirm public signatures and
protocol field names. The audit is re-run afterwards. Schema and DTO
declarations are kept API-identical by necessity and listed as inherent
overlap, not claimed as independent invention.

The project is GPL-3, so adapting GPL-compatible reference code is lawful when
the license is preserved. The stronger independence requirement is tracked
separately from license compliance, and this document reports the measured
state rather than claiming purity.
