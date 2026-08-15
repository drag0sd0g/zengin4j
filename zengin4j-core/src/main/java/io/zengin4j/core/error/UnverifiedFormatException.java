package io.zengin4j.core.error;

/**
 * A format descriptor whose layout has not been confirmed against two
 * independent published sources was used without opting in.
 *
 * <p>This exception is the enforcement point of the verification protocol
 * (§0.3, R-0.1). Every descriptor shipped in 0.1.0 is {@code verified: false},
 * because the layouts were transcribed from a single working draft. Reading
 * such a file requires an explicit
 * {@code ReaderOptions.builder().allowUnverifiedFormats(true)}, which exists
 * so that the decision to trust a provisional layout is recorded in the
 * caller's own source code rather than assumed by this library.
 *
 * @since 0.1.0
 */
public final class UnverifiedFormatException extends ZenginException {

    private final String formatId;

    /**
     * Creates a diagnostic naming the unverified format.
     *
     * @param formatId the descriptor id, for example {@code "sougou-furikomi"}
     */
    public UnverifiedFormatException(String formatId) {
        super("format '" + formatId + "' is marked verified: false — its byte layout has not been confirmed"
                        + " against two independent published sources, so parsing it may silently misread"
                        + " financial instructions. To proceed anyway, set"
                        + " ReaderOptions.builder().allowUnverifiedFormats(true) and validate the output"
                        + " against your own institution's specification.",
                "フォーマット '" + formatId + "' は verified: false です。バイト配置が独立した 2 つの公開資料で"
                        + "確認されていないため、金融取引データを誤読する可能性があります。続行するには"
                        + " ReaderOptions.builder().allowUnverifiedFormats(true) を指定し、"
                        + "取引金融機関の仕様書と照合してください。");
        this.formatId = formatId;
    }

    /**
     * Returns the id of the unverified format.
     *
     * @return the descriptor id, never {@code null}
     */
    public String formatId() {
        return formatId;
    }
}
