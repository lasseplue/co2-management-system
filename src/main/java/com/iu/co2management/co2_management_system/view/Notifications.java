package com.iu.co2management.co2_management_system.view;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;


public final class Notifications {

    private static final int SUCCESS_MS = 3000;
    private static final int ERROR_MS = 5000;

    private Notifications() {
    }

    public static void success(String text) {
        Notification notification = Notification.show(text, SUCCESS_MS, Notification.Position.BOTTOM_START);
        notification.addThemeVariants(NotificationVariant.SUCCESS);
    }

    public static void error(String text) {
        Notification notification = Notification.show(text, ERROR_MS, Notification.Position.BOTTOM_START);
        notification.addThemeVariants(NotificationVariant.ERROR);
    }
}