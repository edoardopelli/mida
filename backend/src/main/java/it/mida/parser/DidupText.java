package it.mida.parser;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Shared decoding and line-ending normalisation for the DidUP text formats.
 */
final class DidupText {

    private DidupText() {
    }

    static String decode(byte[] content) {
        if (content == null) {
            throw new DidupParseException("Contenuto del file mancante");
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw new DidupParseException("Il file non è un testo UTF-8 valido", ex);
        }
    }

    static String normalize(String content) {
        if (content == null) {
            throw new DidupParseException("Contenuto del file mancante");
        }
        String text = content;
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
