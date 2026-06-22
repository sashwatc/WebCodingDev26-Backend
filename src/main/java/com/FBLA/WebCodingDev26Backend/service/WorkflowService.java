package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class WorkflowService {
    private static final Set<String> CLAIM_STATUSES = Set.of(
            "submitted",
            "pending_review",
            "under_review",
            "need_more_info",
            "approved",
            "rejected",
            "completed"
    );
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-z0-9]+");

    private final FoundItemRepository foundItems;
    private final LostReportRepository lostReports;
    private final ClaimRepository claims;
    private final ClockService clock;

    public WorkflowService(
            FoundItemRepository foundItems,
            LostReportRepository lostReports,
            ClaimRepository claims,
            ClockService clock
    ) {
        this.foundItems = foundItems;
        this.lostReports = lostReports;
        this.claims = claims;
        this.clock = clock;
    }

    public LostReport syncMatchesForLostReport(LostReport report) {
        if (report == null || blank(report.getId())) {
            return report;
        }

        List<Map<String, Object>> matches = mergeMatches(report.getMatchedItems(), foundItems.findAll().stream()
                .map(item -> score(report, item))
                .filter(Objects::nonNull)
                .toList());

        report.setMatchedItems(new ArrayList<>(matches));
        if (!matches.isEmpty() && !Set.of("resolved", "closed").contains(normalize(report.getStatus()))) {
            report.setStatus("matched");
        }
        report.setUpdatedDate(clock.now());
        return lostReports.save(report);
    }

    public void syncMatchesForFoundItem(FoundItem foundItem) {
        if (foundItem == null || blank(foundItem.getId())) {
            return;
        }

        for (LostReport report : lostReports.findAll()) {
            Map<String, Object> explicitMatch = null;
            if (!blank(report.getId()) && report.getId().equals(firstNonBlank(foundItem.getLinkedLostReportId()))) {
                explicitMatch = match(foundItem.getId(), 100, List.of("finder response"), "finder_response");
            }

            Map<String, Object> aiMatch = score(report, foundItem);
            List<Map<String, Object>> nextMatches = new ArrayList<>();
            if (explicitMatch != null) {
                nextMatches.add(explicitMatch);
            }
            if (aiMatch != null) {
                nextMatches.add(aiMatch);
            }
            List<Map<String, Object>> matches = mergeMatches(report.getMatchedItems(), nextMatches);

            if (sameMatches(report.getMatchedItems(), matches)) {
                continue;
            }

            report.setMatchedItems(new ArrayList<>(matches));
            if (!matches.isEmpty() && !Set.of("resolved", "closed").contains(normalize(report.getStatus()))) {
                report.setStatus("matched");
            }
            report.setUpdatedDate(clock.now());
            lostReports.save(report);
        }
    }

    public boolean hasFoundItemReferences(String foundItemId) {
        if (blank(foundItemId)) {
            return false;
        }

        return !claims.findByFoundItemId(foundItemId).isEmpty() || lostReports.findAll().stream()
                .anyMatch(report -> safeMatches(report).stream()
                        .anyMatch(match -> foundItemId.equals(matchFoundItemId(match))));
    }

    public void validateClaim(Claim claim, Claim previousClaim) {
        if (claim == null) {
            throw new BadRequestException("Claim payload is required");
        }
        if (blank(claim.getFoundItemId())) {
            throw new BadRequestException("found_item_id is required");
        }
        if (blank(claim.getClaimantName())) {
            throw new BadRequestException("Claimant name is required");
        }
        if (blank(claim.getClaimantEmail())) {
            throw new BadRequestException("Claimant email is required");
        }
        if (blank(claim.getClaimReason())) {
            throw new BadRequestException("Claim reason is required");
        }
        if (!blank(claim.getStatus()) && !CLAIM_STATUSES.contains(claim.getStatus())) {
            throw new BadRequestException("Invalid claim status: " + claim.getStatus());
        }

        FoundItem foundItem = foundItems.findById(claim.getFoundItemId())
                .orElseThrow(() -> new NotFoundException("Claim must reference an existing Found Item"));
        if (Set.of("returned", "archived").contains(normalize(foundItem.getStatus())) && !"completed".equals(claim.getStatus())) {
            throw new BadRequestException("This Found Item is no longer available for claims");
        }
        if (previousClaim == null && Set.of("approved", "completed").contains(normalize(claim.getStatus()))) {
            throw new BadRequestException("New claims must be submitted before admin approval");
        }
        if ("approved".equals(claim.getStatus())) {
            boolean alreadyApproved = claims.findByFoundItemId(claim.getFoundItemId()).stream()
                    .anyMatch(existingClaim -> !Objects.equals(existingClaim.getId(), claim.getId())
                            && Set.of("approved", "completed").contains(normalize(existingClaim.getStatus())));
            if (alreadyApproved) {
                throw new BadRequestException("This Found Item already has an approved claim");
            }
        }
    }

    public void applyClaimStatusSideEffects(Claim claim, Claim previousClaim) {
        if (claim == null || blank(claim.getFoundItemId()) || Objects.equals(claim.getStatus(), previousClaim == null ? null : previousClaim.getStatus())) {
            return;
        }

        FoundItem item = foundItems.findById(claim.getFoundItemId()).orElse(null);
        if (item == null) {
            return;
        }

        if ("approved".equals(claim.getStatus())) {
            item.setStatus("claimed");
            item.setUpdatedDate(clock.now());
            foundItems.save(item);
            return;
        }

        if ("completed".equals(claim.getStatus())) {
            String completedAt = blank(claim.getReceivedConfirmedAt()) ? clock.now() : claim.getReceivedConfirmedAt();
            item.setStatus("returned");
            item.setClaimConfirmed(true);
            item.setClaimConfirmedAt(completedAt);
            item.setUpdatedDate(clock.now());
            foundItems.save(item);
            return;
        }

        if ("rejected".equals(claim.getStatus()) && previousClaim != null && "approved".equals(previousClaim.getStatus())) {
            boolean stillApproved = claims.findByFoundItemId(claim.getFoundItemId()).stream()
                    .anyMatch(existingClaim -> !Objects.equals(existingClaim.getId(), claim.getId())
                            && Set.of("approved", "completed").contains(normalize(existingClaim.getStatus())));
            if (!stillApproved) {
                item.setStatus("approved");
                item.setUpdatedDate(clock.now());
                foundItems.save(item);
            }
        }
    }

    private Map<String, Object> score(LostReport report, FoundItem item) {
        if (report == null || item == null || blank(item.getId())) {
            return null;
        }
        if ("lost".equals(normalize(item.getRecordType())) || Set.of("returned", "archived").contains(normalize(item.getStatus()))) {
            return null;
        }

        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (same(report.getCategory(), item.getCategory())) {
            score += 25;
            reasons.add("same category");
        }
        if (!blank(report.getColor()) && same(report.getColor(), item.getColor())) {
            score += 15;
            reasons.add("same color");
        }
        if (!blank(report.getBrand()) && same(report.getBrand(), item.getBrand())) {
            score += 20;
            reasons.add("same brand");
        }

        Set<String> reportTokens = tokens(String.join(" ",
                value(report.getTitle()),
                value(report.getDescription()),
                value(report.getExtraNotes())
        ));
        Set<String> itemTokens = tokens(String.join(" ",
                value(item.getTitle()),
                value(item.getDescription()),
                value(item.getDistinguishingFeatures()),
                item.getTags() == null ? "" : String.join(" ", item.getTags())
        ));
        Set<String> overlap = reportTokens.stream().filter(itemTokens::contains).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!overlap.isEmpty()) {
            score += Math.min(25, overlap.size() * 6);
            reasons.add("similar words: " + String.join(", ", overlap.stream().limit(4).toList()));
        }
        if (nearDates(report.getDateLost(), item.getDateFound())) {
            score += 10;
            reasons.add("dates are close");
        }
        if (!blank(report.getLocationLost()) && !blank(item.getLocationFound())
                && (containsIgnoreCase(report.getLocationLost(), item.getLocationFound())
                || containsIgnoreCase(item.getLocationFound(), report.getLocationLost()))) {
            score += 10;
            reasons.add("near reported location");
        }

        return score >= 35 ? match(item.getId(), Math.min(98, score), reasons, "ai_suggestion") : null;
    }

    private List<Map<String, Object>> mergeMatches(List<Object> currentMatches, List<Map<String, Object>> nextMatches) {
        Map<String, Map<String, Object>> byFoundItem = new LinkedHashMap<>();
        List<Object> combined = new ArrayList<>();
        if (currentMatches != null) {
            combined.addAll(currentMatches);
        }
        if (nextMatches != null) {
            combined.addAll(nextMatches);
        }

        for (Object rawMatch : combined) {
            Map<String, Object> normalized = normalizeMatch(rawMatch);
            if (normalized == null) {
                continue;
            }

            String foundItemId = String.valueOf(normalized.get("found_item_id"));
            Map<String, Object> existing = byFoundItem.get(foundItemId);
            if (existing == null || number(normalized.get("confidence")) >= number(existing.get("confidence"))) {
                List<String> reasons = new ArrayList<>();
                reasons.addAll(stringList(existing == null ? null : existing.get("reasons")));
                reasons.addAll(stringList(normalized.get("reasons")));
                normalized.put("reasons", reasons.stream().filter(reason -> !blank(reason)).distinct().toList());
                byFoundItem.put(foundItemId, normalized);
            }
        }

        return byFoundItem.values().stream()
                .sorted(Comparator.comparingInt(match -> -number(match.get("confidence"))))
                .limit(8)
                .toList();
    }

    private Map<String, Object> normalizeMatch(Object rawMatch) {
        if (rawMatch instanceof String foundItemId) {
            return match(foundItemId, 0, List.of(), "legacy_match");
        }
        if (!(rawMatch instanceof Map<?, ?> rawMap)) {
            return null;
        }

        String foundItemId = firstNonBlank(rawMap.get("found_item_id"), rawMap.get("foundItemId"));
        if (blank(foundItemId)) {
            return null;
        }

        return match(
                foundItemId,
                Math.max(0, Math.min(100, number(rawMap.get("confidence")))),
                stringList(rawMap.get("reasons")),
                value(firstNonBlank(rawMap.get("source"), "ai_suggestion")),
                value(firstNonBlank(rawMap.get("created_date"), rawMap.get("createdDate"), clock.now()))
        );
    }

    private Map<String, Object> match(String foundItemId, int confidence, List<String> reasons, String source) {
        return match(foundItemId, confidence, reasons, source, clock.now());
    }

    private Map<String, Object> match(String foundItemId, int confidence, List<String> reasons, String source, String createdDate) {
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("found_item_id", foundItemId);
        match.put("confidence", confidence);
        match.put("reasons", reasons == null ? List.of() : reasons.stream().filter(reason -> !blank(reason)).distinct().toList());
        match.put("source", blank(source) ? "ai_suggestion" : source);
        match.put("created_date", blank(createdDate) ? clock.now() : createdDate);
        return match;
    }

    private String matchFoundItemId(Object rawMatch) {
        if (rawMatch instanceof String foundItemId) {
            return foundItemId;
        }
        if (rawMatch instanceof Map<?, ?> rawMap) {
            return firstNonBlank(rawMap.get("found_item_id"), rawMap.get("foundItemId"));
        }
        return "";
    }

    private boolean sameMatches(List<Object> currentMatches, List<Map<String, Object>> nextMatches) {
        return Objects.equals(matchSignatures(mergeMatches(currentMatches, List.of())), matchSignatures(nextMatches));
    }

    private List<String> matchSignatures(List<Map<String, Object>> matches) {
        return matches.stream()
                .map(match -> value(match.get("found_item_id")) + ":" + number(match.get("confidence")) + ":" + value(match.get("source")))
                .toList();
    }

    private List<Object> safeMatches(LostReport report) {
        return report.getMatchedItems() == null ? List.of() : report.getMatchedItems();
    }

    private boolean nearDates(String left, String right) {
        try {
            return Math.abs(ChronoUnit.DAYS.between(LocalDate.parse(left), LocalDate.parse(right))) <= 3;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Set<String> tokens(String text) {
        return TOKEN_SPLIT.splitAsStream(normalize(text))
                .filter(token -> token.length() > 2)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private int number(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private boolean same(String left, String right) {
        return !blank(left) && normalize(left).equals(normalize(right));
    }

    private boolean containsIgnoreCase(String text, String part) {
        return normalize(text).contains(normalize(part));
    }

    private String firstNonBlank(Object... values) {
        for (Object next : values) {
            String text = value(next);
            if (!blank(text)) {
                return text;
            }
        }
        return "";
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String normalize(String value) {
        return value(value).trim().toLowerCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
