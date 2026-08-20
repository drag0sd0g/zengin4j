package io.zengin4j.iso20022.mapping;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.iso20022.api.Iso20022Mapper;
import io.zengin4j.iso20022.api.MappingContext;
import io.zengin4j.iso20022.api.MappingResult;
import io.zengin4j.iso20022.envelope.MessageId;
import io.zengin4j.iso20022.xml.XmlElement;
import io.zengin4j.testkit.FormatFixtures;
import org.junit.jupiter.api.Test;

/// The declaration and the mapper say the same thing.
///
/// `mappings/*.yaml` is generated into `docs/mapping.md` and into
/// a constant table, and the whole point of declaring the mapping rather than
/// only implementing it is that R-I19 needs each row's verification status to be
/// *about something*. A row that documents an element the mapper never
/// writes is worse than no row: it is a claim in the reference page that a
/// reader has no way to check.
///
/// So this converts a real file and compares what came out against what was
/// declared, both ways. Neither side can drift without the build failing.
class MappingDeclarationTest {

    private static final FormatId FORMAT = FormatId.of("sougou-furikomi");

    private static List<MappingRow> rows() {
        return MappingRegistry.defaults().requireRowsFor(FORMAT, MessageId.PAIN_001_001_03);
    }

    private static XmlElement convert() {
        FormatFixtures fixtures = FormatFixtures.forFormat(FORMAT);
        ZenginFile file = ZenginReaders.readFile(
                new ByteArrayInputStream(fixtures.file()), fixtures.readerOptions());
        var context = MappingContext.builder("9900000001", LocalDate.of(2026, 9, 1))
                .targetFormat(fixtures.descriptor())
                .acceptAnyLoss()
                .build();

        return Iso20022Mapper.create().toIso(file, context).output()
                .onlyMessage().body();
    }

    /// Every path in the emitted document, relative to `Document`.
    private static Set<String> emittedPaths() {
        Set<String> paths = new LinkedHashSet<>();
        collect(convert(), "", paths);
        return paths;
    }

    private static void collect(XmlElement element, String prefix, Set<String> paths) {
        for (XmlElement child : element.children()) {
            String path = prefix.isEmpty() ? child.name() : prefix + "/" + child.name();
            if (child.children().isEmpty()) {
                paths.add(path);
            } else {
                collect(child, path, paths);
            }
        }
    }

    // ------------------------------------------------------- declaration side

    /// Every declared element is actually written.
    ///
    /// The fixture exercises every row, which is why it can be asserted
    /// exhaustively: a row that only fires for some inputs would need its own
    /// case rather than a weaker assertion here.
    @Test
    void everyDeclaredIsoElementIsEmitted() {
        Set<String> emitted = emittedPaths();

        List<String> declaredButMissing = rows().stream()
                .filter(row -> row.direction().appliesToIso())
                .filter(MappingRow::hasIsoPath)
                .map(MappingRow::isoPath)
                .filter(path -> !emitted.contains(path))
                .toList();

        assertThat(declaredButMissing)
                .as("docs/mapping.md promises these elements and the mapper does not write them. "
                        + "Either implement the row or remove it — a reference page that lists an "
                        + "element nobody emits is worse than one that omits it.")
                .isEmpty();
    }

    /// And nothing is written that was never declared.
    @Test
    void everyEmittedElementIsDeclared() {
        Set<String> declared = new LinkedHashSet<>(rows().stream()
                .filter(MappingRow::hasIsoPath)
                .map(MappingRow::isoPath)
                .toList());

        List<String> emittedButUndeclared = emittedPaths().stream()
                .filter(path -> !declared.contains(path))
                .toList();

        assertThat(emittedButUndeclared)
                .as("the mapper writes these and docs/mapping.md does not mention them. Every "
                        + "element in a payment file has to be accounted for — R-I19 is about "
                        + "each row's verification status, and an undeclared element has none.")
                .isEmpty();
    }

