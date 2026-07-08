package com.corpusai.subject;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SlugGenerator {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");

    private SlugGenerator() {
    }

    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Cannot generate a slug from empty input");
        }

        String withoutDj = input.replace('đ', 'd').replace('Đ', 'D');
        String normalized = Normalizer.normalize(withoutDj, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(normalized).replaceAll("");
        String lowerCased = withoutDiacritics.toLowerCase(Locale.ROOT);
        String dashed = NON_ALPHANUMERIC.matcher(lowerCased).replaceAll("-");
        String trimmed = EDGE_DASHES.matcher(dashed).replaceAll("");

        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Slug would be empty for input: " + input);
        }
        return trimmed;
    }
}
