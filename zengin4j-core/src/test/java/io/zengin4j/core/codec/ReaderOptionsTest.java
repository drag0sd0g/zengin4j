package io.zengin4j.core.codec;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.testing.Fixtures;
import org.junit.jupiter.api.Test;

/// The defaults matter: they are what a caller who reads no documentation gets.
class ReaderOptionsTest {

    @Test
    void defaultsAreTheConservativeChoices() {
        ReaderOptions options = ReaderOptions.defaults();

        assertThat(options.charset()).isEqualTo(ZenginCharset.MS932);
        assertThat(options.allowUnverifiedFormats()).isFalse();
        assertThat(options.mode()).isEqualTo(ParseMode.STRICT);
        assertThat(options.byteOrderMark()).isEqualTo(ByteOrderMarkPolicy.REJECT);
        assertThat(options.bufferRecords()).isEqualTo(ReaderOptions.DEFAULT_BUFFER_RECORDS);
        assertThat(options.format()).isEmpty();
        assertThat(options.recordLength()).isEmpty();
        assertThat(options.registry().all()).isNotEmpty();
        assertThat(options.warningListener()).isNotNull();
    }

    @Test
    void everySettingRoundTripsThroughTheBuilder() {
        FormatRegistry registry = Fixtures.registry();
        List<ZenginWarning> warnings = new ArrayList<>();
        ReaderOptions options = ReaderOptions.builder()
                .charset(ZenginCharset.SHIFT_JIS)
                .allowUnverifiedFormats(true)
                .format(Fixtures.SOUGOU_FURIKOMI)
                .recordLength(122)
                .mode(ParseMode.LENIENT)
                .bufferRecords(8)
                .byteOrderMark(ByteOrderMarkPolicy.STRIP)
                .registry(registry)
                .warningListener(warnings::add)
                .build();

        ReaderOptions copy = options.toBuilder().build();

        assertThat(copy.charset()).isEqualTo(ZenginCharset.SHIFT_JIS);
        assertThat(copy.allowUnverifiedFormats()).isTrue();
        assertThat(copy.format()).contains(Fixtures.SOUGOU_FURIKOMI);
        assertThat(copy.recordLength()).hasValue(122);
        assertThat(copy.mode()).isEqualTo(ParseMode.LENIENT);
        assertThat(copy.bufferRecords()).isEqualTo(8);
        assertThat(copy.byteOrderMark()).isEqualTo(ByteOrderMarkPolicy.STRIP);
        assertThat(copy.registry()).isSameAs(registry);
        assertThat(copy.warningListener()).isSameAs(options.warningListener());
    }

    @Test
    void rejectsSettingsThatCannotWork() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReaderOptions.builder().recordLength(0))
                .withMessageContaining("record length must be positive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReaderOptions.builder().bufferRecords(0))
                .withMessageContaining("at least one record");
        assertThatNullPointerException().isThrownBy(() -> ReaderOptions.builder().charset(null));
        assertThatNullPointerException().isThrownBy(() -> ReaderOptions.builder().mode(null));
        assertThatNullPointerException().isThrownBy(() -> ReaderOptions.builder().registry(null));
        assertThatNullPointerException().isThrownBy(() -> ReaderOptions.builder().format(null));
        assertThatNullPointerException().isThrownBy(() -> ReaderOptions.builder().byteOrderMark(null));
        assertThatNullPointerException().isThrownBy(() -> ReaderOptions.builder().warningListener(null));
    }

    @Test
    void warningsCarryBothLanguagesAndACode() {
        ZenginWarning warning = new ZenginWarning("W-TEST", "english", "日本語", 42);

        assertThat(warning.code()).isEqualTo("W-TEST");
        assertThat(warning.messageEn()).isEqualTo("english");
        assertThat(warning.messageJa()).isEqualTo("日本語");
        assertThat(warning.byteOffset()).isEqualTo(42);
        assertThatNullPointerException()
                .isThrownBy(() -> new ZenginWarning(null, "en", "ja", 0));
        // The default listener writes one line through System.Logger and returns.
        warning.log();
    }

    @Test
    void namesTheFormatItCannotFind() {
        assertThat(FormatId.of("absent")).isNotEqualTo(Fixtures.SOUGOU_FURIKOMI);
    }
}
