package com.FBLA.WebCodingDev26Backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * REQUEST DTO: a file upload submitted as an inline data URL.
 *
 * <p>Direction: client -> server (inbound request body for the upload
 * endpoint). A mutable POJO so Jackson/Spring can bind it via setters. The file
 * bytes are carried inline in {@code dataUrl} rather than as multipart, letting
 * the server decode and persist the file and return a hosted URL.</p>
 */
public class UploadRequest {
    // Desired file name for the stored file; defaults to "upload" if the client
    // does not supply one.
    private String fileName = "upload";
    // MIME content type of the file (e.g. "image/png"); defaults to empty string.
    private String contentType = "";

    // The file contents as a data URL (e.g. "data:image/png;base64,...").
    // Required: @NotBlank rejects null/empty/whitespace-only values.
    @NotBlank(message = "data_url is required")
    private String dataUrl;

    // Returns the target file name (defaulted to "upload" when unset).
    public String getFileName() {
        return fileName;
    }

    // Sets the target file name (bound from the request body).
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    // Returns the declared content type (defaulted to "" when unset).
    public String getContentType() {
        return contentType;
    }

    // Sets the declared content type (bound from the request body).
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    // Returns the inline data-URL payload of the file.
    public String getDataUrl() {
        return dataUrl;
    }

    // Sets the inline data-URL payload of the file (bound from the request body).
    public void setDataUrl(String dataUrl) {
        this.dataUrl = dataUrl;
    }
}
