package com.iu.co2management.co2_management_system.view;

import com.iu.co2management.co2_management_system.service.EmissionCsvService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
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

@Route(value = "csv", layout = MainLayout.class)
@PageTitle("CSV Import/Export")
@RolesAllowed({"SUSTAINABILITY_OFFICER", "ADMIN"})
public class CsvView extends VerticalLayout {

    private final EmissionCsvService csvService;

    private final TextArea csvArea = new TextArea("CSV-Daten (Kategorie;Menge;Datum)");
    private final Div resultBox = new Div();
    private final DatePicker exportFromField = new DatePicker("Von (leer = unbegrenzt)");
    private final DatePicker exportToField = new DatePicker("Bis (leer = unbegrenzt)");

    private LocalDate exportFrom = LocalDate.now().minusYears(1);
    private LocalDate exportTo = LocalDate.now();

    public CsvView(EmissionCsvService csvService) {
        this.csvService = csvService;

        csvArea.setWidthFull();
        csvArea.setMinHeight("200px");
        csvArea.setPlaceholder("Strom;120,5;2026-09-01\nHeizung;80;01.09.2026");

        Button importButton = new Button("Importieren", event -> importData());

        exportFromField.setValue(exportFrom);
        exportToField.setValue(exportTo);
        exportFromField.addValueChangeListener(event -> exportFrom = event.getValue());
        exportToField.addValueChangeListener(event -> exportTo = event.getValue());

        Anchor exportLink = new Anchor(DownloadHandler.fromInputStream(event -> {
            LocalDate from = exportFrom != null ? exportFrom : LocalDate.of(1900, 1, 1);
            LocalDate to = exportTo != null ? exportTo : LocalDate.of(9999, 12, 31);
            byte[] data = csvService.exportCsv(from, to).getBytes(StandardCharsets.UTF_8);
            return new DownloadResponse(new ByteArrayInputStream(data), "emissionen.csv", "text/csv", data.length);
        }, "emissionen.csv"), "CSV herunterladen");

        add(new H2("CSV Import/Export"),
                new H3("Import"), csvArea, importButton, resultBox,
                new H3("Export"), new HorizontalLayout(exportFromField, exportToField), exportLink);
    }

    private void importData() {
        resultBox.removeAll();
        String text = csvArea.getValue();
        if (text == null || text.isBlank()) {
            resultBox.add(errorLine("Bitte CSV-Daten einfügen."));
            return;
        }

        EmissionCsvService.ImportResult result = csvService.importCsv(text);
        if (result.errors().isEmpty()) {
            resultBox.add(new Div(result.importedCount() + " Einträge importiert."));
            csvArea.clear();
        } else {
            resultBox.add(errorLine("Import abgebrochen, es wurde nichts gespeichert:"));
            result.errors().forEach(error -> resultBox.add(errorLine(error)));
        }
    }

    private Div errorLine(String text) {
        Div line = new Div(text);
        line.getStyle().set("color", "#c62828");
        return line;
    }
}