package com.FBLA.WebCodingDev26Backend.config;

import com.FBLA.WebCodingDev26Backend.model.EventRecoveryHub;
import com.FBLA.WebCodingDev26Backend.repository.EventRecoveryHubRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seeds the demo event recovery hub referenced by the seeded found items
 * (eventHubId = "hub_basketball_game"). Kept isolated from SeedDataConfig so it
 * cannot affect the rest of the seed wiring; idempotent and gated on app.seed.enabled.
 */
@Configuration
public class EventHubSeedConfig {

    @Bean
    CommandLineRunner seedEventHubs(
            EventRecoveryHubRepository eventHubs,
            @Value("${app.seed.enabled:true}") boolean seedEnabled
    ) {
        return args -> {
            if (!seedEnabled || eventHubs.count() > 0) {
                return;
            }
            EventRecoveryHub hub = new EventRecoveryHub();
            hub.setId("hub_basketball_game");
            hub.setTenantId("pvhs");
            hub.setName("PVHS vs. Bettendorf Basketball Game");
            hub.setDescription("Lost & found recovery hub for the home basketball game.");
            hub.setEventType("athletics");
            hub.setStartTime("2026-03-14T18:00:00Z");
            hub.setEndTime("2026-03-14T21:00:00Z");
            hub.setStatus("active");
            hub.setCampusZoneIds(List.of("zone_gym_bleachers", "zone_gym_entrance", "zone_athletics"));
            hub.setPublicEnabled(Boolean.TRUE);
            hub.setDisplayEnabled(Boolean.TRUE);
            hub.setCreatedDate("2026-03-10T10:00:00Z");
            hub.setUpdatedDate("2026-03-10T10:00:00Z");
            eventHubs.save(hub);
        };
    }
}
