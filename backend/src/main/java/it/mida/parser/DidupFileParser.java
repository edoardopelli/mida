package it.mida.parser;

import it.mida.config.MidaProperties;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Parser for the plain-text export produced by DidUP Family.
 *
 * <p>The parser is intentionally independent from the filesystem and from the
 * database. It only transforms UTF-8 bytes into structured, validated
 * assignments. A file is parsed completely or an exception is thrown: partial
 * results are never returned.</p>
 */
@Component
public class DidupFileParser {

    private static final Pattern ASSIGNED_PATTERN = Pattern.compile(
            "^\\s*ASSEGNATI\\s+IL\\s+(.+?)\\s*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern SUBJECT_PATTERN = Pattern.compile(
            "^\\s*(\\p{L}[\\p{L}\\p{M}\\p{N}\\s'’.,&\\-–/]*?)\\s*\\(([^()]*)\\)\\s*:\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern DUE_PATTERN = Pattern.compile(
            "^\\s*-{2,}\\s*CONSEGNA\\s+(.+?)\\s*-{2,}\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final SchoolDateParser dateParser;
    private final MidaProperties properties;

    public DidupFileParser(SchoolDateParser dateParser, MidaProperties properties) {
        this.dateParser = dateParser;
        this.properties = properties;
    }

    public ParsedDidupFile parse(byte[] content) {
        return parse(DidupText.normalize(DidupText.decode(content)));
    }

    public ParsedDidupFile parse(String content) {
        String text = DidupText.normalize(content);
        String[] lines = text.split("\n", -1);

        int schoolYearStart = properties.getSchoolYearStart();
        List<ParsedAssignment> assignments = new ArrayList<>();

        LocalDate currentAssignedDate = null;
        String currentAssignedRaw = null;
        SubjectBlock current = null;

        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            int lineNumber = index + 1;

            Matcher assignedMatcher = ASSIGNED_PATTERN.matcher(line);
            if (assignedMatcher.matches()) {
                if (current != null) {
                    throw error(lineNumber, "Trovata una nuova intestazione ASSEGNATI IL prima della CONSEGNA del compito di "
                            + current.subject);
                }
                SchoolDateParser.ParsedSchoolDate parsed =
                        dateWithContext(assignedMatcher.group(1), "data di assegnazione", lineNumber);
                currentAssignedDate = parsed.value();
                currentAssignedRaw = parsed.raw();
                continue;
            }

            Matcher subjectMatcher = SUBJECT_PATTERN.matcher(line);
            if (subjectMatcher.matches()) {
                if (current != null) {
                    throw error(lineNumber, "Il compito di " + current.subject
                            + " non ha una riga --- CONSEGNA ... ---");
                }
                if (currentAssignedDate == null) {
                    throw error(lineNumber, "Materia trovata prima di qualsiasi intestazione ASSEGNATI IL");
                }
                String subject = normalizeSpaces(subjectMatcher.group(1));
                String teacher = normalizeSpaces(subjectMatcher.group(2));
                if (subject.isBlank()) {
                    throw error(lineNumber, "Materia mancante");
                }
                if (teacher.isBlank()) {
                    throw error(lineNumber, "Professore mancante per la materia " + subject);
                }
                current = new SubjectBlock(subject, teacher, currentAssignedDate, currentAssignedRaw);
                continue;
            }

            Matcher dueMatcher = DUE_PATTERN.matcher(line);
            if (dueMatcher.matches()) {
                if (current == null) {
                    throw error(lineNumber, "Riga di consegna senza una materia associata");
                }
                SchoolDateParser.ParsedSchoolDate parsed =
                        dateWithContext(dueMatcher.group(1), "data di consegna", lineNumber);
                String description = String.join("\n", current.descriptionLines).strip();
                if (description.isBlank()) {
                    throw error(lineNumber, "Descrizione mancante per il compito di " + current.subject);
                }
                if (parsed.value().isBefore(current.assignedDate)) {
                    throw error(lineNumber, "La data di consegna " + parsed.value()
                            + " precede la data di assegnazione " + current.assignedDate);
                }
                assignments.add(new ParsedAssignment(
                        current.assignedDate,
                        current.assignedDateRaw,
                        parsed.value(),
                        parsed.raw(),
                        current.subject,
                        current.teacher,
                        description));
                current = null;
                continue;
            }

            if (line.isBlank()) {
                if (current != null) {
                    current.descriptionLines.add(line);
                }
                continue;
            }

            if (current == null) {
                throw error(lineNumber, "Contenuto non riconosciuto: \"" + line.strip() + "\"");
            }
            current.descriptionLines.add(line);
        }

        if (current != null) {
            throw new DidupParseException("Il compito di " + current.subject
                    + " non ha una riga --- CONSEGNA ... ---");
        }
        if (assignments.isEmpty()) {
            throw new DidupParseException("Nessun compito valido trovato nel file");
        }

        return new ParsedDidupFile(schoolYearStart, List.copyOf(assignments));
    }

    private SchoolDateParser.ParsedSchoolDate dateWithContext(String raw, String label, int lineNumber) {
        try {
            return dateParser.parse(raw, properties.getSchoolYearStart());
        } catch (DidupParseException ex) {
            throw error(lineNumber, "Errore nella " + label + ": " + ex.getMessage());
        }
    }

    private DidupParseException error(int lineNumber, String message) {
        return new DidupParseException("Riga " + lineNumber + ": " + message);
    }

    private String normalizeSpaces(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    private static final class SubjectBlock {
        private final String subject;
        private final String teacher;
        private final LocalDate assignedDate;
        private final String assignedDateRaw;
        private final List<String> descriptionLines = new ArrayList<>();

        private SubjectBlock(String subject, String teacher, LocalDate assignedDate, String assignedDateRaw) {
            this.subject = subject;
            this.teacher = teacher;
            this.assignedDate = assignedDate;
            this.assignedDateRaw = assignedDateRaw;
        }
    }

    public record ParsedDidupFile(int schoolYearStart, List<ParsedAssignment> assignments) {
    }

    public record ParsedAssignment(
            LocalDate assignedDate,
            String assignedDateRaw,
            LocalDate dueDate,
            String dueDateRaw,
            String subject,
            String teacher,
            String description) {
    }
}
