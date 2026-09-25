package com.iu.co2management.co2_management_system.view;

import com.iu.co2management.co2_management_system.entity.Location;
import com.iu.co2management.co2_management_system.repository.LocationRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.ValueContext;

@Route("locations")
@PageTitle("Standorte")
@RolesAllowed("ADMIN")
public class LocationManagementView extends VerticalLayout {

    private final LocationRepository locationRepository;

    private final Grid<Location> grid = new Grid<>(Location.class, false);
    private final TextField nameField = new TextField("Name");
    private final Binder<Location> binder = new Binder<>(Location.class);

    private Location selectedLocation;

    public LocationManagementView(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;

        grid.addColumn(Location::getName).setHeader("Name");
        grid.addSelectionListener(event -> {
            selectedLocation = event.getFirstSelectedItem().orElse(null);
            if (selectedLocation != null) {
                binder.readBean(selectedLocation);
            } else {
                clearForm();
            }
        });

        binder.forField(nameField)
                .asRequired("Name darf nicht leer sein")
                .withValidator(this::isNameUnique)
                .bind(Location::getName, Location::setName);

        Button saveButton = new Button("Speichern", event -> save());
        Button newButton = new Button("Neu", event -> clearForm());
        Button deleteButton = new Button("Löschen", event -> delete());

        HorizontalLayout formLayout = new HorizontalLayout(nameField, saveButton, newButton, deleteButton);

        add(new H2("Standortverwaltung"), grid, formLayout);

        refreshGrid();
    }

    private void save() {
        if (selectedLocation == null) {
            selectedLocation = new Location(nameField.getValue());
        }
        try {
            binder.writeBean(selectedLocation);
        } catch (ValidationException e) {
            return;
        }
        locationRepository.save(selectedLocation);
        refreshGrid();
        clearForm();
    }

    private void delete() {
        if (selectedLocation != null && selectedLocation.getId() != null) {
            locationRepository.delete(selectedLocation);
            refreshGrid();
            clearForm();
        }
    }

    private void clearForm() {
        selectedLocation = null;
        binder.readBean(null);
        grid.deselectAll();
    }

    private void refreshGrid() {
        grid.setItems(locationRepository.findAll());
    }

    private ValidationResult isNameUnique(String name, ValueContext context) {
        boolean conflict = locationRepository.findByName(name)
                .filter(existing -> selectedLocation == null || !existing.getId().equals(selectedLocation.getId()))
                .isPresent();
        return conflict
                ? ValidationResult.error("Ein Standort mit diesem Namen existiert bereits")
                : ValidationResult.ok();
    }
}