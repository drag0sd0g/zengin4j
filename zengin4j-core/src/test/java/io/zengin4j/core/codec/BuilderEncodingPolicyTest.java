package io.zengin4j.core.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.zengin4j.core.charset.CharacterSet;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.kana.TruncationPolicy;
import io.zengin4j.core.kana.UnmappableCharacterPolicy;
import io.zengin4j.core.kana.UntransliterableCharacterException;
import io.zengin4j.core.loss.LossCollector;
import io.zengin4j.core.loss.LossSeverity;
import io.zengin4j.core.model.DataRecord;
import io.zengin4j.core.model.ZenginFile;
import org.junit.jupiter.api.Test;

/**
 * R-C18's policies, reached from the builder rather than from the encoder.
 *
 * <p>They were implemented on {@link RecordEncoder} and unreachable from here,
 * which made them unreachable in practice: {@link ZenginFileBuilder} is the
 * documented way to build a file, and a caller who dropped down to the encoder
 * to get transliteration gave up the trailer arithmetic and record numbering
 * this class exists for. A requirement that can only be used by abandoning the
 * API is not really implemented.
 */
class BuilderEncodingPolicyTest {

    private static final FormatDescriptor SOUGOU = FormatRegistry.defaults()
            .byId(FormatId.of("sougou-furikomi")).orElseThrow();

    private static final FormatDescriptor PAYROLL = FormatRegistry.defaults()
            .byId(FormatId.of("kyuyo-furikomi")).orElseThrow();

    private static ZenginFileBuilder builder(FormatDescriptor descriptor) {
        return ZenginFileBuilder.forFormat(descriptor)
                .allowUnverifiedFormats(true)
                .header(h -> h.set("originatorCode", "9900000001")
                        .set("originatorName", "ﾃｽﾄｼﾖｳｼﾞ"));
    }

    private static String nameOf(ZenginFile file, FormatDescriptor descriptor) {
        DataRecord record = file.allData().get(0);
        FieldDescriptor field = descriptor.record(RecordKind.DATA).field("beneficiaryName");
        return ZenginCharset.MS932
                .decode(record.rawBytes(), field.offset(), field.length()).strip();
    }

    // ---------------------------------------------------------------- default

