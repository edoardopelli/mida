package it.mida.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.mida.config.MidaProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DidupDiaryParserTest {

    private DidupDiaryParser parser;
    private DidupContentDetector detector;

    @BeforeEach
    void setUp() {
        MidaProperties properties = new MidaProperties();
        properties.setSchoolYearStart(2026);
        parser = new DidupDiaryParser(new SchoolDateParser(), properties);
        detector = new DidupContentDetector();
    }

    @Test
    void parsesTheProvidedDiarySample() {
        byte[] fixture = fixture("text-D1A2B3C4D5E6-1.txt");
        assertThat(detector.detect(fixture)).isEqualTo(DidupFileKind.DIARY);
        var parsed = parser.parse(fixture);

        assertThat(parsed.entries()).hasSize(5);
        assertThat(parsed.entries()).allSatisfy(entry -> {
            assertThat(entry.activityDate()).isEqualTo(LocalDate.of(2026, 11, 13));
            assertThat(entry.activityDateRaw()).isEqualTo("13 NOVEMBRE");
        });
        assertThat(parsed.entries())
                .extracting(DidupDiaryParser.ParsedDiaryEntry::period)
                .containsExactly(1, 2, 4, 5, 6);
        assertThat(parsed.entries())
                .extracting(DidupDiaryParser.ParsedDiaryEntry::periodLabel)
                .containsExactly("1° ORA", "2° ORA", "4° ORA", "5° ORA", "6° ORA");
        assertThat(parsed.entries())
                .extracting(DidupDiaryParser.ParsedDiaryEntry::subject)
                .containsExactly("INGLESE", "ITALIANO", "SCIENZE", "FRANCESE", "MUSICA");
        assertThat(parsed.entries())
                .extracting(DidupDiaryParser.ParsedDiaryEntry::teacher)
                .containsExactly("E. NIFOSI", "G. FAZIO", "C. ARIOTTI", "M. LEONARDI", "D. FRATTA");
        assertThat(parsed.entries().get(3).description())
                .isEqualTo("Interrogazioni. Dati personali, fornire le proprie coordinate, Tu es né où? "
                        + "Tu habites où? Studio dei verbi del primo gruppo, presente indicativo di parler, "
                        + "laver e tutti i verbi del primo gruppo.");
    }

    @Test
    void supportsMultipleDiaryDaysAndBlankLines() {
        String text = """
                Attività svolta il 13 NOVEMBRE

                1° ORA
                INGLESE (E. NIFOSI)
                Virtual classmates


                2° ORA
                ITALIANO (G. FAZIO)
                Prima riga
                Seconda riga

                Attività svolta il 14 NOVEMBRE

                3° ORA
                STORIA (L. BIANCHI)
                La Rivoluzione francese
                """;
        var parsed = parser.parse(text);

        assertThat(parsed.entries()).hasSize(3);
        assertThat(parsed.entries().get(0).activityDate()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(parsed.entries().get(1).activityDate()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(parsed.entries().get(1).description()).isEqualTo("Prima riga\nSeconda riga");
        assertThat(parsed.entries().get(2).activityDate()).isEqualTo(LocalDate.of(2026, 11, 14));
    }

    @Test
    void supportsBomAndCrLf() {
        byte[] fixture = fixture("text-D1A2B3C4D5E6-1.txt");
        String content = new String(fixture, StandardCharsets.UTF_8);
        byte[] converted = ("\uFEFF" + content.replace("\n", "\r\n")).getBytes(StandardCharsets.UTF_8);
        assertThat(parser.parse(converted).entries()).hasSize(5);
    }

    @Test
    void rejectsMalformedDiaryFiles() {
        assertThatThrownBy(() -> parser.parse("""
                1° ORA
                INGLESE (E. NIFOSI)
                Virtual classmates
                """))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("ATTIVITÀ SVOLTA IL");

        assertThatThrownBy(() -> parser.parse("""
                Attività svolta il 13 NOVEMBRE

                INGLESE (E. NIFOSI)
                Virtual classmates
                """))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("senza un'ora");

        assertThatThrownBy(() -> parser.parse("""
                Attività svolta il 31 NOVEMBRE

                1° ORA
                INGLESE (E. NIFOSI)
                Virtual classmates
                """))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("Data non valida");

        assertThatThrownBy(() -> parser.parse("Attività svolta il 13 NOVEMBRE"))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("Nessuna attività");
    }

    @Test
    void acceptsAndPreservesHoursWithoutDescription() {
        var parsed = parser.parse("""
                Attività svolta il 11 NOVEMBRE

                1° ORA
                MATEMATICA (C. ARIOTTI)
                correzione compiti

                2° ORA
                SCIENZE MOTORIE E SPORTIVE (C. MARCIA)



                3° ORA
                SCIENZE MOTORIE E SPORTIVE (C. MARCIA)
                """);

        assertThat(parsed.entries()).hasSize(3);
        assertThat(parsed.entries().get(1).subject()).isEqualTo("SCIENZE MOTORIE E SPORTIVE");
        assertThat(parsed.entries().get(1).description()).isEmpty();
        assertThat(parsed.entries().get(2).description()).isEmpty();
    }

    @Test
    void detectorDistinguishesHomeworkDiaryAndUnknownFiles() {
        assertThat(detector.detect("""
                ASSEGNATI IL 11 NOVEMBRE

                MATEMATICA (C. ARIOTTI):
                Svolgere es
                --- CONSEGNA MERCOLEDÌ 18 NOVEMBRE ---
                """.getBytes(StandardCharsets.UTF_8))).isEqualTo(DidupFileKind.HOMEWORK);

        assertThatThrownBy(() -> detector.detect("testo libero".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("Formato DidUP non riconosciuto");

        assertThatThrownBy(() -> detector.detect("""
                ASSEGNATI IL 11 NOVEMBRE
                Attività svolta il 13 NOVEMBRE
                """.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("sia compiti sia attività");
    }

    private byte[] fixture(String name) {
        try (InputStream stream = getClass().getResourceAsStream("/fixtures/didup/" + name)) {
            if (stream == null) {
                throw new IllegalStateException("Fixture non trovata: " + name);
            }
            return stream.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Impossibile leggere la fixture " + name, ex);
        }
    }
}
