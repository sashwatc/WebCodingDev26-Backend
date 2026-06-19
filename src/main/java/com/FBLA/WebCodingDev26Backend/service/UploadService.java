package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.dto.UploadRequest;
import com.FBLA.WebCodingDev26Backend.dto.UploadResponse;
import org.springframework.stereotype.Service;

@Service
public class UploadService {
    public UploadResponse upload(UploadRequest request) {
        return new UploadResponse(request.getDataUrl());
    }
}