    @Test
    void theBuilderStillRefusesByDefault() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder(SOUGOU)
                        .payment(p -> p.set("beneficiaryName", "ガクブチ ジロウ")
                                .set("accountNumber", "9876543")
                                .set("amount", 150_000L))
                        .build())
                .withMessageContaining("cannot be written");
    }

    // --------------------------------------------------------- TRANSLITERATE

    @Test
    void theBuilderCanBeToldToTransliterate() {
        LossCollector loss = new LossCollector();

        ZenginFile file = builder(SOUGOU)
                .encoding(EncodingOptions.builder()
                        .characters(CharacterWritePolicy.TRANSLITERATE).build(), loss)
                .payment(p -> p.set("beneficiaryName", "ガクブチ ジロウ")
                        .set("accountNumber", "9876543")
                        .set("amount", 150_000L))
                .build();

        assertThat(nameOf(file, SOUGOU)).isEqualTo("ｶﾞｸﾌﾞﾁ ｼﾞﾛｳ");
        assertThat(loss.build().entries()).isNotEmpty();
    }

    /** And the trailer arithmetic still happens, which was the whole point. */
    @Test
    void transliteratingDoesNotCostTheBuilderSTrailerComputation() {
        ZenginFile file = builder(SOUGOU)
                .encoding(EncodingOptions.builder()
                        .characters(CharacterWritePolicy.TRANSLITERATE).build(),
                        new LossCollector())
                .payment(p -> p.set("beneficiaryName", "ガクブチ")
                        .set("accountNumber", "9876543").set("amount", 100L))
                .payment(p -> p.set("beneficiaryName", "ヤマダ")
                        .set("accountNumber", "9876544").set("amount", 200L))
                .build();

        assertThat(file.batches().get(0).trailer()).get()
                .satisfies(trailer -> {
                    assertThat(trailer.recordCount()).isEqualTo(2);
                    assertThat(trailer.totalAmount()).isEqualTo(300L);
                });
    }

    @Test
    void theFieldsOwnCharacterClassDecides() {
        EncodingOptions options = EncodingOptions.builder()
                .characters(CharacterWritePolicy.TRANSLITERATE).build();

        assertThat(nameOf(builder(SOUGOU).encoding(options, new LossCollector())
                .payment(p -> p.set("beneficiaryName", "ヨーコ")
                        .set("accountNumber", "9876543").set("amount", 100L))
                .build(), SOUGOU))
                .isEqualTo("ﾖ-ｺ");

        assertThatExceptionOfType(UntransliterableCharacterException.class)
                .as("payroll names admit no symbols, so the hyphen has nowhere to go")
                .isThrownBy(() -> builder(PAYROLL).encoding(options, new LossCollector())
                        .payment(p -> p.set("beneficiaryName", "ヨーコ")
                                .set("accountNumber", "9876543").set("amount", 100L))
                        .build());
    }

    @Test
    void aLongNameCanBeShortenedThroughTheBuilder() {
        LossCollector loss = new LossCollector();

        ZenginFile file = builder(SOUGOU)
                .encoding(EncodingOptions.builder()
                        .characters(CharacterWritePolicy.TRANSLITERATE)
                        .truncation(TruncationPolicy.TRUNCATE_SAFE).build(), loss)
                .payment(p -> p.set("beneficiaryName",
                                "ガクブチガクブチガクブチガクブチガクブチガクブチ")
                        .set("accountNumber", "9876543").set("amount", 100L))
                .build();

        FieldDescriptor field = SOUGOU.record(RecordKind.DATA).field("beneficiaryName");
        assertThat(ZenginCharset.MS932.encode(nameOf(file, SOUGOU)))
                .hasSizeLessThanOrEqualTo(field.length());
        assertThat(loss.build().atLeast(LossSeverity.MATERIAL)).isNotEmpty();
    }

    // --------------------------------------------------------------- REPLACE

    @Test
    void theBuilderCanBeToldToReplace() {
        LossCollector loss = new LossCollector();

        ZenginFile file = builder(SOUGOU)
                .encoding(EncodingOptions.builder()
                        .characters(CharacterWritePolicy.REPLACE)
                        .replacement((byte) ' ').build(), loss)
                .payment(p -> p.set("beneficiaryName", "ﾔﾏﾀﾞ*ﾀﾛｳ")
                        .set("accountNumber", "9876543").set("amount", 100L))
                .build();

        assertThat(nameOf(file, SOUGOU)).isEqualTo("ﾔﾏﾀﾞ ﾀﾛｳ");
        assertThat(loss.build().entries()).isNotEmpty();
    }

    // ----------------------------------------------------------- the promise

    /**
     * Whatever policy the builder is given, the file it produces is writable.
     *
     * <p>The same claim {@code WritePolicyTest} makes for the encoder, restated
     * for the path callers actually use.
     */
    @Test
    void everyFileTheBuilderProducesIsWritable() {
        for (CharacterWritePolicy policy : CharacterWritePolicy.values()) {
            for (String name : java.util.List.of("ﾔﾏﾀﾞ ﾀﾛｳ", "ガクブチ", "キャノン", "ヨーコ",
                    "ＡＢＣ", "ﾔﾏﾀﾞ*ﾀﾛｳ")) {
                ZenginFile file;
                try {
                    file = builder(SOUGOU)
                            .encoding(EncodingOptions.builder()
                                    .characters(policy)
                                    .truncation(TruncationPolicy.TRUNCATE_SAFE)
                                    .unmappable(UnmappableCharacterPolicy.DROP).build(),
                                    new LossCollector())
                            .payment(p -> p.set("beneficiaryName", name)
                                    .set("accountNumber", "9876543").set("amount", 100L))
                            .build();
                } catch (RuntimeException refused) {
                    continue;
                }

                FieldDescriptor field = SOUGOU.record(RecordKind.DATA).field("beneficiaryName");
                byte[] bytes = file.allData().get(0).rawBytes();
                assertThat(CharacterSet.validate(bytes, field.offset(), field.length(),
                                field.charClass()))
                        .as("%s built '%s' as something the field refuses", policy, name)
                        .isEmpty();
            }
        }
    }

    // ------------------------------------------------------------- the shape

    /**
     * The collector is not optional.
     *
     * <p>Every policy but {@code REJECT} alters somebody's name to make it fit.
     * Requiring a collector is the smallest way to stop that being a decision
     * nobody records (P5).
     */
    @Test
    void aPolicyCannotBeChosenWithoutSomewhereToRecordWhatItDid() {
        assertThatNullPointerException()
                .isThrownBy(() -> builder(SOUGOU).encoding(EncodingOptions.defaults(), null));
        assertThatNullPointerException()
                .isThrownBy(() -> builder(SOUGOU).encoding(null, new LossCollector()));
    }

    @Test
    void aCleanValueRecordsNothingWhateverThePolicy() {
        for (CharacterWritePolicy policy : CharacterWritePolicy.values()) {
            LossCollector loss = new LossCollector();

            builder(SOUGOU)
                    .encoding(EncodingOptions.builder().characters(policy).build(), loss)
                    .payment(p -> p.set("beneficiaryName", "ﾔﾏﾀﾞ ﾀﾛｳ")
                            .set("accountNumber", "9876543").set("amount", 100L))
                    .build();

            assertThat(loss.isLossless()).as("%s", policy).isTrue();
        }
    }
}
