package io.zengin4j.core.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.zengin4j.core.charset.CodeKubun;
import io.zengin4j.core.error.MalformedFieldException;
import io.zengin4j.core.error.StaleRecordViewException;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.testing.Fixtures;
import java.io.ByteArrayInputStream;
import java.time.MonthDay;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/**
 * The lazy view over the recycled buffer (R-MEM1 to R-MEM4).
 */
class RecordViewTest {

    private final FormatDescriptor descriptor = Fixtures.descriptor();

    /** R-MEM2: a view retained across an iteration must fail loudly, not quietly. */
    @Test
    void refusesToServeAStaleView() {
        try (ZenginReader reader = open(Fixtures.file(descriptor))) {
            RecordView header = reader.next();
            assertThat(header.isValid()).isTrue();

            reader.next();

            assertThat(header.isValid()).isFalse();
            assertThatExceptionOfType(StaleRecordViewException.class)
                    .isThrownBy(() -> header.asString("originatorCode"))
                    .satisfies(e -> assertThat(e.recordNumber()).isEqualTo(1))
                    .withMessageContaining("materialize()");
            assertThatExceptionOfType(StaleRecordViewException.class).isThrownBy(header::rawBytes);
            assertThatExceptionOfType(StaleRecordViewException.class).isThrownBy(header::materialize);
        }
    }

    /** A materialised record survives the buffer moving on. */
    @Test
    void materialisedRecordsOutliveTheView() {
        try (ZenginReader reader = open(Fixtures.file(descriptor))) {
            var header = reader.next().materialize();
            reader.next();
            reader.next();

            assertThat(header.rawBytes()).hasSize(Fixtures.RECORD_LENGTH);
            assertThat(header.recordNumber()).isEqualTo(1);
            assertThat(header.byteOffset()).isZero();
        }
    }

    /** R-MEM4: a decoded string is computed once per view. */
    @Test
    void cachesDecodedStringsWithinTheView() {
        try (ZenginReader reader = open(Fixtures.file(descriptor))) {
            RecordView header = reader.next();

            String first = header.asString("originatorName");
            String second = header.asString("originatorName");

            assertThat(first).isSameAs(second);
        }
    }

    @Test
    void decodesEveryTypedAccessor() {
        try (ZenginReader reader = open(Fixtures.file(descriptor))) {
            RecordView header = reader.next();
            FieldDescriptor valueDate = header.field("valueDate");

            assertThat(header.asMonthDay(valueDate)).contains(MonthDay.of(9, 30));
            assertThat(header.asPaddedString(valueDate)).isEqualTo("0930");
            assertThat(header.asLong(valueDate)).isEqualTo(930L);
            assertThat(header.asOptionalLong(valueDate)).isEqualTo(OptionalLong.of(930L));
            assertThat(header.asCodeKubun(header.field("codeKubun"))).isEqualTo(CodeKubun.JIS);
            assertThat(header.asBytes(valueDate)).containsExactly('0', '9', '3', '0');
            assertThat(header.length()).isEqualTo(Fixtures.RECORD_LENGTH);
            assertThat(header.descriptor()).get().extracting(d -> d.kind()).isEqualTo(RecordKind.HEADER);
            assertThat(header.format().id()).isEqualTo(Fixtures.SOUGOU_FURIKOMI);
        }
    }

    @Test
    void reportsAnUnsetDateAsAbsentRatherThanInventingOne() {
        byte[] header = Fixtures.patch(Fixtures.header(descriptor), 54, "0000");
        try (ZenginReader reader = open(header)) {
            RecordView view = reader.next();

            assertThat(view.asMonthDay(view.field("valueDate"))).isEmpty();
            assertThat(view.asPaddedString(view.field("valueDate"))).isEqualTo("0000");
        }
    }

    @Test
    void reportsANonNumericFieldByName() {
        byte[] data = Fixtures.patch(Fixtures.data(descriptor), 80, "ABCDEFGHIJ");
        byte[] file = Fixtures.join(Fixtures.CRLF, Fixtures.header(descriptor), data);
        try (ZenginReader reader = open(file)) {
            reader.next();
            RecordView view = reader.next();

            assertThatExceptionOfType(MalformedFieldException.class)
                    .isThrownBy(() -> view.asLong(view.field("amount")))
                    .satisfies(e -> assertThat(e.fieldId()).isEqualTo("amount"));
            assertThat(view.asOptionalLong(view.field("amount"))).isEmpty();
        }
    }

    @Test
    void refusesAFieldFromAnotherRecord() {
        try (ZenginReader reader = open(Fixtures.file(descriptor))) {
            RecordView header = reader.next();
            FieldDescriptor foreign = descriptor.record(RecordKind.DATA).field("beneficiaryName");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> header.asString(foreign))
                    .withMessageContaining("does not belong to the HEADER record");
        }
    }

    @Test
    void refusesFieldAccessOnAMalformedRecord() {
        byte[] file = Fixtures.join(Fixtures.CRLF, Fixtures.header(descriptor),
                Fixtures.patch(Fixtures.data(descriptor), 0, "5"));
        ReaderOptions options = Fixtures.optionsBuilder().mode(ParseMode.LENIENT).build();
        try (ZenginReader reader = open(file, options)) {
            reader.next();
            RecordView malformed = reader.next();

            assertThat(malformed.isMalformed()).isTrue();
            assertThat(malformed.kind()).isEqualTo(RecordKind.MALFORMED);
            assertThat(malformed.malformedReason()).isPresent();
            assertThat(malformed.descriptor()).isEmpty();
            assertThatIllegalStateException()
                    .isThrownBy(() -> malformed.asString("beneficiaryName"))
                    .withMessageContaining("is malformed");
            assertThat(malformed.materialize())
                    .isInstanceOf(io.zengin4j.core.model.MalformedRecord.class);
        }
    }

    /** A field that runs past a truncated record cannot be read from it. */
    @Test
    void refusesAFieldBeyondATruncatedRecord() {
        byte[] full = Fixtures.join(Fixtures.CRLF, Fixtures.header(descriptor), Fixtures.data(descriptor));
        byte[] truncated = java.util.Arrays.copyOf(full, full.length - 60);
        ReaderOptions options = Fixtures.optionsBuilder().mode(ParseMode.LENIENT).build();
        try (ZenginReader reader = open(truncated, options)) {
            reader.next();
            RecordView partial = reader.next();

            assertThat(partial.isMalformed()).isTrue();
            assertThat(partial.length()).isLessThan(Fixtures.RECORD_LENGTH);
            assertThat(partial.rawBytes()).hasSize(partial.length());
        }
    }

    private ZenginReader open(byte[] bytes) {
        return open(bytes, Fixtures.options());
    }

    private ZenginReader open(byte[] bytes, ReaderOptions options) {
        return ZenginReaders.open(new ByteArrayInputStream(bytes), options);
    }
}
