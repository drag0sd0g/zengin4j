package io.zengin4j.codegen;

import module java.base;

/// Emits the transliteration tables as committed Java (R-K9).
///
/// **The mechanical correspondence is derived, not transcribed.**
/// Unicode already defines which half-width form corresponds to which full-width
/// one, through the compatibility decompositions: normalising `ｶﾞ` under
/// NFKC yields `ガ`, so inverting that over the half-width block yields the
/// narrowing table complete with voiced decomposition. Writing those ~190 pairs
/// by hand would be the same error-prone transcription R-F2 forbids for byte
/// offsets, and a slip would be invisible — ｼ for ｿ reads as a plausible name.
///
/// What cannot be derived is which characters the standard's field rules
/// refuse and what to write instead. Those are declared in
/// `kana-substitutions.yaml` and merged in here.
///
/// The generator asserts its own output: every narrowed form must be a single
/// byte in JIS X 0201, and the table must have the expected cardinality. A JDK
/// whose Unicode data disagreed would fail the build rather than quietly emit a
/// different table on one leg of the CI matrix.
final class KanaTablesGenerator {

    static final String PACKAGE = "io.zengin4j.core.kana.generated";
    static final String GENERATOR = "io.zengin4j.codegen.KanaTablesGenerator";

    private static final String NL = "\n";

    /// JIS X 0201 as Windows extends it — the encoding these files actually use.
    private static final Charset JIS = Charset.forName("windows-31j");

    /// Half-width katakana and punctuation.
    private static final char HALF_WIDTH_FIRST = 0xFF61;
    private static final char HALF_WIDTH_LAST = 0xFF9F;

    /// Full-width ASCII forms.
    private static final char FULL_WIDTH_ASCII_FIRST = 0xFF01;
    private static final char FULL_WIDTH_ASCII_LAST = 0xFF5E;

    private static final char DAKUTEN = 0xFF9E;
    private static final char HANDAKUTEN = 0xFF9F;

    /// Hiragana and katakana run in parallel at this distance (R-K5).
    private static final int HIRAGANA_TO_KATAKANA = 0x60;

    private static final char HIRAGANA_FIRST = 0x3041;
    private static final char HIRAGANA_LAST = 0x3096;

    /// What the derivation is expected to produce.
    ///
    /// Pinned so that a change in the JDK's Unicode data is a build failure
    /// with a number in it, rather than a silently different table.
    private static final int EXPECTED_NARROWING_ENTRIES = 186;

    private final Path javaRoot;

    KanaTablesGenerator(Path javaRoot) {
        this.javaRoot = javaRoot;
    }

    /// Generates the tables.
    ///
    /// @param substitutions the declared judgement calls
    /// @param source        the file they came from, for the header
    /// @return the generated file
    GeneratedFile generate(List<KanaSubstitutionReader.Substitution> substitutions, String source) {
        Map<String, String> narrowing = deriveNarrowing();
        verify(narrowing, substitutions);

        StringBuilder out = new StringBuilder();
        header(out, source);
        narrowTable(out, narrowing);
        widenTable(out, narrowing);
        substitutionTable(out, substitutions);
        hiragana(out);
        out.append('}').append(NL);

        Path path = javaRoot.resolve(PACKAGE.replace('.', '/')).resolve("KanaTables.java");
        return new GeneratedFile(path, out.toString());
    }

    /// The package's `package-info`.
    GeneratedFile packageInfo() {
        String content =
                "/// Transliteration tables, compiled from Unicode and from the declared" + NL
                + "/// substitutions." + NL
                + "///" + NL
                + "/// GENERATED and committed, never hand-edited (R-M8). The mechanical" + NL
                + "/// width correspondence is derived from Unicode's compatibility" + NL
                + "/// decompositions; the judgement calls come from" + NL
                + "/// `zengin4j-core/kana/kana-substitutions.yaml`." + NL
                + "///" + NL
                + "/// @since 0.4.0" + NL
                + "package " + PACKAGE + ";" + NL;
        Path path = javaRoot.resolve(PACKAGE.replace('.', '/')).resolve("package-info.java");
        return new GeneratedFile(path, content);
    }

