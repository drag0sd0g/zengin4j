package io.zengin4j.core.model;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.core.charset.CodeKubun;
import io.zengin4j.core.codec.ReaderOptions;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.model.generated.GeneratedRecords;
import io.zengin4j.core.model.generated.SougouFurikomiData;
import io.zengin4j.core.model.generated.SougouFurikomiEnd;
import io.zengin4j.core.model.generated.SougouFurikomiHeader;
import io.zengin4j.core.model.generated.SougouFurikomiTrailer;
import io.zengin4j.core.testing.Fixtures;
import org.junit.jupiter.api.Test;

/// R-F3, R-M8, R-D1: the committed, format-shaped record types.
class GeneratedRecordsTest {

    private final FormatDescriptor descriptor = Fixtures.descriptor();

    @Test
    void materialisesEveryRecordKindAsItsGeneratedType() {
        ZenginFile file = read(Fixtures.file(descriptor));
        Batch batch = file.batches().get(0);

        assertThat(batch.header()).isInstanceOf(SougouFurikomiHeader.class);
        assertThat(batch.data().get(0)).isInstanceOf(SougouFurikomiData.class);
        assertThat(batch.trailer()).get().isInstanceOf(SougouFurikomiTrailer.class);
        assertThat(file.endRecord()).get().isInstanceOf(SougouFurikomiEnd.class);
    }

    /// R-D1: the record carries exactly its own fields, in its own order.
    @Test
    void exposesTheFieldsTheRecordActuallyHas() {
        SougouFurikomiData data = (SougouFurikomiData) read(Fixtures.file(descriptor))
                .allData().get(0);

        assertThat(data.dataKubun()).isEqualTo("2");
        assertThat(data.beneficiaryBankCode()).isEqualTo("9999");
        assertThat(data.beneficiaryBankName()).isEqualTo(Fixtures.BANK_NAME);
        assertThat(data.beneficiaryBranchCode()).isEqualTo("999");
        assertThat(data.beneficiaryBranchName()).isEqualTo("ﾃｽﾄｼﾃﾝ");
        assertThat(data.clearingHouseCode()).isEqualTo("0000");
        assertThat(data.accountType()).isEqualTo("1");
        assertThat(data.accountNumber()).isEqualTo(Fixtures.ACCOUNT);
        assertThat(data.beneficiaryName()).isEqualTo(Fixtures.BENEFICIARY);
        assertThat(data.amount()).isEqualTo(Fixtures.AMOUNT);
        assertThat(data.newCode()).isEqualTo("0");
        assertThat(data.customerCode1()).isEqualTo("INV2026000");
        assertThat(data.customerCode2()).isEqualTo("1");
        assertThat(data.transferCategory()).isEqualTo("7");
        assertThat(data.identification()).isEmpty();
        assertThat(data.dummy()).isEmpty();
        assertThat(data.kind()).isEqualTo(io.zengin4j.core.format.RecordKind.DATA);
        assertThat(data.formatId()).isEqualTo(SougouFurikomiData.FORMAT_ID);
    }

    /// The generated offset constants must agree with the descriptor they came from.
    @Test
    void publishesOffsetsThatMatchTheDescriptor() {
        var data = descriptor.record(io.zengin4j.core.format.RecordKind.DATA);

        assertThat(SougouFurikomiData.RECORD_LENGTH).isEqualTo(descriptor.recordLength());
        assertThat(SougouFurikomiData.DISCRIMINATOR).isEqualTo((byte) '2');
        assertThat(SougouFurikomiData.AMOUNT_OFFSET).isEqualTo(data.field("amount").offset());
        assertThat(SougouFurikomiData.AMOUNT_LENGTH).isEqualTo(data.field("amount").length());
        assertThat(SougouFurikomiData.BENEFICIARY_NAME_OFFSET)
                .isEqualTo(data.field("beneficiaryName").offset());
        assertThat(SougouFurikomiData.CUSTOMER_CODE1_OFFSET)
                .isEqualTo(data.field("customerCode1").offset());
        assertThat(SougouFurikomiHeader.FORMAT_ID).isEqualTo(descriptor.id());
    }

