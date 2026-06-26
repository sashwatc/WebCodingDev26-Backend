package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.model.CampusZone;
import com.FBLA.WebCodingDev26Backend.repository.CampusZoneRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public lookup of campus zones (used by the Beacon and Event Hub display pages). */
@RestController
public class CampusZoneController {
    private final CampusZoneRepository campusZones;

    public CampusZoneController(CampusZoneRepository campusZones) {
        this.campusZones = campusZones;
    }

    @GetMapping("/api/campus-zones")
    public List<CampusZone> list() {
        return campusZones.findAll();
    }
}
