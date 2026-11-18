package it.mida.google.email;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decodifica le intestazioni MIME RFC 2047 usate da Gmail per oggetto e nomi
 * dei mittenti.
 */
public final class MimeHeaderDecoder {

    private static final Pattern ENCODED_WORD =
            Pattern.compile("=\\?([^?]+)\\?([BbQq])\\?([^?]*)\\?=");

    private MimeHeaderDecoder() {
    }

    public static String decode(String value) {
        if (value == null || value.isBlank() || !value.contains("=?")) {
            return value;
        }
        Matcher matcher = ENCODED_WORD.matcher(value);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            String between = value.substring(last, matcher.start());
            if (!(result.length() > 0 && between.isBlank())) {
                result.append(between);
            }
            String charset = matcher.group(1).split("\\*", 2)[0];
            String encoding = matcher.group(2).toUpperCase();
            String text = matcher.group(3);
            result.append(decodeWord(charset, encoding, text));
            last = matcher.end();
        }
        result.append(value.substring(last));
        return result.toString();
    }

    private static String decodeWord(String charset, String encoding, String text) {
        try {
            byte[] bytes = "B".equals(encoding)
                    ? Base64.getDecoder().decode(text)
                    : decodeQuotedPrintable(text);
            Charset target;
            try {
                target = Charset.forName(charset);
            } catch (RuntimeException ex) {
                target = StandardCharsets.UTF_8;
            }
            return new String(bytes, target);
        } catch (RuntimeException ex) {
            return "=?" + charset + "?" + encoding + "?" + text + "?=";
        }
    }

    private static byte[] decodeQuotedPrintable(String text) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '_') {
                output.write(' ');
            } else if (character == '=' && index + 2 < text.length()) {
                int value = Integer.parseInt(text.substring(index + 1, index + 3), 16);
                output.write(value);
                index += 2;
            } else {
                output.write((byte) character);
            }
        }
        return output.toByteArray();
    }
}
