package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.exception.UnsupportedEntityException;
import com.FBLA.WebCodingDev26Backend.mapper.PatchMapper;
import com.FBLA.WebCodingDev26Backend.model.AuditLog;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.Notification;
import com.FBLA.WebCodingDev26Backend.repository.AuditLogRepository;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Service;

@Service
public class GenericEntityService {
    private final LostReportRepository lostReports;
    private final ClaimRepository claims;
    private final NotificationRepository notifications;
    private final AuditLogRepository auditLogs;
    private final PatchMapper mapper;
    private final ClockService clock;
    private final WorkflowService workflow;

    public GenericEntityService(
            LostReportRepository lostReports,
            ClaimRepository claims,
            NotificationRepository notifications,
            AuditLogRepository auditLogs,
            PatchMapper mapper,
            ClockService clock,
            WorkflowService workflow
    ) {
        this.lostReports = lostReports;
        this.claims = claims;
        this.notifications = notifications;
        this.auditLogs = auditLogs;
        this.mapper = mapper;
        this.clock = clock;
        this.workflow = workflow;
    }

    public List<?> list(String entityName) {
        return adapter(entityName).repository().findAll();
    }

    public Object create(String entityName, Map<String, Object> data) {
        EntityAdapter<?> adapter = adapter(entityName);
        Object entity = mapper.convert(data, adapter.type());
        applyCreateDefaults(entity, adapter.prefix());
        if (entity instanceof Claim claim) {
            workflow.validateClaim(claim, null);
        }
        Object saved = save(adapter, entity);
        if (saved instanceof LostReport lostReport) {
            return workflow.syncMatchesForLostReport(lostReport);
        }
        return saved;
    }

    public Object update(String entityName, String id, Map<String, Object> data) {
        EntityAdapter<?> adapter = adapter(entityName);
        Object existing = adapter.repository().findById(id).orElseThrow(() -> new NotFoundException(entityName + " not found"));
        Object previous = mapper.convert(Map.of(), adapter.type());
        mapper.copyNonNull(existing, previous);
        Object patch = mapper.convert(data, adapter.type());
        mapper.copyPresent(data, patch, existing, "id", "createdDate");
        applyUpdateTimestamp(existing);
        if (existing instanceof Claim claim) {
            workflow.validateClaim(claim, (Claim) previous);
        }
        Object saved = save(adapter, existing);
        if (saved instanceof Claim claim) {
            workflow.applyClaimStatusSideEffects(claim, (Claim) previous);
        }
        if (saved instanceof LostReport lostReport) {
            return workflow.syncMatchesForLostReport(lostReport);
        }
        return saved;
    }

    public boolean delete(String entityName, String id) {
        EntityAdapter<?> adapter = adapter(entityName);
        if (!adapter.repository().existsById(id)) {
            throw new NotFoundException(entityName + " not found");
        }
        adapter.repository().deleteById(id);
        return true;
    }

    private EntityAdapter<?> adapter(String entityName) {
        return switch (entityName) {
            case "LostReport" -> new EntityAdapter<>(lostReports, LostReport.class, "lost");
            case "Claim" -> new EntityAdapter<>(claims, Claim.class, "claim");
            case "Notification" -> new EntityAdapter<>(notifications, Notification.class, "notif");
            case "AuditLog" -> new EntityAdapter<>(auditLogs, AuditLog.class, "audit");
            default -> throw new UnsupportedEntityException(entityName);
        };
    }

    private <T> T save(EntityAdapter<T> adapter, Object entity) {
        return adapter.repository().save(adapter.type().cast(entity));
    }

    private void applyCreateDefaults(Object entity, String prefix) {
        String now = clock.now();
        if (entity instanceof LostReport lostReport) {
            lostReport.setId(valueOrGenerated(lostReport.getId(), prefix));
            lostReport.setStatus(valueOrDefault(lostReport.getStatus(), "open"));
            lostReport.setUrgency(valueOrDefault(lostReport.getUrgency(), "medium"));
            lostReport.setCreatedDate(valueOrDefault(lostReport.getCreatedDate(), now));
            lostReport.setUpdatedDate(valueOrDefault(lostReport.getUpdatedDate(), now));
        } else if (entity instanceof Claim claim) {
            claim.setId(valueOrGenerated(claim.getId(), prefix));
            claim.setStatus(valueOrDefault(claim.getStatus(), "submitted"));
            claim.setRiskScore(claim.getRiskScore() == null ? 0 : claim.getRiskScore());
            claim.setCreatedDate(valueOrDefault(claim.getCreatedDate(), now));
            claim.setUpdatedDate(valueOrDefault(claim.getUpdatedDate(), now));
        } else if (entity instanceof Notification notification) {
            notification.setId(valueOrGenerated(notification.getId(), prefix));
            notification.setIsRead(Boolean.TRUE.equals(notification.getIsRead()));
            notification.setCreatedDate(valueOrDefault(notification.getCreatedDate(), now));
            notification.setUpdatedDate(valueOrDefault(notification.getUpdatedDate(), now));
        } else if (entity instanceof AuditLog auditLog) {
            auditLog.setId(valueOrGenerated(auditLog.getId(), prefix));
            auditLog.setCreatedDate(valueOrDefault(auditLog.getCreatedDate(), now));
        }
    }

    private void applyUpdateTimestamp(Object entity) {
        if (entity instanceof LostReport lostReport) {
            lostReport.setUpdatedDate(clock.now());
        } else if (entity instanceof Claim claim) {
            claim.setUpdatedDate(clock.now());
        } else if (entity instanceof Notification notification) {
            notification.setUpdatedDate(clock.now());
        }
    }

    private String valueOrGenerated(String value, String prefix) {
        return value == null || value.isBlank() ? prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) : value;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record EntityAdapter<T>(MongoRepository<T, String> repository, Class<T> type, String prefix) {
    }
}
