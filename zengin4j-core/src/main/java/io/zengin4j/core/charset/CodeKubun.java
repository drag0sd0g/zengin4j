package io.zengin4j.core.charset;

/**
 * コード区分 — the encoding indicator carried in every header record.
 *
 * <p>Values per Appendix A of the specification: {@code 0} JIS, {@code 1}
 * EBCDIC. The list is treated as open: an unrecognised value maps to
 * {@link #UNKNOWN} and the raw field content remains available on the record,
 * rather than being forced into one of the two known constants.
 *
 * @since 0.1.0
 */
public enum CodeKubun {
    /** {@code 0} — JIS. The only variant this library decodes. */
    JIS("0"),

    /**
     * {@code 1} — EBCDIC. Detected and rejected by name rather than decoded
     * (R-C14).
     */
    EBCDIC("1"),

    /** Any value that is neither {@code 0} nor {@code 1}. */
    UNKNOWN("");

    private final String code;

    CodeKubun(String code) {
        this.code = code;
    }

    /**
     * Maps raw field content to a constant.
     *
     * @param raw the raw コード区分 field content, may be {@code null}
     * @return the matching constant, or {@link #UNKNOWN} if the value is not
     *         recognised
     */
    public static CodeKubun of(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        String trimmed = raw.trim();
        for (CodeKubun candidate : values()) {
            if (candidate != UNKNOWN && candidate.code.equals(trimmed)) {
                return candidate;
            }
        }
        return UNKNOWN;
    }

    /**
     * Returns the field content this constant corresponds to.
     *
     * @return the one-character code, or the empty string for {@link #UNKNOWN}
     */
    public String code() {
        return code;
    }

    /**
     * Reports whether this library can decode files declaring this variant.
     *
     * @return {@code true} only for {@link #JIS}
     */
    public boolean isSupported() {
        return this == JIS;
    }
}
