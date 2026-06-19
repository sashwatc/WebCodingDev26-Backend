package com.FBLA.WebCodingDev26Backend.exception;

public class UnsupportedEntityException extends RuntimeException {
    public UnsupportedEntityException(String entityName) {
        super("Entity not found: " + entityName);
    }
}