    // ------------------------------------------------------------- derivation

    /// Full-width form to half-width sequence, derived from Unicode.
    ///
    /// Two directions are needed because the compatibility data runs one way
    /// for each block: a full-width ASCII form normalises *to* its ASCII
    /// character, while a half-width kana normalises *to* its full-width
    /// one, so the kana table is the inverse of what NFKC gives.
    private static Map<String, String> deriveNarrowing() {
        Map<String, String> narrowing = new LinkedHashMap<>();

        for (char full = FULL_WIDTH_ASCII_FIRST; full <= FULL_WIDTH_ASCII_LAST; full++) {
            String ascii = Normalizer.normalize(String.valueOf(full), Normalizer.Form.NFKC);
            if (ascii.length() == 1 && ascii.charAt(0) < 0x80) {
                narrowing.put(String.valueOf(full), ascii);
            }
        }
        // The ideographic space, which is not in the full-width ASCII block.
        narrowing.put("　", " ");

        for (char half = HALF_WIDTH_FIRST; half <= HALF_WIDTH_LAST; half++) {
            for (String sequence : List.of(String.valueOf(half),
                    "" + half + DAKUTEN, "" + half + HANDAKUTEN)) {
                String full = Normalizer.normalize(sequence, Normalizer.Form.NFKC);
                if (full.codePointCount(0, full.length()) == 1 && !full.equals(sequence)) {
                    // putIfAbsent: the bare kana is offered before its voiced
                    // forms, so a kana that is its own composition keeps the
                    // shorter sequence.
                    narrowing.putIfAbsent(full, sequence);
                }
            }
        }
        return narrowing;
    }

    /// Fails the build rather than emitting a table that cannot be right.
    private static void verify(Map<String, String> narrowing,
            List<KanaSubstitutionReader.Substitution> substitutions) {

        if (narrowing.size() != EXPECTED_NARROWING_ENTRIES) {
            throw new CodegenException("the derived narrowing table has " + narrowing.size()
                    + " entries, expected " + EXPECTED_NARROWING_ENTRIES
                    + ". The JDK's Unicode data has changed, or the derivation has."
                    + " Check the diff before updating the expected count — this table decides"
                    + " how payee names are spelled.");
        }

        for (Map.Entry<String, String> entry : narrowing.entrySet()) {
            byte[] encoded = entry.getValue().getBytes(JIS);
            if (encoded.length != entry.getValue().length()) {
                throw new CodegenException("narrowing '" + entry.getKey() + "' gives '"
                        + entry.getValue() + "', which is " + encoded.length
                        + " bytes in JIS X 0201 rather than one byte per character."
                        + " A fixed-length field counts bytes, so this mapping cannot be used.");
            }
        }

        for (KanaSubstitutionReader.Substitution substitution : substitutions) {
            // A replacement must itself be narrowable, or writable as it stands.
            for (int i = 0; i < substitution.to().length(); ) {
                int codePoint = substitution.to().codePointAt(i);
                String character = new String(Character.toChars(codePoint));
                boolean narrowable = narrowing.containsKey(character);
                boolean alreadyNarrow = character.getBytes(JIS).length == 1;
                if (!narrowable && !alreadyNarrow) {
                    throw new CodegenException("substitution '" + substitution.from() + "' -> '"
                            + substitution.to() + "' produces '" + character
                            + "', which is neither narrowable nor already a single byte");
                }
                i += Character.charCount(codePoint);
            }
        }
    }

    // --------------------------------------------------------------- emission

