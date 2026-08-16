# Examples

Runnable programs, one per use case in §4 of the build specification.

| File | Use case | Status |
|---|---|---|
| [`ParseSougouFurikomi.java`](ParseSougouFurikomi.java) | UC-2 — read a 総合振込 file into a downstream pipeline | ✅ |
| [`BuildSougouFurikomi.java`](BuildSougouFurikomi.java) | UC-6 — generate a test fixture for a downstream service | ✅ |
| — | UC-1 — pre-submission validation | Epic 4 |
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
./gradlew :zengin4j-testkit:jar

java -cp "zengin4j-core/build/libs/*:zengin4j-testkit/build/libs/*" \
     examples/ParseSougouFurikomi.java

java -cp "zengin4j-core/build/libs/*:zengin4j-testkit/build/libs/*" \
     examples/BuildSougouFurikomi.java
```

On Windows, use `;` instead of `:` as the class-path separator.

The examples build their own input with the testkit rather than reading a
committed file. That is deliberate: no data resembling a real payment,
account number or institution belongs in this repository (R-L1, P1).
