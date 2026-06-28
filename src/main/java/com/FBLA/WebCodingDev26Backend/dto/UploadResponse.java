package com.FBLA.WebCodingDev26Backend.dto;

/**
 * RESPONSE DTO: the result of a successful file upload.
 *
 * <p>Direction: server -> client (outbound response for the upload endpoint).
 * Returns where the stored file can now be retrieved.</p>
 */
public record UploadResponse(
        // Public/hosted URL at which the uploaded file is accessible.
        String fileUrl
) {
}
