package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.UploadRequest;
import com.FBLA.WebCodingDev26Backend.dto.UploadResponse;
import com.FBLA.WebCodingDev26Backend.service.UploadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for file/image uploads.
 *
 * <p>Base route: {@code /api/uploads}. Returns JSON. Delegates all work (validation, storage, URL
 * generation) to {@link UploadService}; this controller is a thin HTTP entry point.
 */
@RestController // JSON REST controller
@RequestMapping("/api/uploads") // shared base path for all handlers
public class UploadController {
    /** Handles the actual upload processing and storage. */
    private final UploadService service;

    /** Constructor injection of the upload service. */
    public UploadController(UploadService service) {
        this.service = service;
    }

    /**
     * POST {@code /api/uploads} — accept an upload and return its stored location/metadata.
     *
     * @param request the upload payload; {@code @Valid} triggers bean validation on
     *                {@link UploadRequest} (invalid input -> 400 Bad Request).
     * @return 201 CREATED with an {@link UploadResponse} (e.g. the stored file URL/metadata).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // success returns HTTP 201
    public UploadResponse upload(@Valid @RequestBody UploadRequest request) {
        return service.upload(request);
    }
}
