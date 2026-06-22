package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.mapper.PatchMapper;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.PreventionAlert;
import com.FBLA.WebCodingDev26Backend.repository.CampusZoneRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.PreventionAlertRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LossSentinelService {
    private final PreventionAlertRepository alerts;
    private final LostReportRepository lostReports;
    private final CampusZoneRepository zones;
    private final PatchMapper mapper;
    private final ClockService clock;

    public LossSentinelService(
            PreventionAlertRepository alerts,
            LostReportRepository lostReports,
            CampusZoneRepository zones,
            PatchMapper mapper,
            ClockService clock
    ) {
        this.alerts = alerts;
        this.lostReports = lostReports;
        this.zones = zones;
        this.mapper = mapper;
        this.clock = clock;
    }

    public List<PreventionAlert> list() {
        return alerts.findAll();
    }

    public List<PreventionAlert> recompute() {
        LocalDate today = LocalDate.parse(clock.now().substring(0, 10));
        LocalDate recentStart = today.minusDays(7);
        LocalDate baselineStart = today.minusDays(37);
        Map<GroupKey, Counts> grouped = new LinkedHashMap<>();
        Map<AlertKey, PreventionAlert> existingByKey = new LinkedHashMap<>();
        for (PreventionAlert alert : alerts.findAll()) {
            existingByKey.putIfAbsent(alertKey(alert), alert);
        }

        for (LostReport report : lostReports.findAll()) {
            LocalDate date = parseDate(report.getDateLost());
            if (date == null || report.getCategory() == null || report.getCategory().isBlank()) {
                continue;
            }
            GroupKey key = new GroupKey(valueOrDefault(report.getCampusZoneId(), "unknown"), report.getCategory());
            Counts counts = grouped.computeIfAbsent(key, ignored -> new Counts());
            if (!date.isBefore(recentStart)) {
                counts.observed++;
            } else if (!date.isBefore(baselineStart)) {
                counts.baselineRaw++;
            }
        }

        for (Map.Entry<GroupKey, Counts> entry : grouped.entrySet()) {
            Counts counts = entry.getValue();
            int baseline = Math.max(1, (int) Math.ceil(counts.baselineRaw / 4.0));
            if (counts.observed < 4 || counts.observed < baseline * 2) {
                continue;
            }

            AlertKey alertKey = new AlertKey(
                    "pvhs",
                    "volume_spike",
                    entry.getKey().campusZoneId(),
                    entry.getKey().category(),
                    recentStart.toString(),
                    today.toString()
            );
            PreventionAlert alert = existingByKey.get(alertKey);
            if (alert != null && isResolved(alert.getStatus())) {
                continue;
            }
            if (alert == null) {
                alert = new PreventionAlert();
                alert.setId("alert_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
                alert.setCreatedDate(clock.now());
            }
            alert.setTenantId("pvhs");
            alert.setTitle("Unusual increase detected");
            alert.setAlertType("volume_spike");
            alert.setSeverity(counts.observed >= baseline * 3 ? "high" : "medium");
            alert.setCampusZoneId(entry.getKey().campusZoneId());
            alert.setCategory(entry.getKey().category());
            alert.setTimeWindowStart(recentStart.toString());
            alert.setTimeWindowEnd(today.toString());
            alert.setBaselineCount(baseline);
            alert.setObservedCount(counts.observed);
            alert.setReasons(List.of(counts.observed + " " + entry.getKey().category() + " reports in the recent window.", "Recent volume is at least 2x the historical baseline."));
            alert.setSuggestedActions(List.of("Check likely benches, shelves, and office intake bins.", "Create recovery mission for the affected zone.", "Post a reminder near the zone exit."));
            alert.setStatus(valueOrDefault(alert.getStatus(), "open"));
            alerts.save(alert);
        }

        return alerts.findAll();
    }

    public PreventionAlert update(String id, Map<String, Object> data, String adminEmail) {
        PreventionAlert existing = alerts.findById(id).orElseThrow(() -> new NotFoundException("Prevention alert not found"));
        PreventionAlert patch = mapper.convert(data, PreventionAlert.class);
        mapper.copyPresent(data, patch, existing, "id", "createdDate");
        if (isResolved(existing.getStatus()) && (existing.getResolvedDate() == null || existing.getResolvedDate().isBlank())) {
            existing.setResolvedDate(clock.now());
            existing.setResolvedBy(adminEmail);
        }
        return alerts.save(existing);
    }

    private boolean isResolved(String status) {
        return status != null && (status.equalsIgnoreCase("resolved") || status.equalsIgnoreCase("dismissed"));
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private AlertKey alertKey(PreventionAlert alert) {
        return new AlertKey(
                valueOrDefault(alert.getTenantId(), "pvhs"),
                valueOrDefault(alert.getAlertType(), "volume_spike"),
                valueOrDefault(alert.getCampusZoneId(), "unknown"),
                valueOrDefault(alert.getCategory(), ""),
                valueOrDefault(alert.getTimeWindowStart(), ""),
                valueOrDefault(alert.getTimeWindowEnd(), "")
        );
    }

    private record GroupKey(String campusZoneId, String category) {
    }

    private record AlertKey(String tenantId, String alertType, String campusZoneId, String category, String timeWindowStart, String timeWindowEnd) {
    }

    private static class Counts {
        private int observed;
        private int baselineRaw;
    }
}
