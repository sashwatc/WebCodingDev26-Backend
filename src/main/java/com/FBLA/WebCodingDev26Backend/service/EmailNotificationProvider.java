package com.FBLA.WebCodingDev26Backend.service;

public interface EmailNotificationProvider {
    DeliveryProviderResult send(EmailMessage message);
}
