# Benchmarks

Measurement harness for R-P1 through R-P4.

> **No number here is a promise.** Every figure states the machine, JDK and JVM
> flags it was produced on (P9). A throughput number without its conditions is
> not a measurement, and this project does not publish one.

## Running them

```bash
./gradlew :benchmarks:jmh                                  # every benchmark
./gradlew :benchmarks:jmh -PjmhArgs="Streaming"            # one, by regex
./gradlew :benchmarks:jmh -PjmhArgs="-rf json -rff $PWD/benchmarks/results/latest.json"

./gradlew :benchmarks:constantMemory                       # R-P2, ~10 seconds
./gradlew :benchmarks:constantMemory -Pbytes=$((100 * 1024 * 1024))   # smaller
```

Benchmarks are never part of `check`. They are slow, they are sensitive to what
else the machine is doing, and a build gate that fails because a laptop was
compiling something else teaches people to ignore it.

## What is measured

`ReadBenchmark` reports three numbers because "how fast does it parse" has three
answers, and quoting the wrong one misleads in a predictable direction:

| Benchmark | What it covers | Use it for |
|---|---|---|
| `streamingSkippingFields` | framing only — find every record, decode nothing | the floor: what the reader costs before any field is read |
| `streamingDecodingAmounts` | framing plus one numeric decode per data record | the realistic streaming case |
| `wholeFileMaterialised` | everything materialised into immutable records | the convenient API, and necessarily the slowest |

Throughput is reported in operations per second over a whole file, so
**MB/s = ops/s × file size ÷ 1024²**. Two file sizes are measured: about 1 MB,
which fits in cache, and about 10 MB, which does not. Production files are not
cache-resident, so the larger figure is the one to quote.

`ConstantMemoryCheck` is not a benchmark. It streams a 1 GB file under a 64 MB
heap and either finishes or does not — the constrained heap is the assertion,
not a number to interpret. It prints a throughput figure, and that figure is
explicitly *not* for publication: it is measured alongside file generation and
under a deliberately small heap.

## Recorded results

Copy this template for each recorded run. Do not overwrite an old run with a new
one on different hardware — add a row, so a change in the number can be told
apart from a change in the machine.

### 2026-08-16 — Apple M5 Max, JDK 25.0.4

| | |
|---|---|
| CPU | Apple M5 Max, 18 cores (18 physical) |
| Memory | 128 GB |
| OS | macOS 26.6.1 (build 25G76) |
| JDK | Temurin 25.0.4+7 LTS, 64-Bit Server VM |
| JVM flags | `-Xms1g -Xmx1g -XX:+AlwaysPreTouch` |
| JMH | 1.37, 2 forks, 3 × 2s warmup, 5 × 2s measurement |
| Other load | interactive session; not an isolated machine |

| Benchmark | File | ops/s | ± (99.9%) | **MB/s** |
|---|---:|---:|---:|---:|
| `streamingSkippingFields` | 0.93 MB | 5037.1 | 87.8 | 4690 |
| `streamingSkippingFields` | 9.31 MB | 499.7 | 5.4 | **4652** |
| `streamingDecodingAmounts` | 0.93 MB | 3230.1 | 87.5 | 3008 |
| `streamingDecodingAmounts` | 9.31 MB | 346.7 | 2.7 | **3227** |
| `wholeFileMaterialised` | 0.93 MB | 467.9 | 2.3 | 436 |
| `wholeFileMaterialised` | 9.31 MB | 44.9 | 2.3 | **418** |

**R-P1 requires ≥ 50 MB/s single-threaded.** The realistic streaming case is
3,227 MB/s and the slowest mode — everything materialised into immutable
records — is 418 MB/s. The requirement is met with roughly eight times the
margin even in the mode nobody should quote.

Two things this measurement does **not** cover, and which matter more than the
numbers above on most real workloads:

- **No I/O.** Input is a `ByteArrayInputStream`, so these figures are the
  parser's cost with the file already in memory. Reading from a disk or a
  network share will be bounded by that, not by this.
- **Fast hardware, quiet-ish machine.** An M5 Max on an interactive session.
  Treat these as an upper bound, and re-measure on your own hardware before
  designing around a number.

Raw results are in [`results/latest.json`](results/latest.json), committed so a
later run can be compared against this one rather than against a recollection of
it.

**R-P2:** 1 GB streamed under `-Xmx64m` — 8,802,795 records read, 9 MB of heap
in use at the end. The CI job `constant-memory` runs this on every push.

## Interpreting a regression

A benchmark that moves by less than about 10% between runs on the same machine
has probably not moved. JIT compilation, allocation timing and OS scheduling all
contribute more than that. What is worth investigating:

- a change of more than about 20% on the same machine and JDK;
- `streamingSkippingFields` moving at all — the framing path does almost
  nothing, so it should be stable and any change is real;
- heap in use in `ConstantMemoryCheck` growing with file size, which would mean
  R-P2 is broken regardless of what the throughput says.
