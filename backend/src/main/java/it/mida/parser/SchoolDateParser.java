package it.mida.parser;

import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Parses the Italian dates found in DidUP files. Dates do not carry a year:
 * September-December belong to the first year of the school year, while
 * January-August belong to the following calendar year.
 */
@Component
public class SchoolDateParser {

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "^(?:(LUNEDI|MARTEDI|MERCOLEDI|GIOVEDI|VENERDI|SABATO|DOMENICA)\\s+)?"
                    + "(\\d{1,2})\\s+([A-Z]+)(?:\\s+(\\d{4}))?$");

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("GENNAIO", 1),
            Map.entry("FEBBRAIO", 2),
            Map.entry("MARZO", 3),
            Map.entry("APRILE", 4),
            Map.entry("MAGGIO", 5),
            Map.entry("GIUGNO", 6),
            Map.entry("LUGLIO", 7),
            Map.entry("AGOSTO", 8),
            Map.entry("SETTEMBRE", 9),
            Map.entry("OTTOBRE", 10),
            Map.entry("NOVEMBRE", 11),
            Map.entry("DICEMBRE", 12));

    private static final Map<String, DayOfWeek> WEEKDAYS = Map.of(
            "LUNEDI", DayOfWeek.MONDAY,
            "MARTEDI", DayOfWeek.TUESDAY,
            "MERCOLEDI", DayOfWeek.WEDNESDAY,
            "GIOVEDI", DayOfWeek.THURSDAY,
            "VENERDI", DayOfWeek.FRIDAY,
            "SABATO", DayOfWeek.SATURDAY,
            "DOMENICA", DayOfWeek.SUNDAY);

    public ParsedSchoolDate parse(String raw, int schoolYearStart) {
        if (raw == null || raw.isBlank()) {
            throw new DidupParseException("Data scolastica mancante");
        }
        String trimmed = raw.strip();
        String lookup = normalizeLookup(trimmed);
        Matcher matcher = DATE_PATTERN.matcher(lookup);
        if (!matcher.matches()) {
            throw new DidupParseException("Data non interpretabile: \"" + trimmed + "\"");
        }

        String weekdayToken = matcher.group(1);
        int day = Integer.parseInt(matcher.group(2));
        String monthToken = matcher.group(3);
        Integer explicitYear = matcher.group(4) == null ? null : Integer.valueOf(matcher.group(4));

        Integer month = MONTHS.get(monthToken);
        if (month == null) {
            throw new DidupParseException("Mese non riconosciuto: \"" + trimmed + "\"");
        }

        int year;
        if (explicitYear != null) {
            if (explicitYear != schoolYearStart && explicitYear != schoolYearStart + 1) {
                throw new DidupParseException("Anno " + explicitYear + " non coerente con l'anno scolastico "
                        + schoolYearStart + "/" + (schoolYearStart + 1) + ": \"" + trimmed + "\"");
            }
            year = explicitYear;
        } else {
            year = month >= 9 ? schoolYearStart : schoolYearStart + 1;
        }

        LocalDate date;
        try {
            date = LocalDate.of(year, month, day);
        } catch (DateTimeException ex) {
            throw new DidupParseException("Data non valida: \"" + trimmed + "\"", ex);
        }

        if (weekdayToken != null) {
            DayOfWeek declared = WEEKDAYS.get(weekdayToken);
            if (declared != date.getDayOfWeek()) {
                throw new DidupParseException("Giorno della settimana incoerente: \"" + trimmed
                        + "\" corrisponde a " + date.getDayOfWeek() + " (" + date + ")");
            }
        }

        return new ParsedSchoolDate(date, trimmed);
    }

    private String normalizeLookup(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toUpperCase(Locale.ITALIAN).strip();
    }

    public record ParsedSchoolDate(LocalDate value, String raw) {
    }
}
