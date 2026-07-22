package net.siberanka.discordsocialspy.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ContentFilter {

    public enum Match { WORD, REGEX }

    private static final int MAX_INPUT_LENGTH = 4_096;

    private final boolean enabled;
    private final List<String> words;
    private final List<String> whitelist;
    private final List<Pattern> patterns;

    private ContentFilter(boolean enabled, List<String> words, List<String> whitelist, List<Pattern> patterns) {
        this.enabled = enabled;
        this.words = words;
        this.whitelist = whitelist;
        this.patterns = patterns;
    }

    public static ContentFilter compile(boolean enabled, List<String> words, List<String> whitelist,
            List<String> expressions) {
        List<String> normalizedWords = normalize(words);
        List<String> normalizedWhitelist = normalize(whitelist);
        List<Pattern> compiled = new ArrayList<>();
        for (String expression : expressions) {
            if (RegexSafety.isSafe(expression)) {
                compiled.add(Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            }
        }
        return new ContentFilter(enabled, normalizedWords, normalizedWhitelist,
                Collections.unmodifiableList(compiled));
    }

    public Match find(String input) {
        if (!enabled || input == null || input.isEmpty()) {
            return null;
        }

        String bounded = input.length() <= MAX_INPUT_LENGTH ? input : input.substring(0, MAX_INPUT_LENGTH);
        String searchable = bounded.toLowerCase(Locale.ROOT);
        for (String allowed : whitelist) {
            searchable = searchable.replace(allowed, " ");
        }
        for (String word : words) {
            if (searchable.contains(word)) {
                return Match.WORD;
            }
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
            String normalized = value.toLowerCase(Locale.ROOT).trim();
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return Collections.unmodifiableList(result);
    }
}
