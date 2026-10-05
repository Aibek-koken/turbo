package com.kora.ecommerce.auditnotification.notification;

public interface EmailNotificationPort {

    void send(EmailNotificationPayload payload);
}
