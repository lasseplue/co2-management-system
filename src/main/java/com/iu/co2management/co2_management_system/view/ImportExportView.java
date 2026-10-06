package com.iu.co2management.co2_management_system.view;

import com.iu.co2management.co2_management_system.entity.Location;
import com.iu.co2management.co2_management_system.service.EmissionCsvService;
import com.iu.co2management.co2_management_system.service.EmissionExportService;
import com.iu.co2management.co2_management_system.service.EmissionService;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;

import jakarta.annotation.security.RolesAllowed;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;


@Route(value = "import-export", layout = MainLayout.class)
@PageTitle("Import/Export")
@RolesAllowed({"SUSTAINABILITY_OFFICER", "EXECUTIVE", "ADMIN"})
public class ImportExportView extends VerticalLayout {

    private final EmissionCsvService csvService;

    private final TextArea csvArea = new TextArea("CSV-Daten (Kategorie;Menge;Datum)");
    private final ComboBox<Location> importLocationBox = new ComboBox<>("Ziel-Standort");
    private final Div resultBox = new Div();
    private final ComboBox<Location> exportLocationBox = new ComboBox<>("Standort");
    private final Checkbox allLocationsBox = new Checkbox("Alle Standorte (mit zusätzlicher Spalte \"Standort\")");
    private final DatePicker exportFromField = new DatePicker("Von (leer = unbegrenzt)");
    private final DatePicker exportToField = new DatePicker("Bis (leer = unbegrenzt)");

    private volatile LocalDate exportFrom = LocalDate.now().minusYears(1);
    private volatile LocalDate exportTo = LocalDate.now();
    private volatile Long exportLocationId;
    private volatile boolean exportAll;

    public ImportExportView(EmissionCsvService csvService,
                            EmissionService emissionService,
                            EmissionExportService exportService) {
        this.csvService = csvService;

        boolean canReadAll = emissionService.canReadAllLocations();
        boolean canWriteAll = emissionService.canWriteAllLocations();

        csvArea.setWidthFull();
        csvArea.setMinHeight("200px");
        csvArea.setPlaceholder("Strom;120,5;2026-09-01\nHeizung;80;01.09.2026");

        Button importButton = new Button("Importieren", event -> importData());

        exportFromField.setValue(exportFrom);
        exportToField.setValue(exportTo);
        exportFromField.addValueChangeListener(event -> exportFrom = event.getValue());
        exportToField.addValueChangeListener(event -> exportTo = event.getValue());

        if (canReadAll) {
            List<Location> locations = emissionService.getAllLocations();

            exportLocationBox.setItems(locations);
            exportLocationBox.setItemLabelGenerator(Location::getName);
            exportLocationBox.setPlaceholder("Eigener Standort");
            exportLocationBox.setClearButtonVisible(true);
            exportLocationBox.addValueChangeListener(event ->
                    exportLocationId = event.getValue() == null ? null : event.getValue().getId());

            allLocationsBox.addValueChangeListener(event -> {
                exportAll = event.getValue();
                exportLocationBox.setEnabled(!event.getValue());
            });

            if (canWriteAll) {
                importLocationBox.setItems(locations);
                importLocationBox.setItemLabelGenerator(Location::getName);
                importLocationBox.setPlaceholder("Eigener Standort");
                importLocationBox.setClearButtonVisible(true);
            }
        }

        Anchor csvLink = new Anchor(DownloadHandler.fromInputStream(event -> {
            LocalDate from = exportFrom != null ? exportFrom : LocalDate.of(1900, 1, 1);
            LocalDate to = exportTo != null ? exportTo : LocalDate.of(9999, 12, 31);
            String csv = exportAll
                    ? csvService.exportAllLocationsCsv(from, to)
                    : csvService.exportCsv(from, to, exportLocationId);
            byte[] data = csv.getBytes(StandardCharsets.UTF_8);
            return new DownloadResponse(new ByteArrayInputStream(data), "emissionen.csv", "text/csv", data.length);
        }, "emissionen.csv"), "CSV herunterladen");

        Anchor pdfLink = new Anchor(DownloadHandler.fromInputStream(event -> {
            LocalDate from = exportFrom != null ? exportFrom : LocalDate.of(1900, 1, 1);
            LocalDate to = exportTo != null ? exportTo : LocalDate.of(9999, 12, 31);
            byte[] data = exportService.exportPdf(from, to, exportLocationId, exportAll);
            return new DownloadResponse(new ByteArrayInputStream(data), "emissionsbericht.pdf",
                    "application/pdf", data.length);
        }, "emissionsbericht.pdf"), "PDF herunterladen");

        add(new H2("Import/Export"));

        if (emissionService.canRecordEmissions()) {
            add(new H3("Import"));
            if (canWriteAll) {
                add(importLocationBox);
            }
            add(csvArea, importButton, resultBox);
        }

        add(new H3("Export"));
        if (canReadAll) {
            add(exportLocationBox, allLocationsBox);
        }
        add(new HorizontalLayout(exportFromField, exportToField), new HorizontalLayout(csvLink, pdfLink));
    }

    private void importData() {
        resultBox.removeAll();
        String text = csvArea.getValue();
        if (text == null || text.isBlank()) {
            resultBox.add(errorLine("Bitte CSV-Daten einfügen."));
            return;
        }

        Location target = importLocationBox.getValue();
        Long targetId = target == null ? null : target.getId();

        EmissionCsvService.ImportResult result = csvService.importCsv(text, targetId);
        if (result.errors().isEmpty()) {
            String where = target == null ? "den eigenen Standort" : "Standort " + target.getName();
            resultBox.add(new Div(result.importedCount() + " Einträge für " + where + " importiert."));
            Notification.show(result.importedCount() + " Einträge importiert", 3000, Notification.Position.BOTTOM_START);
            csvArea.clear();
        } else {
            resultBox.add(errorLine("Import abgebrochen, es wurde nichts gespeichert:"));
            result.errors().forEach(error -> resultBox.add(errorLine(error)));
            Notification.show("Import abgebrochen, bitte Fehler prüfen", 4000, Notification.Position.MIDDLE);
        }
    }

    private Div errorLine(String text) {
        Div line = new Div(text);
        line.getStyle().set("color", "#c62828");
        return line;
    }
}