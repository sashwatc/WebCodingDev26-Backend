package com.FBLA.WebCodingDev26Backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * REQUEST DTO: credentials/identity payload for the sign-in endpoint.
 *
 * <p>Direction: client -> server (inbound request body). A mutable POJO (not a
 * record) so Jackson/Spring can bind it via the no-arg constructor and setters.
 * Sign-in here is email-based: the email identifies (and, for new users, can
 * create) the account; an optional full name may be supplied.</p>
 */
public class SignInRequest {
    // fullName is optional on sign-in; existing stored name is preserved when omitted
    private String fullName;

    // Email identifying the account. Required and must be a syntactically valid
    // address: @NotBlank rejects null/empty/whitespace; @Email validates format.
    @Email(message = "Enter a valid email address.")
    @NotBlank(message = "Email is required.")
    private String email;

    // Returns the optional full name (may be null when omitted).
    public String getFullName() {
        return fullName;
    }

    // Sets the optional full name (bound from the request body).
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    // Returns the sign-in email.
    public String getEmail() {
        return email;
    }

    // Sets the sign-in email (bound from the request body).
    public void setEmail(String email) {
        this.email = email;
    }
}
