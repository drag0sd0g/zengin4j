package io.zengin4j.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.core.charset.CharacterSet;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FieldType;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.model.Batch;
import io.zengin4j.core.model.DataRecord;
import io.zengin4j.core.model.ZenginFile;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Everything the generator writes is writable in every format.
 *
 * <p>This exists because it did not, and the gap cost something. The name list
 * contained ﾀﾞﾐｰ ｻﾌﾞﾛｳ — with a 長音 ｰ, which the standard never permits — and
 * the fixture tests did not catch it, because they check the worked-example
 * record rather than generated output. It surfaced only when
 * {@code zengin validate} was run over a 25-payment payroll file and reported
 * six {@code V-202} errors against this project's own generator.
 *
 * <p>The lesson is about coverage of the <em>data</em>, not of the code: every
 * line of the generator was covered while one of its eight values was wrong. So
 * these tests generate enough records to draw every name, and check each
 * against the strictest field it could land in.
 */
class GeneratorNamesTest {
    /** Enough payments that all eight names are drawn with overwhelming probability. */
    private static final int ENOUGH_TO_DRAW_EVERY_NAME = 200;

    static List<FormatId> formats() {
        return FormatFixtures.supported();
    }

    /**
     * Every generated text field satisfies its own character class, for every
     * format — including the payroll formats, which are strictest.
     */
    @ParameterizedTest
    @MethodSource("formats")
    void everyGeneratedTextFieldIsWritableInItsClass(FormatId id) {
        FormatFixtures fixtures = FormatFixtures.forFormat(id);
        byte[] bytes = ZenginGenerator.builder()
                .format(id)
                .payments(ENOUGH_TO_DRAW_EVERY_NAME)
                .seed(31L)
                .build()
                .generate();

        ZenginFile file = ZenginReaders.readFile(
                new ByteArrayInputStream(bytes), fixtures.readerOptions());
        RecordDescriptor layout = fixtures.descriptor()
                .record(io.zengin4j.core.format.RecordKind.DATA);

        for (Batch batch : file.batches()) {
            for (DataRecord record : batch.data()) {
                for (FieldDescriptor field : layout.fields()) {
                    if (field.type() != FieldType.C) {
                        continue;
                    }
                    assertThat(CharacterSet.validate(record.rawBytes(), field.offset(),
                                    field.length(), field.charClass()))
                            .as("%s record %d field %s (%s)", id.value(), record.recordNumber(),
                                    field.id(), field.charClass())
                            .isEmpty();
                }
            }
        }
    }

    /**
     * Every name in the list, held against the strictest class any format uses
     * for a name — checked directly, so a bad name fails even if the random
     * draw happens not to pick it.
     */
    @Test
    void everyNameInTheListIsValidUnderTheStrictestNameClass() {
        for (String name : generatedNames()) {
            byte[] encoded = ZenginCharset.MS932.encode(name);
            for (io.zengin4j.core.charset.CharacterClass characterClass
                    : List.of(io.zengin4j.core.charset.CharacterClass.PAYROLL_NAME,
                            io.zengin4j.core.charset.CharacterClass.PARTY_NAME)) {
                assertThat(CharacterSet.validate(encoded, 0, encoded.length, characterClass))
                        .as("'%s' is not writable as a %s", name, characterClass)
                        .isEmpty();
            }
        }
    }

    /** The specific byte that got through: 0xB0, the 長音 ｰ. */
    @Test
    void noGeneratedNameContainsTheProlongedSoundMark() {
        for (String name : generatedNames()) {
            assertThat(ZenginCharset.MS932.encode(name))
                    .as("'%s' contains ｰ (0xB0), which the standard never permits; "
                            + "write a long vowel as - (0x2D) where the field admits it", name)
                    .doesNotContain((byte) 0xB0);
        }
    }

    /** Small kana are excluded from every class too. */
    @Test
    void noGeneratedNameContainsASmallKana() {
        for (String name : generatedNames()) {
            for (byte b : ZenginCharset.MS932.encode(name)) {
                int value = b & 0xFF;
                assertThat(value >= 0xA7 && value <= 0xAF)
                        .as("'%s' contains a small kana (0x%02X)", name, value)
                        .isFalse();
            }
        }
    }

    /**
     * The names the generator can actually emit, recovered from generated
     * output rather than from a copy of the list — a copy would agree with
     * itself while the real list drifted.
     */
    private static List<String> generatedNames() {
        FormatFixtures fixtures = SougouFurikomiFixtures.create();
        byte[] bytes = ZenginGenerator.builder()
                .payments(ENOUGH_TO_DRAW_EVERY_NAME)
                .seed(31L)
                .build()
                .generate();

        ZenginFile file = ZenginReaders.readFile(
                new ByteArrayInputStream(bytes), fixtures.readerOptions());
        FieldDescriptor name = fixtures.descriptor()
                .record(io.zengin4j.core.format.RecordKind.DATA)
                .field("beneficiaryName");

        List<String> names = file.allData().stream()
                .map(record -> new String(record.rawBytes(), name.offset(), name.length(),
                        ZenginCharset.MS932.charset()).strip())
                .distinct()
                .sorted()
                .toList();

        assertThat(names).as("the draw should have reached every name").hasSize(8);
        return names;
    }
}
