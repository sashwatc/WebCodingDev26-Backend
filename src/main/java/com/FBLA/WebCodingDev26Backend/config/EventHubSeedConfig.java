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

    /**
     * Registers a {@link CommandLineRunner} that seeds the single demo event
     * recovery hub once the application context is ready.
     *
     * <p>Exposed as a {@link Bean} so Spring runs it at startup. The seeding is
     * idempotent and guarded: it does nothing when seeding is disabled or when any
     * hub already exists, so it never duplicates data on subsequent runs.</p>
     *
     * @param eventHubs   repository used to count existing hubs and persist the new one
     * @param seedEnabled value of {@code app.seed.enabled} (defaults to {@code true});
     *                    when false, seeding is skipped entirely
     * @return a runner that inserts the {@code hub_basketball_game} document if absent
     */
    @Bean
    CommandLineRunner seedEventHubs(
            EventRecoveryHubRepository eventHubs,
            @Value("${app.seed.enabled:true}") boolean seedEnabled
    ) {
        // Lambda body executes after startup with the app's command-line args.
        return args -> {
            // Skip when seeding is turned off, or when hubs already exist (idempotency guard).
            if (!seedEnabled || eventHubs.count() > 0) {
                return;
            }
            // Build the demo hub referenced by seeded found items via eventHubId="hub_basketball_game".
            EventRecoveryHub hub = new EventRecoveryHub();
            hub.setId("hub_basketball_game"); // stable id so found items/lost reports can link to it
            hub.setTenantId("pvhs"); // multi-tenant key: Pleasant Valley High School
            hub.setName("PVHS vs. Bettendorf Basketball Game");
            hub.setDescription("Lost & found recovery hub for the home basketball game.");
            hub.setEventType("athletics");
            hub.setStartTime("2026-03-14T18:00:00Z"); // event window start (ISO-8601 UTC)
            hub.setEndTime("2026-03-14T21:00:00Z"); // event window end (ISO-8601 UTC)
            hub.setStatus("active");
            // Campus zones the hub covers, matching the seeded gym/athletics zones.
            hub.setCampusZoneIds(List.of("zone_gym_bleachers", "zone_gym_entrance", "zone_athletics"));
            hub.setPublicEnabled(Boolean.TRUE); // visible on the public-facing hub view
            hub.setDisplayEnabled(Boolean.TRUE); // shown on display boards/kiosks
            hub.setCreatedDate("2026-03-10T10:00:00Z");
            hub.setUpdatedDate("2026-03-10T10:00:00Z");
            eventHubs.save(hub); // persist the hub document
        };
    }
}
