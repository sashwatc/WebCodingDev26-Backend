package com.FBLA.WebCodingDev26Backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebRouteController {
    @GetMapping({
            "/",
            "/report-lost",
            "/report-found",
            "/browse",
            "/claim",
            "/admin",
            "/chat",
            "/admin/chat",
            "/login",
            "/signup",
            "/verify-email",
            "/auth/callback",
            "/sources"
    })
    public String app() {
        return "forward:/index.html";
    }
}
