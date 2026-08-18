# Examples

Runnable programs, one per use case in §4 of the build specification.

| File | Use case | Status |
|---|---|---|
| [`ParseSougouFurikomi.java`](ParseSougouFurikomi.java) | UC-2 — read a 総合振込 file into a downstream pipeline | ✅ |
| [`BuildSougouFurikomi.java`](BuildSougouFurikomi.java) | UC-6 — generate a test fixture for a downstream service | ✅ |
| [`ValidateBeforeSubmitting.java`](ValidateBeforeSubmitting.java) | UC-1 — catch what a bank would reject, before sending | ✅ |
| [`GenerateTestFixtures.java`](GenerateTestFixtures.java) | UC-6 — fixtures for every bundled format, reproducibly | ✅ |
| [`TransliterateNames.java`](TransliterateNames.java) | §16 — getting names from a source system into a payment file | ✅ |
| [`ConvertToIso20022.java`](ConvertToIso20022.java) | UC-3 — an ISO 20022 edge adapter, and what conversion costs | ✅ |
| [`CustomMappingRegistry.java`](CustomMappingRegistry.java) | R-X4 — using the bundled mapping with a descriptor of your own | ✅ |

`ConvertToIso20022.java` covers UC-4 (a ZEDI `pain.001` with its business
application header) and UC-5 (migration analysis with `dryRun` and `roundTrip`)
as well. They are the same program from three angles, and three programs that
converted the same file would be three places to keep in step.

R-X5 asks for one worked custom implementation per SPI as well, and that is not
complete:

| SPI | Worked example |
|---|---|
| `FormatDescriptor` (R-X1) | `GeneratedRecordsTest.fallsBackToDescriptorDrivenRecordsForRuntimeFormats` |
| `MappingRegistry` (R-X4) | [`CustomMappingRegistry.java`](CustomMappingRegistry.java) |
| `Rule` (R-X2) | **none yet** — a custom validation rule |
| `BusinessCalendar`, `ReferenceDataProvider` (R-X3) | **none yet** |

The three missing ones belong to the validation layer. An SPI with no worked
example does not get used, which is what R-X5 says and why the gap is written
down here rather than left to be noticed.

That last one is worth reading for what it says it *cannot* do. Mapping rows are
a declaration rather than an executable rule interface, so registering one makes
the mapper accept a format id it would otherwise refuse and does not change how
any field is mapped — see
[ADR-0035](../docs/adr/0035-the-mapping-is-data-not-a-rule-engine.md), which
records where that diverges from §15.3 and what it costs.

## Running them

```bash
./gradlew runExamples
```

That builds what each example needs and runs all of them, which is also what CI
does (R-DOC6).

To run one by hand, take the class path from Gradle rather than globbing
`build/libs/*` — that glob also matches the sources and javadoc jars, and a
sources jar carries a copy of every resource, sorts first, and shadows the real
one. A stale one will serve an example an old message bundle and look like a
bug in the code.

Most of what these show is also available without writing Java:

```bash
./gradlew :zengin4j-cli:shadedJar
java -jar zengin4j-cli/build/libs/zengin4j-cli-*-all.jar --help
```

See [`docs/cli.md`](../docs/cli.md).

The examples build their own input with the testkit rather than reading a
committed file. That is deliberate: no data resembling a real payment,
account number or institution belongs in this repository (R-L1, P1).
