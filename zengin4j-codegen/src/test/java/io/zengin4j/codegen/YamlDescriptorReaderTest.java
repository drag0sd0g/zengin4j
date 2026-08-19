package io.zengin4j.codegen;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.zengin4j.core.format.CodeList;
import io.zengin4j.core.format.CodeValue;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.format.RecordKind;
import org.junit.jupiter.api.Test;

/// Descriptor validation, which since ADR-0016 happens at build time.
///
/// Nearly every case here is a rejection. A descriptor is a transcription of
/// a byte layout, and the cheapest place to catch a transcription error is the
/// build that compiles it into the library.
class YamlDescriptorReaderTest {

    private static final Map<String, CodeList> CODE_LISTS = Map.of(
            "accountType", new CodeList("accountType", "預金種目", "Account Type", false, true,
                    List.of(), List.of(), Optional.empty()));

    /// R-F2: offsets are computed from cumulative lengths, never transcribed.
    @Test
    void computesOffsetsFromCumulativeLengths() {
        FormatDescriptor descriptor = load(descriptorYaml("""
                        - { seq: 1, id: dataKubun, ja: データ区分, en: Record Type, type: N, length: 1, const: "1" }
                        - { seq: 2, id: typeCode, ja: 種別コード, en: Business Type, type: N, length: 2, const: "21" }
                        - { seq: 3, id: originatorName, ja: 委託者名, en: Originator, type: C, length: 7 }
                """, 10));

        RecordDescriptor header = descriptor.record(RecordKind.HEADER);
        assertThat(header.fields()).extracting(FieldDescriptor::offset).containsExactly(0, 1, 3);
        assertThat(header.fields()).extracting(FieldDescriptor::endOffset).containsExactly(1, 3, 10);
        assertThat(header.discriminator()).isEqualTo((byte) '1');
    }

    /// R-F1: the single check that catches most transcription errors.
    @Test
    void rejectsFieldLengthsThatDoNotSumToTheRecordLength() {
        assertThatFails(descriptorYaml("""
                        - { seq: 1, id: dataKubun, ja: データ区分, en: Record Type, type: N, length: 1, const: "1" }
                        - { seq: 2, id: typeCode, ja: 種別コード, en: Business Type, type: N, length: 2, const: "21" }
                """, 10))
                .withMessageContaining("field lengths sum to 3 but the record length is 10");
    }

    @Test
    void rejectsSequenceNumbersOutOfOrder() {
        assertThatFails(descriptorYaml("""
                        - { seq: 1, id: dataKubun, ja: データ区分, en: Record Type, type: N, length: 1, const: "1" }
                        - { seq: 3, id: typeCode, ja: 種別コード, en: Business Type, type: N, length: 9, const: "21" }
                """, 10))
                .withMessageContaining("declares seq 3 but is in position 2");
    }

    @Test
    void rejectsDuplicateFieldIds() {
        assertThatFails(descriptorYaml("""
                        - { seq: 1, id: dataKubun, ja: データ区分, en: Record Type, type: N, length: 1, const: "1" }
                        - { seq: 2, id: dataKubun, ja: 種別コード, en: Business Type, type: N, length: 9 }
                """, 10))
                .withMessageContaining("declares field id 'dataKubun' twice");
    }

    @Test
    void rejectsAConstantThatContradictsTheDiscriminator() {
        assertThatFails(descriptorYaml("""
                        - { seq: 1, id: dataKubun, ja: データ区分, en: Record Type, type: N, length: 1, const: "2" }
                        - { seq: 2, id: rest, ja: 残り, en: Rest, type: C, length: 9 }
                """, 10))
                .withMessageContaining("has discriminator '1' but its first field 'dataKubun' is fixed at '2'");
    }

