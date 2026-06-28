package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.dto.UploadRequest;
import com.FBLA.WebCodingDev26Backend.dto.UploadResponse;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Validates user-submitted photo uploads that arrive as base64 {@code data:} URLs
 * (rather than multipart files) and echoes the validated data URL back to the caller.
 *
 * <p>Business rules enforced: only JPEG/PNG/WebP images are accepted, the declared
 * extension / supplied content type / embedded data-URL MIME type must all agree,
 * and the decoded payload must be at most 2 MB. The service does not persist the
 * image to disk/storage — it simply guarantees the data URL is well-formed and safe
 * to store inline on the owning record.
 */
@Service
public class UploadService {
    /** Maximum allowed decoded image size: 2 MB. */
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    /** Whitelist of acceptable image MIME types. */
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    /** Whitelist of acceptable file extensions (lower-case, no dot). */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    /** Maps each allowed extension to the MIME type it must correspond to (cross-check). */
    private static final Map<String, String> EXTENSION_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp"
    );

    /**
     * Validates the upload request and returns a response wrapping the same data URL.
     *
     * @param request the upload payload (data URL, optional file name and content type)
     * @return an {@link UploadResponse} carrying the validated data URL
     * @throws BadRequestException if validation fails (see {@link #validate})
     */
    public UploadResponse upload(UploadRequest request) {
        validate(request);
        return new UploadResponse(request.getDataUrl());
    }

    /**
     * Enforces all upload constraints, throwing {@link BadRequestException} on the
     * first violation.
     *
     * <p>Checks, in order: (1) a non-blank data URL is present; (2) if a file name is
     * given, its extension is allowed; (3) the data URL is a base64 {@code data:} URL;
     * (4) the supplied content type (if any) matches the data-URL MIME type;
     * (5) the file extension's expected MIME type matches the data-URL MIME type;
     * (6) the data-URL MIME type is an allowed image type; (7) the base64 payload
     * decodes and is within the size limit.
     *
     * @param request the upload payload to validate
     * @throws BadRequestException when any rule is violated
     */
    private void validate(UploadRequest request) {
        // (1) The base64 data URL is the actual payload and is mandatory.
        if (request == null || request.getDataUrl() == null || request.getDataUrl().isBlank()) {
            throw new BadRequestException("Upload data is required.");
        }

        // (2) If a filename was provided, its extension must be in the whitelist.
        String extension = extension(request.getFileName());
        if (!extension.isBlank() && !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Photos must be jpg, jpeg, png, or webp files.");
        }

        // (3) Must look like "data:<mime>;base64,<payload>".
        String dataUrl = request.getDataUrl();
        if (!dataUrl.startsWith("data:") || !dataUrl.contains(";base64,")) {
            throw new BadRequestException("Photo upload must be a base64 data URL.");
        }

        // Extract the MIME type embedded in the data URL: between "data:" (index 5)
        // and the ";base64," marker, normalized to lower case.
        String contentType = dataUrl.substring(5, dataUrl.indexOf(";base64,")).toLowerCase(Locale.ROOT);
        String suppliedType = request.getContentType() == null ? "" : request.getContentType().trim().toLowerCase(Locale.ROOT);
        // (4) A separately-supplied content type, if present, must agree with the data URL.
        if (!suppliedType.isBlank() && !suppliedType.equals(contentType)) {
            throw new BadRequestException("Photo content type does not match the uploaded file.");
        }
        // (5) The extension's expected MIME type must also agree (e.g. .png ↔ image/png).
        if (!extension.isBlank() && !EXTENSION_TYPES.get(extension).equals(contentType)) {
            throw new BadRequestException("Photo file extension does not match the uploaded file type.");
        }
        // (6) Finally the embedded MIME type itself must be an allowed image type.
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BadRequestException("Photos must be jpg, jpeg, png, or webp files.");
        }

        try {
            // (7) Decode the base64 payload (everything after ";base64," — 8 chars long)
            // and enforce the 2 MB cap on the actual decoded bytes.
            byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(";base64,") + 8));
            if (bytes.length > MAX_BYTES) {
                throw new BadRequestException("Photos must be 2MB or smaller.");
            }
        } catch (IllegalArgumentException exception) {
            // decode() throws IllegalArgumentException on malformed base64.
            throw new BadRequestException("Photo data is not valid base64.");
        }
    }

    /**
     * Extracts the lower-cased file extension from a file name.
     *
     * @param fileName the original file name (may be null/blank/without a dot)
     * @return the extension after the last dot, lower-cased, or "" when none is present
     */
    private String extension(String fileName) {
        // No name, or no dot → no determinable extension.
        if (fileName == null || fileName.isBlank() || !fileName.contains(".")) {
            return "";
        }
        // Take everything after the final '.' and normalize case.
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
