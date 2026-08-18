package io.zengin4j.cli.command;

import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.kana.TruncationPolicy;
import io.zengin4j.core.kana.UnmappableCharacterPolicy;
import io.zengin4j.core.loss.LossSeverity;
import io.zengin4j.iso20022.api.EndToEndIdPolicy;
import io.zengin4j.iso20022.api.MappingContext;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import picocli.CommandLine;

/**
 * The conversion settings {@code convert} and {@code dryrun} share.
 *
 * <p>§27 sketches these as a {@code --context=ctx.yaml} file. They are flags
 * instead — see {@code docs/adr/0034-the-mapping-context-is-flags-not-a-file.md}.
 * The short version: there are eight values here, and a YAML file would be a
 * second configuration language for eight values, in a tool whose library
 * deliberately parses no YAML at runtime.
 *
 * @since 0.5.0
 */
public final class MappingOptions {

    @CommandLine.Option(
            names = "--originator-code",
            paramLabel = "CODE",
            description = "委託者コード. Required when converting to Zengin: the XML does not "
                    + "carry it, and an initiating party identifier is not the same thing "
                    + "(R-I20).")
    String originatorCode;

    @CommandLine.Option(
            names = "--as-of",
            paramLabel = "YYYY-MM-DD",
            description = "The date yearless MMDD fields resolve against, and the basis of the "
                    + "message id and creation timestamp. Defaults to today — set it to make "
                    + "a conversion reproducible.")
    String asOf;

    @CommandLine.Option(
            names = "--target-format",
            paramLabel = "ID",
            description = "The Zengin format to produce, e.g. sougou-furikomi. Required when "
                    + "converting to Zengin.")
    String targetFormat;

    @CommandLine.Option(
            names = "--message-id",
            paramLabel = "ID",
            description = "GrpHdr/MsgId. Derived from the originator code and --as-of if omitted.")
    String messageId;

    @CommandLine.Option(
            names = "--receiver",
            paramLabel = "ID",
            description = "Who the business application header is addressed to. Defaults to the "
                    + "file's own 仕向銀行番号.")
    String receiver;

    @CommandLine.Option(
            names = "--truncate",
            paramLabel = "POLICY",
            description = "What to do when a name will not fit: ${COMPLETION-CANDIDATES}. "
                    + "Default: ${DEFAULT-VALUE} — a payee's name is not a codec's to cut.")
    TruncationPolicy truncate = TruncationPolicy.REJECT_IF_TOO_LONG;

    @CommandLine.Option(
            names = "--unmappable",
            paramLabel = "POLICY",
            description = "What to do with a character that has no half-width form: "
                    + "${COMPLETION-CANDIDATES}. Default: ${DEFAULT-VALUE}.")
    UnmappableCharacterPolicy unmappable = UnmappableCharacterPolicy.REJECT;

    @CommandLine.Option(
            names = "--end-to-end",
            paramLabel = "POLICY",
            description = "Where EndToEndId lives on the Zengin side: "
                    + "${COMPLETION-CANDIDATES}. Default: ${DEFAULT-VALUE}.")
    EndToEndIdPolicy endToEnd = EndToEndIdPolicy.CUSTOMER_CODE_1;

    @CommandLine.Option(
            names = "--accept-loss",
            description = "Convert even when the loss could misroute money. Off by default: a "
                    + "conversion refuses at CRITICAL rather than returning quietly.")
    boolean acceptLoss;

    /**
     * Builds the context, or explains what is missing.
     *
     * @param err            where to write the explanation
     * @param needTheFormat  whether a target format is required, which it is
     *                       when producing a Zengin file
     * @return the context, or {@code null} when something required is absent
     */
    MappingContext toContext(PrintWriter err, boolean needTheFormat) {
        return toContext(err, needTheFormat, ZenginCharset.defaultCharset());
    }

    /**
     * Builds the context, or explains what is missing.
     *
     * @param err            where to write the explanation
     * @param needTheFormat  whether a target format is required, which it is
     *                       when producing a Zengin file
     * @param charset        the charset of the fixed-length side, taken from
     *                       {@code --charset} so that reading and converting
     *                       cannot disagree about it
     * @return the context, or {@code null} when something required is absent
     */
    MappingContext toContext(PrintWriter err, boolean needTheFormat, ZenginCharset charset) {
        if (needTheFormat && (originatorCode == null || originatorCode.isBlank())) {
            err.println("--originator-code is required when converting to Zengin. The XML does "
                    + "not carry 委託者コード, and using the initiating party's identifier "
                    + "instead would produce a file the bank rejects (R-I20).");
            return null;
        }

        LocalDate reference;
        try {
            reference = asOf == null ? LocalDate.now() : LocalDate.parse(asOf);
        } catch (DateTimeParseException notADate) {
            err.println("--as-of must be YYYY-MM-DD, not '" + asOf + "'");
            return null;
        }

        MappingContext.Builder builder =
                MappingContext.builder(originatorCode == null ? "" : originatorCode, reference)
                        .truncation(truncate)
                        .unmappable(unmappable)
                        .endToEndPolicy(endToEnd)
                        .targetCharset(charset);

        if (messageId != null) {
            builder.messageId(messageId);
        }
        if (receiver != null) {
            builder.receiver(receiver);
        }
        if (acceptLoss) {
            builder.acceptAnyLoss();
        }

        FormatDescriptor descriptor = descriptor(err, needTheFormat);
        if (needTheFormat && descriptor == null) {
            return null;
        }
        if (descriptor != null) {
            builder.targetFormat(descriptor);
        }
        return builder.build();
    }

    private FormatDescriptor descriptor(PrintWriter err, boolean required) {
        if (targetFormat == null || targetFormat.isBlank()) {
            if (required) {
                err.println("--target-format is required when converting to Zengin: 総合振込 and "
                        + "給与振込 have different fields and different character rules, and the "
                        + "XML does not say which is wanted.");
            }
            return null;
        }
        return FormatRegistry.defaults().byId(FormatId.of(targetFormat))
                .orElseGet(() -> {
                    err.println("no such format '" + targetFormat + "'. Available: "
                            + FormatRegistry.defaults().all().stream()
                                    .map(candidate -> candidate.id().value()).sorted().toList());
                    return null;
                });
    }

    /**
     * The severity at which the command reports failure, whatever the context
     * does.
     *
     * @return the threshold
     */
    static LossSeverity criticalThreshold() {
        return LossSeverity.CRITICAL;
    }
}