    private void header(StringBuilder out, String source) {
        out.append("package ").append(PACKAGE).append(';').append(NL).append(NL)
                .append("import module java.base;").append(NL)
                .append("import io.zengin4j.core.annotation.Generated;").append(NL)
                .append("import io.zengin4j.core.kana.KanaSubstitution;").append(NL)
                .append("import io.zengin4j.core.loss.LossSeverity;").append(NL).append(NL)
                .append("/// Transliteration tables (R-K9).").append(NL)
                .append("///").append(NL)
                .append("/// GENERATED by ").append(GENERATOR).append(" — do not edit.").append(NL)
                .append("///").append(NL)
                .append("/// The width correspondence is derived from Unicode's compatibility")
                .append(NL)
                .append("/// decompositions rather than transcribed, so a slip of the kind that")
                .append(NL)
                .append("/// turns ｼ into ｿ cannot happen here. The substitutions below it are the")
                .append(NL)
                .append("/// judgement calls, declared in {@code ").append(source).append("}.")
                .append(NL)
                .append("///").append(NL)
                .append("/// @since 0.4.0").append(NL)
                .append("@Generated(value = \"").append(GENERATOR)
                .append("\", source = \"kana/kana-substitutions.yaml + Unicode NFKC\")").append(NL)
                .append("public final class KanaTables {").append(NL).append(NL)
                .append("    private KanaTables() {").append(NL)
                .append("    }").append(NL).append(NL);
    }

    private void narrowTable(StringBuilder out, Map<String, String> narrowing) {
        out.append("    /// Full-width form to its half-width sequence.").append(NL)
                .append("    private static final Map<String, String> NARROW = narrow();").append(NL)
                .append(NL)
                .append("    private static Map<String, String> narrow() {").append(NL)
                .append("        Map<String, String> table = new LinkedHashMap<>(")
                .append(narrowing.size() * 2).append(");").append(NL);
        narrowing.forEach((full, half) ->
                out.append("        table.put(").append(literal(full)).append(", ")
                        .append(literal(half)).append(");").append(NL));
        out.append("        return Map.copyOf(table);").append(NL)
                .append("    }").append(NL).append(NL)
                .append("    /// The half-width sequence for a full-width character.").append(NL)
                .append("    ///").append(NL)
                .append("    /// @param character the character to narrow").append(NL)
                .append("    /// @return the half-width sequence, or `null` if there is none")
                .append(NL)
                .append("    public static String narrow(String character) {").append(NL)
                .append("        return NARROW.get(character);").append(NL)
                .append("    }").append(NL).append(NL);
    }

    private void widenTable(StringBuilder out, Map<String, String> narrowing) {
        Map<String, String> widening = new LinkedHashMap<>();
        narrowing.forEach((full, half) -> widening.putIfAbsent(half, full));

        out.append("    /// Half-width sequence back to its full-width form (R-K8).").append(NL)
                .append("    ///").append(NL)
                .append("    /// The inverse of the table above, and not a perfect one: several")
                .append(NL)
                .append("    /// full-width characters narrow to the same half-width sequence, so")
                .append(NL)
                .append("    /// widening is informational rather than reversible.").append(NL)
                .append("    private static final Map<String, String> WIDEN = widen();").append(NL)
                .append(NL)
                .append("    private static Map<String, String> widen() {").append(NL)
                .append("        Map<String, String> table = new LinkedHashMap<>(")
                .append(widening.size() * 2).append(");").append(NL);
        widening.forEach((half, full) ->
                out.append("        table.put(").append(literal(half)).append(", ")
                        .append(literal(full)).append(");").append(NL));
        out.append("        return Map.copyOf(table);").append(NL)
                .append("    }").append(NL).append(NL)
                .append("    /// The full-width form for a half-width sequence.").append(NL)
                .append("    ///").append(NL)
                .append("    /// @param sequence the half-width character, with its voicing mark")
                .append(NL)
                .append("    ///   where it has one").append(NL)
                .append("    /// @return the full-width form, or `null` if there is none")
                .append(NL)
                .append("    public static String widen(String sequence) {").append(NL)
                .append("        return WIDEN.get(sequence);").append(NL)
                .append("    }").append(NL).append(NL);
    }

