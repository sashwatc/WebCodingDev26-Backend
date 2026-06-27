package com.FBLA.WebCodingDev26Backend.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read stub for the optional Partner Relay mesh. The demo backend does not persist
 * partner relays, so this returns an empty set instead of letting the request 404.
 * The recovery dashboard already renders an empty state for an empty relay list,
 * so behaviour is unchanged — this only keeps the network log clean.
 */
@RestController
public class RecoveryMeshController {

    @GetMapping("/api/partner-relays")
    public List<Object> partnerRelays() {
        return List.of();
    }
}
