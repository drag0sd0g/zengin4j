package io.zengin4j.core.format;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.zengin4j.core.error.FormatDescriptorException;
import org.junit.jupiter.api.Test;

/// Covers loading the bundled descriptors (issues 1.2, 1.3) and the immutability
/// contract of R-T1.
class FormatRegistryTest {

    /// 総合振込, 給与振込, 賞与振込 and 預金口座振替. Grows as Epic 8 adds the 200-byte formats.
    private static final int BUNDLED_FORMATS = 4;

    @Test
    void loadsTheBundledSougouFurikomiDescriptor() {
        FormatRegistry registry = FormatRegistry.defaults();

        FormatDescriptor descriptor = registry.byId(FormatId.of("sougou-furikomi")).orElseThrow();

        assertThat(descriptor.nameJa()).isEqualTo("総合振込");
        assertThat(descriptor.nameEn()).isEqualTo("Bulk Credit Transfer");
        assertThat(descriptor.typeCode()).isEqualTo("21");
        assertThat(descriptor.recordLength()).isEqualTo(120);
        assertThat(descriptor.records().keySet())
                .containsExactlyInAnyOrder(RecordKind.HEADER, RecordKind.DATA, RecordKind.TRAILER, RecordKind.END);
    }

    /// R-0.3, R-0.2: everything shipped in 0.1.0 is still provisional and says
    /// so — but no longer for want of evidence. Each descriptor now cites the
    /// sources its offsets were checked against; what holds the flag at false
    /// is an unresolved field-attribute disagreement (D-002), which R-0.2 says
    /// keeps a format unverified until it is settled.
    @Test
    void everyBundledDescriptorIsUnverifiedButCitesItsEvidence() {
        for (FormatDescriptor descriptor : FormatRegistry.defaults().all()) {
            assertThat(descriptor.verified())
                    .as("descriptor %s is held unverified by an open discrepancy", descriptor.id())
                    .isFalse();
            assertThat(descriptor.sources())
                    .as("descriptor %s must cite what its layout was checked against", descriptor.id())
                    .hasSizeGreaterThanOrEqualTo(FormatDescriptor.REQUIRED_SOURCES_FOR_VERIFICATION);
            assertThat(descriptor.note())
                    .as("descriptor %s must say why it is still unverified", descriptor.id())
                    .isPresent();
        }
    }

    /// R-F2: offsets are computed, and they must match the published layout.
    @Test
    void computesTheDataRecordOffsetsFromCumulativeLengths() {
        RecordDescriptor data = FormatRegistry.defaults()
                .byId(FormatId.of("sougou-furikomi")).orElseThrow()
                .record(RecordKind.DATA);

        assertThat(data.field("dataKubun").offset()).isZero();
        assertThat(data.field("beneficiaryBankCode").offset()).isEqualTo(1);
        assertThat(data.field("beneficiaryBankName").offset()).isEqualTo(5);
        assertThat(data.field("beneficiaryBranchCode").offset()).isEqualTo(20);
        assertThat(data.field("beneficiaryBranchName").offset()).isEqualTo(23);
        assertThat(data.field("clearingHouseCode").offset()).isEqualTo(38);
        assertThat(data.field("accountType").offset()).isEqualTo(42);
        assertThat(data.field("accountNumber").offset()).isEqualTo(43);
        assertThat(data.field("beneficiaryName").offset()).isEqualTo(50);
        assertThat(data.field("amount").offset()).isEqualTo(80);
        assertThat(data.field("newCode").offset()).isEqualTo(90);
        assertThat(data.field("customerCode1").offset()).isEqualTo(91);
        assertThat(data.field("customerCode2").offset()).isEqualTo(101);
        assertThat(data.field("transferCategory").offset()).isEqualTo(111);
        assertThat(data.field("identification").offset()).isEqualTo(112);
        assertThat(data.field("dummy").offset()).isEqualTo(113);
        assertThat(data.field("dummy").endOffset()).isEqualTo(120);
    }

    @Test
    void computesTheHeaderRecordOffsetsFromCumulativeLengths() {
        RecordDescriptor header = FormatRegistry.defaults()
                .byId(FormatId.of("sougou-furikomi")).orElseThrow()
                .record(RecordKind.HEADER);

        assertThat(header.field("dataKubun").offset()).isZero();
        assertThat(header.field("typeCode").offset()).isEqualTo(1);
        assertThat(header.field("codeKubun").offset()).isEqualTo(3);
        assertThat(header.field("originatorCode").offset()).isEqualTo(4);
        assertThat(header.field("originatorName").offset()).isEqualTo(14);
        assertThat(header.field("valueDate").offset()).isEqualTo(54);
        assertThat(header.field("originBankCode").offset()).isEqualTo(58);
        assertThat(header.field("originBankName").offset()).isEqualTo(62);
        assertThat(header.field("originBranchCode").offset()).isEqualTo(77);
        assertThat(header.field("originBranchName").offset()).isEqualTo(80);
        assertThat(header.field("accountType").offset()).isEqualTo(95);
        assertThat(header.field("accountNumber").offset()).isEqualTo(96);
        assertThat(header.field("dummy").offset()).isEqualTo(103);
        assertThat(header.field("dummy").endOffset()).isEqualTo(120);
    }

