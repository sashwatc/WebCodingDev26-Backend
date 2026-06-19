package com.FBLA.WebCodingDev26Backend.dto;

import jakarta.validation.constraints.NotBlank;

public class UploadRequest {
    private String fileName = "upload";
    private String contentType = "";

    @NotBlank(message = "data_url is required")
    private String dataUrl;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getDataUrl() {
        return dataUrl;
    }

    public void setDataUrl(String dataUrl) {
        this.dataUrl = dataUrl;
    }
}
