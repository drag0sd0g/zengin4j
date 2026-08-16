package io.zengin4j.core.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.zengin4j.core.charset.CharacterClass;
import io.zengin4j.core.error.CharacterSetViolationException;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.testing.Fixtures;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Issue 3.1: strict mode over the permitted character set (R-C13).
 */
class CharacterPolicyTest {

    private final FormatDescriptor descriptor = Fixtures.descriptor();

    /**
     * A beneficiary name written with the long vowel mark instead of a hyphen —
     * the mistake that looks correct.
     */
    private byte[] fileWithProlongedSoundMark() {
        byte[] data = Fixtures.data(descriptor, "ﾔﾏﾀﾞｰﾀﾛｳ", Fixtures.AMOUNT);
        return Fixtures.join(Fixtures.CRLF, Fixtures.header(descriptor), data,
                Fixtures.trailer(descriptor, 1, Fixtures.AMOUNT), Fixtures.end(descriptor));
    }

    /** Collects what the reader reports, since warnings are the reader's, not the file's. */
    private final List<ZenginWarning> collected = new java.util.ArrayList<>();

    private ReaderOptions options(CharacterPolicy policy) {
        return ReaderOptions.builder()
                .registry(Fixtures.registry())
                .allowUnverifiedFormats(true)
                .characterPolicy(policy)
                .warningListener(collected::add)
                .build();
    }

    /** The default. Content is not the reader's business (R-E1). */
    @Test
    void ignoresCharacterViolationsByDefault() {
        assertThat(Fixtures.options().characterPolicy()).isEqualTo(CharacterPolicy.IGNORE);

        ZenginFile file = ZenginReaders.readFile(
                new ByteArrayInputStream(fileWithProlongedSoundMark()), options(CharacterPolicy.IGNORE));

        assertThat(file.totalRecords()).isEqualTo(4);
        assertThat(collected).extracting(ZenginWarning::code)
                .doesNotContain(ZenginWarning.CHARACTER_NOT_PERMITTED);
    }

    @Test
    void warnsWithTheOffsetOfEveryOffendingByte() {
        ZenginFile file = ZenginReaders.readFile(
                new ByteArrayInputStream(fileWithProlongedSoundMark()), options(CharacterPolicy.WARN));

        // Reading still succeeds and returns every record.
        assertThat(file.totalRecords()).isEqualTo(4);

        List<ZenginWarning> warnings = collected.stream()
                .filter(warning -> warning.code().equals(ZenginWarning.CHARACTER_NOT_PERMITTED))
                .toList();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).messageEn()).contains("long vowel mark").contains("0x2D");
        // 受取人名 starts at offset 50 of the data record. ﾔﾏﾀﾞｰ is five bytes —
        // the dakuten is its own byte — so the ｰ sits at 50 + 4. The data record
        // is the second in the file, each record plus CRLF being 122 bytes.
        assertThat(warnings.get(0).byteOffset()).isEqualTo(122 + 50 + 4);
    }

    /** R-C13: refuse the file, naming every violation in the offending record. */
    @Test
    void rejectsTheFileWhenToldTo() {
        assertThatExceptionOfType(CharacterSetViolationException.class)
                .isThrownBy(() -> ZenginReaders.readFile(
                        new ByteArrayInputStream(fileWithProlongedSoundMark()),
                        options(CharacterPolicy.REJECT)))
                .satisfies(thrown -> {
                    assertThat(thrown.recordNumber()).isEqualTo(2);
                    assertThat(thrown.violations()).hasSize(1);
                    assertThat(thrown.violations().get(0).isProlongedSoundMark()).isTrue();
                    assertThat(thrown.messageEn()).contains("record 2").contains("long vowel");
                    assertThat(thrown.messageJa()).isNotBlank();
                });
    }

    /** A clean file passes under every policy, which is what makes the strict mode usable. */
    @Test
    void aCleanFilePassesUnderEveryPolicy() {
        for (CharacterPolicy policy : CharacterPolicy.values()) {
            ZenginFile file = ZenginReaders.readFile(
                    new ByteArrayInputStream(Fixtures.file(descriptor)), options(policy));

            assertThat(file.totalRecords()).as("%s", policy).isEqualTo(4);
            assertThat(collected).extracting(ZenginWarning::code)
                    .doesNotContain(ZenginWarning.CHARACTER_NOT_PERMITTED);
        }
    }

    /** Filler is never policed: R-D5 requires those bytes back verbatim, whatever they are. */
    @Test
    void fillerIsNotChecked() {
        byte[] header = Fixtures.patch(Fixtures.header(descriptor), 110, "abc");
        byte[] source = Fixtures.join(Fixtures.CRLF, header, Fixtures.data(descriptor),
                Fixtures.trailer(descriptor, 1, Fixtures.AMOUNT), Fixtures.end(descriptor));
        ZenginFile file = ZenginReaders.readFile(
                new ByteArrayInputStream(source), options(CharacterPolicy.REJECT));

        assertThat(file.totalRecords()).isEqualTo(4);
        assertThat(ZenginWriters.toByteArray(file, WriterOptions.defaults())).isEqualTo(source);
    }

    @Test
    void validatesARecordAgainstItsDescriptor() {
        byte[] clean = Fixtures.data(descriptor);
        byte[] dirty = Fixtures.data(descriptor, "ﾔﾏﾀﾞｰﾀﾛｳ", Fixtures.AMOUNT);

        assertThat(RecordCharacters.validate(clean, descriptor.record(RecordKind.DATA))).isEmpty();
        assertThat(RecordCharacters.isClean(clean, descriptor.record(RecordKind.DATA))).isTrue();

        assertThat(RecordCharacters.validate(dirty, descriptor.record(RecordKind.DATA)))
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.offset()).isEqualTo(54);
                    assertThat(violation.permitted()).isEqualTo(CharacterClass.PARTY_NAME);
                });
        assertThat(RecordCharacters.isClean(dirty, descriptor.record(RecordKind.DATA))).isFalse();
    }
}
