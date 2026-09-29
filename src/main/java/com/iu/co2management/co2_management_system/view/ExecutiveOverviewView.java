package com.iu.co2management.co2_management_system.view;

import com.iu.co2management.co2_management_system.service.EmissionService;
import com.iu.co2management.co2_management_system.service.LocationSummary;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Route("executive-overview")
@PageTitle("Standortübergreifende Übersicht")
@RolesAllowed({"EXECUTIVE", "ADMIN"})
@JavaScript("https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js")
public class ExecutiveOverviewView extends VerticalLayout {

    private final EmissionService emissionService;

    private final DatePicker fromField = new DatePicker("Von");
    private final DatePicker toField = new DatePicker("Bis");
    private final Grid<LocationSummary> grid = new Grid<>(LocationSummary.class, false);
    private final Div chartContainer = new Div();

    public ExecutiveOverviewView(EmissionService emissionService) {
        this.emissionService = emissionService;

        fromField.setValue(LocalDate.now().minusDays(30));
        toField.setValue(LocalDate.now());

        Button filterButton = new Button("Filtern", event -> refresh());

        grid.addColumn(LocationSummary::locationName).setHeader("Standort");
        grid.addColumn(LocationSummary::totalKgCo2e).setHeader("Gesamt (kg CO₂e)");

        HorizontalLayout filterLayout = new HorizontalLayout(fromField, toField, filterButton);

        chartContainer.setWidthFull();
        chartContainer.setHeight("300px");
        Element canvasElement = new Element("canvas");
        canvasElement.setAttribute("id", "executiveChartCanvas");
        chartContainer.getElement().appendChild(canvasElement);

        add(new H2("Standortübergreifende Übersicht"), filterLayout, grid, chartContainer);

        refresh();
    }

    private void refresh() {
        LocalDate from = fromField.getValue();
        LocalDate to = toField.getValue();
        if (from == null || to == null) {
            return;
        }

        List<LocationSummary> summaries = emissionService.getAllLocationsSummary(from, to);
        grid.setItems(summaries);
        updateChart(summaries);
    }

    private void updateChart(List<LocationSummary> summaries) {
        String labels = summaries.stream()
                .map(s -> "\"" + s.locationName() + "\"")
                .collect(Collectors.joining(","));
        String data = summaries.stream()
                .map(s -> String.valueOf(s.totalKgCo2e()))
                .collect(Collectors.joining(","));

        String script = """
                const canvas = document.getElementById('executiveChartCanvas');
                if (window.executiveChartInstance) {
                    window.executiveChartInstance.destroy();
                }
                window.executiveChartInstance = new Chart(canvas, {
                    type: 'bar',
                    data: {
                        labels: [%s],
                        datasets: [{
                            label: 'kg CO2e',
                            data: [%s],
                            backgroundColor: 'rgba(255, 99, 132, 0.6)'
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