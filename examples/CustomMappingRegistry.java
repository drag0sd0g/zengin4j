import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.iso20022.api.Iso20022Mapper;
import io.zengin4j.iso20022.api.MappingContext;
import io.zengin4j.iso20022.envelope.MessageId;
import io.zengin4j.iso20022.envelope.ZediFile;
import io.zengin4j.iso20022.mapping.MappingRegistry;
import io.zengin4j.iso20022.mapping.MappingRow;
import io.zengin4j.iso20022.mapping.UnsupportedMappingException;
import io.zengin4j.testkit.FormatFixtures;

/// R-X4, R-X5 — using the bundled mapping with a descriptor of your own.
///
/// Run it with:
///
/// ```
/// ./gradlew runExamples
/// ```
///
/// An institution that publishes its own 総合振込 variant gets its own
/// `FormatId` (R-X1). The bundled mapping is keyed on
/// `sougou-furikomi`, so converting that variant fails until the rows are
/// registered under the new id — which is what this shows.
///
/// **Rows are a declaration, not a rule engine.** Registering
/// them makes the mapper accept a format id it would otherwise refuse. It does
/// not change how any field is mapped: editing a row's ISO path changes what the
/// mapping *claims* and not what it *does*. See
/// `docs/adr/0035-the-mapping-is-data-not-a-rule-engine.md`, and use
/// `MappingContext.endToEndPolicy` to redirect where `EndToEndId`
/// lands, which the mapper does act on.
///
/// **Every identifier here is invented** (R-L1).
private static final LocalDate REFERENCE = LocalDate.of(2026, 9, 1);

/// What an institution's own variant of the standard layout might be called.
private static final FormatId HOUSE_VARIANT = FormatId.of("sougou-furikomi-house");

void main() {
    FormatDescriptor variant = aVariantOfTheStandardLayout();

    withoutARegisteredMapping(variant);
    withOne(variant);
    whatItDoesNotDo();
}

/// A descriptor with its own id and the standard layout.
///
/// Real variants differ in a filler field or a narrowed code list. What
/// matters for the mapping is that the field *ids* are the standard
/// ones, because that is what the mapper reads fields by.
///
/// The records are rebuilt rather than borrowed: a `RecordDescriptor`
/// carries the id of the format it belongs to, and the descriptor refuses one
/// that names a different format. That check is why a variant cannot
/// accidentally share a record with the layout it was copied from.
private static FormatDescriptor aVariantOfTheStandardLayout() {
    FormatDescriptor standard = FormatRegistry.defaults()
            .byId(FormatId.of("sougou-furikomi")).orElseThrow();

    Map<RecordKind, RecordDescriptor> records = new LinkedHashMap<>();
    standard.records().forEach((kind, record) -> records.put(kind,
            new RecordDescriptor(HOUSE_VARIANT, record.kind(), record.discriminator(),
                    record.recordLength(), record.fields())));

    return new FormatDescriptor(HOUSE_VARIANT, standard.nameJa(), standard.nameEn(),
            standard.typeCode(), standard.recordLength(), standard.verified(),
            standard.sources(), standard.note(), records);
}

/// The unverified-format warning is silenced here; the other examples show it.
private static ZenginFile aFileInThatVariant(FormatDescriptor variant) {
    FormatFixtures fixtures = FormatFixtures.forFormat(FormatId.of("sougou-furikomi"));
    return ZenginReaders.readFile(
            new ByteArrayInputStream(fixtures.file(2, SeparatorStyle.CRLF, false)),
            io.zengin4j.core.codec.ReaderOptions.builder()
                    .registry(FormatRegistry.defaults().withFormat(variant))
                    .format(HOUSE_VARIANT)
                    .allowUnverifiedFormats(true)
                    .warningListener(warning -> { })
                    .build());
}

// ------------------------------------------------------------- before

private static void withoutARegisteredMapping(FormatDescriptor variant) {
    System.out.println("== a variant with no mapping registered ==");

    MappingContext context = MappingContext.builder("9900000001", REFERENCE)
            .targetFormat(variant)
            .acceptAnyLoss()
            .build();

    try {
        Iso20022Mapper.create().toIso(aFileInThatVariant(variant), context);
        System.out.println("  (converted, which was not expected)");
    } catch (UnsupportedMappingException refused) {
        System.out.println("  refused, and said what is available:");
        System.out.println("    " + refused.getMessage());
    }
    System.out.println();
}

// -------------------------------------------------------------- after

private static void withOne(FormatDescriptor variant) {
    System.out.println("== the bundled rows, registered under the variant's id ==");

    List<MappingRow> bundled = MappingRegistry.defaults()
            .requireRowsFor(FormatId.of("sougou-furikomi"), MessageId.PAIN_001_001_03);

    MappingRegistry registry = MappingRegistry.defaults()
            .withMapping(HOUSE_VARIANT, MessageId.PAIN_001_001_03, bundled);

    System.out.println("  registry now covers: " + registry.supported());

    MappingContext context = MappingContext.builder("9900000001", REFERENCE)
            .targetFormat(variant)
            .acceptAnyLoss()
            .build();

    ZediFile converted = Iso20022Mapper.using(registry)
            .toIso(aFileInThatVariant(variant), context)
            .output();

    System.out.println("  converted: " + converted.onlyMessage().messageId().orElseThrow()
            + ", " + converted.onlyMessage().body()
                    .at("CstmrCdtTrfInitn/GrpHdr/NbOfTxs").orElseThrow().text()
            + " payments");
    System.out.println();
}

// ------------------------------------------------------- the honest part

private static void whatItDoesNotDo() {
    System.out.println("== what registering a row does not do ==");
    System.out.println("  A row is a declaration: it says what the mapper does, drives");
    System.out.println("  docs/mapping.md, and is held to the code by tests. It is not");
    System.out.println("  interpreted at runtime, so changing a row's ISO path changes what");
    System.out.println("  the mapping claims and not what it does (ADR-0035).");
    System.out.println();
    System.out.println("  To redirect where EndToEndId lands, use the policy the mapper");
    System.out.println("  actually reads:");
    System.out.println("    MappingContext.builder(...).endToEndPolicy(CUSTOMER_CODE_2)");
}
