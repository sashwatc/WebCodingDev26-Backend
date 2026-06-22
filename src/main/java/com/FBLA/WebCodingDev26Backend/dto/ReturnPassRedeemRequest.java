package com.FBLA.WebCodingDev26Backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ReturnPassRedeemRequest(
        @NotBlank(message = "One-time code is required.") String oneTimeCode
) {
}
