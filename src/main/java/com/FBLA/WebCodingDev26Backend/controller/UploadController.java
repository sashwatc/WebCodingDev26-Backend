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

@RestController
@RequestMapping("/api/uploads")
public class UploadController {
    private final UploadService service;

    public UploadController(UploadService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse upload(@Valid @RequestBody UploadRequest request) {
        return service.upload(request);
    }
}
