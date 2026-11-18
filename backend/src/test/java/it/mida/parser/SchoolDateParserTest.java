package it.mida.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SchoolDateParserTest {

    private final SchoolDateParser parser = new SchoolDateParser();

    @Test
    void mapsSeptemberToDecemberToTheStartYear() {
        assertThat(parser.parse("11 NOVEMBRE", 2026).value()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(parser.parse("1 SETTEMBRE", 2026).value()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(parser.parse("31 DICEMBRE", 2026).value()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void mapsJanuaryToAugustToTheFollowingYear() {
        assertThat(parser.parse("11 GENNAIO", 2026).value()).isEqualTo(LocalDate.of(2027, 1, 11));
        assertThat(parser.parse("31 AGOSTO", 2026).value()).isEqualTo(LocalDate.of(2027, 8, 31));
    }

    @Test
    void validatesDeclaredWeekdayIgnoringAccentsAndCase() {
        assertThat(parser.parse("mercoledì 18 novembre", 2026).value()).isEqualTo(LocalDate.of(2026, 11, 18));
        assertThat(parser.parse("MERCOLEDI 18 NOVEMBRE", 2026).value()).isEqualTo(LocalDate.of(2026, 11, 18));
        assertThat(parser.parse("LUNEDÌ 11 GENNAIO", 2026).value()).isEqualTo(LocalDate.of(2027, 1, 11));
        assertThatThrownBy(() -> parser.parse("LUNEDÌ 18 NOVEMBRE", 2026))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("incoerente");
    }

    @Test
    void supportsAnExplicitYearInsideTheSchoolYear() {
        assertThat(parser.parse("18 NOVEMBRE 2026", 2026).value()).isEqualTo(LocalDate.of(2026, 11, 18));
        assertThatThrownBy(() -> parser.parse("18 NOVEMBRE 2024", 2026))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("non coerente");
    }

    @Test
    void rejectsUnknownDates() {
        assertThatThrownBy(() -> parser.parse("18 SMARCH", 2026))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("Mese non riconosciuto");
        assertThatThrownBy(() -> parser.parse("0 NOVEMBRE", 2026))
                .isInstanceOf(DidupParseException.class)
                .hasMessageContaining("Data non valida");
    }
}
