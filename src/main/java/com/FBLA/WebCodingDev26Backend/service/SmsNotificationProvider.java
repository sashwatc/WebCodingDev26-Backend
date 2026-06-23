package com.FBLA.WebCodingDev26Backend.service;

public interface SmsNotificationProvider {
    DeliveryProviderResult send(SmsMessage message);
}
