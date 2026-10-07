package com.iu.co2management.co2_management_system.service;

import com.iu.co2management.co2_management_system.entity.EmissionEntry;
import com.iu.co2management.co2_management_system.entity.Location;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmissionCsvServiceTest {

    private static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);

    @Mock
    private EmissionService emissionService;

    private EmissionCsvService csvService;

    @BeforeEach
    void setUp() {
        csvService = new EmissionCsvService(emissionService);
    }

    // ---------------------------------------------------------------- Import: gültige Eingaben

    @Test
    void importsValidRowsForOwnLocation() {
        String csv = "Strom;120,5;2026-09-01\nHeizung;80;01.09.2026";

        EmissionCsvService.ImportResult result = csvService.importCsv(csv, null);

        assertThat(result.errors()).isEmpty();
        assertThat(result.importedCount()).isEqualTo(2);
        verify(emissionService).recordEmission("Strom", 120.5, SEP_1);
        verify(emissionService).recordEmission("Heizung", 80.0, SEP_1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Kategorie;Menge;Datum",
            "KATEGORIE;MENGE;DATUM",
            "kategorie;menge (kg CO2e);datum"
    })
    void headerLineIsSkipped(String header) {
        EmissionCsvService.ImportResult result =
                csvService.importCsv(header + "\nStrom;10;2026-09-01", null);

        assertThat(result.errors()).isEmpty();
        assertThat(result.importedCount()).isEqualTo(1);
        verify(emissionService).recordEmission("Strom", 10.0, SEP_1);
    }

    @Test
    void headerIsOnlyRecognizedAsFirstLine() {
        // Eine "Kategorie"-Zeile mitten in den Daten ist keine Kopfzeile, sondern eine ungültige Datenzeile
        EmissionCsvService.ImportResult result =
                csvService.importCsv("Strom;10;2026-09-01\nKategorie;Menge;Datum", null);

        assertThat(result.importedCount()).isZero();
        assertThat(result.errors().get(0)).contains("Zeile 2").contains("Kategorie");
        verifyNoInteractions(emissionService);
    }

    @Test
    void byteOrderMarkIsIgnored() {
        // Excel schreibt beim Speichern als "CSV UTF-8" ein BOM an den Anfang
        EmissionCsvService.ImportResult withHeader =
                csvService.importCsv("\uFEFFKategorie;Menge;Datum\nStrom;10;2026-09-01", null);
        EmissionCsvService.ImportResult withoutHeader =
                csvService.importCsv("\uFEFFHeizung;5;2026-09-01", null);

        assertThat(withHeader.errors()).isEmpty();
        assertThat(withoutHeader.errors()).isEmpty();
        verify(emissionService).recordEmission("Strom", 10.0, SEP_1);
        verify(emissionService).recordEmission("Heizung", 5.0, SEP_1);
    }

    @Test
    void windowsLineEndingsAreAccepted() {
        EmissionCsvService.ImportResult result =
                csvService.importCsv("Strom;10;2026-09-01\r\nHeizung;5;2026-09-01\r\n", null);

        assertThat(result.errors()).isEmpty();
        assertThat(result.importedCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({
            "strom,Strom",
            "HEIZUNG,Heizung",
            "mobilität,Mobilität",
            "Sonstiges,Sonstiges"
    })
    void categoryIsMatchedCaseInsensitivelyAndStoredInCanonicalSpelling(String input, String expected) {
        csvService.importCsv(input + ";10;2026-09-01", null);

        verify(emissionService).recordEmission(expected, 10.0, SEP_1);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "120,5|120.5",
            "120.5|120.5",
            "80|80.0",
            "0,001|0.001"
    })
    void amountAcceptsCommaAndDot(String text, double expected) {
        EmissionCsvService.ImportResult result =
                csvService.importCsv("Strom;" + text + ";2026-09-01", null);

        assertThat(result.errors()).isEmpty();
        verify(emissionService).recordEmission("Strom", expected, SEP_1);
    }

    @Test
    void scientificNotationIsCurrentlyAccepted() {
        // Dokumentiert das aktuelle Verhalten: Double.parseDouble versteht "1e3".
        // Ob das gewollt ist, ist eine fachliche Entscheidung (siehe Reflexion).
        EmissionCsvService.ImportResult result = csvService.importCsv("Strom;1e3;2026-09-01", null);

        assertThat(result.errors()).isEmpty();
        verify(emissionService).recordEmission("Strom", 1000.0, SEP_1);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2026-09-01|2026-09-01",
            "01.09.2026|2026-09-01",
            "29.02.2024|2024-02-29",
            "2024-02-29|2024-02-29"
    })
    void validDatesInBothFormats(String text, String expectedIso) {
        EmissionCsvService.ImportResult result =
                csvService.importCsv("Strom;10;" + text, null);

        assertThat(result.errors()).isEmpty();
        verify(emissionService).recordEmission("Strom", 10.0, LocalDate.parse(expectedIso));
    }

    // ---------------------------------------------------------------- Import: ungültige Eingaben

    @Test
    void impossibleDateIsRejectedInsteadOfCorrected() {
        EmissionCsvService.ImportResult result = csvService.importCsv("Strom;10;31.02.2026", null);

        assertThat(result.importedCount()).isZero();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("Zeile 1").contains("Datum");
        verifyNoInteractions(emissionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "31.02.2026",   // gibt es nie
            "29.02.2025",   // kein Schaltjahr
            "2025-02-29",   // ISO-Format, kein Schaltjahr
            "2026-02-31",
            "32.01.2026",
            "2026-13-01",
            "1.9.2026",     // Tag/Monat müssen zweistellig sein (aktuelles Verhalten)
            "01/09/2026",   // unbekanntes Format
            "morgen",
            ""
    })
    void invalidDatesAreRejected(String date) {
        EmissionCsvService.ImportResult result = csvService.importCsv("Strom;10;" + date, null);

        assertThat(result.importedCount()).isZero();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("Datum");
        verifyNoInteractions(emissionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0",
            "-5",
            "abc",
            "",
            "NaN",
            "Infinity",
            "-Infinity",
            "1.234,50"      // deutsches Tausendertrennzeichen wird aktuell abgelehnt
    })
    void invalidAmountsAreRejected(String amount) {
        EmissionCsvService.ImportResult result =
                csvService.importCsv("Strom;" + amount + ";2026-09-01", null);

        assertThat(result.importedCount()).isZero();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("Menge");
        verifyNoInteractions(emissionService);
    }

    @Test
    void unknownCategoryIsRejectedAndListsAllowedOnes() {
        EmissionCsvService.ImportResult result = csvService.importCsv("Benzin;5;2026-09-01", null);

        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0))
                .contains("Zeile 1")
                .contains("Benzin")
                .contains("Strom", "Heizung", "Mobilität", "Sonstiges");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Strom",
            "Strom;10",
            "Strom;10;2026-09-01;x"
    })
    void wrongNumberOfColumnsIsRejected(String line) {
        EmissionCsvService.ImportResult result = csvService.importCsv(line, null);

        assertThat(result.importedCount()).isZero();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("Zeile 1").contains("3 Spalten");
        verifyNoInteractions(emissionService);
    }

    @Test
    void allErrorsOfOneLineAreReported() {
        EmissionCsvService.ImportResult result = csvService.importCsv("Benzin;abc;31.02.2026", null);

        assertThat(result.errors()).hasSize(3);
        assertThat(result.errors()).allMatch(error -> error.startsWith("Zeile 1:"));
    }

    @Test
    void errorsFromSeveralLinesAreCollected() {
        String csv = "Benzin;5;2026-09-01\nStrom;10;2026-09-01\nStrom;-1;2026-09-01";

        EmissionCsvService.ImportResult result = csvService.importCsv(csv, null);

        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors().get(0)).contains("Zeile 1");
        assertThat(result.errors().get(1)).contains("Zeile 3");
    }

    @Test
    void lineNumbersCountHeaderAndEmptyLines() {
        String csv = "Kategorie;Menge;Datum\r\n\r\nStrom;10;2026-09-01\r\nBenzin;5;2026-09-02";

        EmissionCsvService.ImportResult result = csvService.importCsv(csv, null);

        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("Zeile 4");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\n", "Kategorie;Menge;Datum"})
    void inputWithoutDataRowsIsRejected(String csv) {
        EmissionCsvService.ImportResult result = csvService.importCsv(csv, null);

        assertThat(result.importedCount()).isZero();
        assertThat(result.errors()).containsExactly("Keine Datenzeilen gefunden.");
        verifyNoInteractions(emissionService);
    }

    // ---------------------------------------------------------------- Import: Alles-oder-nichts, Ziel, Rechte

    @Test
    void oneInvalidRowPreventsAnySaving() {
        String csv = "Strom;10;2026-09-01\nBenzin;5;2026-09-02";

        EmissionCsvService.ImportResult result = csvService.importCsv(csv, null);

        assertThat(result.importedCount()).isZero();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("Zeile 2").contains("Benzin");
        verify(emissionService, never()).recordEmission(any(), anyDouble(), any());
    }

    @Test
    void importWithTargetLocationUsesLocationSpecificMethod() {
        EmissionCsvService.ImportResult result = csvService.importCsv("Strom;10;2026-09-01", 5L);

        assertThat(result.importedCount()).isEqualTo(1);
        verify(emissionService).recordEmissionForLocation(5L, "Strom", 10.0, SEP_1);
        verify(emissionService, never()).recordEmission(any(), anyDouble(), any());
    }

    @Test
    void accessDeniedFromEmissionServiceIsNotSwallowed() {
        // Die Rechteprüfung liegt im EmissionService; der CSV-Service darf die Exception nicht verschlucken.
        // (Das Zurückrollen der Transaktion selbst lässt sich nur mit einem Integrationstest und echter DB prüfen.)
        doThrow(new AccessDeniedException("keine Berechtigung"))
                .when(emissionService).recordEmission(any(), anyDouble(), any());

        assertThatThrownBy(() -> csvService.importCsv("Strom;10;2026-09-01", null))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------------------------------------------------------------- Export

    @Test
    void exportHasBomHeaderAndGermanDecimalComma() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        when(emissionService.getEmissionsForOwnLocation(from, to)).thenReturn(List.of(
                entry("Strom", 120.5, LocalDate.of(2026, 3, 15))));

        String csv = csvService.exportCsv(from, to, null);

        assertThat(csv).startsWith("\uFEFFKategorie;Menge (kg CO2e);Datum\n");
        assertThat(csv).contains("Strom;120,5;2026-03-15\n");
    }

    @Test
    void exportWithoutEntriesContainsOnlyHeader() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        when(emissionService.getEmissionsForOwnLocation(from, to)).thenReturn(List.of());

        assertThat(csvService.exportCsv(from, to, null))
                .isEqualTo("\uFEFFKategorie;Menge (kg CO2e);Datum\n");
    }

    @Test
    void wholeAndLargeNumbersAreWrittenPlainly() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        when(emissionService.getEmissionsForOwnLocation(from, to)).thenReturn(List.of(
                entry("Heizung", 80.0, LocalDate.of(2026, 3, 15)),
                entry("Strom", 1.0E7, LocalDate.of(2026, 3, 16))));

        String csv = csvService.exportCsv(from, to, null);

        // Ganze Zahlen behalten aktuell ihre Nachkommastelle ("80,0"), große Zahlen erscheinen nie als "1.0E7"
        assertThat(csv).contains("Heizung;80,0;2026-03-15\n");
        assertThat(csv).contains("Strom;10000000;2026-03-16\n");
    }

    @Test
    void exportForSelectedLocationDoesNotUseOwnLocation() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        when(emissionService.getEmissionsForLocation(7L, from, to)).thenReturn(List.of(
                entry("Strom", 10.0, LocalDate.of(2026, 3, 1))));

        String csv = csvService.exportCsv(from, to, 7L);

        assertThat(csv).contains("Strom;10,0;2026-03-01\n");
        verify(emissionService, never()).getEmissionsForOwnLocation(any(), any());
    }

    @Test
    void exportOfAllLocationsAddsLocationColumnAndEscapesNames() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);

        // Location hat keinen Setter für die id, deshalb Mocks
        Location sued = mock(Location.class);
        Location nordOst = mock(Location.class);
        Location werk = mock(Location.class);
        when(sued.getId()).thenReturn(1L);
        when(sued.getName()).thenReturn("Süd");
        when(nordOst.getId()).thenReturn(2L);
        when(nordOst.getName()).thenReturn("Nord;Ost");
        when(werk.getId()).thenReturn(3L);
        when(werk.getName()).thenReturn("Das \"Werk\"");

        when(emissionService.getAllLocations()).thenReturn(List.of(sued, nordOst, werk));
        when(emissionService.getEmissionsForLocation(1L, from, to)).thenReturn(List.of(
                entry("Strom", 120.5, LocalDate.of(2026, 3, 15))));
        when(emissionService.getEmissionsForLocation(2L, from, to)).thenReturn(List.of(
                entry("Heizung", 80.0, LocalDate.of(2026, 3, 16))));
        when(emissionService.getEmissionsForLocation(3L, from, to)).thenReturn(List.of(
                entry("Sonstiges", 5.0, LocalDate.of(2026, 3, 17))));

        String csv = csvService.exportAllLocationsCsv(from, to);

        assertThat(csv).isEqualTo(
                "\uFEFFStandort;Kategorie;Menge (kg CO2e);Datum\n"
                        + "Süd;Strom;120,5;2026-03-15\n"
                        + "\"Nord;Ost\";Heizung;80,0;2026-03-16\n"
                        + "\"Das \"\"Werk\"\"\";Sonstiges;5,0;2026-03-17\n");
    }

    // ---------------------------------------------------------------- Hilfsmethode

    private static EmissionEntry entry(String category, double amount, LocalDate date) {
        // Standort und Benutzer werden vom CSV-Export nicht gelesen
        return new EmissionEntry(category, amount, date, null, null);
    }
}