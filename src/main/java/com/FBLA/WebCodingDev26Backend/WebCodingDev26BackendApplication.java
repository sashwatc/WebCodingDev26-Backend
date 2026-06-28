package com.FBLA.WebCodingDev26Backend;

import org.springframework.boot.SpringApplication; // bootstraps and runs the Spring application context
import org.springframework.boot.autoconfigure.SpringBootApplication; // meta-annotation enabling auto-configuration + component scanning

/**
 * Application entry point for the WebCodingDev26 (PVHS Recovery Mesh) Spring Boot backend.
 *
 * <p>This is the root configuration class for the whole service. The
 * {@link SpringBootApplication} annotation is a convenience composite of
 * {@code @Configuration}, {@code @EnableAutoConfiguration} and
 * {@code @ComponentScan}, so placing it here (in the base package
 * {@code com.FBLA.WebCodingDev26Backend}) tells Spring to scan this package and
 * everything beneath it — picking up the controllers, services, repositories,
 * model/DTO classes, and the {@code config} classes (CORS, Jackson, seed data,
 * event-hub seeding) — and to wire them together automatically.</p>
 *
 * <p>It owns no business logic itself; it simply launches the container that
 * hosts all the other components.</p>
 */
@SpringBootApplication
public class WebCodingDev26BackendApplication {

	/**
	 * JVM entry point. Boots the Spring application context, which triggers
	 * auto-configuration, component scanning, and the embedded web server, then
	 * runs any {@code CommandLineRunner} beans (e.g. the data seeders).
	 *
	 * @param args command-line arguments forwarded to Spring Boot (e.g. property overrides)
	 */
	public static void main(String[] args) {
		// Start the embedded server and initialize the full application context.
		SpringApplication.run(WebCodingDev26BackendApplication.class, args);
	}

}
