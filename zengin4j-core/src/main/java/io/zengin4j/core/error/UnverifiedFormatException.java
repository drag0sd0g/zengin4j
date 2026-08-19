package io.zengin4j.core.error;

/// A format descriptor whose layout has not been confirmed against two
/// independent published sources was used without opting in.
///
/// This exception is the enforcement point of the verification protocol
/// (§0.3, R-0.1). Every descriptor shipped in 0.1.0 is `verified: false`.
/// Reading such a file requires
/// `ReaderOptions.builder().allowUnverifiedFormats(true)`, and building
/// one requires `ZenginFileBuilder#allowUnverifiedFormats(boolean)` —
/// opt-ins that exist so the decision to trust a provisional layout is recorded
/// in the caller's own source rather than assumed by this library.
///
/// The message names whichever of the two the caller actually needs. A
/// diagnostic that prescribes the wrong remedy costs more than one that says
/// nothing.
///
/// @since 0.1.0
public final class UnverifiedFormatException extends ZenginException {

    /// Which operation was refused, so the message can name the right opt-in.
    ///
    /// @since 0.1.0
    public enum Operation {

        /// Parsing bytes into records.
        READING("parsing it may silently misread financial instructions",
                "ReaderOptions.builder().allowUnverifiedFormats(true)",
                "金融取引データを誤読する可能性があります",
                "ReaderOptions.builder().allowUnverifiedFormats(true)"),

        /// Placing values at descriptor-defined offsets. The harsher wording is
        /// deliberate: a misread lands in the caller's own system, where their
        /// reconciliation may catch it; a miswrite lands at a bank.
        BUILDING("the values may be written to the wrong byte offsets, producing a payment"
                        + " instruction that is wrong in a way nothing downstream will catch",
                "ZenginFileBuilder.forFormat(...).allowUnverifiedFormats(true)",
                "値が誤ったバイト位置に書き込まれ、後続の処理では検出できない誤った振込指図となる"
                        + "可能性があります",
                "ZenginFileBuilder.forFormat(...).allowUnverifiedFormats(true)");

        private final String riskEn;
        private final String remedyEn;
        private final String riskJa;
        private final String remedyJa;

        Operation(String riskEn, String remedyEn, String riskJa, String remedyJa) {
            this.riskEn = riskEn;
            this.remedyEn = remedyEn;
            this.riskJa = riskJa;
            this.remedyJa = remedyJa;
        }
    }

    private final String formatId;
    private final Operation operation;

    /// Creates a diagnostic naming the unverified format, for a read.
    ///
    /// @param formatId the descriptor id, for example `"sougou-furikomi"`
    public UnverifiedFormatException(String formatId) {
        this(formatId, Operation.READING);
    }

    /// Creates a diagnostic naming the unverified format and the operation
    /// refused.
    ///
    /// @param formatId  the descriptor id, for example `"sougou-furikomi"`
    /// @param operation what was refused, which selects the remedy named
    public UnverifiedFormatException(String formatId, Operation operation) {
        super("format '" + formatId + "' is marked verified: false — its byte layout has not been confirmed"
                        + " against two independent published sources, so " + operation.riskEn
                        + ". To proceed anyway, set " + operation.remedyEn + " and validate the output"
                        + " against your own institution's specification.",
                "フォーマット '" + formatId + "' は verified: false です。バイト配置が独立した 2 つの公開資料で"
                        + "確認されていないため、" + operation.riskJa + "。続行するには "
                        + operation.remedyJa + " を指定し、取引金融機関の仕様書と照合してください。");
        this.formatId = formatId;
        this.operation = operation;
    }

    /// Returns the id of the unverified format.
    ///
    /// @return the descriptor id, never `null`
    public String formatId() {
        return formatId;
    }

    /// Returns what was refused.
    ///
    /// @return the operation, never `null`
    public Operation operation() {
        return operation;
    }
}
