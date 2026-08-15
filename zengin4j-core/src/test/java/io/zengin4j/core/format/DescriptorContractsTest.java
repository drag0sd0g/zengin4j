package io.zengin4j.core.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.zengin4j.core.error.FormatDescriptorException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The descriptor types defend their own invariants, whether they were loaded
 * from YAML or built in code by a consumer registering a variant (R-X1).
 */
class DescriptorContractsTest {

    private static final FormatId ID = FormatId.of("example");

    @Test
    void formatIdsAreLowerCaseSlugs() {
        assertThat(FormatId.of("sougou-furikomi").value()).isEqualTo("sougou-furikomi");
        assertThat(FormatId.of("sougou-furikomi").toString()).isEqualTo("sougou-furikomi");
        assertThat(FormatId.of("sougou-furikomi").toTypeNamePrefix()).isEqualTo("SougouFurikomi");
        assertThat(FormatId.of("kouza-furikae-2").toTypeNamePrefix()).isEqualTo("KouzaFurikae2");
        assertThat(FormatId.of("a").compareTo(FormatId.of("b"))).isNegative();
        assertThat(FormatId.of("a")).isEqualTo(FormatId.of("a")).hasSameHashCodeAs(FormatId.of("a"));
    }

    @Test
    void formatIdsRejectAnythingElse() {
        assertThatIllegalArgumentException().isThrownBy(() -> FormatId.of(""))
                .withMessageContaining("must not be blank");
        assertThatIllegalArgumentException().isThrownBy(() -> FormatId.of("Sougou"))
                .withMessageContaining("unsupported character 'S'");
        assertThatIllegalArgumentException().isThrownBy(() -> FormatId.of("sougou_furikomi"))
                .withMessageContaining("unsupported character '_'");
        assertThatNullPointerException().isThrownBy(() -> FormatId.of(null));
    }

