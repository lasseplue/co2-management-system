package com.iu.co2management.co2_management_system.view;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.security.PermitAll;

@PermitAll
public class MainLayout extends AppLayout {

    public MainLayout(AuthenticationContext authenticationContext) {
        H1 title = new H1("CO₂-Management-System");
        title.getStyle().set("font-size", "1.3rem").set("margin", "0");

        Button logoutButton = new Button("Logout", event -> authenticationContext.logout());

        HorizontalLayout header = new HorizontalLayout(new DrawerToggle(), title, logoutButton);
        header.setWidthFull();
        header.setAlignItems(HorizontalLayout.Alignment.CENTER);
        header.expand(title);

        addToNavbar(header);
        addToDrawer(createSideNav());
    }

    private SideNav createSideNav() {
        Set<String> roles = currentUserRoles();

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Start", HomeView.class));

        if (roles.contains("ROLE_USER") || roles.contains("ROLE_SUSTAINABILITY_OFFICER") || roles.contains("ROLE_ADMIN")) {
            nav.addItem(new SideNavItem("Emissionsübersicht", EmissionOverviewView.class));
        }
        if (roles.contains("ROLE_SUSTAINABILITY_OFFICER") || roles.contains("ROLE_ADMIN")) {
            nav.addItem(new SideNavItem("Emissionen erfassen", EmissionEntryView.class));
        }
        if (roles.contains("ROLE_EXECUTIVE") || roles.contains("ROLE_ADMIN")) {
            nav.addItem(new SideNavItem("Standortübergreifende Übersicht", ExecutiveOverviewView.class));
        }
        if (roles.contains("ROLE_ADMIN")) {
            nav.addItem(new SideNavItem("Standorte", LocationManagementView.class));
            nav.addItem(new SideNavItem("Benutzer", UserManagementView.class));
        }

        return nav;
    }

    private Set<String> currentUserRoles() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}