package io.zengin4j.core.charset;

/**
 * Which kana may carry a voicing mark, and which may not (R-K7).
 *
 * <p>A fact about the encoding rather than a policy, which is why it lives
 * here: {@code ﾞ} and {@code ﾟ} are separate characters that modify the kana
 * before them, and only some kana have a voiced form for them to name. {@code ﾜﾞ}
 * is not a character — it is a mark stranded after a kana that has no voiced
 * reading.
 *
 * <p>Used on both sides. The transliterator refuses to <em>produce</em> such a
 * sequence, and validation rule {@code V-206} reports one it <em>finds</em>.
 * Keeping the ranges in one place is the point: two copies of this table would
 * eventually disagree, and then the library would write files it rejects.
 *
 * @since 0.4.0
 */
public final class VoicingMarks {
    /** {@code ﾞ}, the voiced mark. */
    public static final int DAKUTEN = 0xDE;

    /** {@code ﾟ}, the semi-voiced mark. */
    public static final int HANDAKUTEN = 0xDF;

    private static final int KA_FIRST = 0xB6;
    private static final int KA_LAST = 0xBA;
    private static final int SA_FIRST = 0xBB;
    private static final int SA_LAST = 0xBF;
    private static final int TA_FIRST = 0xC0;
    private static final int TA_LAST = 0xC4;
    private static final int HA_FIRST = 0xCA;
    private static final int HA_LAST = 0xCE;
    private static final int U = 0xB3;

    private VoicingMarks() {
    }

    /**
     * Whether a byte is a voicing mark.
     *
     * @param unsigned the byte value, 0–255
     * @return {@code true} for {@code 0xDE} or {@code 0xDF}
     */
    public static boolean isMark(int unsigned) {
        return unsigned == DAKUTEN || unsigned == HANDAKUTEN;
    }

    /**
     * Whether a kana may carry a dakuten.
     *
     * <p>The か, さ, た and は rows, plus ｳ — which takes one to write ｳﾞ, the
     * only way these files can spell a {@code v} sound.
     *
     * @param base the base kana's byte value, 0–255
     * @return {@code true} if a dakuten may follow it
     */
    public static boolean takesDakuten(int base) {
        return (base >= KA_FIRST && base <= KA_LAST)
                || (base >= SA_FIRST && base <= SA_LAST)
                || (base >= TA_FIRST && base <= TA_LAST)
                || (base >= HA_FIRST && base <= HA_LAST)
                || base == U;
    }

    /**
     * Whether a kana may carry a handakuten.
     *
     * <p>The は row alone: ﾊﾟ, ﾋﾟ, ﾌﾟ, ﾍﾟ, ﾎﾟ and nothing else.
     *
     * @param base the base kana's byte value, 0–255
     * @return {@code true} if a handakuten may follow it
     */
    public static boolean takesHandakuten(int base) {
        return base >= HA_FIRST && base <= HA_LAST;
    }

    /**
     * Whether a mark may follow a base.
     *
     * @param base the preceding byte, or a negative value if the mark is first
     * @param mark the mark's byte value
     * @return {@code true} if the pair is a character the standard recognises
     */
    public static boolean isLegal(int base, int mark) {
        if (base < 0) {
            return false;
        }
        return mark == HANDAKUTEN ? takesHandakuten(base) : takesDakuten(base);
    }
}
