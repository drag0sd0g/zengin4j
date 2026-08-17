package io.zengin4j.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.zengin4j.core.charset.CharacterSet;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FieldType;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.model.Batch;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.model.ZenginRecord;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every format's fixtures, held to the same bar as 総合振込's.
 *
 * <p>The point of generating fixtures for four formats is worthless if three of
 * them produce bytes the library cannot read back, so each one goes through the
 * published reader and has to come out with the batch totals it went in with.
 *
 * <p>The character checks matter more here than they look. The payroll formats
 * use a name class that admits no Latin letters, and Epic 4 found that this
 * project's own fixtures contained a small kana the standard excludes. Fixtures
 * that violate the character rules teach every downstream test the wrong thing.
 */
class AllFormatFixturesTest {
    static List<FormatId> formats() {
        return FormatFixtures.supported();
    }

    private static ZenginFile read(FormatFixtures fixtures, byte[] bytes) {
        return ZenginReaders.readFile(new ByteArrayInputStream(bytes), fixtures.readerOptions());
    }

    @Test
    void coversEveryBundledFormat() {
        assertThat(FormatFixtures.supported())
                .as("the testkit should produce every format the library reads")
                .extracting(FormatId::value)
                .containsExactly("sougou-furikomi", "kyuyo-furikomi", "shoyo-furikomi",
                        "kouza-furikae");
    }

    @ParameterizedTest
    @MethodSource("formats")
    void producesAFileThatParsesEndToEnd(FormatId id) {
        FormatFixtures fixtures = FormatFixtures.forFormat(id);
        ZenginFile file = read(fixtures, fixtures.file());

        assertThat(file.format()).isEqualTo(id);
        assertThat(file.totalRecords()).isEqualTo(4);
        assertThat(file.endRecord()).isPresent();
        assertThat(file.framing().separator()).isEqualTo(SeparatorStyle.CRLF);
        assertThat(file.batches()).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("formats")
    void theTrailerAgreesWithTheRecordsItSummarises(FormatId id) {
        FormatFixtures fixtures = FormatFixtures.forFormat(id);
        ZenginFile file = read(fixtures, fixtures.file(5, SeparatorStyle.LF, false));

        Batch batch = file.batches().get(0);
        assertThat(batch.data()).hasSize(5);
        assertThat(batch.trailer()).isPresent();
        assertThat(batch.trailer().orElseThrow().recordCount())
                .as("%s trailer count", id.value())
                .isEqualTo(5);
        assertThat(batch.trailer().orElseThrow().totalAmount())
                .as("%s trailer total", id.value())
                .isEqualTo(batch.computedTotal());
    }

    @ParameterizedTest
    @MethodSource("formats")
    void everyRecordIsTheDeclaredLength(FormatId id) {
        FormatFixtures fixtures = FormatFixtures.forFormat(id);
        int expected = fixtures.descriptor().recordLength();

        assertThat(List.of(fixtures.header(), fixtures.data(), fixtures.trailer(1, 1L),
                        fixtures.end()))
                .allSatisfy(record -> assertThat(record).hasSize(expected));
    }

    /**
     * Every text field holds only characters its own class permits.
     *
     * <p>Checked against the descriptor's per-field character class rather than
     * one blanket rule, because the classes genuinely differ: bank names admit
     * one symbol, party names four, and payroll names no Latin at all.
     */
    @ParameterizedTest
    @MethodSource("formats")
    void everyTextFieldIsWritableInItsOwnCharacterClass(FormatId id) {
        FormatFixtures fixtures = FormatFixtures.forFormat(id);

        for (ZenginRecord record : recordsOf(fixtures)) {
            RecordDescriptor layout = fixtures.descriptor()
                    .forDiscriminator(record.rawBytes()[0]).orElseThrow();
            for (FieldDescriptor field : layout.fields()) {
                if (field.type() != FieldType.C) {
                    continue;
                }
                assertThat(CharacterSet.validate(record.rawBytes(), field.offset(), field.length(),
                                field.charClass()))
                        .as("%s %s.%s must hold only characters its class permits",
                                id.value(), layout.kind(), field.id())
                        .isEmpty();
            }
        }
    }

    private static List<ZenginRecord> recordsOf(FormatFixtures fixtures) {
        ZenginFile file = read(fixtures, fixtures.file());
        List<ZenginRecord> records = new java.util.ArrayList<>();
        for (Batch batch : file.batches()) {
            records.add(batch.header());
            records.addAll(batch.data());
            batch.trailer().ifPresent(records::add);
        }
        file.endRecord().ifPresent(records::add);
        return records;
    }

    @ParameterizedTest
    @MethodSource("formats")
    void theSameSeedProducesTheSameBytes(FormatId id) {
        byte[] first = ZenginGenerator.builder().format(id).seed(7L).payments(12).build().generate();
        byte[] second = ZenginGenerator.builder().format(id).seed(7L).payments(12).build().generate();

        assertThat(first).as("R-CLI3: generation must be reproducible").isEqualTo(second);
    }

    @ParameterizedTest
    @MethodSource("formats")
    void aDifferentSeedProducesDifferentBytes(FormatId id) {
        byte[] first = ZenginGenerator.builder().format(id).seed(7L).payments(12).build().generate();
        byte[] second = ZenginGenerator.builder().format(id).seed(8L).payments(12).build().generate();

        assertThat(first).isNotEqualTo(second);
    }

    @ParameterizedTest
    @MethodSource("formats")
    void generatedFilesParseAndBalance(FormatId id) {
        ZenginGenerator generator =
                ZenginGenerator.builder().format(id).seed(99L).payments(25).build();
        FormatFixtures fixtures = FormatFixtures.forFormat(id);

        ZenginFile file = read(fixtures, generator.generate());

        Batch batch = file.batches().get(0);
        assertThat(batch.data()).hasSize(25);
        assertThat(batch.trailer().orElseThrow().totalAmount()).isEqualTo(batch.computedTotal());
        assertThat(batch.trailer().orElseThrow().recordCount()).isEqualTo(25);
    }

    @Test
    void anUnknownFormatIsRefusedByName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FormatFixtures.forFormat(FormatId.of("furikomi-nyukin-tsuchi")))
                .withMessageContaining("no fixtures for format 'furikomi-nyukin-tsuchi'")
                .withMessageContaining("sougou-furikomi");
    }

    /**
     * 預金口座振替 moves money the other way, and the fixtures say so in the
     * field ids they populate rather than only in prose.
     */
    @Test
    void directDebitFixturesNameThePayerNotTheBeneficiary() {
        KouzaFurikaeFixtures fixtures = KouzaFurikaeFixtures.create();
        RecordDescriptor data = fixtures.descriptor()
                .record(io.zengin4j.core.format.RecordKind.DATA);

        assertThat(data.find("payerAccountNumber")).isPresent();
        assertThat(data.find("beneficiaryBankCode"))
                .as("a direct debit has no beneficiary in its data record")
                .isEmpty();
    }

    /** An instruction file carries no outcome yet. */
    @Test
    void directDebitInstructionsCarryNoResultYet() {
        KouzaFurikaeFixtures fixtures = KouzaFurikaeFixtures.create();
        ZenginFile file = read(fixtures, fixtures.file());

        RecordDescriptor data = fixtures.descriptor()
                .record(io.zengin4j.core.format.RecordKind.DATA);
        FieldDescriptor result = data.field("transferResult");
        byte[] bytes = file.batches().get(0).data().get(0).rawBytes();

        assertThat((char) bytes[result.offset()]).isEqualTo('0');
    }
}
