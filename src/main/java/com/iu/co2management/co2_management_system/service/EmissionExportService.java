package com.iu.co2management.co2_management_system.service;

import com.iu.co2management.co2_management_system.entity.EmissionEntry;
import com.iu.co2management.co2_management_system.entity.Location;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class EmissionExportService {

    private record Row(String locationName, EmissionEntry entry) {
    }

    private static final DateTimeFormatter GERMAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final EmissionService emissionService;

    public EmissionExportService(EmissionService emissionService) {
        this.emissionService = emissionService;
    }

    /**
     * locationId == null: eigener Standort; locationId gesetzt: gewählter Standort (nur ADMIN/EXECUTIVE);
     * allLocations == true: alle Standorte mit zusätzlicher Spalte (nur ADMIN/EXECUTIVE).
     * Die Rechteprüfung passiert in den aufgerufenen EmissionService-Methoden.
     */
    public byte[] exportPdf(LocalDate from, LocalDate to, Long locationId, boolean allLocations) {
        List<Row> rows = new ArrayList<>();
        String scope;

        if (allLocations) {
            for (Location location : emissionService.getAllLocations()) {
                for (EmissionEntry entry : emissionService.getEmissionsForLocation(location.getId(), from, to)) {
                    rows.add(new Row(location.getName(), entry));
                }
            }
            scope = "Alle Standorte";
        } else if (locationId == null) {
            emissionService.getEmissionsForOwnLocation(from, to).forEach(entry -> rows.add(new Row(null, entry)));
            scope = emissionService.getCurrentUserLocationName();
        } else {
            emissionService.getEmissionsForLocation(locationId, from, to).forEach(entry -> rows.add(new Row(null, entry)));
            scope = emissionService.getAllLocations().stream()
                    .filter(location -> location.getId().equals(locationId))
                    .map(Location::getName)
                    .findFirst()
                    .orElse("Unbekannt");
        }

        double total = rows.stream().mapToDouble(row -> row.entry().getAmountKgCo2e()).sum();

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("CO2-Emissionsbericht", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18)));
            document.add(new Paragraph("Standort: " + scope));
            document.add(new Paragraph("Zeitraum: " + formatBound(from) + " bis " + formatBound(to)));
            document.add(new Paragraph(" "));

            int columns = allLocations ? 4 : 3;
            PdfPTable table = new PdfPTable(columns);
            table.setWidthPercentage(100);

            if (allLocations) {
                addHeaderCell(table, "Standort", Element.ALIGN_LEFT);
            }
            addHeaderCell(table, "Kategorie", Element.ALIGN_LEFT);
            addHeaderCell(table, "Menge (kg CO2e)", Element.ALIGN_RIGHT);
            addHeaderCell(table, "Datum", Element.ALIGN_LEFT);

            for (Row row : rows) {
                if (allLocations) {
                    table.addCell(row.locationName());
                }
                table.addCell(row.entry().getCategory());
                addRightAligned(table, formatAmount(row.entry().getAmountKgCo2e()), false);
                table.addCell(row.entry().getDate().format(GERMAN_DATE));
            }

            // Summenzeile: Label in der ersten Spalte, Wert unter "Menge", Rest leer
            PdfPCell totalLabel = new PdfPCell(new Phrase("Gesamt", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
            table.addCell(totalLabel);
            if (allLocations) {
                table.addCell("");
            }
            addRightAligned(table, formatAmount(total), true);
            table.addCell("");

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF konnte nicht erstellt werden", e);
        }
    }

    private void addHeaderCell(PdfPTable table, String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
        cell.setBackgroundColor(Color.LIGHT_GRAY);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private void addRightAligned(PdfPTable table, String text, boolean bold) {
        Phrase phrase = bold
                ? new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11))
                : new Phrase(text);
        PdfPCell cell = new PdfPCell(phrase);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell);
    }

    private String formatAmount(double amount) {
        return String.format(Locale.GERMAN, "%,.2f", amount);
    }

    /** Die View ersetzt leere Datumsfelder durch 1900 bzw. 9999; im Bericht soll dann "unbegrenzt" stehen. */
    private String formatBound(LocalDate date) {
        return date.getYear() <= 1900 || date.getYear() >= 9999 ? "unbegrenzt" : date.format(GERMAN_DATE);
    }
}