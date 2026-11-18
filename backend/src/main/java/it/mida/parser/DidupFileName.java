package it.mida.parser;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises the DidUP Family filenames without assigning any meaning to the
 * optional numeric suffix.
 */
public record DidupFileName(String sourceCode, Integer filenameSuffix, String originalFilename) {

    private static final Pattern FILENAME_PATTERN =
            Pattern.compile("^text-([0-9A-Fa-f]{12})(?:-(\\d{1,9}))?\\.txt$");

    public static Optional<DidupFileName> parse(String filename) {
        if (filename == null || filename.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = FILENAME_PATTERN.matcher(filename);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String code = matcher.group(1).toUpperCase(Locale.ROOT);
        Integer suffix = matcher.group(2) == null ? null : Integer.valueOf(matcher.group(2));
        return Optional.of(new DidupFileName(code, suffix, filename));
    }
}
