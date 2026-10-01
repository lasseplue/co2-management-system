package com.iu.co2management.co2_management_system.view;

import com.iu.co2management.co2_management_system.entity.EmissionEntry;
import com.iu.co2management.co2_management_system.service.EmissionService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import com.vaadin.flow.component.html.Span;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.TreeMap;

@Route(value = "emissions", layout = MainLayout.class)
@PageTitle("Emissionsübersicht")
@RolesAllowed({"USER", "SUSTAINABILITY_OFFICER", "ADMIN"})
@JavaScript("https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js")
public class EmissionOverviewView extends VerticalLayout {

    private final EmissionService emissionService;

    private final Span locationLabel = new Span();
    private final DatePicker fromField = new DatePicker("Von");
    private final DatePicker toField = new DatePicker("Bis");
    private final Grid<EmissionEntry> grid = new Grid<>(EmissionEntry.class, false);
    private final H3 totalLabel = new H3();
    private final Div chartContainer = new Div();

    public EmissionOverviewView(EmissionService emissionService) {
        this.emissionService = emissionService;
        locationLabel.setText("Standort: " + emissionService.getCurrentUserLocationName());

        fromField.setValue(LocalDate.now().minusDays(30));
        toField.setValue(LocalDate.now());

        Button filterButton = new Button("Filtern", event -> refresh());

        grid.addColumn(EmissionEntry::getCategory).setHeader("Kategorie");
        grid.addColumn(EmissionEntry::getAmountKgCo2e).setHeader("Menge (kg CO₂e)");
        grid.addColumn(EmissionEntry::getDate).setHeader("Datum");

        HorizontalLayout filterLayout = new HorizontalLayout(fromField, toField, filterButton);

        chartContainer.setWidthFull();
        chartContainer.setHeight("300px");
        Element canvasElement = new Element("canvas");
        canvasElement.setAttribute("id", "emissionChartCanvas");
        chartContainer.getElement().appendChild(canvasElement);

        add(new H2("Emissionsübersicht"), locationLabel, filterLayout, grid, totalLabel, chartContainer);

        refresh();
    }

    private void refresh() {
        LocalDate from = fromField.getValue();
        LocalDate to = toField.getValue();
        if (from == null || to == null) {
            return;
        }

        List<EmissionEntry> entries = emissionService.getEmissionsForOwnLocation(from, to);
        grid.setItems(entries);

        double total = emissionService.getTotalKgCo2eForOwnLocation(from, to);
        totalLabel.setText("Gesamt im Zeitraum: %.2f kg CO₂e".formatted(total));

        updateChart(entries, from, to);
    }


    private void updateChart(List<EmissionEntry> entries, LocalDate from, LocalDate to) {
        Map<LocalDate, Double> totalsByDate = new TreeMap<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            totalsByDate.put(cursor, 0.0);
            cursor = cursor.plusDays(1);
        }
        for (EmissionEntry entry : entries) {
            totalsByDate.merge(entry.getDate(), entry.getAmountKgCo2e(), Double::sum);
        }

        String labels = totalsByDate.keySet().stream()
                .map(date -> "\"" + date + "\"")
                .collect(Collectors.joining(","));
        String data = totalsByDate.values().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String script = """
                const canvas = document.getElementById('emissionChartCanvas');
                if (window.emissionChartInstance) {
                    window.emissionChartInstance.destroy();
                }
                window.emissionChartInstance = new Chart(canvas, {
                    type: 'bar',
                    data: {
                        labels: [%s],
                        datasets: [{
                            label: 'kg CO2e',
                            data: [%s],
                            backgroundColor: 'rgba(54, 162, 235, 0.6)'
                        }]
                    },
                    options: {
                        responsive: true,
                        scales: { y: { beginAtZero: true } }
                    }
                });
                """.formatted(labels, data);

        getElement().executeJs(script);
    }

}