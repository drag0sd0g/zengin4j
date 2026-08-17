# Releasing

Publishing goes through the [Release workflow](.github/workflows/release.yml)
and nowhere else.

> **A published Maven coordinate is permanent.** It cannot be withdrawn,
> corrected or replaced — only superseded by a later version that people have to
> notice and upgrade to. That is why the release path has more gates than
> anything else in this repository, and why none of them are on a laptop.

## You cannot release from a developer machine

This is enforced by the build, not by convention. The remote Maven repository is
registered only when `-PcentralPublish` is passed, and nothing outside the
release workflow passes it. Without it there is no remote repository configured,
so `./gradlew publish` has nowhere to go.

What *is* safe to run locally:

```bash
./gradlew generatePomFileForMavenPublication   # writes build/publications/, nothing else
./gradlew :zengin4j-core:cyclonedxBom          # writes build/reports/bom.json
./gradlew assemble                             # builds the jars, publishes nothing
```

## One-time setup

Before the first release, in the repository settings:

1. **Create the `maven-central` environment** (Settings → Environments) and add
   yourself as a **required reviewer**. Every release then waits for an explicit
   approval, in the GitHub UI, from you.
2. **Add these secrets to that environment**, not to the repository. Environment
   secrets are unreachable from any workflow that does not name the environment,
   so no other workflow — present or future, yours or a contributor's — can
   touch the signing key.

   | Secret | What it is |
   |---|---|
   | `SIGNING_KEY` | ASCII-armoured GPG private key (`gpg --armor --export-secret-keys KEYID`) |
   | `SIGNING_PASSWORD` | that key's passphrase |
   | `CENTRAL_USERNAME` | Central Portal token username |
   | `CENTRAL_PASSWORD` | Central Portal token password |

3. **Publish the GPG public key** to a keyserver, or Central will reject the
   signatures:
   ```bash
   gpg --keyserver keyserver.ubuntu.com --send-keys KEYID
   ```
4. **Register the namespace** `io.github.drag0sd0g` on
   [central.sonatype.com](https://central.sonatype.com). Verification is by
   GitHub account ownership — no domain required, which is why this group ID was
   chosen (R-B3).

## Releasing

1. Update `CHANGELOG.md`: move `[Unreleased]` to the version being released,
   with the date. The workflow fails if the version is not mentioned there
   (R-B7).
2. Check the version against R-B10. **Any change to parsed output for the same
   input bytes is a major bump** — a corrected byte offset changes what a file
   means, and that is not a patch.
3. Actions → **Release** → *Run workflow*. Enter the version twice, and leave
   **dry run** ticked.
4. Approve the environment when GitHub asks.
5. Read what the dry run produced: the artefact list, the SBOMs, the signatures.
   The jars are uploaded as a workflow artefact for exactly this.
6. Run it again with **dry run** unticked.
7. Complete the release in the Central Portal, and tag the commit:
   ```bash
   git tag -s v0.1.0 -m "0.1.0"
   git push origin v0.1.0
   ```

## What gets published

| Module | Published | Why |
|---|---|---|
| `zengin4j-core` | yes | the library |
| `zengin4j-testkit` | yes | consumers need it on their own test class path (R-M4) |
| `zengin4j-validation` | yes | since 0.2.0; depends only on core (R-M2) |
| `zengin4j-codegen` | no | builds the descriptors; not part of the product |
| `benchmarks` | no | a measurement harness |
| `zengin4j-iso20022`, `-cli`, `-spring-boot-starter` | no | skeletons until Epics 5–7 land |

Publishing an empty jar would claim a name on Central for ever and disappoint
anyone who downloaded it. R-B11 says completeness is not a release gate; it does
not say to reserve names.

Each published module carries a sources jar, a javadoc jar, detached GPG
signatures and a CycloneDX SBOM (R-B4, R-B6). Archives are reproducible (R-B5).

**`zengin4j-core`'s POM declares no dependencies**, and **`zengin4j-validation`'s
declares exactly one** — core. `check` fails if the first ever stops being true — the ArchUnit rule checks the code, and this checks the
metadata a consumer actually resolves.

## Versioning

Strict semantic versioning (R-B10), with one project-specific rule that is
stricter than usual:

> Any change to parsed output for the same input bytes is a **major** version
> bump.

That includes correcting a byte offset, changing a field's declared attribute,
or resolving a discrepancy in `docs/DISCREPANCIES.md` in a way that moves a
value. Those look like bug fixes and are not: downstream, a payment file means
something different afterwards.

The version is set by the workflow, not committed — `-Pzengin4j.version=`
overrides the `0.1.0-SNAPSHOT` default, so `main` never carries a release
version that has to be bumped afterwards.
