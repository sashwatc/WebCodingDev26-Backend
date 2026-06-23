package com.FBLA.WebCodingDev26Backend.service;

import java.util.Map;

public record WebhookMessage(Map<String, Object> payload) {
}
