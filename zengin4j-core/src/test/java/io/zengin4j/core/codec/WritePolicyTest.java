package io.zengin4j.core.codec;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.zengin4j.core.charset.CharacterSet;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.kana.UnmappableCharacterPolicy;
import io.zengin4j.core.kana.UntransliterableCharacterException;
import io.zengin4j.core.loss.LossCollector;
import io.zengin4j.core.loss.LossKind;
import io.zengin4j.core.loss.LossSeverity;
import org.junit.jupiter.api.Test;

/// The write-side character policies (R-C18).
///
/// Deferred from Epic 3, because `TRANSLITERATE` needed an engine that
/// did not exist yet. The default has always been to refuse — writing a
/// character the standard forbids means producing a file this library's own
/// validator rejects.
class WritePolicyTest {

    private static final RecordDescriptor DATA = FormatRegistry.defaults()
            .byId(FormatId.of("sougou-furikomi")).orElseThrow()
            .record(RecordKind.DATA);

    private static final RecordDescriptor PAYROLL_DATA = FormatRegistry.defaults()
            .byId(FormatId.of("kyuyo-furikomi")).orElseThrow()
            .record(RecordKind.DATA);

    private static Map<String, String> withName(String name) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("beneficiaryName", name);
        return values;
    }

    private static String fieldOf(byte[] frame, RecordDescriptor record, String id) {
        FieldDescriptor field = record.field(id);
        return ZenginCharset.MS932.decode(frame, field.offset(), field.length()).strip();
    }

    // ---------------------------------------------------------------- REJECT

    @Test
    void theDefaultRefusesAFullWidthName() {
        // Full-width katakana is two bytes in MS932 and not a permitted
        // character, so this is refused on both counts.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RecordEncoder.encode(DATA, ZenginCharset.MS932,
                        withName("ヤマダ タロウ")));
    }

    @Test
    void aValueTheFieldAcceptsIsWrittenUnchangedUnderEveryPolicy() {
        for (CharacterWritePolicy policy : CharacterWritePolicy.values()) {
            var options = EncodingOptions.builder().characters(policy).build();
            var loss = new LossCollector();

            byte[] frame = RecordEncoder.encode(DATA, ZenginCharset.MS932,
                    withName("ﾔﾏﾀﾞ ﾀﾛｳ"), options, loss);

            assertThat(fieldOf(frame, DATA, "beneficiaryName"))
                    .as("%s", policy)
                    .isEqualTo("ﾔﾏﾀﾞ ﾀﾛｳ");
            assertThat(loss.isLossless()).as("%s should have nothing to record", policy).isTrue();
        }
    }

    // --------------------------------------------------------- TRANSLITERATE

    @Test
    void transliterateConvertsAFullWidthNameAndSaysWhatItDid() {
        var options = EncodingOptions.builder()
                .characters(CharacterWritePolicy.TRANSLITERATE).build();
        var loss = new LossCollector();

        byte[] frame = RecordEncoder.encode(DATA, ZenginCharset.MS932,
                withName("ガクブチ ジロウ"), options, loss);

        assertThat(fieldOf(frame, DATA, "beneficiaryName")).isEqualTo("ｶﾞｸﾌﾞﾁ ｼﾞﾛｳ");
        assertThat(loss.build().entries()).isNotEmpty();
    }

    @Test
    void transliterateLocatesItsLossesAtTheFieldTheyHappenedIn() {
        var options = EncodingOptions.builder()
                .characters(CharacterWritePolicy.TRANSLITERATE).build();
        var loss = new LossCollector();

        RecordEncoder.encode(DATA, ZenginCharset.MS932, withName("キャノン"), options, loss);

        assertThat(loss.build().entries())
                .allSatisfy(entry -> assertThat(entry.targetField()).contains("beneficiaryName"));
    }

    /// The engine still refuses what it cannot convert, whatever the policy.
    @Test
    void transliterateStillRefusesKanji() {
        var options = EncodingOptions.builder()
                .characters(CharacterWritePolicy.TRANSLITERATE).build();

        assertThatExceptionOfType(UntransliterableCharacterException.class)
                .isThrownBy(() -> RecordEncoder.encode(DATA, ZenginCharset.MS932,
                        withName("山田太郎"), options, new LossCollector()));
    }

    /// And the field's own class decides, not the format's.
    ///
    /// ヨーコ becomes ﾖ-ｺ, which a party name permits and a payroll name does
    /// not. Same policy, same input, different answer — which is the whole
    /// reason the transliterator takes a character class.
    @Test
    void transliterateAppliesTheFieldsOwnCharacterClass() {
        var options = EncodingOptions.builder()
                .characters(CharacterWritePolicy.TRANSLITERATE).build();

        byte[] party = RecordEncoder.encode(DATA, ZenginCharset.MS932,
                withName("ヨーコ"), options, new LossCollector());
        assertThat(fieldOf(party, DATA, "beneficiaryName")).isEqualTo("ﾖ-ｺ");

        assertThatExceptionOfType(UntransliterableCharacterException.class)
                .as("payroll names admit no symbols, so the hyphen has nowhere to go")
                .isThrownBy(() -> RecordEncoder.encode(PAYROLL_DATA, ZenginCharset.MS932,
                        withName("ヨーコ"), options, new LossCollector()));
    }

    @Test
    void transliterateCanBeToldToDropWhatItCannotWrite() {
        var options = EncodingOptions.builder()
                .characters(CharacterWritePolicy.TRANSLITERATE)
                .unmappable(UnmappableCharacterPolicy.DROP)
                .build();
        var loss = new LossCollector();

        byte[] frame = RecordEncoder.encode(PAYROLL_DATA, ZenginCharset.MS932,
                withName("ヨーコ"), options, loss);

        assertThat(fieldOf(frame, PAYROLL_DATA, "beneficiaryName")).isEqualTo("ﾖｺ");
        assertThat(loss.build().bySeverity(LossSeverity.MATERIAL)).isNotEmpty();
    }

    // --------------------------------------------------------------- REPLACE

    @Test
    void replaceSubstitutesTheConfiguredByteAndRecordsIt() {
        var options = EncodingOptions.builder()
                .characters(CharacterWritePolicy.REPLACE)
                .replacement((byte) '.')
                .build();
        var loss = new LossCollector();

        byte[] frame = RecordEncoder.encode(DATA, ZenginCharset.MS932,
                withName("ﾔﾏﾀﾞ*ﾀﾛｳ"), options, loss);

        assertThat(fieldOf(frame, DATA, "beneficiaryName")).isEqualTo("ﾔﾏﾀﾞ.ﾀﾛｳ");
        assertThat(loss.build().entries())
                .anySatisfy(entry -> assertThat(entry.kind()).isEqualTo(LossKind.COERCED));
    }

    /// A replacement the field would refuse is refused.
    ///
    /// `'?'` is the obvious choice and is permitted by no name class,
    /// so `REPLACE` — a policy for salvaging a value — used to produce a
    /// field `V-202` then rejected.
    @Test
    void aReplacementTheFieldWouldRefuseIsRefused() {
        var options = EncodingOptions.builder()
                .characters(CharacterWritePolicy.REPLACE)
                .replacement((byte) '?')
                .build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> RecordEncoder.encode(DATA, ZenginCharset.MS932,
                        withName("ﾔﾏﾀﾞ*ﾀﾛｳ"), options, new LossCollector()))
                .withMessageContaining("not permitted");
    }

    /// And a voicing mark as a replacement would strand itself.
    @Test
    void aVoicingMarkCannotBeUsedAsAReplacement() {
        var options = EncodingOptions.builder()
                .characters(CharacterWritePolicy.REPLACE)
                .replacement((byte) 0xDE)
                .build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> RecordEncoder.encode(DATA, ZenginCharset.MS932,
                        withName("ﾔﾏﾀﾞ*ﾀﾛｳ"), options, new LossCollector()))
                .withMessageContaining("voicing mark");
    }

    /// **Whatever the policy, what gets written is writable.**
    ///
    /// The property all three of this epic's late bugs violated: a truncation
    /// marker no class permits, a replacement byte no class permits, and a
    /// replacement that stranded a voicing mark. Each produced a field the
    /// library's own rules reject, and each was found only by looking. Stated
    /// once, here, so the next one fails a test instead.
    @Test
    void nothingAnyPolicyWritesIsEverInvalid() {
        var inputs = List.of("ﾔﾏﾀﾞ ﾀﾛｳ", "ガクブチ ジロウ", "キャノン", "ヨーコ",
                "ﾔﾏﾀﾞ*ﾀﾛｳ", "ＡＢＣ", "abc", "ｱｲｳｴｵ");

        for (RecordDescriptor record : List.of(DATA, PAYROLL_DATA)) {
            FieldDescriptor field = record.field("beneficiaryName");

            for (CharacterWritePolicy policy : CharacterWritePolicy.values()) {
                for (String input : inputs) {
                    var options = EncodingOptions.builder()
                            .characters(policy)
                            .truncation(io.zengin4j.core.kana.TruncationPolicy.TRUNCATE_SAFE)
                            .unmappable(UnmappableCharacterPolicy.DROP)
                            .build();

                    byte[] frame;
                    try {
                        Map<String, String> values = new LinkedHashMap<>();
                        values.put("beneficiaryName", input);
                        frame = RecordEncoder.encode(record, ZenginCharset.MS932, values,
                                options, new LossCollector());
                    } catch (RuntimeException refused) {
                        // Refusing is always a valid outcome; writing badly is not.
                        continue;
                    }

                    assertThat(CharacterSet.validate(frame, field.offset(), field.length(),
                                    field.charClass()))
                            .as("%s wrote '%s' into %s as something the field refuses",
                                    policy, input, record.formatId())
                            .isEmpty();

                    // And no stranded voicing mark, which the class check cannot see.
                    for (int i = field.offset(); i < field.endOffset(); i++) {
                        int mark = frame[i] & 0xFF;
                        if (!io.zengin4j.core.charset.VoicingMarks.isMark(mark)) {
                            continue;
                        }
                        int base = i == field.offset() ? -1 : frame[i - 1] & 0xFF;
                        assertThat(io.zengin4j.core.charset.VoicingMarks.isLegal(base, mark))
                                .as("%s wrote '%s' with a stranded voicing mark", policy, input)
                                .isTrue();
                    }
                }
            }
        }
    }

    /// Replacement is byte for byte, so nothing after it shifts.
    ///
    /// A voiced kana is two bytes. Replacing it with one would move every
    /// later character left, which is a different failure from the one being
    /// fixed — and a silent one.
    @Test
    void replaceKeepsTheFieldTheSameLength() {
        var options = EncodingOptions.builder()
                .characters(CharacterWritePolicy.REPLACE)
                .replacement((byte) ' ')
                .build();

        byte[] frame = RecordEncoder.encode(DATA, ZenginCharset.MS932,
                withName("ﾔﾏﾀﾞ*ﾀﾛｳ"), options, new LossCollector());

        assertThat(frame).hasSize(DATA.recordLength());
    }

    @Test
    void whatReplaceProducesIsWritableIntoTheField() {
        var options = EncodingOptions.builder()
                .characters(CharacterWritePolicy.REPLACE)
                .replacement((byte) ' ')
                .build();

        byte[] frame = RecordEncoder.encode(DATA, ZenginCharset.MS932,
                withName("ﾔﾏﾀﾞ*ﾀﾛｳ"), options, new LossCollector());

        FieldDescriptor field = DATA.field("beneficiaryName");
        assertThat(CharacterSet.validate(frame, field.offset(), field.length(), field.charClass()))
                .isEmpty();
    }

    // ---------------------------------------------------------------- shape

    /// Truncation is measured in the encoding the record is written in.
    ///
    /// `transliterate` used to build its options without the encoder's
    /// charset, so it measured MS932 while the caller wrote UTF-8 — calling a
    /// 45-byte value a 15-byte one and letting it overflow the field. Fifteen
    /// kana is 15 bytes in MS932 and 45 in UTF-8; the field is 30.
    @Test
    void transliterateMeasuresLengthInTheEncoderSCharset() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("beneficiaryName", "アイウエオカキクケコサシスセソ");
        var options = EncodingOptions.builder()
                .characters(CharacterWritePolicy.TRANSLITERATE)
                .truncation(io.zengin4j.core.kana.TruncationPolicy.TRUNCATE_SAFE)
                .build();

        byte[] frame = RecordEncoder.encode(DATA, ZenginCharset.UTF_8, values, options,
                new LossCollector());

        FieldDescriptor field = DATA.field("beneficiaryName");
        String written = ZenginCharset.UTF_8.decode(frame, field.offset(), field.length()).strip();
        assertThat(ZenginCharset.UTF_8.encode(written))
                .as("'%s' must fit the %d-byte field in the encoding it is written in",
                        written, field.length())
                .hasSizeLessThanOrEqualTo(field.length());
        assertThat(frame).hasSize(DATA.recordLength());
    }

    @Test
    void numericFieldsAreLeftAloneBecauseDigitsArePermittedEverywhere() {
        var options = EncodingOptions.builder()
                .characters(CharacterWritePolicy.TRANSLITERATE).build();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("amount", "150000");
        var loss = new LossCollector();

        byte[] frame = RecordEncoder.encode(DATA, ZenginCharset.MS932, values, options, loss);

        assertThat(fieldOf(frame, DATA, "amount")).isEqualTo("0000150000");
        assertThat(loss.isLossless()).isTrue();
    }

    @Test
    void theDefaultsAreWhatTheRequirementSays() {
        assertThat(EncodingOptions.defaults().characters())
                .as("R-C18: REJECT is the default")
                .isEqualTo(CharacterWritePolicy.REJECT);
    }

    @Test
    void theOriginalSignatureStillMeansReject() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RecordEncoder.encode(DATA, ZenginCharset.MS932,
                        withName("ヤマダ")));
    }
}
