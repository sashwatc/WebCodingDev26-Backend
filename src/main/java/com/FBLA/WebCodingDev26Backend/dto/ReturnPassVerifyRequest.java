package com.FBLA.WebCodingDev26Backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ReturnPassVerifyRequest(
        @NotBlank(message = "One-time code is required.") String oneTimeCode
) {
}
