package io.zengin4j.core.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.zengin4j.core.model.SeparatorStyle;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Issue 1.6: framing with optional separators (R-C6 to R-C10, §19.1).
 */
class RecordFramerTest {

    private final RecordFramer framer = new RecordFramer(4);

    @Test
    void reportsItsRecordLength() {
        assertThat(framer.recordLength()).isEqualTo(4);
    }

    @Test
    void refusesANonPositiveRecordLength() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RecordFramer(0));
    }

    @Test
    void findsTheNextRecordAcrossEverySeparatorConvention() {
        assertThat(offsetAfterFirstRecord("AAAABBBB")).isEqualTo(4);
        assertThat(offsetAfterFirstRecord("AAAA\r\nBBBB")).isEqualTo(6);
        assertThat(offsetAfterFirstRecord("AAAA\nBBBB")).isEqualTo(5);
        assertThat(offsetAfterFirstRecord("AAAA\rBBBB")).isEqualTo(5);
    }

    @Test
    void stopsAtTheBufferEnd() {
        byte[] buffer = "AAAA".getBytes(StandardCharsets.US_ASCII);

        assertThat(framer.nextRecordOffset(buffer, 0, buffer.length)).isEqualTo(4);
        assertThat(framer.skipSeparators(buffer, 4, buffer.length)).isEqualTo(4);
    }

    @Test
    void identifiesSeparatorBytes() {
        assertThat(RecordFramer.isSeparator((byte) '\r')).isTrue();
        assertThat(RecordFramer.isSeparator((byte) '\n')).isTrue();
        assertThat(RecordFramer.isSeparator((byte) ' ')).isFalse();
        assertThat(RecordFramer.isSeparator(RecordFramer.EOF_BYTE)).isFalse();
    }

    @Test
    void detectsAByteOrderMark() {
        byte[] withMark = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '1'};
        byte[] without = new byte[] {'1', '2', '3', '4'};

        assertThat(RecordFramer.startsWithByteOrderMark(withMark, 0, withMark.length)).isTrue();
        assertThat(RecordFramer.startsWithByteOrderMark(without, 0, without.length)).isFalse();
        assertThat(RecordFramer.startsWithByteOrderMark(withMark, 0, 2)).isFalse();
    }

    @Test
    void classifiesSeparatorRuns() {
        assertThat(RecordFramer.classify(new byte[] {'\r', '\n'})).contains(SeparatorStyle.CRLF);
        assertThat(RecordFramer.classify(new byte[] {'\n'})).contains(SeparatorStyle.LF);
        assertThat(RecordFramer.classify(new byte[] {'\r'})).contains(SeparatorStyle.CR);
        assertThat(RecordFramer.classify(new byte[0])).contains(SeparatorStyle.NONE);
        assertThat(RecordFramer.classify(new byte[] {'\n', '\r'})).isEmpty();
    }

    private int offsetAfterFirstRecord(String content) {
        byte[] buffer = content.getBytes(StandardCharsets.US_ASCII);
        return framer.nextRecordOffset(buffer, 0, buffer.length);
    }
}