    @Test
    void recordDescriptorsLookFieldsUpAndSayWhenTheyCannot() {
        RecordDescriptor record = record(RecordKind.HEADER, (byte) '1', 5,
                field(1, "a", 0, 2, Optional.empty()),
                field(2, "b", 2, 3, Optional.of(FieldFormat.AMOUNT)));

        assertThat(record.find("a")).isPresent();
        assertThat(record.find("z")).isEmpty();
        assertThat(record.field("b").length()).isEqualTo(3);
        assertThat(record.findByFormat(FieldFormat.AMOUNT)).get().extracting(FieldDescriptor::id)
                .isEqualTo("b");
        assertThat(record.findByFormat(FieldFormat.MMDD)).isEmpty();
        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> record.field("z"))
                .withMessageContaining("has no field 'z'; declared fields: a, b");
    }

    @Test
    void recordDescriptorsRejectAmbiguousInterpretations() {
        RecordDescriptor record = record(RecordKind.TRAILER, (byte) '8', 6,
                field(1, "a", 0, 3, Optional.of(FieldFormat.AMOUNT)),
                field(2, "b", 3, 3, Optional.of(FieldFormat.AMOUNT)));

        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> record.findByFormat(FieldFormat.AMOUNT))
                .withMessageContaining("declares format AMOUNT on both 'a' and 'b'");
    }

    @Test
    void recordDescriptorsRejectStructuralNonsense() {
        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> record(RecordKind.MALFORMED, (byte) '1', 2, field(1, "a", 0, 2,
                        Optional.empty())))
                .withMessageContaining("MALFORMED is not a declarable record kind");
        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> new RecordDescriptor(ID, RecordKind.HEADER, (byte) '1', 2, List.of()))
                .withMessageContaining("declares no fields");
        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> record(RecordKind.HEADER, (byte) '1', 4,
                        field(1, "a", 0, 2, Optional.empty()),
                        field(2, "a", 2, 2, Optional.empty())))
                .withMessageContaining("field id 'a' twice");
        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> record(RecordKind.HEADER, (byte) '1', 4,
                        field(1, "a", 0, 2, Optional.empty()),
                        field(2, "b", 3, 1, Optional.empty())))
                .withMessageContaining("has offset 3 but the preceding fields end at 2");
        assertThatNullPointerException()
                .isThrownBy(() -> new RecordDescriptor(null, RecordKind.HEADER, (byte) '1', 2,
                        List.of(field(1, "a", 0, 2, Optional.empty()))));
    }

    @Test
    void formatDescriptorsExposeTheirRecords() {
        FormatDescriptor format = format(Map.of(
                RecordKind.HEADER, record(RecordKind.HEADER, (byte) '1', 4,
                        field(1, "a", 0, 4, Optional.empty())),
                RecordKind.DATA, record(RecordKind.DATA, (byte) '2', 4,
                        field(1, "a", 0, 4, Optional.empty()))));

        assertThat(format.find(RecordKind.DATA)).isPresent();
        assertThat(format.find(RecordKind.END)).isEmpty();
        assertThat(format.record(RecordKind.HEADER).discriminator()).isEqualTo((byte) '1');
        assertThat(format.forDiscriminator((byte) '2')).get().extracting(RecordDescriptor::kind)
                .isEqualTo(RecordKind.DATA);
        assertThat(format.forDiscriminator((byte) '9')).isEmpty();
        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> format.record(RecordKind.END))
                .withMessageContaining("declares no 'END' record");
    }

    @Test
    void formatDescriptorsRejectStructuralNonsense() {
        RecordDescriptor header = record(RecordKind.HEADER, (byte) '1', 4,
                field(1, "a", 0, 4, Optional.empty()));

        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> new FormatDescriptor(ID, "例", "Example", "21", 0, false, List.of(),
                        Optional.empty(), Map.of(RecordKind.HEADER, header)))
                .withMessageContaining("record length must be positive");
        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> new FormatDescriptor(ID, "例", "Example", " ", 4, false, List.of(),
                        Optional.empty(), Map.of(RecordKind.HEADER, header)))
                .withMessageContaining("type code must not be blank");
        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> new FormatDescriptor(ID, "例", "Example", "21", 4, false, List.of(),
                        Optional.empty(), Map.of()))
                .withMessageContaining("must declare a header record");
        Map<RecordKind, RecordDescriptor> misfiled = new EnumMap<>(RecordKind.class);
        misfiled.put(RecordKind.HEADER, header);
        misfiled.put(RecordKind.DATA, header);
        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> new FormatDescriptor(ID, "例", "Example", "21", 4, false, List.of(),
                        Optional.empty(), misfiled))
                .withMessageContaining("reports kind 'HEADER'");

        Map<RecordKind, RecordDescriptor> clashing = new EnumMap<>(RecordKind.class);
        clashing.put(RecordKind.HEADER, header);
        clashing.put(RecordKind.DATA, record(RecordKind.DATA, (byte) '1', 4,
                field(1, "a", 0, 4, Optional.empty())));
        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> new FormatDescriptor(ID, "例", "Example", "21", 4, false, List.of(),
                        Optional.empty(), clashing))
                .withMessageContaining("share データ区分 '1'");
        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> new FormatDescriptor(ID, "例", "Example", "21", 8, false, List.of(),
                        Optional.empty(), Map.of(RecordKind.HEADER, header)))
                .withMessageContaining("is 4 bytes but the format is 8 bytes");
        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> new FormatDescriptor(FormatId.of("other"), "例", "Example", "21", 4, false,
                        List.of(), Optional.empty(), Map.of(RecordKind.HEADER, header)))
                .withMessageContaining("belongs to format 'example'");
    }

    @Test
    void fieldDescriptorsRejectImpossibleGeometry() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> field(1, " ", 0, 1, Optional.empty()))
                .withMessageContaining("field id must not be blank");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> field(1, "a", 0, 0, Optional.empty()))
                .withMessageContaining("at least one byte");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> field(1, "a", -1, 1, Optional.empty()))
                .withMessageContaining("negative offset");
    }

    @Test
    void codeValuesRejectEmptyCodes() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CodeValue("", "名", "Name", false, Optional.empty()))
                .withMessageContaining("must not be empty");
        assertThatNullPointerException()
                .isThrownBy(() -> new CodeValue(null, "名", "Name", false, Optional.empty()));
    }

    @Test
    void recordKindsMapToAndFromDescriptorKeys() {
        assertThat(RecordKind.fromDescriptorKey("header")).contains(RecordKind.HEADER);
        assertThat(RecordKind.fromDescriptorKey("footer")).isEmpty();
        assertThat(RecordKind.END.descriptorKey()).isEqualTo("end");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(RecordKind.MALFORMED::descriptorKey);
    }

    @Test
    void fieldFormatsDescribeThemselves() {
        assertThat(FieldFormat.parse("MMDD", "x")).isEqualTo(FieldFormat.MMDD);
        assertThat(FieldFormat.MMDD.requiredLength()).contains(4);
        assertThat(FieldFormat.AMOUNT.requiredLength()).isEmpty();
        assertThat(FieldFormat.supportedValues()).contains("MMDD", "AMOUNT", "COUNT", "CODE-KUBUN");
        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> FieldFormat.parse("NONSENSE", "x"))
                .withMessageContaining("unknown field format");
    }

    @Test
    void codeListsAnswerMembershipQuestions() {
        CodeList closed = new CodeList("x", "名", "Name", false, false,
                List.of(new CodeValue("1", "一", "One", false, Optional.empty())), List.of(),
                Optional.empty());

        assertThat(closed.accepts("1")).isTrue();
        assertThat(closed.accepts("2")).isFalse();
        assertThat(closed.byCode("1")).isPresent();
        assertThat(closed.byCode("2")).isEmpty();
    }

    /** R-0.1 applies to code lists too: a claim of verification needs evidence. */
    @Test
    void codeListsCannotClaimVerificationWithoutSources() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CodeList("x", "名", "Name", true, true, List.of(),
                        List.of("only one citation"), Optional.empty()))
                .withMessageContaining("at least 2 are required");

        CodeList verified = new CodeList("x", "名", "Name", true, true, List.of(),
                List.of("first citation", "second citation"), Optional.empty());
        assertThat(verified.verified()).isTrue();
        assertThat(verified.sources()).hasSize(2);
    }

    private static FormatDescriptor format(Map<RecordKind, RecordDescriptor> records) {
        return new FormatDescriptor(ID, "例", "Example", "21", 4, false, List.of(), Optional.empty(),
                new EnumMap<>(records));
    }

    private static RecordDescriptor record(RecordKind kind, byte discriminator, int length,
            FieldDescriptor... fields) {
        return new RecordDescriptor(ID, kind, discriminator, length, List.of(fields));
    }

    private static FieldDescriptor field(int sequence, String id, int offset, int length,
            Optional<FieldFormat> format) {
        return new FieldDescriptor(sequence, id, "項目", "Field", FieldType.N, offset, length,
                false, false, false, format, Optional.empty(), Optional.empty(), Optional.empty());
    }
}
