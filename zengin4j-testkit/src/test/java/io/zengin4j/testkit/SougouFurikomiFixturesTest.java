package io.zengin4j.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.model.Batch;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import java.io.ByteArrayInputStream;
import java.time.MonthDay;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Milestone M1 seen from outside the library: the published fixtures parse
 * through the published reader.
 */
class SougouFurikomiFixturesTest {

    private final SougouFurikomiFixtures fixtures = SougouFurikomiFixtures.create();

    @Test
    void producesAFileThatParsesEndToEnd() {
        ZenginFile file = read(fixtures.file());

        assertThat(file.format()).isEqualTo(SougouFurikomiFixtures.FORMAT);
        assertThat(file.totalRecords()).isEqualTo(4);
        assertThat(file.endRecord()).isPresent();
        assertThat(file.framing().separator()).isEqualTo(SeparatorStyle.CRLF);

        Batch batch = file.batches().get(0);
        assertThat(batch.header().originatorCode()).isEqualTo(SougouFurikomiFixtures.ORIGINATOR_CODE);
        assertThat(batch.header().originatorName()).isEqualTo(SougouFurikomiFixtures.ORIGINATOR_NAME);
        assertThat(batch.header().valueDate()).contains(MonthDay.of(9, 30));
        assertThat(batch.computedTotal()).isEqualTo(SougouFurikomiFixtures.AMOUNT);
        assertThat(batch.trailer()).get().extracting(t -> t.recordCount()).isEqualTo(1);
    }

    @Test
    void producesEveryRecordAtTheDeclaredLength() {
        assertThat(fixtures.header()).hasSize(120);
        assertThat(fixtures.data()).hasSize(120);
        assertThat(fixtures.trailer(1, 150_000L)).hasSize(120);
        assertThat(fixtures.end()).hasSize(120);
        assertThat(fixtures.descriptor().recordLength()).isEqualTo(120);
    }

    @Test
    void writesEverySeparatorConvention() {
        for (SeparatorStyle style : List.of(SeparatorStyle.NONE, SeparatorStyle.CR,
                SeparatorStyle.LF, SeparatorStyle.CRLF)) {
            ZenginFile file = read(fixtures.file(style, false));
            assertThat(file.framing().separator()).as("%s", style).isEqualTo(style);
        }
        assertThat(read(fixtures.file(SeparatorStyle.CRLF, true)).framing().trailingEofByte()).isTrue();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fixtures.file(SeparatorStyle.MIXED, false))
                .withMessageContaining("MIXED is an observation");
    }

    @Test
    void scalesToManyPayments() {
        ZenginFile file = read(fixtures.file(50, SeparatorStyle.CRLF, false));

        assertThat(file.allData()).hasSize(50);
        assertThat(file.batches().get(0).computedTotal())
                .isEqualTo(SougouFurikomiFixtures.AMOUNT * 50);
    }

    /** R-CLI3: the same seed produces the same bytes, everywhere, always. */
    @Test
    void theGeneratorIsDeterministic() {
        byte[] first = ZenginGenerator.builder().seed(42).payments(20).build().generate();
        byte[] second = ZenginGenerator.builder().seed(42).payments(20).build().generate();
        byte[] different = ZenginGenerator.builder().seed(43).payments(20).build().generate();

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(different);
    }

    @Test
    void theGeneratorProducesReadableFiles() {
        byte[] generated = ZenginGenerator.builder()
                .seed(7)
                .payments(15)
                .separator(SeparatorStyle.LF)
                .trailingEofByte(true)
                .build()
                .generate();

        ZenginFile file = read(generated);

        assertThat(file.allData()).hasSize(15);
        assertThat(file.framing().separator()).isEqualTo(SeparatorStyle.LF);
        assertThat(file.framing().trailingEofByte()).isTrue();
        // The trailer the generator wrote agrees with what the records add up to.
        assertThat(file.batches().get(0).trailer().orElseThrow().totalAmount())
                .isEqualTo(file.batches().get(0).computedTotal());
    }

    @Test
    void theGeneratorRefusesANegativePaymentCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ZenginGenerator.builder().payments(-1))
                .withMessageContaining("must not be negative");
    }

    @Test
    void refusesFieldsThatDoNotExist() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SyntheticRecords.encode(
                        fixtures.descriptor().record(io.zengin4j.core.format.RecordKind.DATA),
                        io.zengin4j.core.charset.ZenginCharset.MS932,
                        Map.of("noSuchField", "x")))
                .withMessageContaining("has no field 'noSuchField'");
    }

    /** P1, R-L1: nothing in the testkit resembles a real identifier. */
    @Test
    void everyIdentifierIsOutsideTheRangesRealInstitutionsUse() {
        assertThat(SougouFurikomiFixtures.BENEFICIARY_BANK_CODE).isEqualTo("9999");
        assertThat(SougouFurikomiFixtures.ORIGIN_BANK_CODE).isEqualTo("9999");
        assertThat(SougouFurikomiFixtures.BENEFICIARY_BRANCH_CODE).startsWith("9");
        assertThat(SougouFurikomiFixtures.BENEFICIARY_ACCOUNT).startsWith("9");
        assertThat(SougouFurikomiFixtures.ORIGINATOR_CODE).startsWith("99");
    }

    private ZenginFile read(byte[] bytes) {
        return ZenginReaders.readFile(new ByteArrayInputStream(bytes), fixtures.readerOptions());
    }
}
