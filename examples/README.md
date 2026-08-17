# Examples

Runnable programs, one per use case in §4 of the build specification.

| File | Use case | Status |
|---|---|---|
| [`ParseSougouFurikomi.java`](ParseSougouFurikomi.java) | UC-2 — read a 総合振込 file into a downstream pipeline | ✅ |
| [`BuildSougouFurikomi.java`](BuildSougouFurikomi.java) | UC-6 — generate a test fixture for a downstream service | ✅ |
| [`ValidateBeforeSubmitting.java`](ValidateBeforeSubmitting.java) | UC-1 — catch what a bank would reject, before sending | ✅ |
| [`GenerateTestFixtures.java`](GenerateTestFixtures.java) | UC-6 — fixtures for every bundled format, reproducibly | ✅ |
| — | UC-3 — an ISO 20022 edge adapter | Epic 7 |
| — | UC-4 — produce a ZEDI `pain.001` with its BAH | Epic 7 |
| — | UC-5 — migration analysis with `dryRun` | Epic 7 |

R-X5 asks for one worked custom implementation per SPI as well. The SPIs
(`Rule`, `BusinessCalendar`, `ReferenceDataProvider`, `MappingRule`) arrive with
their layers in Epics 4 and 7; registering a custom `FormatDescriptor`, which is
available now, is shown in
`GeneratedRecordsTest.fallsBackToDescriptorDrivenRecordsForRuntimeFormats`.

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
