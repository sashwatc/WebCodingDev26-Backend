package com.FBLA.WebCodingDev26Backend.service;

public interface WebhookNotificationProvider {
    DeliveryProviderResult post(WebhookMessage message);
}