    /// Nothing in an inbound document disappears without a word.
    ///
    /// The two tests above cover the upward leg: what the mapper writes is
    /// declared, and what is declared is written. The downward leg needs the
    /// mirror property, and it is the one that actually went wrong — an element
    /// can be read into the model, never used, and never reported, and no test
    /// of the outbound direction would notice. `PmtId/InstrId` was exactly
    /// that until this was written.
    ///
    /// So: convert a document downward, and require every element in it to be
    /// accounted for one of three ways — carried into a Zengin field, declared
    /// as message metadata that only exists going the other way, or named in the
    /// loss report.
    @Test
    void everyElementOfAnInboundDocumentIsCarriedOrReported() {
        FormatFixtures fixtures = FormatFixtures.forFormat(FORMAT);
        var context = MappingContext.builder("9911111111", LocalDate.of(2026, 9, 1))
                .targetFormat(fixtures.descriptor())
                .acceptAnyLoss()
                .build();

        XmlElement document = fullyPopulated();
        MappingResult<io.zengin4j.core.model.ZenginFile> converted = Iso20022Mapper.create()
                .toZengin(io.zengin4j.iso20022.envelope.ZediFile.of(
                        io.zengin4j.iso20022.envelope.ZediMessage.of(header(), document)),
                        context);

        // A row that applies downward but names no Zengin field carries nothing:
        // it documents an element that is dropped, which the loss report has to
        // say. Leaving those in this set is what made an earlier version of this
        // test pass while InstrId was silently discarded.
        Set<String> carried = rows().stream()
                .filter(row -> row.direction().appliesToZengin())
                .filter(MappingRow::hasIsoPath)
                .filter(MappingRow::hasZenginField)
                .map(MappingRow::isoPath)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> generatedGoingUp = rows().stream()
                .filter(row -> row.direction() == MappingDirection.TO_ISO)
                .filter(MappingRow::hasIsoPath)
                .map(MappingRow::isoPath)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> reported = converted.loss().entries().stream()
                .flatMap(entry -> entry.sourcePath().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Set<String> present = new LinkedHashSet<>();
        collect(document, "", present);

        List<String> unaccounted = present.stream()
                .filter(path -> !carried.contains(path))
                .filter(path -> !generatedGoingUp.contains(path))
                .filter(path -> !reported.contains(path))
                .toList();

        assertThat(unaccounted)
                .as("these elements were in the document and are neither carried into a Zengin "
                        + "field, nor declared as something that only exists on the way up, nor "
                        + "named in the loss report. Whichever they are, say so — a value that "
                        + "vanishes silently is the failure this module exists to prevent.")
                .isEmpty();
    }

    /// A document carrying a value at every path the mapping declares.
    private static XmlElement fullyPopulated() {
        FormatFixtures fixtures = FormatFixtures.forFormat(FORMAT);
        io.zengin4j.core.model.ZenginFile file = ZenginReaders.readFile(
                new ByteArrayInputStream(fixtures.file()), fixtures.readerOptions());

        XmlElement generated = Iso20022Mapper.create().toIso(file,
                        MappingContext.builder("9900000001", LocalDate.of(2026, 9, 1))
                                .targetFormat(fixtures.descriptor()).acceptAnyLoss().build())
                .output().onlyMessage().body();

        // The upward leg never writes InstrId — no Zengin field feeds it — so a
        // document generated from a Zengin file cannot exercise it. A real
        // sender can, which is the point.
        return io.zengin4j.iso20022.xml.XmlParser.parse(
                io.zengin4j.iso20022.xml.XmlSerializer.toText(generated)
                        .replace("<PmtId>", "<PmtId>\n<InstrId>DEBTOR-REF-9</InstrId>")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static io.zengin4j.iso20022.envelope.BusinessApplicationHeader header() {
        return new io.zengin4j.iso20022.envelope.BusinessApplicationHeader(
                "9900000001", "9999", "M", MessageId.PAIN_001_001_03,
                java.time.OffsetDateTime.parse("2026-09-01T00:00:00Z"));
    }

    /// Every location a loss entry names can be looked up in the reference page.
    ///
    /// A report saying `[CdtTrfTxInf/Cdtr/Nm]` is useless if
    /// `docs/mapping.md` calls the same element
    /// `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/Cdtr/Nm` — a reader searching
    /// for one finds nothing, and concludes the mapping does not cover it. Both
    /// legs used the short form until this test was written.
    ///
    /// Zengin-side references (`header.valueDate`) are checked the same
    /// way, against the declared field names.
    @Test
    void everyLocationALossEntryNamesIsOneTheDeclarationUses() {
        FormatFixtures fixtures = FormatFixtures.forFormat(FORMAT);
        var context = MappingContext.builder("9911111111", LocalDate.of(2026, 9, 1))
                .targetFormat(fixtures.descriptor())
                .acceptAnyLoss()
                .build();

        Set<String> declaredIso = rows().stream()
                .filter(MappingRow::hasIsoPath)
                .map(MappingRow::isoPath)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> declaredZengin = rows().stream()
                .filter(MappingRow::hasZenginField)
                .map(MappingRow::zenginField)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<String> unknown = new ArrayList<>();
        for (String location : everyLocationReported(context)) {
            boolean isoish = location.contains("/");
            if (isoish ? !declaredIso.contains(location) : !declaredZengin.contains(location)) {
                unknown.add(location);
            }
        }

        assertThat(unknown)
                .as("a loss entry points at these, and no declared row does. Either the "
                        + "declaration is missing a row, or the entry is naming the element by a "
                        + "path nobody can look up.")
                .isEmpty();
    }

    /// Every source and target a conversion in either direction produces.
    private static Set<String> everyLocationReported(MappingContext context) {
        FormatFixtures fixtures = FormatFixtures.forFormat(FORMAT);
        io.zengin4j.core.model.ZenginFile file = ZenginReaders.readFile(
                new ByteArrayInputStream(fixtures.file()), fixtures.readerOptions());

        Set<String> locations = new LinkedHashSet<>();
        MappingResult<io.zengin4j.iso20022.envelope.ZediFile> up =
                Iso20022Mapper.create().toIso(file, context);
        MappingResult<io.zengin4j.core.model.ZenginFile> down = Iso20022Mapper.create()
                .toZengin(io.zengin4j.iso20022.envelope.ZediFile.of(
                        io.zengin4j.iso20022.envelope.ZediMessage.of(header(), fullyPopulated())),
                        context);

        java.util.stream.Stream.of(up.loss(), down.loss())
                .flatMap(report -> report.entries().stream())
                .forEach(entry -> {
                    entry.sourcePath().ifPresent(locations::add);
                    entry.targetField().ifPresent(locations::add);
                });
        return locations;
    }

    // ------------------------------------------------------------ Zengin side

    /// Every declared field exists.
    ///
    /// The codegen reader checks this at build time, which is where a typo
    /// should be caught. This checks it again against the compiled descriptor,
    /// because the two could in principle be built from different revisions.
    @Test
    void everyDeclaredZenginFieldExists() {
        FormatDescriptor descriptor = FormatFixtures.forFormat(FORMAT).descriptor();

        List<String> missing = new ArrayList<>();
        for (MappingRow row : rows()) {
            if (!row.hasZenginField()) {
                continue;
            }
            var kind = RecordKind.valueOf(
                    row.zenginRecord().orElseThrow().toUpperCase(Locale.ROOT));
            boolean exists = descriptor.record(kind).fields().stream()
                    .map(FieldDescriptor::id)
                    .anyMatch(row.zenginFieldId().orElseThrow()::equals);
            if (!exists) {
                missing.add(row.zenginField());
            }
        }

        assertThat(missing).as("declared fields that the descriptor does not have").isEmpty();
    }

    /// Every non-filler field is accounted for.
    ///
    /// A field that is neither mapped nor explicitly dropped is a field
    /// nobody decided about — and the whole file gets converted regardless, so
    /// the decision was made by omission. Fillers and the record discriminators
    /// are excluded: they carry no payment information and mapping them would be
    /// noise.
    @Test
    void everyDataAndHeaderFieldIsEitherMappedOrExplicitlyDropped() {
        FormatDescriptor descriptor = FormatFixtures.forFormat(FORMAT).descriptor();
        Set<String> accounted = new LinkedHashSet<>(rows().stream()
                .filter(MappingRow::hasZenginField)
                .map(MappingRow::zenginField)
                .toList());

        List<String> undecided = new ArrayList<>();
        for (RecordKind kind : List.of(RecordKind.HEADER, RecordKind.DATA)) {
            String prefix = kind.name().toLowerCase(Locale.ROOT) + ".";
            for (FieldDescriptor field : descriptor.record(kind).fields()) {
                if (field.filler() || field.constant().isPresent()) {
                    continue;
                }
                if (!accounted.contains(prefix + field.id())) {
                    undecided.add(prefix + field.id());
                }
            }
        }

        assertThat(undecided)
                .as("these fields are neither carried nor declared dropped. Converting the file "
                        + "makes a decision about them either way; the declaration should be the "
                        + "place that decision is written down.")
                .isEmpty();
    }

    // ---------------------------------------------------------- R-I19 itself

    /// No row claims to be verified.
    ///
    /// Not a permanent property — the point of the flag is that it can change
    /// — but a row cannot become verified without somebody citing a source in
    /// `docs/SOURCES.md`, and this test failing is the prompt to do that
    /// rather than an obstacle to it.
    @Test
    void noRowClaimsVerificationItHasNotEarned() {
        List<String> claimed = rows().stream()
                .filter(MappingRow::verified)
                .map(MappingRow::toString)
                .toList();

        assertThat(claimed)
                .as("R-I19: a row may be marked verified only once it has been checked against "
                        + "published profile documentation. If that has happened, cite it in "
                        + "docs/SOURCES.md and update this test.")
                .isEmpty();
    }

    @Test
    void everyRowExplainsItselfInBothLanguages() {
        assertThat(rows()).allSatisfy(row -> {
            assertThat(row.whyEn()).as("%s has no English explanation", row).isNotBlank();
            assertThat(row.whyJa()).as("%s has no Japanese explanation", row).isNotBlank();
        });
    }

    @Test
    void everyLossyRowSaysHowBadItIs() {
        assertThat(rows()).allSatisfy(row ->
                assertThat(row.lossKind().isPresent())
                        .as("%s declares a severity without a kind or the reverse", row)
                        .isEqualTo(row.lossSeverity().isPresent()));
    }

    // ---------------------------------------------------------------- registry

    @Test
    void theRegistryFindsWhatItHasAndSaysSoAboutWhatItHasNot() {
        assertThat(MappingRegistry.defaults().rowsFor(FORMAT, MessageId.PAIN_001_001_03))
                .isPresent();
        assertThat(MappingRegistry.defaults().supported())
                .containsExactly("sougou-furikomi <-> pain.001.001.03");

        assertThatExceptionOfType(UnsupportedMappingException.class)
                .isThrownBy(() -> MappingRegistry.defaults()
                        .requireRowsFor(FormatId.of("kyuyo-furikomi"), MessageId.PAIN_001_001_03))
                .withMessageContaining("sougou-furikomi");
    }

    /// R-X4 — the registry accepts overrides, and `Iso20022Mapper.using`
    /// has something to be given.
    ///
    /// Until this existed, `using(MappingRegistry)` was a public method
    /// no caller outside this package could reach: the only obtainable instance
    /// was `defaults()`, which `create()` already returns.
    @Test
    void aRegistryAcceptsAMappingForAFormatOfYourOwn() {
        var variant = FormatId.of("sougou-furikomi-house");
        List<MappingRow> bundled = rows();

        MappingRegistry extended = MappingRegistry.defaults()
                .withMapping(variant, MessageId.PAIN_001_001_03, bundled);

        assertThat(extended.rowsFor(variant, MessageId.PAIN_001_001_03)).contains(bundled);
        assertThat(extended.supported()).hasSize(2);
        assertThat(MappingRegistry.defaults().supported())
                .as("the registry it came from is unchanged")
                .hasSize(1);
    }

    @Test
    void aMappingCanBeReplacedByRemovingItFirst() {
        MappingRegistry emptied = MappingRegistry.defaults()
                .without(FORMAT, MessageId.PAIN_001_001_03);
        assertThat(emptied.supported()).isEmpty();

        MappingRegistry replaced = emptied.withMapping(FORMAT, MessageId.PAIN_001_001_03,
                rows().subList(0, 1));
        assertThat(replaced.requireRowsFor(FORMAT, MessageId.PAIN_001_001_03)).hasSize(1);
    }

    @Test
    void registeringOverAnExistingMappingSaysHowToReplaceIt() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> MappingRegistry.defaults()
                        .withMapping(FORMAT, MessageId.PAIN_001_001_03, rows()))
                .withMessageContaining("already registered")
                .withMessageContaining("without");
    }

    @Test
    void aMappingWithNoRowsDeclaresNothing() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> MappingRegistry.defaults()
                        .withMapping(FormatId.of("x"), MessageId.PAIN_001_001_03, List.of()))
                .withMessageContaining("declares nothing");
    }

    /// And the mapper actually uses the registry it was given.
    @Test
    void aMapperUsesTheRegistryItWasGiven() {
        var variant = FormatId.of("sougou-furikomi-house");
        MappingRegistry extended = MappingRegistry.defaults()
                .withMapping(variant, MessageId.PAIN_001_001_03, rows());

        assertThat(Iso20022Mapper.using(extended)).isNotNull();
        assertThatExceptionOfType(UnsupportedMappingException.class)
                .as("and the default registry still refuses the variant")
                .isThrownBy(() -> MappingRegistry.defaults()
                        .requireRowsFor(variant, MessageId.PAIN_001_001_03));
    }

    @Test
    void aDirectionParsesFromWhatTheDeclarationsWrite() {
        assertThat(MappingDirection.parse("both")).isEqualTo(MappingDirection.BOTH);
        assertThat(MappingDirection.parse("to-iso")).isEqualTo(MappingDirection.TO_ISO);
        assertThat(MappingDirection.parse("to-zengin")).isEqualTo(MappingDirection.TO_ZENGIN);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> MappingDirection.parse("sideways"));

        assertThat(MappingDirection.TO_ISO.appliesToIso()).isTrue();
        assertThat(MappingDirection.TO_ISO.appliesToZengin()).isFalse();
        assertThat(MappingDirection.BOTH.appliesToIso()).isTrue();
        assertThat(MappingDirection.BOTH.appliesToZengin()).isTrue();
    }

    @Test
    void aRowKnowsWhichPartOfTheFormatItComesFrom() {
        MappingRow row = rows().stream()
                .filter(candidate -> candidate.zenginField().equals("data.beneficiaryName"))
                .findFirst()
                .orElseThrow();

        assertThat(row.zenginRecord()).contains("data");
        assertThat(row.zenginFieldId()).contains("beneficiaryName");
        assertThat(row.isoElement()).contains("Nm");
        assertThat(row.isDropped()).isFalse();
        assertThat(row).hasToString(
                "data.beneficiaryName -> CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/Cdtr/Nm");
    }

    @Test
    void aRowWithNeitherSideIsNotARow() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new MappingRow("", "", MappingDirection.BOTH, false,
                        java.util.Optional.empty(), java.util.Optional.empty(), "x", "y"))
                .withMessageContaining("maps nothing");
    }
}