    private void substitutionTable(StringBuilder out,
            List<KanaSubstitutionReader.Substitution> substitutions) {

        out.append("    /// The declared judgement calls: what the field rules refuse.").append(NL)
                .append("    private static final Map<String, KanaSubstitution> SUBSTITUTIONS =")
                .append(NL)
                .append("            buildSubstitutions();").append(NL).append(NL)
                .append("    private static Map<String, KanaSubstitution> buildSubstitutions() {")
                .append(NL)
                .append("        Map<String, KanaSubstitution> table = new LinkedHashMap<>(")
                .append(Math.max(substitutions.size() * 2, 2)).append(");").append(NL);
        for (KanaSubstitutionReader.Substitution substitution : substitutions) {
            out.append("        table.put(").append(literal(substitution.from()))
                    .append(", new KanaSubstitution(").append(literal(substitution.to()))
                    .append(", LossSeverity.").append(substitution.severity()).append(',').append(NL)
                    .append("                ").append(literal(substitution.whyEn())).append(',')
                    .append(NL)
                    .append("                ").append(literal(substitution.whyJa())).append("));")
                    .append(NL);
        }
        out.append("        return Map.copyOf(table);").append(NL)
                .append("    }").append(NL).append(NL)
                .append("    /// The substitution for a character the field rules refuse.").append(NL)
                .append("    ///").append(NL)
                .append("    /// @param character the character").append(NL)
                .append("    /// @return the substitution, or `null` if none applies").append(NL)
                .append("    public static KanaSubstitution substitution(String character) {").append(NL)
                .append("        return SUBSTITUTIONS.get(character);").append(NL)
                .append("    }").append(NL).append(NL)
                .append("    /// Every declared substitution, for the exhaustive table test.").append(NL)
                .append("    ///").append(NL)
                .append("    /// @return the substitutions, keyed by the character they replace")
                .append(NL)
                .append("    public static Map<String, KanaSubstitution> substitutions() {")
                .append(NL)
                .append("        return SUBSTITUTIONS;").append(NL)
                .append("    }").append(NL).append(NL)
                .append("    /// Every narrowing, for the exhaustive table test (R-T10).").append(NL)
                .append("    ///").append(NL)
                .append("    /// @return the table, keyed by full-width character").append(NL)
                .append("    public static Map<String, String> narrowings() {").append(NL)
                .append("        return NARROW;").append(NL)
                .append("    }").append(NL).append(NL);
    }

    private void hiragana(StringBuilder out) {
        out.append("    /// Hiragana and katakana run in parallel at this distance.").append(NL)
                .append("    private static final int HIRAGANA_TO_KATAKANA = 0x")
                .append(Integer.toHexString(HIRAGANA_TO_KATAKANA).toUpperCase(java.util.Locale.ROOT))
                .append(';').append(NL).append(NL)
                .append("    private static final int HIRAGANA_FIRST = 0x")
                .append(Integer.toHexString(HIRAGANA_FIRST).toUpperCase(java.util.Locale.ROOT))
                .append(';').append(NL)
                .append("    private static final int HIRAGANA_LAST = 0x")
                .append(Integer.toHexString(HIRAGANA_LAST).toUpperCase(java.util.Locale.ROOT))
                .append(';').append(NL).append(NL)
                .append("    /// Whether a code point is hiragana.").append(NL)
                .append("    ///").append(NL)
                .append("    /// @param codePoint the code point").append(NL)
                .append("    /// @return `true` if it is").append(NL)
                .append("    public static boolean isHiragana(int codePoint) {").append(NL)
                .append("        return codePoint >= HIRAGANA_FIRST && codePoint <= HIRAGANA_LAST;")
                .append(NL)
                .append("    }").append(NL).append(NL)
                .append("    /// The katakana for a hiragana code point (R-K5).").append(NL)
                .append("    ///").append(NL)
                .append("    /// A fixed offset rather than a table: the two syllabaries were")
                .append(NL)
                .append("    /// encoded in parallel, and every character in the range corresponds.")
                .append(NL)
                .append("    ///").append(NL)
                .append("    /// @param codePoint a hiragana code point").append(NL)
                .append("    /// @return the katakana code point").append(NL)
                .append("    public static int katakanaFor(int codePoint) {").append(NL)
                .append("        return codePoint + HIRAGANA_TO_KATAKANA;").append(NL)
                .append("    }").append(NL);
    }

    /// A Java string literal, with every non-ASCII character escaped.
    ///
    /// Escaped rather than written literally so the generated file is pure
    /// ASCII: it is committed, diffed and reviewed, and a table of kana renders
    /// differently depending on what opens it.
    private static String literal(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20 || c > 0x7E) {
                        out.append(String.format(java.util.Locale.ROOT, "\\u%04X", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