    @Test
    void decodesTheHeaderRoleAccessors() {
        SougouFurikomiHeader header = (SougouFurikomiHeader) read(Fixtures.file(descriptor))
                .batches().get(0).header();

        assertThat(header.codeKubun()).isEqualTo(CodeKubun.JIS);
        assertThat(header.codeKubunRaw()).isEqualTo("0");
        assertThat(header.valueDate()).contains(MonthDay.of(9, 30));
        assertThat(header.valueDateRaw()).isEqualTo("0930");
        assertThat(header.originatorCode()).isEqualTo("9900000001");
        assertThat(header.originatorName()).isEqualTo("ﾃｽﾄｼﾖｳｼﾞ");
    }

    @Test
    void decodesTheTrailerAndEndRoleAccessors() {
        ZenginFile file = read(Fixtures.file(descriptor));

        TrailerRecord trailer = file.batches().get(0).trailer().orElseThrow();
        assertThat(trailer.recordCount()).isEqualTo(1);
        assertThat(trailer.totalAmount()).isEqualTo(Fixtures.AMOUNT);

        EndRecord end = file.endRecord().orElseThrow();
        assertThat(end.filler()).hasSize(Fixtures.RECORD_LENGTH - 1);
        assertThat(new String(end.filler(), java.nio.charset.StandardCharsets.US_ASCII)).isBlank();
    }

    /// R-D5: the raw bytes survive, filler included.
    @Test
    void retainsTheRawBytes() {
        byte[] expected = Fixtures.data(descriptor);
        DataRecord data = read(Fixtures.file(descriptor)).allData().get(0);

        assertThat(data.rawBytes()).isEqualTo(expected);
        assertThat(data.rawBytes()).isNotSameAs(data.rawBytes());
    }

