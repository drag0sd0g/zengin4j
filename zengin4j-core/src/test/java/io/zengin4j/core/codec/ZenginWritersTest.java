package io.zengin4j.core.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.zengin4j.core.error.FormatDescriptorException;
import io.zengin4j.core.error.ZenginIOException;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.model.FileFraming;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.testing.Fixtures;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Issue 2.2: deterministic writing (R-C19) that reproduces what it read
 * (R-D5).
 */
class ZenginWritersTest {
    private final FormatDescriptor descriptor = Fixtures.descriptor();

    private final ReaderOptions options = Fixtures.optionsBuilder()
            .byteOrderMark(ByteOrderMarkPolicy.STRIP)
            .build();

    @Test
    void reproducesEveryFramingConvention() {
        for (SeparatorStyle style : List.of(SeparatorStyle.NONE, SeparatorStyle.CR,
                SeparatorStyle.LF, SeparatorStyle.CRLF)) {
            for (boolean trailing : List.of(true, false)) {
                for (boolean eof : List.of(true, false)) {
                    if (style == SeparatorStyle.NONE && trailing) {
                        continue;
                    }
                    byte[] source = Fixtures.framed(descriptor, new FileFraming(false, style, trailing, eof));

                    byte[] written = ZenginWriters.toByteArray(read(source), WriterOptions.defaults());

                    assertThat(written)
                            .as("%s trailing=%s eof=%s", style, trailing, eof)
                            .isEqualTo(source);
                }
            }
        }
    }

    /** R-C10: a stripped byte order mark is written back, so the file survives intact. */
    @Test
    void reproducesAByteOrderMark() {
        byte[] source = Fixtures.framed(descriptor, new FileFraming(true, SeparatorStyle.CRLF, true, false));

        ZenginFile parsed = read(source);

        assertThat(parsed.framing().byteOrderMarkPresent()).isTrue();
        assertThat(ZenginWriters.toByteArray(parsed, WriterOptions.defaults())).isEqualTo(source);
    }

    /** OQ-4: whether a separator followed the last record is part of the file, and is reproduced. */
    @Test
    void reproducesTheAbsenceOfATrailingSeparator() {
        byte[] withTrailing = Fixtures.framed(descriptor, new FileFraming(false, SeparatorStyle.CRLF, true, false));
        byte[] without = Fixtures.framed(descriptor, new FileFraming(false, SeparatorStyle.CRLF, false, false));

        assertThat(withTrailing).hasSize(without.length + 2);
        assertThat(read(withTrailing).framing().trailingSeparator()).isTrue();
        assertThat(read(without).framing().trailingSeparator()).isFalse();
        assertThat(ZenginWriters.toByteArray(read(without), WriterOptions.defaults())).isEqualTo(without);
    }

