package com.iu.co2management.co2_management_system.view;

import com.iu.co2management.co2_management_system.entity.Location;
import com.iu.co2management.co2_management_system.repository.AppUserRepository;
import com.iu.co2management.co2_management_system.repository.EmissionEntryRepository;
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
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.ValueContext;

import jakarta.annotation.security.RolesAllowed;
import org.springframework.dao.DataIntegrityViolationException;

@Route(value = "locations", layout = MainLayout.class)
@PageTitle("Standorte")
@RolesAllowed("ADMIN")
public class LocationManagementView extends VerticalLayout {

    private final LocationRepository locationRepository;
    private final AppUserRepository appUserRepository;
    private final EmissionEntryRepository emissionEntryRepository;

    private final Grid<Location> grid = new Grid<>(Location.class, false);
    private final TextField nameField = new TextField("Name");
    private final Binder<Location> binder = new Binder<>(Location.class);

    private Location selectedLocation;

    public LocationManagementView(LocationRepository locationRepository,
                                  AppUserRepository appUserRepository,
                                  EmissionEntryRepository emissionEntryRepository) {

        this.locationRepository = locationRepository;
        this.appUserRepository = appUserRepository;
        this.emissionEntryRepository = emissionEntryRepository;

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
        boolean isNew = selectedLocation == null;
        if (isNew) {
            selectedLocation = new Location(nameField.getValue());
        }
        try {
            binder.writeBean(selectedLocation);
        } catch (ValidationException e) {
            return;
        }
        locationRepository.save(selectedLocation);
        Notification.show("Standort „" + selectedLocation.getName() + "“ "
                + (isNew ? "angelegt" : "aktualisiert"), 3000, Notification.Position.BOTTOM_START);
        refreshGrid();
        clearForm();
    }

    private void delete() {
        if (selectedLocation == null || selectedLocation.getId() == null) {
            return;
        }
        String name = selectedLocation.getName();
        Long id = selectedLocation.getId();

        if (appUserRepository.existsByLocationId(id) || emissionEntryRepository.existsByLocationId(id)) {
            Notification.show("„" + name + "“ kann nicht gelöscht werden, es gibt noch zugehörige Benutzer oder Emissionseinträge.",
                    5000, Notification.Position.MIDDLE);
            return;
        }

        locationRepository.delete(selectedLocation);
        Notification.show("Standort „" + name + "“ gelöscht", 3000, Notification.Position.BOTTOM_START);
        refreshGrid();
        clearForm();
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