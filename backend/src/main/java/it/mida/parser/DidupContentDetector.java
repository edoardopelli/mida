package it.mida.parser;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects whether a DidUP TXT file contains homework assignments or a school
 * diary. Detection happens on the content, not on the filename, because both
 * exports use the same {@code text-<CODICE>.txt} naming convention.
 */
@Component
public class DidupContentDetector {

    public static final Pattern HOMEWORK_HEADER = Pattern.compile(
            "^\\s*ASSEGNATI\\s+IL\\s+.+?\\s*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public static final Pattern DIARY_HEADER = Pattern.compile(
            "^\\s*ATTIVIT[ÀA]'?\\s+SVOLTA\\s+IL\\s+.+?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public DidupFileKind detect(byte[] content) {
        String text = DidupText.normalize(DidupText.decode(content));
        boolean homework = false;
        boolean diary = false;
        for (String line : text.split("\n", -1)) {
            if (HOMEWORK_HEADER.matcher(line).matches()) {
                homework = true;
            }
            if (DIARY_HEADER.matcher(line).matches()) {
                diary = true;
            }
            if (homework && diary) {
                throw new DidupParseException(
                        "Il file contiene sia compiti sia attività del diario: esportali separatamente");
            }
        }
        if (diary) {
            return DidupFileKind.DIARY;
        }
        if (homework) {
            return DidupFileKind.HOMEWORK;
        }
        throw new DidupParseException(
                "Formato DidUP non riconosciuto: manca una intestazione ASSEGNATI IL o ATTIVITÀ SVOLTA IL");
    }
}
