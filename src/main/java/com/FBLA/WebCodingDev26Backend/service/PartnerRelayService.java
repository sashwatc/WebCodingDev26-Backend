package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.dto.PartnerRelayResponse;
import com.FBLA.WebCodingDev26Backend.model.PartnerRelay;
import com.FBLA.WebCodingDev26Backend.model.RecoveryNode;
import com.FBLA.WebCodingDev26Backend.repository.PartnerRelayRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryNodeRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PartnerRelayService {
    private final RecoveryNodeRepository nodes;
    private final PartnerRelayRepository relays;

    public PartnerRelayService(RecoveryNodeRepository nodes, PartnerRelayRepository relays) {
        this.nodes = nodes;
        this.relays = relays;
    }

    public List<RecoveryNode> nodes() {
        return nodes.findAll();
    }

    public List<PartnerRelayResponse> relays() {
        return relays.findAll().stream()
                .map(this::redacted)
                .toList();
    }

    private PartnerRelayResponse redacted(PartnerRelay relay) {
        return new PartnerRelayResponse(
                relay.getId(),
                relay.getSourceNodeId(),
                relay.getTargetNodeId(),
                relay.getRecoveryCaseId(),
                relay.getStatus(),
                relay.getPublicSummary(),
                relay.getRedactedMatchReasons(),
                relay.getCreatedDate(),
                relay.getUpdatedDate()
        );
    }
}
