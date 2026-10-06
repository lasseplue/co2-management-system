package com.iu.co2management.co2_management_system.view;

import com.iu.co2management.co2_management_system.service.EmissionService;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.notification.Notification;

import jakarta.annotation.security.RolesAllowed;

import java.time.LocalDate;
import java.util.List;

@Route(value = "emission-entry", layout = MainLayout.class)
@PageTitle("Emissionen erfassen")
@RolesAllowed({"SUSTAINABILITY_OFFICER", "ADMIN"})
public class EmissionEntryView extends VerticalLayout {

    private final EmissionService emissionService;

    private final ComboBox<String> categoryComboBox = new ComboBox<>("Kategorie");
    private final NumberField amountField = new NumberField("Menge (kg CO₂e)");
    private final DatePicker dateField = new DatePicker("Datum");

    public EmissionEntryView(EmissionService emissionService) {
        this.emissionService = emissionService;

        categoryComboBox.setItems(EmissionService.CATEGORIES);
        categoryComboBox.setRequiredIndicatorVisible(true);

        amountField.setMin(0);
        amountField.setRequiredIndicatorVisible(true);

        dateField.setValue(LocalDate.now());
        dateField.setRequiredIndicatorVisible(true);

        Button saveButton = new Button("Speichern", event -> save());

        FormLayout formLayout = new FormLayout(categoryComboBox, amountField, dateField, saveButton);

        add(new H2("Emissionen erfassen"), formLayout);
    }

    private void save() {
        boolean valid = true;

        String category = categoryComboBox.getValue();
        if (category == null) {
            categoryComboBox.setInvalid(true);
            categoryComboBox.setErrorMessage("Kategorie ist erforderlich");
            valid = false;
        } else {
            categoryComboBox.setInvalid(false);
        }

        Double amount = amountField.getValue();
        if (amount == null || amount <= 0) {
            amountField.setInvalid(true);
            amountField.setErrorMessage("Menge muss größer als 0 sein");
            valid = false;
        } else {
            amountField.setInvalid(false);
        }

        LocalDate date = dateField.getValue();
        if (date == null) {
            dateField.setInvalid(true);
            dateField.setErrorMessage("Datum ist erforderlich");
            valid = false;
        } else {
            dateField.setInvalid(false);
        }

        if (!valid) {
            return;
        }

        emissionService.recordEmission(category, amount, date);
        Notification.show("Emission erfasst: " + amount + " kg CO2e am " + date, 3000, Notification.Position.BOTTOM_START);
        clearForm();
    }

    private void clearForm() {
        categoryComboBox.clear();
        categoryComboBox.setInvalid(false);
        amountField.clear();
        amountField.setInvalid(false);
        dateField.setValue(LocalDate.now());
    }
}