package com.iu.co2management.co2_management_system.view;

import com.iu.co2management.co2_management_system.entity.EmissionEntry;
import com.iu.co2management.co2_management_system.entity.Location;
import com.iu.co2management.co2_management_system.service.EmissionService;
import com.iu.co2management.co2_management_system.service.LocationSummary;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Emissionsübersicht")
@RolesAllowed({"USER", "SUSTAINABILITY_OFFICER", "EXECUTIVE", "ADMIN"})
@JavaScript("https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js")
public class EmissionOverviewView extends VerticalLayout {

    /** Tabellenzeile: Standortname kommt aus der Schleife über die Standorte (kein Lazy Loading nötig). */
    private record Row(String locationName, EmissionEntry entry) {
    }

    private static final String LOCATION_CANVAS_ID = "locationChartCanvas";
    private static final String TIME_CANVAS_ID = "emissionChartCanvas";

    private final EmissionService emissionService;

    private final Span locationLabel = new Span();
    private final DatePicker fromField = new DatePicker("Von");
    private final DatePicker toField = new DatePicker("Bis");
    private record LocationOption(Location location, String label) {

    }

    private static final LocationOption ALL_LOCATIONS = new LocationOption(null, "Alle Standorte");

    private final ComboBox<LocationOption> locationBox = new ComboBox<>("Standort");
    private final H3 totalLabel = new H3();

    // Diagramm-Bereich: zwei "Karten" nebeneinander
    private final Div locationChartCard;   // nur im Modus "Alle Standorte" sichtbar
    private final Div timeChartCard;

    private final Grid<Row> grid = new Grid<>();

    public EmissionOverviewView(EmissionService emissionService) {
        this.emissionService = emissionService;
        boolean canReadAll = emissionService.canReadAllLocations();

        fromField.setValue(LocalDate.now().minusDays(30));
        toField.setValue(LocalDate.now());

        Button filterButton = new Button("Filtern", event -> refresh());

        HorizontalLayout filterLayout = new HorizontalLayout(fromField, toField);
        filterLayout.setDefaultVerticalComponentAlignment(HorizontalLayout.Alignment.END);

        if (canReadAll) {
            List<LocationOption> options = new ArrayList<>();
            options.add(ALL_LOCATIONS);
            emissionService.getAllLocations()
                    .forEach(location -> options.add(new LocationOption(location, location.getName())));

            locationBox.setItems(options);
            locationBox.setItemLabelGenerator(LocationOption::label);
            locationBox.setPlaceholder("Eigener Standort");
            locationBox.setClearButtonVisible(true);
            locationBox.addValueChangeListener(event -> refresh());
            filterLayout.add(locationBox);
        }
        filterLayout.add(filterButton);
        filterLayout.getStyle().set("flex-wrap", "wrap");

        totalLabel.getStyle().set("margin", "0 0 var(--lumo-space-s) 0");

        HorizontalLayout topRow = new HorizontalLayout(filterLayout, totalLabel);
        topRow.setWidthFull();
        topRow.setPadding(false);
        topRow.setJustifyContentMode(HorizontalLayout.JustifyContentMode.BETWEEN);
        topRow.setDefaultVerticalComponentAlignment(HorizontalLayout.Alignment.END);
        topRow.getStyle().set("flex-wrap", "wrap");

        locationChartCard = createChartCard("Emissionen je Standort", LOCATION_CANVAS_ID);
        timeChartCard = createChartCard("Emissionen im Zeitverlauf", TIME_CANVAS_ID);
        locationChartCard.setVisible(false);

        HorizontalLayout chartsRow = new HorizontalLayout(locationChartCard, timeChartCard);
        chartsRow.setWidthFull();
        chartsRow.setPadding(false);
        chartsRow.getStyle().set("flex-wrap", "wrap");

        grid.addColumn(row -> row.entry().getCategory()).setHeader("Kategorie");
        grid.addColumn(row -> row.entry().getAmountKgCo2e()).setHeader("Menge (kg CO₂e)");
        grid.addColumn(row -> row.entry().getDate()).setHeader("Datum");
        grid.addColumn(Row::locationName).setHeader("Standort");

        add(new H2("Emissionsübersicht"), locationLabel, topRow, chartsRow, grid);

        refresh();
    }

