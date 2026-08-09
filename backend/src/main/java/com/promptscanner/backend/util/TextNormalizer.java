package com.promptscanner.backend.util;

import java.text.Normalizer;
import java.util.Map;

/**
 * Folds text into a canonical form before pattern matching.
 *
 * An LLM reads a word spelled with a Cyrillic "i" (U+0456) exactly as if it were
 * the Latin letter, but to a static rule they are different codepoints, so every
 * phrase check misses it. The same applies to fullwidth forms, mathematical
 * alphanumerics, accented letters and zero-width padding. Normalising all of
 * these to plain text closes the gap without loosening the rules themselves.
 */
public final class TextNormalizer {

    private TextNormalizer() {}

    /**
     * Cross-script characters that render as Latin letters.
     *
     * Compatibility forms (fullwidth, math alphanumerics, ligatures) are handled
     * by NFKD and are deliberately absent here. Cyrillic and Greek are NOT
     * compatibility-equivalent to Latin -- Unicode treats them as distinct
     * letters -- so they need this explicit table.
     *
     * The keys are literal glyphs, which is only safe because the build pins
     * project.build.sourceEncoding to UTF-8; a TextNormalizerTest asserts the
     * table survived compilation intact.
     */
    private static final Map<Character, Character> CONFUSABLES = Map.ofEntries(
            // --- Cyrillic lowercase ---
            Map.entry('а', 'a'), // а
            Map.entry('в', 'b'), // в
            Map.entry('е', 'e'), // е
            Map.entry('к', 'k'), // к
            Map.entry('м', 'm'), // м
            Map.entry('н', 'h'), // н
            Map.entry('о', 'o'), // о
            Map.entry('р', 'p'), // р
            Map.entry('с', 'c'), // с
            Map.entry('т', 't'), // т
            Map.entry('у', 'y'), // у
            Map.entry('х', 'x'), // х
            Map.entry('і', 'i'), // і
            Map.entry('ј', 'j'), // ј
            Map.entry('ѕ', 's'), // ѕ
            Map.entry('ԁ', 'd'), // ԁ

            // --- Cyrillic uppercase ---
            Map.entry('А', 'A'), Map.entry('В', 'B'), Map.entry('Е', 'E'),
            Map.entry('К', 'K'), Map.entry('М', 'M'), Map.entry('Н', 'H'),
            Map.entry('О', 'O'), Map.entry('Р', 'P'), Map.entry('С', 'C'),
            Map.entry('Т', 'T'), Map.entry('У', 'Y'), Map.entry('Х', 'X'),
            Map.entry('І', 'I'), Map.entry('Ј', 'J'), Map.entry('Ѕ', 'S'),

            // --- Greek lowercase ---
            Map.entry('α', 'a'), // α
            Map.entry('ο', 'o'), // ο
            Map.entry('ρ', 'p'), // ρ
            Map.entry('ν', 'v'), // ν
            Map.entry('υ', 'u'), // υ
            Map.entry('κ', 'k'), // κ
            Map.entry('ι', 'i'), // ι
            Map.entry('τ', 't'), // τ
            Map.entry('ε', 'e'), // ε
            Map.entry('ϲ', 'c'), // ϲ lunate sigma

            // --- Greek uppercase ---
            Map.entry('Α', 'A'), Map.entry('Β', 'B'), Map.entry('Ε', 'E'),
            Map.entry('Ζ', 'Z'), Map.entry('Η', 'H'), Map.entry('Ι', 'I'),
            Map.entry('Κ', 'K'), Map.entry('Μ', 'M'), Map.entry('Ν', 'N'),
            Map.entry('Ο', 'O'), Map.entry('Ρ', 'P'), Map.entry('Τ', 'T'),
            Map.entry('Υ', 'Y'), Map.entry('Χ', 'X'),

            // --- Latin lookalikes from other blocks ---
            Map.entry('ı', 'i'), // ı dotless i
            Map.entry('ȷ', 'j'), // ȷ dotless j
            Map.entry('ɑ', 'a'), // ɑ latin alpha
            Map.entry('ɡ', 'g'), // ɡ script g
            Map.entry('ᴏ', 'O')  // ᴏ small capital O
    );

    /**
     * Returns text with disguised characters folded to their plain equivalents:
     * compatibility forms decomposed, combining marks and invisible formatting
     * characters removed, and cross-script lookalikes mapped to Latin.
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        if (text.isEmpty()) {
            return text;
        }

        // NFKD decomposes fullwidth forms, math alphanumerics, ligatures and
        // accented letters into a base character plus separate combining marks.
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD);

        StringBuilder out = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);

            int type = Character.getType(c);
            // Drop the combining marks left by decomposition, plus zero-width
            // spaces and bidi overrides used to pad words apart.
            if (type == Character.NON_SPACING_MARK
                    || type == Character.ENCLOSING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.FORMAT) {
                continue;
            }

            Character mapped = CONFUSABLES.get(c);
            out.append(mapped != null ? mapped : c);
        }
        return out.toString();
    }
}
