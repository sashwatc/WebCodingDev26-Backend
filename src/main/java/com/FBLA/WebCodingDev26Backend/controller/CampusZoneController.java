package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.model.CampusZone;
import com.FBLA.WebCodingDev26Backend.repository.CampusZoneRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public lookup of campus zones (used by the Beacon and Event Hub display pages).
 *
 * <p>Exposes a single endpoint, {@code GET /api/campus-zones}, that returns all
 * configured campus zones straight from {@link CampusZoneRepository}. No
 * authorization is enforced — this is public reference data.</p>
 */
@RestController // REST controller: handler return value is serialized to the response body
public class CampusZoneController {
    // Repository providing the list of campus zones.
    private final CampusZoneRepository campusZones;

    /** Constructor injection of the campus-zone repository. */
    public CampusZoneController(CampusZoneRepository campusZones) {
        this.campusZones = campusZones;
    }

    /**
     * GET /api/campus-zones — list all campus zones.
     *
     * @return every {@link CampusZone} record; 200 OK. No authorization required.
     */
    @GetMapping("/api/campus-zones")
    public List<CampusZone> list() {
        return campusZones.findAll();
    }
}
