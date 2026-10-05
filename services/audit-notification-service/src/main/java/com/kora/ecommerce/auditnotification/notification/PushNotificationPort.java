package com.kora.ecommerce.auditnotification.notification;

public interface PushNotificationPort {

    void send(PushNotificationPayload payload);
}
