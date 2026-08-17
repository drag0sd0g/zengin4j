package io.zengin4j.core.kana;

import io.zengin4j.core.error.ZenginException;

/**
 * Text begins with a voicing mark that has no kana in front of it.
 *
 * <p>{@code ﾞ} and {@code ﾟ} are separate characters that modify the kana
 * before them (§16.1). One at the start of a field modifies nothing, which
 * means the text arrived already damaged — most often because something
 * upstream truncated it at a byte boundary and kept the wrong half.
 *
 * <p>Reported rather than repaired: the mark could belong to any of several
 * kana, and inventing one would name a payee nobody chose.
 *
 * @since 0.4.0
 */
public final class OrphanedVoicingMarkException extends ZenginException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param text the text beginning with a voicing mark
     */
    public OrphanedVoicingMarkException(String text) {
        super("'" + text + "' begins with a voicing mark, which modifies the character before it"
                        + " and so has nothing to modify here. Text in this state has usually been"
                        + " cut at a byte boundary somewhere upstream. The kana it belonged to"
                        + " cannot be recovered — several would fit — so it is reported rather"
                        + " than guessed at.",
                "'" + text + "' が濁点・半濁点で始まっています。これらは直前の文字を修飾するため、"
                        + "先頭にあると修飾する対象がありません。上流でバイト境界により切断された"
                        + "可能性があります。元の仮名は復元できないため、推測せず報告します。");
    }
}
