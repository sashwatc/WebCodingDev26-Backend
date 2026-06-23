package com.FBLA.WebCodingDev26Backend.service;

public record DeliveryProviderResult(String status, String providerMessageId, String errorMessage) {
    public static DeliveryProviderResult sent(String providerMessageId) {
        return new DeliveryProviderResult("sent", providerMessageId, null);
    }

    public static DeliveryProviderResult skipped(String reason) {
        return new DeliveryProviderResult("skipped", null, reason);
    }

    public static DeliveryProviderResult failed(String message) {
        return new DeliveryProviderResult("failed", null, message);
    }
}
