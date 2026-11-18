package it.mida.parser;

import it.mida.config.MidaProperties;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Parser for the DidUP Family diary export:
 *
 * <pre>
 * Attività svolta il 13 NOVEMBRE
 *
 * 1° ORA
 * INGLESE (E. NIFOSI)
 * Virtual classmates
 * </pre>
 *
 * <p>The parser is independent from the filesystem and the database. A file is
 * parsed completely or an exception is thrown: partial results are never
 * returned.</p>
 */
@Component
public class DidupDiaryParser {

    private static final Pattern DIARY_HEADER = Pattern.compile(
            "^\\s*ATTIVIT[ÀA]'?\\s+SVOLTA\\s+IL\\s+(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern PERIOD_PATTERN = Pattern.compile(
            "^\\s*(\\d{1,2})\\s*(?:[°º^]|\\.)?\\s*ORA\\s*:?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern SUBJECT_PATTERN = Pattern.compile(
            "^\\s*(\\p{L}[\\p{L}\\p{M}\\p{N}\\s'’.,&\\-–/]*?)\\s*\\(([^()]*)\\)\\s*:?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final SchoolDateParser dateParser;
    private final MidaProperties properties;

    public DidupDiaryParser(SchoolDateParser dateParser, MidaProperties properties) {
        this.dateParser = dateParser;
        this.properties = properties;
    }

    public ParsedDiaryFile parse(byte[] content) {
        return parse(DidupText.normalize(DidupText.decode(content)));
    }

    public ParsedDiaryFile parse(String content) {
        String text = DidupText.normalize(content);
        String[] lines = text.split("\n", -1);

        List<ParsedDiaryEntry> entries = new ArrayList<>();
        LocalDate activityDate = null;
        String activityDateRaw = null;
        Integer period = null;
        String periodLabel = null;
        String subject = null;
        String teacher = null;
        List<String> descriptionLines = null;

        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            int lineNumber = index + 1;

            Matcher header = DIARY_HEADER.matcher(line);
            if (header.matches()) {
                EntryDraft current = new EntryDraft(
                        activityDate, activityDateRaw, period, periodLabel, subject, teacher, descriptionLines);
                ParsedDiaryEntry completed = current.finish(lineNumber);
                if (completed != null) {
                    entries.add(completed);
                }
                SchoolDateParser.ParsedSchoolDate parsed =
                        dateWithContext(header.group(1), "data del diario", lineNumber);
                activityDate = parsed.value();
                activityDateRaw = parsed.raw();
                period = null;
                periodLabel = null;
                subject = null;
                teacher = null;
                descriptionLines = null;
                continue;
            }

            Matcher periodMatcher = PERIOD_PATTERN.matcher(line);
            if (periodMatcher.matches()) {
                if (activityDate == null) {
                    throw error(lineNumber, "Ora trovata prima di qualsiasi intestazione ATTIVITÀ SVOLTA IL");
                }
                EntryDraft current = new EntryDraft(
                        activityDate, activityDateRaw, period, periodLabel, subject, teacher, descriptionLines);
                ParsedDiaryEntry completed = current.finish(lineNumber);
                if (completed != null) {
                    entries.add(completed);
                }
                period = Integer.parseInt(periodMatcher.group(1));
                periodLabel = line.strip();
                subject = null;
                teacher = null;
                descriptionLines = null;
                continue;
            }

            Matcher subjectMatcher = SUBJECT_PATTERN.matcher(line);
            if (subjectMatcher.matches()) {
                if (activityDate == null) {
                    throw error(lineNumber, "Materia trovata prima di qualsiasi intestazione ATTIVITÀ SVOLTA IL");
                }
                if (period == null) {
                    throw error(lineNumber, "Materia trovata senza un'ora precedente");
                }
                if (subject != null) {
                    throw error(lineNumber, "La materia " + subject + " non ha una descrizione");
                }
                subject = normalizeSpaces(subjectMatcher.group(1));
                teacher = normalizeSpaces(subjectMatcher.group(2));
                if (subject.isBlank()) {
                    throw error(lineNumber, "Materia mancante");
                }
                if (teacher.isBlank()) {
                    throw error(lineNumber, "Professore mancante per la materia " + subject);
                }
                descriptionLines = new ArrayList<>();
                continue;
            }

            if (line.isBlank()) {
                if (descriptionLines != null) {
                    descriptionLines.add(line);
                }
                continue;
            }

            if (descriptionLines == null) {
                throw error(lineNumber, "Contenuto non riconosciuto: \"" + line.strip() + "\"");
            }
            descriptionLines.add(line);
        }

        ParsedDiaryEntry last = new EntryDraft(
                activityDate, activityDateRaw, period, periodLabel, subject, teacher, descriptionLines)
                .finish(lines.length);
        if (last != null) {
            entries.add(last);
        }

        if (entries.isEmpty()) {
            throw new DidupParseException("Nessuna attività valida trovata nel diario");
        }
        return new ParsedDiaryFile(properties.getSchoolYearStart(), List.copyOf(entries));
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

    private final class EntryDraft {
        private final LocalDate activityDate;
        private final String activityDateRaw;
        private final Integer period;
        private final String periodLabel;
        private final String subject;
        private final String teacher;
        private final List<String> descriptionLines;

        private EntryDraft(
                LocalDate activityDate,
                String activityDateRaw,
                Integer period,
                String periodLabel,
                String subject,
                String teacher,
                List<String> descriptionLines) {
            this.activityDate = activityDate;
            this.activityDateRaw = activityDateRaw;
            this.period = period;
            this.periodLabel = periodLabel;
            this.subject = subject;
            this.teacher = teacher;
            this.descriptionLines = descriptionLines;
        }

        private ParsedDiaryEntry finish(int lineNumber) {
            if (subject == null) {
                if (period != null) {
                    throw error(lineNumber, "L'ora " + period + " non contiene una materia");
                }
                return null;
            }
            if (activityDate == null) {
                throw error(lineNumber, "Attività senza una data di riferimento");
            }
            if (descriptionLines == null) {
                throw error(lineNumber, "Descrizione mancante per l'attività di " + subject);
            }
            String description = String.join("\n", descriptionLines).strip();
            String label = periodLabel == null ? period + "° ORA" : periodLabel;
            return new ParsedDiaryEntry(
                    activityDate,
                    activityDateRaw,
                    period,
                    label,
                    subject,
                    teacher,
                    description);
        }
    }

    public record ParsedDiaryFile(int schoolYearStart, List<ParsedDiaryEntry> entries) {
    }

    public record ParsedDiaryEntry(
            LocalDate activityDate,
            String activityDateRaw,
            int period,
            String periodLabel,
            String subject,
            String teacher,
            String description) {
    }
}
