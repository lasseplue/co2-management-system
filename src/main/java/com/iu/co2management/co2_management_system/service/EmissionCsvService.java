package com.iu.co2management.co2_management_system.service;

import com.iu.co2management.co2_management_system.entity.EmissionEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.iu.co2management.co2_management_system.entity.Location;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;


@Service
public class EmissionCsvService {

    public record ImportResult(int importedCount, List<String> errors) {
    }

    private record ParsedRow(String category, double amount, LocalDate date) {
    }

    private static final DateTimeFormatter GERMAN_DATE =
            DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);

    private final EmissionService emissionService;

    public EmissionCsvService(EmissionService emissionService) {
        this.emissionService = emissionService;
    }

    @Transactional
    public ImportResult importCsv(String csvText, Long locationId) {
        List<ParsedRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        String[] lines = csvText.replace("\uFEFF", "").split("\\R");
        boolean headerChecked = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            int lineNumber = i + 1;
            if (line.isEmpty()) {
                continue;
            }
            if (!headerChecked) {
                headerChecked = true;
                if (line.toLowerCase().startsWith("kategorie")) {
                    continue;
                }
            }

            String[] parts = line.split(";", -1);
            if (parts.length != 3) {
                errors.add("Zeile " + lineNumber + ": 3 Spalten erwartet (Kategorie;Menge;Datum), gefunden: " + parts.length);
                continue;
            }

            String category = findCategory(parts[0].trim());
            Double amount = parseAmount(parts[1].trim());
            LocalDate date = parseDate(parts[2].trim());

            if (category == null) {
                errors.add("Zeile " + lineNumber + ": unbekannte Kategorie '" + parts[0].trim()
                        + "' (erlaubt: " + String.join(", ", EmissionService.CATEGORIES) + ")");
            }
            if (amount == null) {
                errors.add("Zeile " + lineNumber + ": Menge muss eine Zahl größer 0 sein");
            }
            if (date == null) {
                errors.add("Zeile " + lineNumber + ": Datum muss im Format yyyy-MM-dd oder dd.MM.yyyy sein");
            }
            if (category != null && amount != null && date != null) {
                rows.add(new ParsedRow(category, amount, date));
            }
        }

        if (errors.isEmpty() && rows.isEmpty()) {
            errors.add("Keine Datenzeilen gefunden.");
        }
        if (!errors.isEmpty()) {
            return new ImportResult(0, errors);
        }

        rows.forEach(row -> {
            if (locationId == null) {
                emissionService.recordEmission(row.category(), row.amount(), row.date());
            } else {
                emissionService.recordEmissionForLocation(locationId, row.category(), row.amount(), row.date());
            }
        });
        return new ImportResult(rows.size(), List.of());
    }

    public String exportCsv(LocalDate from, LocalDate to, Long locationId) {
        List<EmissionEntry> entries = locationId == null
                ? emissionService.getEmissionsForOwnLocation(from, to)
                : emissionService.getEmissionsForLocation(locationId, from, to);

        StringBuilder csv = new StringBuilder("\uFEFFKategorie;Menge (kg CO2e);Datum\n");
        entries.forEach(entry -> csv.append(formatRow(entry)).append('\n'));
        return csv.toString();
    }

    public String exportAllLocationsCsv(LocalDate from, LocalDate to) {
        StringBuilder csv = new StringBuilder("\uFEFFStandort;Kategorie;Menge (kg CO2e);Datum\n");
        for (Location location : emissionService.getAllLocations()) {
            for (EmissionEntry entry : emissionService.getEmissionsForLocation(location.getId(), from, to)) {
                csv.append(escape(location.getName())).append(';').append(formatRow(entry)).append('\n');
            }
        }
        return csv.toString();
    }

    private String formatRow(EmissionEntry entry) {
        return entry.getCategory() + ";"
                + BigDecimal.valueOf(entry.getAmountKgCo2e()).toPlainString().replace('.', ',')
                + ";" + entry.getDate();
    }

    private String escape(String value) {
        if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String findCategory(String text) {
        return EmissionService.CATEGORIES.stream()
                .filter(category -> category.equalsIgnoreCase(text))
                .findFirst()
                .orElse(null);
    }

    private Double parseAmount(String text) {
        try {
            double value = Double.parseDouble(text.replace(',', '.'));
            return Double.isFinite(value) && value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ignored) {

        }
        try {
            return LocalDate.parse(text, GERMAN_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}