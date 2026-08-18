package io.zengin4j.iso20022.api;

import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.kana.HiraganaPolicy;
import io.zengin4j.core.kana.TruncationPolicy;
import io.zengin4j.core.kana.UnmappableCharacterPolicy;
import io.zengin4j.core.loss.LossSeverity;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything a conversion needs that neither format can supply.
 *
 * <p><strong>Required on the inverse leg, never defaulted (R-I20).</strong>
 * A {@code pain.001} document genuinely does not contain 委託者コード, does not
 * say which Zengin format to produce, and does not say what to do when a
 * beneficiary name will not fit. Guessing any of those would produce a payment
 * file that looks right, so the caller states them.
 *
 * <h2>Deterministic by default</h2>
 *
 * <p>{@code CreDtTm} and {@code MsgId} both default to something derived from
 * {@link #referenceDate()} rather than from the clock or a random source. The
 * same input therefore produces the same XML, byte for byte, which is what
 * makes a golden file meaningful and a diff between two runs readable. A caller
 * who wants a real timestamp sets one.
 *
 * <h2>Failing on loss</h2>
 *
 * <p>{@link #failOnSeverity()} defaults to {@link LossSeverity#CRITICAL}: a
 * conversion that could misroute money refuses rather than returning quietly.
 * R-I14 makes the report impossible to <em>miss</em> by putting it in the
 * return type; this makes the worst class of loss impossible to <em>ignore</em>.
 * See {@code docs/adr/0033-critical-loss-fails-by-default.md}, and
 * {@link Builder#acceptAnyLoss()} for the way out.
 *
 * @since 0.5.0
 */
public final class MappingContext {

    private final String originatorCode;
    private final LocalDate referenceDate;
    private final OffsetDateTime creationDateTime;
    private final String messageId;
    private final String receiver;
    private final TruncationPolicy truncation;
    private final HiraganaPolicy hiragana;
    private final UnmappableCharacterPolicy unmappable;
    private final EndToEndIdPolicy endToEndPolicy;
    private final LossSeverity failOnSeverity;
    private final ZenginCharset targetCharset;
    private final FormatDescriptor targetFormat;

    private MappingContext(Builder builder) {
        this.originatorCode = builder.originatorCode;
        this.referenceDate = builder.referenceDate;
        this.creationDateTime = builder.creationDateTime != null
                ? builder.creationDateTime
                : builder.referenceDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        this.messageId = builder.messageId != null
                ? builder.messageId
                : defaultMessageId(builder.originatorCode, builder.referenceDate);
        this.receiver = builder.receiver;
        this.truncation = builder.truncation;
        this.hiragana = builder.hiragana;
        this.unmappable = builder.unmappable;
        this.endToEndPolicy = builder.endToEndPolicy;
        this.failOnSeverity = builder.failOnSeverity;
        this.targetCharset = builder.targetCharset;
        this.targetFormat = builder.targetFormat;
    }

    private MappingContext(MappingContext source, LossSeverity failOnSeverity) {
        this.originatorCode = source.originatorCode;
        this.referenceDate = source.referenceDate;
        this.creationDateTime = source.creationDateTime;
        this.messageId = source.messageId;
        this.receiver = source.receiver;
        this.truncation = source.truncation;
        this.hiragana = source.hiragana;
        this.unmappable = source.unmappable;
        this.endToEndPolicy = source.endToEndPolicy;
        this.failOnSeverity = failOnSeverity;
        this.targetCharset = source.targetCharset;
        this.targetFormat = source.targetFormat;
    }

    /**
     * The same context, but not refusing on loss.
     *
     * <p>Used by {@code dryRun} and {@code roundTrip}, whose whole purpose is
     * to show what a conversion would cost: stopping at the first critical
     * entry would hide the rest of the answer.
     *
     * @return a context identical to this one except that nothing makes it
     *         refuse
     */
    public MappingContext acceptingAnyLoss() {
        return failOnSeverity == null ? this : new MappingContext(this, null);
    }

    /**
     * Starts building a context.
     *
     * @param originatorCode 委託者コード, which the XML cannot supply
     * @param referenceDate  the date yearless {@code MMDD} fields resolve
     *                       against, and the basis of the deterministic defaults
     * @return a builder
     */
    public static Builder builder(String originatorCode, LocalDate referenceDate) {
        return new Builder(originatorCode, referenceDate);
    }

    /** @return 委託者コード — the originator's identifier at its bank */
    public String originatorCode() {
        return originatorCode;
    }

    /** @return the date yearless {@code MMDD} fields resolve against */
    public LocalDate referenceDate() {
        return referenceDate;
    }

    /** @return what to write in {@code GrpHdr/CreDtTm} */
    public OffsetDateTime creationDateTime() {
        return creationDateTime;
    }

    /** @return what to write in {@code GrpHdr/MsgId} */
    public String messageId() {
        return messageId;
    }

    /**
     * Who the message is addressed to, in the business application header.
     *
     * <p>Left empty, the conversion uses the file's own 仕向銀行番号 — a 総合振込
     * file is sent to the originator's own bank, and that bank's code is in the
     * header record. Set it when the message goes somewhere else, or when the
     * recipient is identified by something other than a bank code.
     *
     * @return the recipient's identifier, or empty to derive it from the file
     */
    public Optional<String> receiver() {
        return receiver.isBlank() ? Optional.empty() : Optional.of(receiver);
    }

    /** @return what to do when a name will not fit its field */
    public TruncationPolicy truncationPolicy() {
        return truncation;
    }

    /** @return what to do with hiragana in a name */
    public HiraganaPolicy hiraganaPolicy() {
        return hiragana;
    }

    /** @return what to do with a character no half-width form exists for */
    public UnmappableCharacterPolicy unmappablePolicy() {
        return unmappable;
    }

    /** @return where {@code EndToEndId} lives on the Zengin side */
    public EndToEndIdPolicy endToEndPolicy() {
        return endToEndPolicy;
    }

    /**
     * The severity at which a conversion refuses.
     *
     * @return the threshold, or empty when the caller accepts any loss
     */
    public Optional<LossSeverity> failOnSeverity() {
        return Optional.ofNullable(failOnSeverity);
    }

    /**
     * The charset of the fixed-length side, in both directions.
     *
     * <p>Named for the direction that produces a file, which is where it
     * matters most, but it governs reading too: the outbound leg decodes fields
     * out of a record's raw bytes with it. A file read as UTF-8 and converted
     * with the default MS932 here would decode every name wrongly, so the two
     * have to agree — and the command line takes both from one
     * {@code --charset}.
     *
     * @return the charset
     */
    public ZenginCharset targetCharset() {
        return targetCharset;
    }

    /**
     * The Zengin format the inverse leg produces.
     *
     * @return the descriptor, or empty when only the outbound leg is used
     */
    public Optional<FormatDescriptor> targetFormat() {
        return Optional.ofNullable(targetFormat);
    }

    /**
     * The descriptor the inverse leg needs, or a diagnostic saying so.
     *
     * @return the descriptor
     * @throws IllegalStateException if no target format was set
     */
    public FormatDescriptor requireTargetFormat() {
        if (targetFormat == null) {
            throw new IllegalStateException(
                    "no target format. The XML does not say which Zengin format to produce — "
                            + "総合振込 and 給与振込 have different fields and different "
                            + "character rules — so MappingContext.builder(...).targetFormat(...) "
                            + "has to say (R-I20).");
        }
        return targetFormat;
    }

    /**
     * A message identifier that is stable for a given originator and date.
     *
     * <p>Not a random or clock-derived value, so that converting the same file
     * twice produces the same bytes. It is a default, not a scheme: an
     * originator sending several files a day should set its own.
     *
     * <p>The date keeps its hyphens. Compacted, it would be a bare eight-digit
     * run in every file this library produces, which the repository's
     * identifier scan reads as a possible account number — and it would be
     * right to: a digit run that means a date is indistinguishable from one
     * that means an account.
     */
    private static String defaultMessageId(String originatorCode, LocalDate referenceDate) {
        return originatorCode + "-" + referenceDate;
    }

    @Override
    public String toString() {
        return "MappingContext[" + originatorCode + ", " + referenceDate
                + ", failOn=" + (failOnSeverity == null ? "nothing" : failOnSeverity) + "]";
    }

    /** Assembles a context. */
    public static final class Builder {
        private final String originatorCode;
        private final LocalDate referenceDate;
        private OffsetDateTime creationDateTime;
        private String messageId;
        private String receiver = "";
        private TruncationPolicy truncation = TruncationPolicy.REJECT_IF_TOO_LONG;
        private HiraganaPolicy hiragana = HiraganaPolicy.CONVERT;
        private UnmappableCharacterPolicy unmappable = UnmappableCharacterPolicy.REJECT;
        private EndToEndIdPolicy endToEndPolicy = EndToEndIdPolicy.CUSTOMER_CODE_1;
        private LossSeverity failOnSeverity = LossSeverity.CRITICAL;
        private ZenginCharset targetCharset = ZenginCharset.MS932;
        private FormatDescriptor targetFormat;

        private Builder(String originatorCode, LocalDate referenceDate) {
            this.originatorCode = Objects.requireNonNull(originatorCode, "originatorCode");
            this.referenceDate = Objects.requireNonNull(referenceDate, "referenceDate");
        }

        /**
         * Sets {@code GrpHdr/CreDtTm}.
         *
         * @param value the timestamp
         * @return this builder
         */
        public Builder creationDateTime(OffsetDateTime value) {
            this.creationDateTime = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Sets {@code GrpHdr/MsgId}.
         *
         * @param value the identifier
         * @return this builder
         */
        public Builder messageId(String value) {
            this.messageId = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Sets who the message is addressed to, in the business application
         * header.
         *
         * @param value the recipient's identifier
         * @return this builder
         */
        public Builder receiver(String value) {
            this.receiver = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Sets what happens when a name will not fit.
         *
         * @param value the policy
         * @return this builder
         */
        public Builder truncation(TruncationPolicy value) {
            this.truncation = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Sets what happens to hiragana in a name.
         *
         * @param value the policy
         * @return this builder
         */
        public Builder hiragana(HiraganaPolicy value) {
            this.hiragana = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Sets what happens to a character with no half-width form.
         *
         * @param value the policy
         * @return this builder
         */
        public Builder unmappable(UnmappableCharacterPolicy value) {
            this.unmappable = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Sets where {@code EndToEndId} lives on the Zengin side.
         *
         * @param value the policy
         * @return this builder
         */
        public Builder endToEndPolicy(EndToEndIdPolicy value) {
            this.endToEndPolicy = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Sets the severity at which a conversion refuses.
         *
         * @param value the threshold
         * @return this builder
         */
        public Builder failOnSeverity(LossSeverity value) {
            this.failOnSeverity = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Accepts any loss, including loss that could misroute a payment.
         *
         * <p>Named so that it reads as what it is at the call site. The loss
         * report is still returned and still says everything it said before —
         * this only stops the conversion refusing.
         *
         * @return this builder
         */
        public Builder acceptAnyLoss() {
            this.failOnSeverity = null;
            return this;
        }

        /**
         * Sets the charset the produced Zengin file is written in.
         *
         * @param value the charset
         * @return this builder
         */
        public Builder targetCharset(ZenginCharset value) {
            this.targetCharset = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Sets the Zengin format the inverse leg produces.
         *
         * @param value the descriptor
         * @return this builder
         */
        public Builder targetFormat(FormatDescriptor value) {
            this.targetFormat = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Builds the context.
         *
         * @return the context
         */
        public MappingContext build() {
            return new MappingContext(this);
        }
    }
}
