package com.iu.co2management.co2_management_system.view;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route("")
@PageTitle("Start")
@PermitAll
public class HomeView extends Span {

    public HomeView() {
        super("Eingeloggt! Login funktioniert.");
    }
}