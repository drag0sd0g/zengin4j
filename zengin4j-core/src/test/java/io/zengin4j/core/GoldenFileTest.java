package io.zengin4j.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.core.codec.ByteOrderMarkPolicy;
import io.zengin4j.core.codec.ReaderOptions;
import io.zengin4j.core.codec.WriterOptions;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.codec.ZenginWriters;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.model.Batch;
import io.zengin4j.core.model.DataRecord;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.model.ZenginRecord;
import io.zengin4j.core.testing.Fixtures;
import io.zengin4j.core.testing.RandomZenginFiles;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Issue 2.5 — golden files (R-T8).
 *
 * <p>A committed file and a committed rendering of what parsing it produces.
 * Any change to how bytes are decoded shows up as a diff a reviewer can read,
 * rather than as a number moving in a coverage report.
 *
 * <p>The rendering is field-per-line rather than the JSON R-T8 names. See
 * {@code docs/adr/0018-golden-files-are-text-not-json.md}.
 *
 * <p>To update the goldens after an intentional change:
 *
 * <pre>./gradlew :zengin4j-core:test --tests '*GoldenFileTest*' -Pgolden.regenerate</pre>
 *
 * <p>Then read the diff before committing it. A golden updated without being
 * read is worse than no golden at all.
 */
class GoldenFileTest {

    /**
     * The corpus lives under {@code input/} because the identifier scan cannot
     * read a fixed-length file: fields abut with no separator, so every digit
     * run begins with a データ区分 constant rather than with the 9 the
     * convention requires (R-L5). {@code input/} is excluded there; the
     * rendering below — one field per line — is not, so every value in the
     * corpus is still scanned, in the form where scanning works.
     */
    private static final String INPUT = "/conformance/input/sougou-furikomi.txt";

    private static final String EXPECTED = "/conformance/sougou-furikomi.expected.txt";

    /** Fixed: the corpus must not change when the generator's randomness is re-seeded. */
    private static final long GOLDEN_SEED = 20_260_816L;

    private final FormatDescriptor descriptor = Fixtures.descriptor();

    private final ReaderOptions options = Fixtures.optionsBuilder()
            .byteOrderMark(ByteOrderMarkPolicy.STRIP)
            .build();

    @Test
    void theCorpusFileStillParsesToTheCommittedResult() {
        String rendered = render(ZenginReaders.readFile(new ByteArrayInputStream(corpus()), options));

        if (regenerating()) {
            write(EXPECTED, rendered);
            return;
        }
        String expected = new String(resource(EXPECTED), StandardCharsets.UTF_8);

        // Checked separately so this failure names itself. A checkout that
        // rewrote the line endings otherwise fails the comparison below with a
        // diff of invisible characters, on Windows and nowhere else (R-T18).
        assertThat(expected)
                .as("the committed rendering reached the test with CRLF line endings, so git"
                        + " rewrote it on checkout — which .gitattributes exists to prevent")
                .doesNotContain("\r\n");
        assertThat(rendered).isEqualTo(expected);
    }

    /** INV-1 against a file that is committed rather than generated. */
    @Test
    void theCorpusFileRoundTripsByteForByte() {
        byte[] bytes = corpus();

        ZenginFile parsed = ZenginReaders.readFile(new ByteArrayInputStream(bytes), options);

        assertThat(ZenginWriters.toByteArray(parsed, WriterOptions.defaults())).isEqualTo(bytes);
    }

    /**
     * The corpus file is produced by the deterministic generator, so this also
     * pins the generator: if it stops producing the same bytes for the same
     * seed, the golden corpus stops being reproducible (R-CLI3).
     */
    @Test
    void theGeneratorStillProducesTheCorpusFile() {
        byte[] generated = generate();

        if (regenerating()) {
            write(INPUT, generated);
            return;
        }
        assertThat(generated).isEqualTo(resource(INPUT));
    }

    /**
     * The corpus bytes. While regenerating they come from the generator, since
     * the committed copy is what is being replaced — which keeps regeneration
     * a single pass rather than a bootstrap dance.
     */
    private byte[] corpus() {
        return regenerating() ? generate() : resource(INPUT);
    }

    private byte[] generate() {
        return RandomZenginFiles.bytes(new Random(GOLDEN_SEED), descriptor).bytes();
    }

    // --------------------------------------------------------------- rendering

    /**
     * Renders a parsed file one field per line. Stable, diffable, and it never
     * prints a value the record does not carry.
     */
    private String render(ZenginFile file) {
        StringBuilder out = new StringBuilder();
        out.append("format          ").append(file.format()).append('\n')
                .append("records         ").append(file.totalRecords()).append('\n')
                .append("batches         ").append(file.batches().size()).append('\n')
                .append("separator       ").append(file.framing().separator()).append('\n')
                .append("trailingSep     ").append(file.framing().trailingSeparator()).append('\n')
                .append("byteOrderMark   ").append(file.framing().byteOrderMarkPresent()).append('\n')
                .append("trailingEofByte ").append(file.framing().trailingEofByte()).append('\n');

        for (Batch batch : file.batches()) {
            out.append('\n').append("=== batch ===\n");
            renderRecord(out, batch.header());
            for (DataRecord data : batch.data()) {
                renderRecord(out, data);
            }
            batch.trailer().ifPresent(trailer -> renderRecord(out, trailer));
            out.append("computedCount   ").append(batch.computedCount()).append('\n')
                    .append("computedTotal   ").append(batch.computedTotal()).append('\n');
        }
        file.endRecord().ifPresent(end -> {
            out.append('\n').append("=== end ===\n");
            renderRecord(out, end);
        });
        return out.toString();
    }

    private void renderRecord(StringBuilder out, ZenginRecord record) {
        out.append("-- record ").append(record.recordNumber())
                .append(' ').append(record.kind())
                .append(" @").append(record.byteOffset()).append('\n');
        RecordDescriptor layout = descriptor.record(record.kind());
        byte[] bytes = record.rawBytes();
        for (FieldDescriptor field : layout.fields()) {
            String value = io.zengin4j.core.codec.FieldCodec.decodeField(
                    bytes, 0, field, io.zengin4j.core.charset.ZenginCharset.MS932);
            out.append("   ").append(pad(field.id())).append(" [")
                    .append(field.offset()).append('+').append(field.length()).append("] ")
                    .append(value.isEmpty() ? "(empty)" : value)
                    .append('\n');
        }
    }

    private static String pad(String id) {
        return id.length() >= 24 ? id : id + " ".repeat(24 - id.length());
    }

    // ---------------------------------------------------------------- fixtures

    private static boolean regenerating() {
        return Boolean.getBoolean("zengin4j.golden.regenerate");
    }

    private static byte[] resource(String name) {
        try (InputStream stream = GoldenFileTest.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new AssertionError("missing golden resource " + name
                        + "; regenerate with -Pgolden.regenerate");
            }
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Writes back into the source tree, not the build output, so the diff is reviewable. */
    private static void write(String name, byte[] content) {
        Path path = Path.of("src/test/resources").resolve(name.substring(1));
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(String name, String content) {
        write(name, content.getBytes(StandardCharsets.UTF_8));
    }
}
