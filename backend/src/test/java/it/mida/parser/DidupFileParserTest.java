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

class DidupFileParserTest {

    private DidupFileParser parser;

    @BeforeEach
    void setUp() {
        MidaProperties properties = new MidaProperties();
        properties.setSchoolYearStart(2026);
        parser = new DidupFileParser(new SchoolDateParser(), properties);
    }

    @Test
    void parsesTheThreeDidupSamples() {
        var first = parser.parse(fixture("text-AC508C2D4537-1.txt"));
        var second = parser.parse(fixture("text-BF82C5CC0482-1.txt"));
        var third = parser.parse(fixture("text-125BB5C551CC.txt"));

        assertThat(first.assignments()).hasSize(3);
        assertThat(second.assignments()).hasSize(2);
        assertThat(third.assignments()).hasSize(1);
        assertThat(first.assignments().size() + second.assignments().size() + third.assignments().size())
                .isEqualTo(6);

        assertThat(first.assignments())
                .allSatisfy(assignment -> {
                    assertThat(assignment.assignedDate()).isEqualTo(LocalDate.of(2026, 11, 11));
                    assertThat(assignment.dueDate()).isEqualTo(LocalDate.of(2026, 11, 18));
                    assertThat(assignment.assignedDateRaw()).isEqualTo("11 NOVEMBRE");
                    assertThat(assignment.dueDateRaw()).isEqualTo("MERCOLEDÌ 18 NOVEMBRE");
                });
        assertThat(first.assignments())
                .extracting(DidupFileParser.ParsedAssignment::subject)
                .containsExactly("ARTE ED IMMAGINE", "ARTE ED IMMAGINE", "MATEMATICA");
        assertThat(first.assignments())
                .extracting(DidupFileParser.ParsedAssignment::teacher)
                .containsExactly("A. FERTITTA", "A. FERTITTA", "C. ARIOTTI");

        assertThat(second.assignments())
                .extracting(DidupFileParser.ParsedAssignment::assignedDate)
                .containsExactly(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 5));
        assertThat(second.assignments())
                .extracting(DidupFileParser.ParsedAssignment::dueDate)
                .containsExactly(LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 19));

        assertThat(third.assignments().getFirst().subject()).isEqualTo("ITALIANO");
        assertThat(third.assignments().getFirst().teacher()).isEqualTo("G. FAZIO");
        assertThat(third.assignments().getFirst().assignedDate()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(third.assignments().getFirst().dueDate()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void keepsDescriptionsVerbatimIncludingQuotesAndMultilineText() {
        var first = parser.parse(fixture("text-AC508C2D4537-1.txt"));
        assertThat(first.assignments().get(0).description())
                .isEqualTo("Portare il consueto materiale di Arte e, in particolare, il foglio d'album con il "
                        + "paesaggio iniziato in classe , matita HB, tratto pen nero e pennarello nero a punta "
                        + "fine, perfettamente funzionanti.");
        assertThat(first.assignments().get(1).description())
                .isEqualTo("Per la prossima lezione, ultimare la tavola “LEGO” realizzando le sfumature dei "
                        + "mattoni del muro.");
        assertThat(first.assignments().get(2).description())
                .isEqualTo("Svolgere es:\n28-29-32-33 pag 109; 54 pag 110; "
                        + "59-60-61-65-66-67-72 pag 111; 90-91 pag 112");

        var third = parser.parse(fixture("text-125BB5C551CC.txt"));
        assertThat(third.assignments().getFirst().description())
                .isEqualTo("Antologia: esercizi su fotocopia \"Dal testo al genere\" e "
                        + "\"Dal testo alla realtà\".");
    }

    @Test
    void doesNotMergeDistinctAssignmentsSharingSubjectTeacherAndDate() {
        var parsed = parser.parse(fixture("text-AC508C2D4537-1.txt"));
        List<DidupFileParser.ParsedAssignment> artAssignments = parsed.assignments().stream()
                .filter(assignment -> assignment.subject().equals("ARTE ED IMMAGINE"))
                .toList();
        assertThat(artAssignments).hasSize(2);
        assertThat(artAssignments.get(0).description()).isNotEqualTo(artAssignments.get(1).description());
    }

    @Test
    void supportsUtf8BomAndCrLf() {
        String fixture = new String(fixture("text-AC508C2D4537-1.txt"), StandardCharsets.UTF_8);
        byte[] crlfWithBom = ("\uFEFF" + fixture.replace("\n", "\r\n")).getBytes(StandardCharsets.UTF_8);
        var parsed = parser.parse(crlfWithBom);
        assertThat(parsed.assignments()).hasSize(3);
        assertThat(parsed.assignments().get(0).description()).contains("foglio d'album");
    }

    @Test
    void toleratesAdditionalSpacesOnStructuralLines() {
        String text = """
                ASSEGNATI   IL   11   NOVEMBRE

                   MATEMATICA   (  C. ARIOTTI  )  :
                  Svolgere gli esercizi
                ---   CONSEGNA   MERCOLEDÌ   18   NOVEMBRE   ---
                """;
        var parsed = parser.parse(text);
        assertThat(parsed.assignments()).hasSize(1);
        assertThat(parsed.assignments().getFirst().subject()).isEqualTo("MATEMATICA");
        assertThat(parsed.assignments().getFirst().teacher()).isEqualTo("C. ARIOTTI");
        assertThat(parsed.assignments().getFirst().description()).isEqualTo("Svolgere gli esercizi");
    }

    @Test
    void interpretsJanuaryInTheFollowingCalendarYear() {
        String text = """
                ASSEGNATI IL 07 GENNAIO

                SCIENZE (M. ROSSI):
                Studiare il capitolo 3
                --- CONSEGNA LUNEDÌ 11 GENNAIO ---
                """;
        var parsed = parser.parse(text);
        assertThat(parsed.assignments().getFirst().assignedDate()).isEqualTo(LocalDate.of(2027, 1, 7));
        assertThat(parsed.assignments().getFirst().dueDate()).isEqualTo(LocalDate.of(2027, 1, 11));
    }

    @Test
    void rejectsEmptyFiles() {
        assertThatThrownBy(() -> parser.parse(""))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("Nessun compito");
        assertThatThrownBy(() -> parser.parse("   \n\n"))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("Nessun compito");
    }

    @Test
    void rejectsMalformedFiles() {
        assertThatThrownBy(() -> parser.parse("""
                MATEMATICA (C. ARIOTTI):
                Senza intestazione
                --- CONSEGNA GIOVEDÌ 19 NOVEMBRE ---
                """))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("ASSEGNATI IL");

        assertThatThrownBy(() -> parser.parse("""
                ASSEGNATI IL 12 NOVEMBRE
                MATEMATICA (C. ARIOTTI):
                Compito senza consegna
                """))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("CONSEGNA");

        assertThatThrownBy(() -> parser.parse("""
                ASSEGNATI IL 12 NOVEMBRE
                --- CONSEGNA GIOVEDÌ 19 NOVEMBRE ---
                """))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("materia");

        assertThatThrownBy(() -> parser.parse("""
                ASSEGNATI IL 12 NOVEMBRE
                MATEMATICA (C. ARIOTTI):
                --- CONSEGNA GIOVEDÌ 19 NOVEMBRE ---
                """))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("Descrizione mancante");

        assertThatThrownBy(() -> parser.parse("""
                ASSEGNATI IL 12 NOVEMBRE
                MATEMATICA (C. ARIOTTI):
                Compito
                --- CONSEGNA GIOVEDÌ 19 NOVEMBRE ---
                Testo inatteso
                """))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("Contenuto non riconosciuto");
    }

    @Test
    void rejectsInvalidDatesAndInconsistentWeekdays() {
        assertThatThrownBy(() -> parser.parse("""
                ASSEGNATI IL 32 NOVEMBRE
                MATEMATICA (C. ARIOTTI):
                Compito
                --- CONSEGNA GIOVEDÌ 19 NOVEMBRE ---
                """))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("Data non valida");

        assertThatThrownBy(() -> parser.parse("""
                ASSEGNATI IL 12 NOVEMBRE
                MATEMATICA (C. ARIOTTI):
                Compito
                --- CONSEGNA LUNEDÌ 19 NOVEMBRE ---
                """))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("Giorno della settimana incoerente");

        assertThatThrownBy(() -> parser.parse("""
                ASSEGNATI IL 12 NOVEMBRE
                MATEMATICA (C. ARIOTTI):
                Compito
                --- CONSEGNA GIOVEDÌ 31 FEBBRAIO ---
                """))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("Data non valida");
    }

    @Test
    void rejectsDueDateBeforeAssignedDate() {
        assertThatThrownBy(() -> parser.parse("""
                ASSEGNATI IL 20 NOVEMBRE
                MATEMATICA (C. ARIOTTI):
                Compito
                --- CONSEGNA GIOVEDÌ 19 NOVEMBRE ---
                """))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("precede");
    }

    @Test
    void rejectsInvalidUtf8() {
        byte[] invalid = new byte[] {(byte) 0xC3, (byte) 0x28};
        assertThatThrownBy(() -> parser.parse(invalid))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    void doesNotAttributeMeaningToFilenameSuffix() {
        assertThat(DidupFileName.parse("text-BF82C5CC0482-1.txt"))
                .get()
                .satisfies(name -> {
                    assertThat(name.sourceCode()).isEqualTo("BF82C5CC0482");
                    assertThat(name.filenameSuffix()).isEqualTo(1);
                });
        assertThat(DidupFileName.parse("text-bf82c5cc0482.txt"))
                .get()
                .satisfies(name -> {
                    assertThat(name.sourceCode()).isEqualTo("BF82C5CC0482");
                    assertThat(name.filenameSuffix()).isNull();
                });
    }

    @Test
    void ignoresUnrelatedFiles() {
        assertThat(DidupFileName.parse("text-AC508C2D4537-99.txt")).isPresent();
        assertThat(DidupFileName.parse("notes.txt")).isEmpty();
        assertThat(DidupFileName.parse("text-1234.txt")).isEmpty();
        assertThat(DidupFileName.parse("text-GGGGGGGGGGGG.txt")).isEmpty();
        assertThat(DidupFileName.parse(".text-AC508C2D4537.txt")).isEmpty();
        assertThat(DidupFileName.parse("text-AC508C2D4537.txt.tmp")).isEmpty();
        assertThat(DidupFileName.parse("TEXT-AC508C2D4537.TXT")).isEmpty();
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
