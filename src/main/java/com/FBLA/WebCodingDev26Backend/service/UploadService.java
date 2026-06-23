package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.dto.UploadRequest;
import com.FBLA.WebCodingDev26Backend.dto.UploadResponse;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class UploadService {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Map<String, String> EXTENSION_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp"
    );

    public UploadResponse upload(UploadRequest request) {
        validate(request);
        return new UploadResponse(request.getDataUrl());
    }

    private void validate(UploadRequest request) {
        if (request == null || request.getDataUrl() == null || request.getDataUrl().isBlank()) {
            throw new BadRequestException("Upload data is required.");
        }

        String extension = extension(request.getFileName());
        if (!extension.isBlank() && !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Photos must be jpg, jpeg, png, or webp files.");
        }

        String dataUrl = request.getDataUrl();
        if (!dataUrl.startsWith("data:") || !dataUrl.contains(";base64,")) {
            throw new BadRequestException("Photo upload must be a base64 data URL.");
        }

        String contentType = dataUrl.substring(5, dataUrl.indexOf(";base64,")).toLowerCase(Locale.ROOT);
        String suppliedType = request.getContentType() == null ? "" : request.getContentType().trim().toLowerCase(Locale.ROOT);
        if (!suppliedType.isBlank() && !suppliedType.equals(contentType)) {
            throw new BadRequestException("Photo content type does not match the uploaded file.");
        }
        if (!extension.isBlank() && !EXTENSION_TYPES.get(extension).equals(contentType)) {
            throw new BadRequestException("Photo file extension does not match the uploaded file type.");
        }
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BadRequestException("Photos must be jpg, jpeg, png, or webp files.");
        }

        try {
            byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(";base64,") + 8));
            if (bytes.length > MAX_BYTES) {
                throw new BadRequestException("Photos must be 2MB or smaller.");
            }
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Photo data is not valid base64.");
        }
    }

    private String extension(String fileName) {
        if (fileName == null || fileName.isBlank() || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
