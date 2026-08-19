package io.zengin4j.core.codec;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.error.MalformedFieldException;
import io.zengin4j.core.format.FieldType;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.testing.Fixtures;
import org.junit.jupiter.api.Test;

/// Issue 1.7: allocation-free numeric decoding (§19.2, R-MEM3) and the two
/// padding conventions (§12.8).
class FieldCodecTest {

    @Test
    void decodesZonedDecimal() {
        byte[] buffer = "0000150000".getBytes(StandardCharsets.US_ASCII);

        assertThat(FieldCodec.decodeNumeric(buffer, 0, 10)).isEqualTo(150_000L);
        assertThat(FieldCodec.decodeNumeric(buffer, 4, 6)).isEqualTo(150_000L);
        assertThat(FieldCodec.decodeNumeric(buffer, 0, 4)).isZero();
    }

    @Test
    void decodesTheTrailerCeiling() {
        byte[] buffer = "999999999999".getBytes(StandardCharsets.US_ASCII);

        assertThat(FieldCodec.decodeNumeric(buffer, 0, 12)).isEqualTo(FieldCodec.MAX_TRAILER_TOTAL);
    }

    @Test
    void namesTheOffendingByteAndOffset() {
        byte[] buffer = "12X4".getBytes(StandardCharsets.US_ASCII);

        assertThatExceptionOfType(MalformedFieldException.class)
                .isThrownBy(() -> FieldCodec.decodeNumeric(buffer, 0, 4, "amount", 80))
                .satisfies(e -> {
                    assertThat(e.fieldId()).isEqualTo("amount");
                    assertThat(e.byteOffset()).isEqualTo(82);
                    assertThat(e.offendingByte()).isEqualTo('X');
                })
                .withMessageContaining("expected an ASCII digit");
    }

    @Test
    void offersANonThrowingDecode() {
        assertThat(FieldCodec.tryDecodeNumeric("0042".getBytes(StandardCharsets.US_ASCII), 0, 4))
                .isEqualTo(OptionalLong.of(42));
        assertThat(FieldCodec.tryDecodeNumeric("00 2".getBytes(StandardCharsets.US_ASCII), 0, 4))
                .isEmpty();
    }

    @Test
    void treatsAnAllSpacesNumericFieldAsMalformed() {
        byte[] buffer = "    ".getBytes(StandardCharsets.US_ASCII);

        assertThat(FieldCodec.tryDecodeNumeric(buffer, 0, 4)).isEmpty();
        assertThatExceptionOfType(MalformedFieldException.class)
                .isThrownBy(() -> FieldCodec.decodeNumeric(buffer, 0, 4));
    }

    /// A C field loses its trailing pad; an N field keeps its leading zeros.
    @Test
    void stripsOnlyThePaddingTheFieldTypeDefines() {
        var descriptor = Fixtures.descriptor().record(RecordKind.DATA);
        byte[] record = Fixtures.data(Fixtures.descriptor());

        assertThat(FieldCodec.decodeField(record, 0, descriptor.field("beneficiaryName"),
                ZenginCharset.MS932)).isEqualTo(Fixtures.BENEFICIARY);
        assertThat(FieldCodec.decodeField(record, 0, descriptor.field("amount"),
                ZenginCharset.MS932)).isEqualTo("0000150000");
        assertThat(FieldCodec.decodeText(record, 0, 1, ZenginCharset.MS932)).isEqualTo("2");
    }

    @Test
    void decodesAnAllPaddingTextFieldAsEmpty() {
        var descriptor = Fixtures.descriptor().record(RecordKind.DATA);
        byte[] record = Fixtures.data(Fixtures.descriptor());

        assertThat(FieldCodec.decodeField(record, 0, descriptor.field("identification"),
                ZenginCharset.MS932)).isEmpty();
    }