    @Test
    void resolvesCodeListReferences() {
        FormatDescriptor descriptor = FormatRegistry.defaults()
                .byId(FormatId.of("sougou-furikomi")).orElseThrow();

        CodeList accountType = descriptor.record(RecordKind.DATA).field("accountType").codeList().orElseThrow();

        assertThat(accountType.id()).isEqualTo("accountType");
        assertThat(accountType.byCode("1").orElseThrow().nameEn()).isEqualTo("Ordinary deposit");
        assertThat(accountType.accepts("7")).as("open lists admit unknown values").isTrue();

        // 4:貯蓄預金 was flagged unconfirmed in the source specification and is
        // now corroborated by the JBA standard and two institution guides.
        assertThat(accountType.verified()).isTrue();
        assertThat(accountType.sources())
                .hasSizeGreaterThanOrEqualTo(FormatDescriptor.REQUIRED_SOURCES_FOR_VERIFICATION);
        assertThat(accountType.byCode("4").orElseThrow().nameJa()).isEqualTo("貯蓄預金");
        assertThat(accountType.byCode("4").orElseThrow().verified()).isTrue();
    }

    @Test
    void findsRecordsByDiscriminator() {
        FormatDescriptor descriptor = FormatRegistry.defaults()
                .byId(FormatId.of("sougou-furikomi")).orElseThrow();

        assertThat(descriptor.forDiscriminator((byte) '1').orElseThrow().kind()).isEqualTo(RecordKind.HEADER);
        assertThat(descriptor.forDiscriminator((byte) '2').orElseThrow().kind()).isEqualTo(RecordKind.DATA);
        assertThat(descriptor.forDiscriminator((byte) '8').orElseThrow().kind()).isEqualTo(RecordKind.TRAILER);
        assertThat(descriptor.forDiscriminator((byte) '9').orElseThrow().kind()).isEqualTo(RecordKind.END);
        assertThat(descriptor.forDiscriminator((byte) '5')).isEmpty();
    }

    @Test
    void looksFormatsUpByTypeCode() {
        FormatRegistry registry = FormatRegistry.defaults();

        assertThat(registry.byTypeCode("21")).hasSize(1);
        assertThat(registry.byTypeCode("99")).isEmpty();
        assertThat(registry.describeTypeCodes()).contains("21 (sougou-furikomi)");
    }

    /// R-T1: adding a format yields a new registry rather than mutating one.
    @Test
    void withFormatDoesNotMutateTheReceiver() {
        FormatRegistry original = FormatRegistry.defaults();
        FormatDescriptor copy = renamed(original.byId(FormatId.of("sougou-furikomi")).orElseThrow(), "variant-a");

        FormatRegistry extended = original.withFormat(copy);

        assertThat(original.all()).hasSize(BUNDLED_FORMATS);
        assertThat(extended.all()).hasSize(BUNDLED_FORMATS + 1);
        assertThat(original.byId(FormatId.of("variant-a"))).isEmpty();
        assertThat(extended.byId(FormatId.of("variant-a"))).isPresent();
    }

    @Test
    void rejectsDuplicateFormatIds() {
        FormatRegistry registry = FormatRegistry.defaults();
        FormatDescriptor existing = registry.byId(FormatId.of("sougou-furikomi")).orElseThrow();

        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> registry.withFormat(existing))
                .withMessageContaining("already registered");
    }

    @Test
    void exposesTheSharedCodeLists() {
        Map<String, CodeList> lists = FormatRegistry.defaults().codeLists();

        assertThat(lists.keySet())
                .containsExactly("dataKubun", "typeCode", "codeKubun", "accountType", "newCode",
                        "transferCategory", "transferResult");
        assertThat(lists.values())
                .allSatisfy(list -> assertThat(list.open())
                        .as("code list %s must stay open: verification confirms the listed values,"
                                + " not the absence of others", list.id())
                        .isTrue());
    }

    /// R-0.1 applies to code lists too, and the loader enforces it.
    @Test
    void everyVerifiedCodeListCitesAtLeastTwoSources() {
        for (CodeList list : FormatRegistry.defaults().codeLists().values()) {
            if (list.verified()) {
                assertThat(list.sources())
                        .as("code list %s claims verification", list.id())
                        .hasSizeGreaterThanOrEqualTo(FormatDescriptor.REQUIRED_SOURCES_FOR_VERIFICATION);
            }
        }
    }

    /// 種別コード 91 is used by both the 預金口座振替 instruction file and its
    /// result file. The registry surfaces that rather than resolving it.
    @Test
    void recordsThatOneBusinessTypeCodeCoversTwoFormats() {
        CodeList typeCode = FormatRegistry.defaults().codeLists().get("typeCode");

        assertThat(typeCode.byCode("91").orElseThrow().note().orElseThrow())
                .contains("both the instruction file and the result file");
    }

    private static FormatDescriptor renamed(FormatDescriptor descriptor, String newId) {
        FormatId id = FormatId.of(newId);
        Map<RecordKind, RecordDescriptor> records = new java.util.EnumMap<>(RecordKind.class);
        descriptor.records().forEach((kind, record) -> records.put(kind,
                new RecordDescriptor(id, record.kind(), record.discriminator(), record.recordLength(),
                        record.fields())));
        return new FormatDescriptor(id, descriptor.nameJa(), descriptor.nameEn(), descriptor.typeCode(),
                descriptor.recordLength(), descriptor.verified(), List.of(), descriptor.note(), records);
    }
}