    @Test
    void rejectsATypeCodeThatContradictsTheHeader() {
        assertThatFails(descriptorYaml("""
                        - { seq: 1, id: dataKubun, ja: データ区分, en: Record Type, type: N, length: 1, const: "1" }
                        - { seq: 2, id: typeCode, ja: 種別コード, en: Business Type, type: N, length: 2, const: "99" }
                        - { seq: 3, id: rest, ja: 残り, en: Rest, type: C, length: 7 }
                """, 10))
                .withMessageContaining("type-code '21' but the header's typeCode field is fixed at '99'");
    }

    @Test
    void rejectsAnUnknownCodeListReference() {
        assertThatFails(descriptorYaml("""
                        - { seq: 1, id: dataKubun, ja: データ区分, en: Record Type, type: N, length: 1, const: "1" }
                        - { seq: 2, id: rest, ja: 残り, en: Rest, type: C, length: 9, codelist: nonexistent }
                """, 10))
                .withMessageContaining("references unknown code list 'nonexistent'");
    }

    @Test
    void resolvesAKnownCodeListReference() {
        FormatDescriptor descriptor = load(descriptorYaml("""
                        - { seq: 1, id: dataKubun, ja: データ区分, en: Record Type, type: N, length: 1, const: "1" }
                        - { seq: 2, id: rest, ja: 残り, en: Rest, type: N, length: 9, codelist: accountType }
                """, 10));

        assertThat(descriptor.record(RecordKind.HEADER).field("rest").codeList())
                .get().extracting(CodeList::id).isEqualTo("accountType");
    }

    /// R-0.1: verification is a claim about evidence, and the evidence must be there.
    @Test
    void rejectsVerifiedTrueWithoutTwoSources() {
        assertThatFails("""
                format:
                  id: example
                  name-ja: 例
                  name-en: Example
                  type-code: "21"
                  record-length: 10
                  verified: true
                  sources: [only one citation]
                  records:
                    header:
                      discriminator: "1"
                      fields:
                        - { seq: 1, id: dataKubun, ja: データ区分, en: Record Type, type: N, length: 1, const: "1" }
                        - { seq: 2, id: rest, ja: 残り, en: Rest, type: C, length: 9 }
                """).withMessageContaining("at least 2 independent cited sources");
    }

    @Test
    void acceptsVerifiedTrueWithTwoSources() {
        FormatDescriptor descriptor = load("""
                format:
                  id: example
                  name-ja: 例
                  name-en: Example
                  type-code: "21"
                  record-length: 10
                  verified: true
                  sources: [first citation, second citation]
                  records:
                    header:
                      discriminator: "1"
                      fields:
                        - { seq: 1, id: dataKubun, ja: データ区分, en: Record Type, type: N, length: 1, const: "1" }
                        - { seq: 2, id: rest, ja: 残り, en: Rest, type: C, length: 9 }
                """);

        assertThat(descriptor.verified()).isTrue();
        assertThat(descriptor.sources()).hasSize(2);
    }

    @Test
    void rejectsAnUnknownRecordKind() {
        assertThatFails("""
                format:
                  id: example
                  name-ja: 例
                  name-en: Example
                  type-code: "21"
                  record-length: 10
                  records:
                    footer:
                      discriminator: "1"
                      fields:
                        - { seq: 1, id: rest, ja: 残り, en: Rest, type: C, length: 10 }
                """).withMessageContaining("unknown record kind 'footer'");
    }

    @Test
    void rejectsAFormatWithNoHeaderRecord() {
        assertThatFails("""
                format:
                  id: example
                  name-ja: 例
                  name-en: Example
                  type-code: "21"
                  record-length: 10
                  records:
                    data:
                      discriminator: "2"
                      fields:
                        - { seq: 1, id: rest, ja: 残り, en: Rest, type: C, length: 10 }
                """).withMessageContaining("must declare a header record");
    }

    @Test
    void rejectsUnknownKeysAnywhere() {
        assertThatFails(descriptorYaml("""
                        - { seq: 1, id: rest, ja: 残り, en: Rest, type: C, length: 10, colour: red }
                """, 10))
                .withMessageContaining("unknown key 'colour'");
    }