    @Test
    void encodesNumericFieldsRightAlignedAndZeroPadded() {
        byte[] buffer = new byte[10];

        FieldCodec.encodeNumeric(150_000L, buffer, 0, 10);

        assertThat(new String(buffer, StandardCharsets.US_ASCII)).isEqualTo("0000150000");
    }

    @Test
    void refusesNumericValuesThatDoNotFit() {
        byte[] buffer = new byte[4];

        assertThatIllegalArgumentException()
                .isThrownBy(() -> FieldCodec.encodeNumeric(99_999L, buffer, 0, 4))
                .withMessageContaining("does not fit in an N(4) field");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FieldCodec.encodeNumeric(-1L, buffer, 0, 4))
                .withMessageContaining("cannot carry a negative value");
    }

    @Test
    void encodesTextLeftAlignedAndSpacePadded() {
        byte[] buffer = new byte[10];

        FieldCodec.encodeText("ﾀﾛｳ", buffer, 0, 10, ZenginCharset.MS932, PadPolicy.LEFT_ALIGNED_SPACE);

        assertThat(ZenginCharset.MS932.decode(buffer, 0, 10)).isEqualTo("ﾀﾛｳ       ");
    }

    @Test
    void encodesTextRightAlignedWhenTheFieldTypeSaysSo() {
        byte[] buffer = new byte[6];

        FieldCodec.encodeText("42", buffer, 0, 6, ZenginCharset.MS932, PadPolicy.of(FieldType.N));

        assertThat(ZenginCharset.MS932.decode(buffer, 0, 6)).isEqualTo("000042");
    }

    /// P5: never truncate silently. A name that does not fit is an error, not a shorter name.
    @Test
    void refusesTextThatDoesNotFit() {
        byte[] buffer = new byte[4];

        assertThatIllegalArgumentException()
                .isThrownBy(() -> FieldCodec.encodeText("ﾔﾏﾀﾞ ﾀﾛｳ", buffer, 0, 4, ZenginCharset.MS932,
                        PadPolicy.LEFT_ALIGNED_SPACE))
                .withMessageContaining("encodes to 8 bytes")
                .withMessageContaining("does not fit a 4-byte field");
    }

    /// The byte count, not the character count, is what has to fit (R-C15).
    @Test
    void measuresTheEncodedLengthInBytes() {
        byte[] buffer = new byte[10];

        FieldCodec.encodeText(Fixtures.BANK_NAME, buffer, 0, 10, ZenginCharset.MS932,
                PadPolicy.LEFT_ALIGNED_SPACE);
        assertThat(ZenginCharset.MS932.decode(buffer, 0, 10)).isEqualTo(Fixtures.BANK_NAME + "  ");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> FieldCodec.encodeText(Fixtures.BANK_NAME, buffer, 0, 10,
                        ZenginCharset.UTF_8, PadPolicy.LEFT_ALIGNED_SPACE))
                .withMessageContaining("encodes to 24 bytes");
    }

    @Test
    void fillsARange() {
        byte[] buffer = new byte[5];

        FieldCodec.fill(buffer, 1, 3, (byte) '0');

        assertThat(buffer).containsExactly(0, '0', '0', '0', 0);
    }

    @Test
    void mapsFieldTypesToPadPolicies() {
        assertThat(PadPolicy.of(FieldType.N)).isEqualTo(PadPolicy.RIGHT_ALIGNED_ZERO);
        assertThat(PadPolicy.of(FieldType.C)).isEqualTo(PadPolicy.LEFT_ALIGNED_SPACE);
        assertThat(PadPolicy.RIGHT_ALIGNED_ZERO.padByte()).isEqualTo((byte) '0');
        assertThat(PadPolicy.LEFT_ALIGNED_SPACE.padByte()).isEqualTo((byte) ' ');
        assertThat(PadPolicy.LEFT_ALIGNED_SPACE.alignment()).isEqualTo(io.zengin4j.core.format.Alignment.LEFT);
        assertThat(FieldType.N.alignment()).isEqualTo(io.zengin4j.core.format.Alignment.RIGHT);
    }
}