    @Test
    void definesEqualityByContentRatherThanPosition() {
        DataRecord first = read(Fixtures.file(descriptor)).allData().get(0);
        DataRecord second = read(twoPaymentFile()).allData().get(1);

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first.recordNumber()).isNotEqualTo(second.recordNumber());
    }

    @Test
    void distinguishesRecordsWithDifferentBytes() {
        DataRecord first = read(Fixtures.file(descriptor)).allData().get(0);
        byte[] other = Fixtures.join(Fixtures.CRLF, Fixtures.header(descriptor),
                Fixtures.data(descriptor, "ﾃｽﾄ ｼﾞﾛｳ", 9_000L),
                Fixtures.trailer(descriptor, 1, 9_000L), Fixtures.end(descriptor));
        DataRecord second = read(other).allData().get(0);

        assertThat(first).isNotEqualTo(second);
        assertThat(first).isNotEqualTo("not a record");
    }

    /// R-E6: an account number does not appear in full in a log line.
    @Test
    void masksAccountNumbersInToString() {
        SougouFurikomiData data = (SougouFurikomiData) read(Fixtures.file(descriptor)).allData().get(0);

        assertThat(data.toString())
                .contains("accountNumber=***6543")
                .doesNotContain(Fixtures.ACCOUNT)
                .contains("beneficiaryName=" + Fixtures.BENEFICIARY)
                .doesNotContain("dummy=");
        assertThat(data.accountNumber()).isEqualTo(Fixtures.ACCOUNT);
    }

    @Test
    void indexesEveryGeneratedFormat() {
        assertThat(GeneratedRecords.formats()).containsExactlyInAnyOrder(
                FormatId.of("sougou-furikomi"), FormatId.of("kyuyo-furikomi"),
                FormatId.of("shoyo-furikomi"), FormatId.of("kouza-furikae"));
        assertThat(GeneratedRecords.forFormat(FormatId.of("sougou-furikomi"))).isPresent();
        assertThat(GeneratedRecords.forFormat(FormatId.of("not-generated"))).isEmpty();
    }

    /// A descriptor registered at runtime has no generated code and falls back.
    @Test
    void fallsBackToDescriptorDrivenRecordsForRuntimeFormats() {
        FormatDescriptor variant = Fixtures.renamed(descriptor, "runtime-variant");
        FormatRegistry registry = FormatRegistry.builder()
                .codeLists(FormatRegistry.defaults().codeLists())
                .register(variant)
                .build();
        ReaderOptions options = Fixtures.optionsBuilder()
                .registry(registry)
                .format(variant.id())
                .build();

        ZenginFile file = ZenginReaders.readFile(
                new ByteArrayInputStream(Fixtures.file(descriptor)), options);
        Batch batch = file.batches().get(0);

        assertThat(batch.header()).isInstanceOf(GenericHeaderRecord.class);
        assertThat(batch.data().get(0)).isInstanceOf(GenericDataRecord.class);
        assertThat(batch.trailer()).get().isInstanceOf(GenericTrailerRecord.class);
        assertThat(file.endRecord()).get().isInstanceOf(GenericEndRecord.class);

        GenericDataRecord data = (GenericDataRecord) batch.data().get(0);
        assertThat(data.amount()).isEqualTo(Fixtures.AMOUNT);
        assertThat(data.value("beneficiaryName")).isEqualTo(Fixtures.BENEFICIARY);
        assertThat(data.value("nonexistent")).isEmpty();
        assertThat(data.values()).containsKeys("amount", "beneficiaryName");
        assertThat(data.formatId()).isEqualTo(variant.id());
        assertThat(data.descriptor().kind()).isEqualTo(io.zengin4j.core.format.RecordKind.DATA);
        assertThat(data.toString()).contains("accountNumber=***6543").doesNotContain(Fixtures.ACCOUNT);
        assertThat(Arrays.equals(data.rawBytes(), Fixtures.data(descriptor))).isTrue();

        GenericHeaderRecord header = (GenericHeaderRecord) batch.header();
        assertThat(header.codeKubun()).isEqualTo(CodeKubun.JIS);
        assertThat(header.effectiveDate()).contains(MonthDay.of(9, 30));
        assertThat(header.originatorName()).isEqualTo("ﾃｽﾄｼﾖｳｼﾞ");
        assertThat(header.kind()).isEqualTo(io.zengin4j.core.format.RecordKind.HEADER);

        GenericEndRecord end = (GenericEndRecord) file.endRecord().orElseThrow();
        assertThat(end.filler()).hasSize(Fixtures.RECORD_LENGTH - 1);
        assertThat(batch.trailer().orElseThrow().recordCount()).isEqualTo(1);
    }

    @Test
    void genericRecordEqualityFollowsContent() {
        FormatDescriptor variant = Fixtures.renamed(descriptor, "runtime-variant");
        FormatRegistry registry = FormatRegistry.builder()
                .codeLists(FormatRegistry.defaults().codeLists())
                .register(variant)
                .build();
        ReaderOptions options = Fixtures.optionsBuilder().registry(registry).format(variant.id()).build();

        ZenginFile first = ZenginReaders.readFile(
                new ByteArrayInputStream(Fixtures.file(descriptor)), options);
        ZenginFile second = ZenginReaders.readFile(
                new ByteArrayInputStream(Fixtures.file(descriptor)), options);

        assertThat(first.allData().get(0))
                .isEqualTo(second.allData().get(0))
                .hasSameHashCodeAs(second.allData().get(0));
        assertThat(first.allData().get(0)).isNotEqualTo(first.batches().get(0).header());
    }

    private ZenginFile read(byte[] file) {
        return ZenginReaders.readFile(new ByteArrayInputStream(file), Fixtures.options());
    }

    private byte[] twoPaymentFile() {
        return Fixtures.join(Fixtures.CRLF, Fixtures.header(descriptor), Fixtures.data(descriptor),
                Fixtures.data(descriptor), Fixtures.trailer(descriptor, 2, Fixtures.AMOUNT * 2),
                Fixtures.end(descriptor));
    }
}
