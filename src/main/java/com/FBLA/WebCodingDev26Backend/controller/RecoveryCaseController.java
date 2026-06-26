package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.model.RecoveryCase;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.RecoveryCaseService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read access to recovery cases for the admin recovery dashboard. */
@RestController
@RequestMapping("/api/recovery-cases")
public class RecoveryCaseController {
    private final RecoveryCaseService recoveryCases;
    private final DemoAuthorizationService authorization;

    public RecoveryCaseController(RecoveryCaseService recoveryCases, DemoAuthorizationService authorization) {
        this.recoveryCases = recoveryCases;
        this.authorization = authorization;
    }

    @GetMapping
    public List<RecoveryCase> list(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorization.requireStaffOrAdmin(userEmail);
        return recoveryCases.list();
    }

    @GetMapping("/{id}")
    public RecoveryCase get(@PathVariable String id, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorization.requireStaffOrAdmin(userEmail);
        return recoveryCases.get(id);
    }
}