    @Test
    void refusesToWriteAFileThatMixedSeparatorConventions() {
        byte[] mixed = Fixtures.concat(List.of(
                Fixtures.header(descriptor), Fixtures.CRLF,
                Fixtures.data(descriptor), Fixtures.LF,
                Fixtures.trailer(descriptor, 1, Fixtures.AMOUNT), Fixtures.CRLF,
                Fixtures.end(descriptor)));

        ZenginFile parsed = read(mixed);

        assertThat(parsed.framing().isReproducible()).isFalse();
        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> ZenginWriters.toByteArray(parsed, WriterOptions.defaults()))
                .withMessageContaining("mixed record separator conventions");
    }

    /** R-C9: and choosing a convention explicitly makes such a file writable again. */
    @Test
    void animposedSeparatorRescuesAMixedFile() {
        byte[] mixed = Fixtures.concat(List.of(
                Fixtures.header(descriptor), Fixtures.CRLF,
                Fixtures.data(descriptor), Fixtures.LF,
                Fixtures.trailer(descriptor, 1, Fixtures.AMOUNT), Fixtures.CRLF,
                Fixtures.end(descriptor)));

        byte[] written = ZenginWriters.toByteArray(read(mixed),
                WriterOptions.separator(SeparatorStyle.CRLF));

        assertThat(read(written).framing().separator()).isEqualTo(SeparatorStyle.CRLF);
        assertThat(written).hasSize(4 * (Fixtures.RECORD_LENGTH + 2));
    }

    /**
     * R-D5: records are written from the bytes they carry. Filler this library
     * does not interpret must come back untouched.
     */
    @Test
    void writesRecordsFromTheirRawBytesRatherThanReEncodingThem() {
        byte[] header = Fixtures.patch(Fixtures.header(descriptor), 110, "XYZ");
        byte[] source = Fixtures.join(Fixtures.CRLF, header, Fixtures.data(descriptor),
                Fixtures.trailer(descriptor, 1, Fixtures.AMOUNT), Fixtures.end(descriptor));

        byte[] written = ZenginWriters.toByteArray(read(source), WriterOptions.defaults());

        assertThat(written).isEqualTo(source);
        assertThat(new String(written, 110, 3, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("XYZ");
    }

    /** Malformed records keep their place, because every record carries its position. */
    @Test
    void preservesRecordOrderIncludingMalformedRecords() {
        byte[] source = Fixtures.join(Fixtures.CRLF, Fixtures.header(descriptor),
                Fixtures.patch(Fixtures.data(descriptor), 0, "5"),
                Fixtures.data(descriptor),
                Fixtures.trailer(descriptor, 1, Fixtures.AMOUNT), Fixtures.end(descriptor));
        ReaderOptions lenient = Fixtures.optionsBuilder().mode(ParseMode.LENIENT).build();

        ZenginFile parsed = ZenginReaders.readFile(new ByteArrayInputStream(source), lenient);

        assertThat(ZenginWriters.toByteArray(parsed, WriterOptions.defaults())).isEqualTo(source);
    }

    @Test
    void writesToAStreamAndToDisk(@TempDir Path directory) throws IOException {
        ZenginFile file = read(Fixtures.file(descriptor));

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ZenginWriters.write(file, stream, WriterOptions.defaults());

        Path path = directory.resolve("out.txt");
        ZenginWriters.write(file, path, WriterOptions.defaults());

        assertThat(stream.toByteArray()).isEqualTo(Fixtures.file(descriptor));
        assertThat(Files.readAllBytes(path)).isEqualTo(Fixtures.file(descriptor));
    }

    @Test
    void reportsAnUnwritablePath(@TempDir Path directory) {
        ZenginFile file = read(Fixtures.file(descriptor));

        assertThatExceptionOfType(ZenginIOException.class)
                .isThrownBy(() -> ZenginWriters.write(file, directory, WriterOptions.defaults()))
                .withMessageContaining("writing");
    }

    @Test
    void exposesSeparatorBytesAndRefusesMixed() {
        assertThat(ZenginWriters.separatorBytes(SeparatorStyle.CRLF)).containsExactly('\r', '\n');
        assertThat(ZenginWriters.separatorBytes(SeparatorStyle.NONE)).isEmpty();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ZenginWriters.separatorBytes(SeparatorStyle.MIXED))
                .withMessageContaining("MIXED is an observation");
    }

    @Test
    void writerOptionsResolveAgainstTheFilesOwnFraming() {
        FileFraming fileFraming = new FileFraming(false, SeparatorStyle.LF, true, false);

        assertThat(WriterOptions.defaults().framingOverride()).isEmpty();
        assertThat(WriterOptions.defaults().resolve(fileFraming)).isEqualTo(fileFraming);
        assertThat(WriterOptions.framing(FileFraming.none()).resolve(fileFraming))
                .isEqualTo(FileFraming.none());
        assertThat(WriterOptions.separator(SeparatorStyle.CR).resolve(fileFraming).separator())
                .isEqualTo(SeparatorStyle.CR);
        assertThat(WriterOptions.separator(SeparatorStyle.CR).resolve(fileFraming).trailingSeparator())
                .isTrue();
        assertThat(WriterOptions.separator(SeparatorStyle.NONE).resolve(fileFraming).trailingSeparator())
                .isFalse();
    }

    private ZenginFile read(byte[] bytes) {
        return ZenginReaders.readFile(new ByteArrayInputStream(bytes), options);
    }
}