    @Test
    void rejectsAnUnknownFieldTypeOrFormat() {
        assertThatFails(descriptorYaml("""
                        - { seq: 1, id: rest, ja: 残り, en: Rest, type: X, length: 10 }
                """, 10))
                .withMessageContaining("field type must be N or C");
        assertThatFails(descriptorYaml("""
                        - { seq: 1, id: rest, ja: 残り, en: Rest, type: N, length: 10, format: EPOCH }
                """, 10))
                .withMessageContaining("unknown field format 'EPOCH'");
    }

    @Test
    void rejectsAnInterpretationOnAWrongTypeOrLength() {
        assertThatFails(descriptorYaml("""
                        - { seq: 1, id: rest, ja: 残り, en: Rest, type: C, length: 10, format: AMOUNT }
                """, 10))
                .withMessageContaining("applies only to N fields");
        assertThatFails(descriptorYaml("""
                        - { seq: 1, id: rest, ja: 残り, en: Rest, type: N, length: 10, format: MMDD }
                """, 10))
                .withMessageContaining("requires length 4");
    }

    /// A repeated key is a mistake, not a last-one-wins override.
    @Test
    void rejectsDuplicateYamlKeys() {
        assertThatFails("""
                format:
                  id: example
                  id: other
                """).withMessageContaining("found duplicate key");
    }

    @Test
    void readsCodeListsWithVerificationState() {
        Map<String, CodeList> lists = YamlDescriptorReader.readCodeLists("""
                code-lists:
                  - id: accountType
                    name-ja: 預金種目
                    name-en: Account Type
                    verified: true
                    open: true
                    note: "[VERIFY] unconfirmed"
                    sources: [first citation, second citation]
                    values:
                      - { code: "1", ja: 普通預金, en: Ordinary, verified: false }
                      - { code: "2", ja: 当座預金, en: Current, verified: true, note: confirmed }
                """, "code-lists.yaml");

        CodeList list = lists.get("accountType");
        assertThat(list.verified()).isTrue();
        assertThat(list.sources()).hasSize(2);
        assertThat(list.note()).contains("[VERIFY] unconfirmed");
        assertThat(list.byCode("2")).get().extracting(CodeValue::verified).isEqualTo(true);
        assertThat(list.accepts("9")).isTrue();
    }

    /// R-0.1 applies to code lists too.
    @Test
    void rejectsAVerifiedCodeListWithoutTwoSources() {
        assertThatExceptionOfType(CodegenException.class)
                .isThrownBy(() -> YamlDescriptorReader.readCodeLists("""
                        code-lists:
                          - id: accountType
                            name-ja: 預金種目
                            name-en: Account Type
                            verified: true
                            sources: [only one]
                        """, "code-lists.yaml"))
                .withMessageContaining("at least 2 are required");
    }

    @Test
    void rejectsDuplicateCodeLists() {
        assertThatExceptionOfType(CodegenException.class)
                .isThrownBy(() -> YamlDescriptorReader.readCodeLists("""
                        code-lists:
                          - id: accountType
                            name-ja: 預金種目
                            name-en: Account Type
                          - id: accountType
                            name-ja: 預金種目
                            name-en: Account Type
                        """, "code-lists.yaml"))
                .withMessageContaining("declared twice");
    }

    private static FormatDescriptor load(String yaml) {
        return YamlDescriptorReader.readFormat(yaml, "example.yaml", CODE_LISTS);
    }

    private static org.assertj.core.api.ThrowableAssertAlternative<CodegenException> assertThatFails(
            String yaml) {
        return assertThatExceptionOfType(CodegenException.class).isThrownBy(() -> load(yaml));
    }

    private static String descriptorYaml(String fields, int recordLength) {
        return """
                format:
                  id: example
                  name-ja: 例
                  name-en: Example
                  type-code: "21"
                  record-length: %d
                  records:
                    header:
                      discriminator: "1"
                      fields:
                %s
                """.formatted(recordLength, fields.stripTrailing());
    }
}
