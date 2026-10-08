package com.iu.co2management.co2_management_system.service;

import com.iu.co2management.co2_management_system.entity.EmissionEntry;
import com.iu.co2management.co2_management_system.entity.Location;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openpdf.text.pdf.PdfReader;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class EmissionExportServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    @Mock
    private EmissionService emissionService;

    private EmissionExportService exportService;

    @BeforeEach
    void setUp() {
        exportService = new EmissionExportService(emissionService);
    }

    // ---------------------------------------------------------------- die drei Modi

    @Test
    void ownLocationReportUsesOnlyOwnLocationData() throws IOException {
        when(emissionService.getEmissionsForOwnLocation(FROM, TO)).thenReturn(List.of(
                entry("Strom", 120.5, LocalDate.of(2026, 9, 1)),
                entry("Mobilität", 80.0, LocalDate.of(2026, 9, 2))));
        when(emissionService.getCurrentUserLocationName()).thenReturn("Hamburg");

        byte[] pdf = exportService.exportPdf(FROM, TO, null, false);

        assertThat(pagesOf(pdf)).isEqualTo(1);
        verify(emissionService, never()).getAllLocations();
        verify(emissionService, never()).getEmissionsForLocation(any(), any(), any());
    }

    @Test
    void selectedLocationReportUsesThatLocationAndNotTheOwnOne() throws IOException {
        Location sued = mock(Location.class);
        when(sued.getId()).thenReturn(7L);
        when(sued.getName()).thenReturn("Süd");
        when(emissionService.getAllLocations()).thenReturn(List.of(sued));
        when(emissionService.getEmissionsForLocation(7L, FROM, TO)).thenReturn(List.of(
                entry("Strom", 10.0, LocalDate.of(2026, 9, 1))));

        byte[] pdf = exportService.exportPdf(FROM, TO, 7L, false);

        assertThat(pagesOf(pdf)).isEqualTo(1);
        verify(emissionService, never()).getEmissionsForOwnLocation(any(), any());
    }

    @Test
    void unknownSelectedLocationStillProducesAReport() throws IOException {
        // Die id passt zu keinem Standort in der Liste; der Bericht nennt dann "Unbekannt" als Standort
        Location other = mock(Location.class);
        when(other.getId()).thenReturn(1L);
        when(emissionService.getAllLocations()).thenReturn(List.of(other));
        when(emissionService.getEmissionsForLocation(99L, FROM, TO)).thenReturn(List.of());

        byte[] pdf = exportService.exportPdf(FROM, TO, 99L, false);

        assertThat(pagesOf(pdf)).isEqualTo(1);
    }

    @Test
    void allLocationsReportCollectsEntriesOfEveryLocation() throws IOException {
        Location sued = mock(Location.class);
        Location nord = mock(Location.class);
        when(sued.getId()).thenReturn(1L);
        when(sued.getName()).thenReturn("Süd");
        when(nord.getId()).thenReturn(2L);
        when(nord.getName()).thenReturn("Nord");
        when(emissionService.getAllLocations()).thenReturn(List.of(sued, nord));
        when(emissionService.getEmissionsForLocation(1L, FROM, TO)).thenReturn(List.of(
                entry("Strom", 10.0, LocalDate.of(2026, 9, 1))));
        when(emissionService.getEmissionsForLocation(2L, FROM, TO)).thenReturn(List.of(
                entry("Heizung", 20.0, LocalDate.of(2026, 9, 2))));

        byte[] pdf = exportService.exportPdf(FROM, TO, null, true);

        assertThat(pagesOf(pdf)).isEqualTo(1);
        verify(emissionService).getEmissionsForLocation(1L, FROM, TO);
        verify(emissionService).getEmissionsForLocation(2L, FROM, TO);
        verify(emissionService, never()).getEmissionsForOwnLocation(any(), any());
    }

    // ---------------------------------------------------------------- Rechte, Fehler

    @Test
    void accessDeniedForAllLocationsIsNotSwallowed() {
        when(emissionService.getAllLocations()).thenThrow(new AccessDeniedException("keine Berechtigung"));

        assertThatThrownBy(() -> exportService.exportPdf(FROM, TO, null, true))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void accessDeniedForSelectedLocationIsNotSwallowed() {
        when(emissionService.getEmissionsForLocation(7L, FROM, TO))
                .thenThrow(new AccessDeniedException("keine Berechtigung"));

        assertThatThrownBy(() -> exportService.exportPdf(FROM, TO, 7L, false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void brokenDataIsWrappedInIllegalStateException() {
        // Ein Eintrag ohne Datum lässt die PDF-Erzeugung scheitern; der Service meldet das einheitlich
        when(emissionService.getEmissionsForOwnLocation(FROM, TO)).thenReturn(List.of(
                entry("Strom", 10.0, null)));
        when(emissionService.getCurrentUserLocationName()).thenReturn("Hamburg");

        assertThatThrownBy(() -> exportService.exportPdf(FROM, TO, null, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PDF konnte nicht erstellt werden");
    }

    // ---------------------------------------------------------------- Grenzfälle der Erzeugung

    @Test
    void emptyReportIsStillAValidPdf() throws IOException {
        when(emissionService.getEmissionsForOwnLocation(FROM, TO)).thenReturn(List.of());
        when(emissionService.getCurrentUserLocationName()).thenReturn("Hamburg");

        byte[] pdf = exportService.exportPdf(FROM, TO, null, false);

        assertThat(pagesOf(pdf)).isEqualTo(1);
    }

    @Test
    void manyEntriesSpanSeveralPages() throws IOException {
        List<EmissionEntry> entries = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            entries.add(entry("Strom", 1.0 + i, FROM.plusDays(i % 30)));
        }
        when(emissionService.getEmissionsForOwnLocation(FROM, TO)).thenReturn(entries);
        when(emissionService.getCurrentUserLocationName()).thenReturn("Hamburg");

        byte[] pdf = exportService.exportPdf(FROM, TO, null, false);

        assertThat(pagesOf(pdf)).isGreaterThan(1);
    }

    @Test
    void unboundedPeriodUsedByTheViewWorks() throws IOException {
        // Die View ersetzt leere Datumsfelder durch 1900-01-01 bzw. 9999-12-31
        LocalDate from = LocalDate.of(1900, 1, 1);
        LocalDate to = LocalDate.of(9999, 12, 31);
        when(emissionService.getEmissionsForOwnLocation(from, to)).thenReturn(List.of(
                entry("Strom", 10.0, LocalDate.of(2026, 9, 1))));
        when(emissionService.getCurrentUserLocationName()).thenReturn("Hamburg");

        byte[] pdf = exportService.exportPdf(from, to, null, false);

        assertThat(pagesOf(pdf)).isEqualTo(1);
    }

    @Test
    void charactersOutsideTheStandardFontDoNotBreakTheReport() throws IOException {
        // Helvetica kennt kein tiefgestelltes ₂ und keine asiatischen Zeichen. Erwartet wird, dass der Bericht
        // trotzdem entsteht. Wird dieser Test rot, ist das ein echter Befund für die Reflexion.
        Location werk = mock(Location.class);
        when(werk.getId()).thenReturn(1L);
        when(werk.getName()).thenReturn("Werk CO₂ 日本");
        when(emissionService.getAllLocations()).thenReturn(List.of(werk));
        when(emissionService.getEmissionsForLocation(1L, FROM, TO)).thenReturn(List.of(
                entry("Mobilität", 10.0, LocalDate.of(2026, 9, 1))));

        byte[] pdf = exportService.exportPdf(FROM, TO, null, true);

        assertThat(pagesOf(pdf)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- Hilfsmethoden

    /** Prüft, dass die Bytes ein lesbares PDF sind, und liefert die Seitenzahl. */
    private static int pagesOf(byte[] pdf) throws IOException {
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        PdfReader reader = new PdfReader(pdf);
        try {
            return reader.getNumberOfPages();
        } finally {
            reader.close();
        }
    }

    private static EmissionEntry entry(String category, double amount, LocalDate date) {
        return new EmissionEntry(category, amount, date, null, null);
    }
}