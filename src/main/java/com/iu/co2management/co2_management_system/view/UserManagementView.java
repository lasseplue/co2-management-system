package com.iu.co2management.co2_management_system.view;

import com.iu.co2management.co2_management_system.entity.AppUser;
import com.iu.co2management.co2_management_system.entity.Location;
import com.iu.co2management.co2_management_system.entity.Role;
import com.iu.co2management.co2_management_system.repository.AppUserRepository;
import com.iu.co2management.co2_management_system.repository.LocationRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.ValueContext;
import com.vaadin.flow.data.validator.EmailValidator;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.stream.Collectors;

@Route("users")
@PageTitle("Benutzerverwaltung")
@RolesAllowed("ADMIN")
public class UserManagementView extends VerticalLayout {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    private final Grid<AppUser> grid = new Grid<>(AppUser.class, false);
    private final TextField nameField = new TextField("Name");
    private final EmailField emailField = new EmailField("E-Mail");
    private final PasswordField passwordField = new PasswordField("Passwort (bei Bearbeitung leer lassen = unverändert)");
    private final ComboBox<Location> locationComboBox = new ComboBox<>("Standort");
    private final CheckboxGroup<Role> rolesField = new CheckboxGroup<>("Rollen");
    private final Binder<AppUser> binder = new Binder<>(AppUser.class);

    private AppUser selectedUser;

    public UserManagementView(AppUserRepository appUserRepository,
                              LocationRepository locationRepository,
                              PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;

        grid.addColumn(AppUser::getName).setHeader("Name");
        grid.addColumn(AppUser::getEmail).setHeader("E-Mail");
        grid.addColumn(user -> user.getLocation().getName()).setHeader("Standort");
        grid.addColumn(user -> user.getRoles().stream()
                        .map(Role::name)
                        .collect(Collectors.joining(", ")))
                .setHeader("Rollen");
        grid.addSelectionListener(event -> {
            selectedUser = event.getFirstSelectedItem().orElse(null);
            if (selectedUser != null) {
                binder.readBean(selectedUser);
                rolesField.setValue(selectedUser.getRoles());
                passwordField.clear();
            } else {
                clearForm();
            }
        });

        locationComboBox.setItems(locationRepository.findAll());
        locationComboBox.setItemLabelGenerator(Location::getName);

        rolesField.setItems(Role.values());

        binder.forField(nameField)
                .asRequired("Name darf nicht leer sein")
                .bind(AppUser::getName, AppUser::setName);

        binder.forField(emailField)
                .asRequired("E-Mail darf nicht leer sein")
                .withValidator(new EmailValidator("Keine gültige E-Mail-Adresse"))
                .withValidator(this::isEmailUnique)
                .bind(AppUser::getEmail, AppUser::setEmail);

        binder.forField(locationComboBox)
                .asRequired("Standort ist erforderlich")
                .bind(AppUser::getLocation, AppUser::setLocation);

        Button saveButton = new Button("Speichern", event -> save());
        Button newButton = new Button("Neu", event -> clearForm());
        Button deleteButton = new Button("Löschen", event -> delete());
        HorizontalLayout buttons = new HorizontalLayout(saveButton, newButton, deleteButton);

        FormLayout formLayout = new FormLayout(nameField, emailField, passwordField, locationComboBox, rolesField);

        add(new H2("Benutzerverwaltung"), grid, formLayout, buttons);

        refreshGrid();
    }

    private void save() {
        if (selectedUser == null) {
            if (passwordField.isEmpty()) {
                Notification.show("Für neue Benutzer:innen ist ein Passwort erforderlich");
                return;
            }
            selectedUser = new AppUser(nameField.getValue(), emailField.getValue(), "", locationComboBox.getValue());
        }
        try {
            binder.writeBean(selectedUser);
        } catch (ValidationException e) {
            return;
        }
        if (!passwordField.isEmpty()) {
            selectedUser.setPasswordHash(passwordEncoder.encode(passwordField.getValue()));
        }
        selectedUser.getRoles().clear();
        selectedUser.getRoles().addAll(rolesField.getValue());

        appUserRepository.save(selectedUser);
        refreshGrid();
        clearForm();
    }

    private void delete() {
        if (selectedUser != null && selectedUser.getId() != null) {
            appUserRepository.delete(selectedUser);
            refreshGrid();
            clearForm();
        }
    }

    private void clearForm() {
        selectedUser = null;
        binder.readBean(null);
        rolesField.clear();
        passwordField.clear();
        grid.deselectAll();
    }

    private void refreshGrid() {
        grid.setItems(appUserRepository.findAllWithLocation());
    }

    private ValidationResult isEmailUnique(String email, ValueContext context) {
        boolean conflict = appUserRepository.findByEmail(email)
                .filter(existing -> selectedUser == null || !existing.getId().equals(selectedUser.getId()))
                .isPresent();
        return conflict
                ? ValidationResult.error("Diese E-Mail-Adresse wird bereits verwendet")
                : ValidationResult.ok();
    }
}