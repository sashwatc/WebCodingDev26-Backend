package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.SupportTicket;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the {@link SupportTicket} entity (help-desk support tickets).
 * Extending {@link MongoRepository} auto-generates the standard CRUD operations;
 * only the custom queries below are declared.
 */
public interface SupportTicketRepository extends MongoRepository<SupportTicket, String> {
    // Derived query: finds the single ticket whose "ticketNumber" equals the argument.
    // Returns Optional.empty() if no ticket has that number.
    Optional<SupportTicket> findByTicketNumber(String ticketNumber);

    // Derived query: returns all tickets whose "status" equals the argument
    // (e.g. all OPEN tickets), in unspecified order.
    List<SupportTicket> findByStatus(String status);

    // Derived query: returns ALL tickets (no filter), sorted by "createdAt" descending
    // (newest first) — the full ticket list most-recent-first.
    List<SupportTicket> findAllByOrderByCreatedAtDesc();
}
