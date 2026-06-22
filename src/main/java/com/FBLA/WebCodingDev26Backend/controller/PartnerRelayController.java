package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.PartnerRelayResponse;
import com.FBLA.WebCodingDev26Backend.model.RecoveryNode;
import com.FBLA.WebCodingDev26Backend.service.PartnerRelayService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PartnerRelayController {
    private final PartnerRelayService partnerRelayService;

    public PartnerRelayController(PartnerRelayService partnerRelayService) {
        this.partnerRelayService = partnerRelayService;
    }

    @GetMapping("/api/recovery-nodes")
    public List<RecoveryNode> nodes() {
        return partnerRelayService.nodes();
    }

    @GetMapping("/api/partner-relays")
    public List<PartnerRelayResponse> relays() {
        return partnerRelayService.relays();
    }
}
