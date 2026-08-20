package io.zengin4j.core.charset;

import module java.base;

/// The permitted character set for a field, which depends on what the field
/// *is* (R-C16).
///
/// There is no single "permitted set" for these formats. 付録1 使用文字一覧
/// states the base set once and then narrows it per field class, and the
/// narrowing is not cosmetic: a branch name may contain exactly one symbol, a
/// party name four, and an EDI payload eight. A validator built on one global
/// set would pass a branch name containing a full stop, which an institution
/// will reject.
///
/// **The long vowel mark is not a permitted character.**
/// `ｰ` (0xB0, 長音) is excluded from every class; the standard writes a
/// long vowel as `-` (0x2D, ハイフン). This is the single most common
/// mistake in hand-entered katakana names — the two glyphs are nearly identical
/// and sort adjacently on a Japanese keyboard — and it is worth stating plainly
/// because a file carrying `ｰ` looks correct to a human reader and is
/// rejected by the bank.
///
/// Sets are defined over **bytes**, not characters (R-C15). The
/// permitted characters are the single-byte JIS X 0201 ones, so a file written
/// in [ZenginCharset#UTF_8] reports violations throughout — correctly:
/// such a file does not conform, whatever it looks like when decoded.
///
/// Sources: 全国銀行協会 付録1 使用文字一覧 (新旧対照表, 令和元年12月);
/// 但馬信用金庫 全銀仕様データレコード使用可能文字; PCA 全銀協使用可能文字;
/// 全銀ネット ZEDI 接続ガイダンス 一括ファイル伝送編 (2020年4月), which maps every
/// fixed-length field to one of these classes and agrees with all four of them —
/// including that only the EDI class admits `ｦ`.
/// Cited in `docs/SOURCES.md`. Where they diverge, see
/// `docs/DISCREPANCIES.md`.
///
/// @since 0.1.0
public enum CharacterClass {

    /// 店舗名 — bank and branch names. Kana, voicing marks, uppercase Latin,
    /// digits, space, and **one** symbol: `-`.
    BANK_NAME("店舗名", "bank and branch names", true, "-"),

    /// 口座名・受取人名・委託者名・振込依頼人名 — party names. As
    /// [#BANK_NAME] plus three more symbols: `( ) .`.
    PARTY_NAME("口座名等", "account and party names", true, "()-."),

    /// Name fields of 給与振込 and 賞与振込, which permit **no Latin
    /// letters at all**. A validator shaped by 総合振込 would never catch
    /// a payroll file carrying `A-Z`.
    PAYROLL_NAME("給与・賞与振込の名称", "payroll and bonus transfer names", false, ""),

    /// EDI情報 — the only class permitting `ｦ`, and the widest symbol set:
    /// `\ ｢ ｣ ( ) - / .`. Never a comma.
    EDI_INFORMATION("EDI情報", "EDI payload", true, "()-./\\｢｣") {
        @Override
        boolean permitsWo() {
            return true;
        }
    },

    /// `N` fields: ASCII digits only, with no symbols and no space.
    NUMERIC("数字", "digits", false, "") {
        @Override
        boolean permitsKana() {
            return false;
        }

        @Override
        boolean permitsSpace() {
            return false;
        }
    },

    /// No constraint. Filler and reserved space, whose content this library does
    /// not interpret and must not police — R-D5 requires those bytes to survive
    /// a round trip whatever they are.
    UNRESTRICTED("制限なし", "unrestricted", true, "");

    // JIS X 0201 single-byte katakana. Small kana (0xA7-0xAF) and the
    // prolonged sound mark ｰ (0xB0) sit between ｦ and ｱ and are excluded from
    // every class, which is why the run of permitted kana starts at 0xB1.
    private static final int WO = 0xA6;
    private static final int KANA_FIRST = 0xB1;
    private static final int KANA_LAST = 0xDD;
    private static final int VOICED_MARK = 0xDE;
    private static final int SEMI_VOICED_MARK = 0xDF;

    private final String nameJa;
    private final String nameEn;
    private final boolean latin;
    private final String symbols;

    /// 256 bits: whether each byte value is permitted. Built once, per constant.
    private final long[] permitted = new long[4];

    CharacterClass(String nameJa, String nameEn, boolean latin, String symbols) {
        this.nameJa = nameJa;
        this.nameEn = nameEn;
        this.latin = latin;
        this.symbols = symbols;
    }

    /// Returns the Japanese name of this field class.
    ///
    /// @return the name, never `null`
    public String nameJa() {
        return nameJa;
    }

    /// Returns an English gloss for this field class.
    ///
    /// @return the gloss, never `null`
    public String nameEn() {
        return nameEn;
    }

    /// Returns the symbols this class permits, as a string.
    ///
    /// @return the permitted symbols, possibly empty, never `null`
    public String symbols() {
        return symbols;
    }

    /// Whether a byte value is permitted in this class.
    ///
    /// @param value the byte value, `0`–`255`
    /// @return `true` if permitted
    public boolean permits(int value) {
        if (value < 0 || value > 0xFF) {
            return false;
        }
        return (permitted[value >>> 6] & (1L << (value & 63))) != 0;
    }

    // Overridden by the constants that differ. Kept package-private: these are
    // the knobs the table is built from, not part of the published surface.

    boolean permitsKana() {
        return true;
    }

    boolean permitsSpace() {
        return true;
    }

    boolean permitsWo() {
        return false;
    }

    static {
        // Built after construction so the overridable predicates above are
        // dispatched on fully-initialised constants.
        for (CharacterClass value : values()) {
            value.buildTable();
        }
    }

    private void buildTable() {
        if (this == UNRESTRICTED) {
            Arrays.fill(permitted, -1L);
            return;
        }
        for (int code = '0'; code <= '9'; code++) {
            allow(code);
        }
        if (latin) {
            for (int code = 'A'; code <= 'Z'; code++) {
                allow(code);
            }
        }
        if (permitsSpace()) {
            allow(' ');
        }
        if (permitsKana()) {
            for (int code = KANA_FIRST; code <= KANA_LAST; code++) {
                allow(code);
            }
            allow(VOICED_MARK);
            allow(SEMI_VOICED_MARK);
            if (permitsWo()) {
                allow(WO);
            }
        }
        for (int i = 0; i < symbols.length(); i++) {
            allow(jisX0201(symbols.charAt(i)));
        }
    }

    private void allow(int value) {
        permitted[value >>> 6] |= 1L << (value & 63);
    }

    /// Maps the two symbols whose JIS X 0201 byte differs from their Unicode
    /// code point. Everything else in these sets is ASCII-identical.
    private static int jisX0201(char symbol) {
        return switch (symbol) {
            case '｢' -> 0xA2;
            case '｣' -> 0xA3;
            default -> symbol;
        };
    }
}
