package io.zengin4j.validation;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.codec.CharacterWritePolicy;
import io.zengin4j.core.codec.EncodingOptions;
import io.zengin4j.core.codec.RecordEncoder;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.kana.TruncationPolicy;
import io.zengin4j.core.kana.UnmappableCharacterPolicy;
import io.zengin4j.core.loss.LossCollector;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.testkit.FormatFixtures;
import io.zengin4j.testkit.SyntheticRecords;
import io.zengin4j.validation.api.Severity;
import io.zengin4j.validation.api.ValidationReport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/// **Anything this library writes, it accepts.**
///
/// The end-to-end statement of a promise that has been broken three separate
/// ways during Epic 6, each time quietly:
///
/// - the default write policy checked a value's length but not its
///   characters, so a full-width name went into a half-width field;
/// - a truncation marker that no field class permits;
/// - a replacement byte that no field class permits, and another that
///   stranded a voicing mark.
///
/// Each was found by looking rather than by failing. This test is the general
/// form: build a record through the encoder under every policy, write it, read it
/// back, and run the validator over it. If the library can be made to produce a
/// file it reports on, that is a defect in the library, not in the file.
///
/// It lives in the validation module because that is the only place both sides
/// are visible — `core` writes, and `core` cannot see the rules.
class WrittenFilesValidateTest {

    /// Names covering every conversion the engine performs.
    private static final List<String> NAMES = List.of(
            "ﾔﾏﾀﾞ ﾀﾛｳ",        // already conformant
            "ガクブチ ジロウ",   // full width, voiced
            "キャノン",          // a small kana
            "ヨーコ",            // a long vowel
            "サッポロ",          // a small tsu
            "ﾊﾟﾋﾟﾌﾟﾍﾟﾎﾟ",      // semi-voiced, already narrow
            "ＡＢＣ",            // full-width Latin
            "abc",               // lowercase
            "ヴｧｲｵﾘﾝ",          // mixed width, vu, small kana
            "ﾔﾏﾀﾞ*ﾀﾛｳ",        // a symbol no class permits
            "ﾀﾛｳﾀﾛｳﾀﾛｳﾀﾛｳﾀﾛｳﾀﾛｳﾀﾛｳ",   // long enough to need shortening
            "ガクブチガクブチガクブチガクブチガクブチ");  // long and voiced

    static List<FormatId> formats() {
        return FormatFixtures.supported();
    }

    /// Every policy, every format, every name: either refused, or written and
    /// clean.
    @ParameterizedTest
    @MethodSource("formats")
    void whateverThePolicyWritesReadsBackWithoutASyntaxFinding(FormatId id) {
        FormatFixtures fixtures = FormatFixtures.forFormat(id);
        FormatDescriptor descriptor = fixtures.descriptor();

        for (CharacterWritePolicy policy : CharacterWritePolicy.values()) {
            for (TruncationPolicy truncation : TruncationPolicy.values()) {
                for (String name : NAMES) {
                    EncodingOptions options = EncodingOptions.builder()
                            .characters(policy)
                            .truncation(truncation)
                            .unmappable(UnmappableCharacterPolicy.DROP)
                            .build();

                    byte[] data;
                    try {
                        data = RecordEncoder.encode(descriptor.record(RecordKind.DATA),
                                ZenginCharset.MS932, dataValues(id, name), options,
                                new LossCollector());
                    } catch (RuntimeException refused) {
                        // Refusing is always allowed. Writing badly is not.
                        continue;
                    }

                    byte[] file = SyntheticRecords.file(
                            List.of(fixtures.header(), data,
                                    fixtures.trailer(1, 150_000L), fixtures.end()),
                            SeparatorStyle.CRLF, false);

                    ZenginFile parsed = ZenginReaders.readFile(
                            new ByteArrayInputStream(file), fixtures.readerOptions());
                    ValidationReport report = ZenginValidator.defaults().validate(parsed);

                    List<String> syntaxErrors = new ArrayList<>();
                    report.findings(Severity.ERROR).stream()
                            .filter(finding -> finding.ruleId().startsWith("V-2"))
                            .forEach(finding -> syntaxErrors.add(
                                    finding.ruleId() + ": " + finding.messageEn()));

                    assertThat(syntaxErrors)
                            .as("%s wrote '%s' into %s under %s/%s, and the validator objects",
                                    policy, name, id.value(), policy, truncation)
                            .isEmpty();
                }
            }
        }
    }

    /// Field values for a data record, whichever format's names they are.
    private static Map<String, String> dataValues(FormatId id, String name) {
        Map<String, String> values = new LinkedHashMap<>();
        if (id.value().equals("kouza-furikae")) {
            values.put("payerName", name);
            values.put("payerBankCode", "9999");
            values.put("payerBranchCode", "999");
            values.put("payerAccountNumber", "9876543");
            values.put("payerAccountType", "1");
            values.put("debitAmount", "150000");
        } else {
            values.put("beneficiaryName", name);
            values.put("beneficiaryBankCode", "9999");
            values.put("beneficiaryBranchCode", "999");
            values.put("accountNumber", "9876543");
            values.put("accountType", "1");
            values.put("amount", "150000");
        }
        return values;
    }
}