    /** Baut eine Karte mit Überschrift und einem Canvas für Chart.js. */
    private Div createChartCard(String title, String canvasId) {
        H3 heading = new H3(title);
        heading.getStyle().set("margin-top", "0");

        // Chart.js braucht einen Container mit fester Höhe und position: relative
        Div chartBox = new Div();
        chartBox.setWidthFull();
        chartBox.setHeight("300px");
        chartBox.getStyle().set("position", "relative");
        Element canvas = new Element("canvas");
        canvas.setAttribute("id", canvasId);
        chartBox.getElement().appendChild(canvas);

        Div card = new Div(heading, chartBox);
        card.getStyle()
                .set("flex", "1 1 420px")   // beide gleich breit; bei nur einer Karte füllt sie die ganze Zeile
                .set("min-width", "0")
                .set("box-sizing", "border-box")
                .set("padding", "var(--lumo-space-m)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)");
        return card;
    }

    private void refresh() {
        LocalDate from = fromField.getValue();
        LocalDate to = toField.getValue();
        if (from == null || to == null) {
            return;
        }

        LocationOption option = locationBox.getValue();
        boolean all = option == ALL_LOCATIONS;
        Location selected = (option != null && !all) ? option.location() : null;

        List<Row> rows = new ArrayList<>();
        String scope;
        if (all) {
            for (Location location : emissionService.getAllLocations()) {
                for (EmissionEntry entry : emissionService.getEmissionsForLocation(location.getId(), from, to)) {
                    rows.add(new Row(location.getName(), entry));
                }
            }
            rows.sort(Comparator.comparing((Row row) -> row.entry().getDate()));
            scope = "Alle Standorte";
        } else if (selected != null) {
            emissionService.getEmissionsForLocation(selected.getId(), from, to)
                    .forEach(entry -> rows.add(new Row(selected.getName(), entry)));
            scope = selected.getName();
        } else {
            String own = emissionService.getCurrentUserLocationName();
            emissionService.getEmissionsForOwnLocation(from, to)
                    .forEach(entry -> rows.add(new Row(own, entry)));
            scope = own;
        }

        locationLabel.setText("Standort: " + scope);
        grid.setItems(rows);

        double total = rows.stream().mapToDouble(row -> row.entry().getAmountKgCo2e()).sum();
        totalLabel.setText("Gesamt im Zeitraum: %.2f kg CO₂e".formatted(total));

        locationChartCard.setVisible(all);
        if (all) {
            updateLocationChart(emissionService.getAllLocationsSummary(from, to));
        }

        updateTimeChart(rows, from, to);
    }

    private void updateTimeChart(List<Row> rows, LocalDate from, LocalDate to) {
        Map<LocalDate, Double> totalsByDate = new TreeMap<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            totalsByDate.put(cursor, 0.0);
            cursor = cursor.plusDays(1);
        }
        for (Row row : rows) {
            totalsByDate.merge(row.entry().getDate(), row.entry().getAmountKgCo2e(), Double::sum);
        }

        String labels = totalsByDate.keySet().stream()
                .map(date -> "\"" + date + "\"")
                .collect(Collectors.joining(","));
        String data = totalsByDate.values().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        renderBarChart(TIME_CANVAS_ID, "emissionChartInstance", labels, data, "rgba(54, 162, 235, 0.6)");
    }

    private void updateLocationChart(List<LocationSummary> summaries) {
        String labels = summaries.stream()
                .map(s -> "\"" + s.locationName() + "\"")
                .collect(Collectors.joining(","));
        String data = summaries.stream()
                .map(s -> String.valueOf(s.totalKgCo2e()))
                .collect(Collectors.joining(","));

        renderBarChart(LOCATION_CANVAS_ID, "locationChartInstance", labels, data, "rgba(255, 99, 132, 0.6)");
    }

    /** Gemeinsame Chart.js-Logik für beide Balkendiagramme. */
    private void renderBarChart(String canvasId, String instanceName, String labels, String data, String color) {
        String script = """
                const canvas = document.getElementById('%1$s');
                if (!canvas) { return; }
                if (window['%2$s']) {
                    window['%2$s'].destroy();
                }
                window['%2$s'] = new Chart(canvas, {
                    type: 'bar',
                    data: {
                        labels: [%3$s],
                        datasets: [{
                            label: 'kg CO2e',
                            data: [%4$s],
                            backgroundColor: '%5$s'
                        }]
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        plugins: { legend: { display: false } },
                        scales: {
                            y: { beginAtZero: true, title: { display: true, text: 'kg CO₂e' } }
                        }
                    }
                });
                """.formatted(canvasId, instanceName, labels, data, color);

        getElement().executeJs(script);
    }
}