package net.siberanka.discordsocialspy.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContentFilter {

    public enum Match { WORD, ADDRESS, REGEX }

    private static final int MAX_INPUT_LENGTH = 4_096;
    private static final String LETTER_OR_NUMBER = "\\p{L}\\p{N}";
    private static final String LOOSE_SEPARATOR = "[^" + LETTER_OR_NUMBER + "]";
    private static final String DOMAIN_PUNCTUATION = "[.\\u3002\\uff0e\\uff61\\u00b7\\u2022,_/\\\\|:;()\\[\\]{}<>-]";
    private static final String LABEL = "[" + LETTER_OR_NUMBER + "](?:[" + LETTER_OR_NUMBER
            + "-]{0,61}[" + LETTER_OR_NUMBER + "])?";
    private static final String TLD = "(?:c" + LOOSE_SEPARATOR + "*o" + LOOSE_SEPARATOR + "*m"
            + "|n" + LOOSE_SEPARATOR + "*e" + LOOSE_SEPARATOR + "*t"
            + "|o" + LOOSE_SEPARATOR + "*r" + LOOSE_SEPARATOR + "*g"
            + "|x" + LOOSE_SEPARATOR + "*y" + LOOSE_SEPARATOR + "*z"
            + "|i" + LOOSE_SEPARATOR + "*n" + LOOSE_SEPARATOR + "*f" + LOOSE_SEPARATOR + "*o"
            + "|o" + LOOSE_SEPARATOR + "*n" + LOOSE_SEPARATOR + "*l" + LOOSE_SEPARATOR + "*i"
                    + LOOSE_SEPARATOR + "*n" + LOOSE_SEPARATOR + "*e"
            + "|s" + LOOSE_SEPARATOR + "*i" + LOOSE_SEPARATOR + "*t" + LOOSE_SEPARATOR + "*e"
            + "|s" + LOOSE_SEPARATOR + "*t" + LOOSE_SEPARATOR + "*o" + LOOSE_SEPARATOR + "*r"
                    + LOOSE_SEPARATOR + "*e"
            + "|t" + LOOSE_SEPARATOR + "*e" + LOOSE_SEPARATOR + "*c" + LOOSE_SEPARATOR + "*h"
            + "|g" + LOOSE_SEPARATOR + "*a" + LOOSE_SEPARATOR + "*m" + LOOSE_SEPARATOR + "*e" + LOOSE_SEPARATOR + "*s"
            + "|c" + LOOSE_SEPARATOR + "*l" + LOOSE_SEPARATOR + "*o" + LOOSE_SEPARATOR + "*u" + LOOSE_SEPARATOR + "*d"
            + "|d" + LOOSE_SEPARATOR + "*e" + LOOSE_SEPARATOR + "*v"
            + "|a" + LOOSE_SEPARATOR + "*p" + LOOSE_SEPARATOR + "*p"
            + "|l" + LOOSE_SEPARATOR + "*i" + LOOSE_SEPARATOR + "*v" + LOOSE_SEPARATOR + "*e"
            + "|l" + LOOSE_SEPARATOR + "*i" + LOOSE_SEPARATOR + "*n" + LOOSE_SEPARATOR + "*k"
            + "|w" + LOOSE_SEPARATOR + "*e" + LOOSE_SEPARATOR + "*b" + LOOSE_SEPARATOR + "*s"
                    + LOOSE_SEPARATOR + "*i" + LOOSE_SEPARATOR + "*t" + LOOSE_SEPARATOR + "*e"
            + "|b" + LOOSE_SEPARATOR + "*i" + LOOSE_SEPARATOR + "*z"
            + "|p" + LOOSE_SEPARATOR + "*r" + LOOSE_SEPARATOR + "*o"
            + "|c" + LOOSE_SEPARATOR + "*l" + LOOSE_SEPARATOR + "*u" + LOOSE_SEPARATOR + "*b"
            + "|n" + LOOSE_SEPARATOR + "*e" + LOOSE_SEPARATOR + "*w" + LOOSE_SEPARATOR + "*s"
            + "|f" + LOOSE_SEPARATOR + "*u" + LOOSE_SEPARATOR + "*n"
            + "|t" + LOOSE_SEPARATOR + "*o" + LOOSE_SEPARATOR + "*p"
            + "|(?:tr|tc|tk|ml|ga|cf|gq|io|gg|me|cc|co|eu|us|uk|de|ru))";
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern DOT_WORD = Pattern.compile(
            "(?<=[" + LETTER_OR_NUMBER + "])\\s*(?:dot|nokta)\\s*(?=[" + LETTER_OR_NUMBER + "])", FLAGS);
    private static final Pattern STRICT_DOMAIN = Pattern.compile(
            "(?<![" + LETTER_OR_NUMBER + "])(?:" + LABEL + "\\s*" + DOMAIN_PUNCTUATION + "+\\s*){1,8}"
                    + TLD + "(?![" + LETTER_OR_NUMBER + "])", FLAGS);
    private static final Pattern AD_PREFIX_DOMAIN = Pattern.compile(
            "(?<![" + LETTER_OR_NUMBER + "])(?:p" + LOOSE_SEPARATOR + "*l" + LOOSE_SEPARATOR + "*a"
                    + LOOSE_SEPARATOR + "*y|m" + LOOSE_SEPARATOR + "*c|o" + LOOSE_SEPARATOR + "*y"
                    + LOOSE_SEPARATOR + "*n" + LOOSE_SEPARATOR + "*a|j" + LOOSE_SEPARATOR + "*o"
                    + LOOSE_SEPARATOR + "*i" + LOOSE_SEPARATOR + "*n|sunucu|server)"
                    + LOOSE_SEPARATOR + "+(?:" + LABEL + LOOSE_SEPARATOR + "+){1,6}" + TLD
                    + "(?![" + LETTER_OR_NUMBER + "])", FLAGS);
    private static final Pattern IPV4 = Pattern.compile(
            "(?<!\\d)(\\d{1,3})" + LOOSE_SEPARATOR + "+(\\d{1,3})" + LOOSE_SEPARATOR
                    + "+(\\d{1,3})" + LOOSE_SEPARATOR + "+(\\d{1,3})(?!\\d)");

    private final boolean enabled;
    private final boolean smartDetection;
    private final List<Pattern> wordPatterns;
    private final List<String> literalWords;
    private final List<String> whitelist;
    private final List<Pattern> patterns;

    private ContentFilter(boolean enabled, boolean smartDetection, List<Pattern> wordPatterns,
            List<String> literalWords, List<String> whitelist, List<Pattern> patterns) {
        this.enabled = enabled;
        this.smartDetection = smartDetection;
        this.wordPatterns = wordPatterns;
        this.literalWords = literalWords;
        this.whitelist = whitelist;
        this.patterns = patterns;
    }

    public static ContentFilter compile(boolean enabled, List<String> words, List<String> whitelist,
            List<String> expressions) {
        return compile(enabled, true, words, whitelist, expressions);
    }

    public static ContentFilter compile(boolean enabled, boolean smartDetection, List<String> words,
            List<String> whitelist, List<String> expressions) {
        List<Pattern> wordPatterns = new ArrayList<>();
        List<String> literalWords = new ArrayList<>();
        for (String word : normalize(words)) {
            if (isPlainTerm(word)) {
                wordPatterns.add(compileWordPattern(word, smartDetection));
            } else {
                literalWords.add(word);
            }
        }
        List<String> normalizedWhitelist = normalize(whitelist);
        List<Pattern> compiled = new ArrayList<>();
        for (String expression : expressions) {
            if (RegexSafety.isSafe(expression)) {
                compiled.add(Pattern.compile(expression, FLAGS));
            }
        }
        return new ContentFilter(enabled, smartDetection, Collections.unmodifiableList(wordPatterns),
                Collections.unmodifiableList(literalWords), normalizedWhitelist, Collections.unmodifiableList(compiled));
    }

    public Match find(String input) {
        if (!enabled || input == null || input.isEmpty()) {
            return null;
        }

        String bounded = input.length() <= MAX_INPUT_LENGTH ? input : input.substring(0, MAX_INPUT_LENGTH);
        String searchable = normalizeText(bounded);
        for (String allowed : whitelist) {
            searchable = searchable.replace(allowed, " ");
        }
        for (String word : literalWords) {
            if (searchable.contains(word)) {
                return Match.WORD;
            }
        }
        String folded = foldForWords(searchable);
        for (Pattern word : wordPatterns) {
            if (word.matcher(folded).find()) {
                return Match.WORD;
            }
        }
        if (smartDetection && containsAddress(searchable)) {
            return Match.ADDRESS;
        }
        for (Pattern pattern : patterns) {
            if (pattern.matcher(searchable).find()) {
                return Match.REGEX;
            }
        }
        return null;
    }

    private static List<String> normalize(List<String> values) {
        List<String> result = new ArrayList<>(values.size());
        for (String value : values) {
            String normalized = normalizeText(value).trim();
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean containsAddress(String input) {
        String dotted = DOT_WORD.matcher(input).replaceAll(".");
        if (STRICT_DOMAIN.matcher(dotted).find() || AD_PREFIX_DOMAIN.matcher(dotted).find()) {
            return true;
        }
        Matcher address = IPV4.matcher(dotted);
        while (address.find()) {
            boolean valid = true;
            for (int group = 1; group <= 4; group++) {
                if (Integer.parseInt(address.group(group)) > 255) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                return true;
            }
        }
        return false;
    }

    private static Pattern compileWordPattern(String input, boolean allowSeparators) {
        String folded = foldForWords(input).trim();
        StringBuilder expression = new StringBuilder("(?<![").append(LETTER_OR_NUMBER).append("])");
        boolean previousWasCharacter = false;
        for (int offset = 0; offset < folded.length();) {
            int codePoint = folded.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isLetterOrDigit(codePoint)) {
                if (previousWasCharacter && allowSeparators) {
                    expression.append(LOOSE_SEPARATOR).append('*');
                } else if (!previousWasCharacter && expression.length() > 0) {
                    expression.append(LOOSE_SEPARATOR).append('*');
                }
                expression.append(Pattern.quote(new String(Character.toChars(codePoint))));
                previousWasCharacter = true;
            } else {
                previousWasCharacter = false;
            }
        }
        expression.append("(?![").append(LETTER_OR_NUMBER).append("])");
        return Pattern.compile(expression.toString(), FLAGS);
    }

    private static boolean isPlainTerm(String value) {
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!Character.isLetterOrDigit(codePoint) && !Character.isWhitespace(codePoint)) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeText(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder clean = new StringBuilder(normalized.length());
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\u00a7' && offset < normalized.length()) {
                int formattingCode = normalized.codePointAt(offset);
                offset += Character.charCount(formattingCode);
                continue;
            }
            int type = Character.getType(codePoint);
            if (type == Character.FORMAT || (type == Character.CONTROL && !Character.isWhitespace(codePoint))) {
                continue;
            }
            clean.appendCodePoint(codePoint);
        }
        return clean.toString();
    }

    private static String foldForWords(String input) {
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        StringBuilder folded = new StringBuilder(decomposed.length());
        for (int offset = 0; offset < decomposed.length();) {
            int codePoint = decomposed.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.getType(codePoint) == Character.NON_SPACING_MARK) {
                continue;
            }
            switch (codePoint) {
                case '\u0131': folded.append('i'); break;
                case '0': folded.append('o'); break;
                case '1': folded.append('i'); break;
                case '3': folded.append('e'); break;
                case '4': folded.append('a'); break;
                case '5': folded.append('s'); break;
                case '7': folded.append('t'); break;
                default: folded.appendCodePoint(codePoint);
            }
        }
        return folded.toString();
    }
}
