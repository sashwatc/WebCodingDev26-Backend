package com.FBLA.WebCodingDev26Backend.model;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Claim represents a person's request to recover a specific found item, including the
 * claimant's contact info, supporting evidence, the staff review/verification outcome, and
 * a post-pickup rating. Spring Data MongoDB document stored in the "claims" collection.
 * @JsonIgnoreProperties(ignoreUnknown = true) makes deserialization tolerant of extra JSON fields.
 *
 * Relationships: references the claimed {@link FoundItem} via {@link #foundItemId}, links to a
 * generated pickup pass via {@link #returnPassId}, and is the subject of {@link CaseMessage} threads.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Document(collection = "claims")
public class Claim {
    // MongoDB document primary key (@Id); maps to Mongo _id.
    @Id
    private String id;
    // Id of the FoundItem being claimed.
    private String foundItemId;
    // Denormalized title of the claimed found item (snapshot for display).
    private String foundItemTitle;
    // Name of the person making the claim.
    private String claimantName;
    // Email of the claimant (contact + identity).
    private String claimantEmail;
    // Phone number of the claimant.
    private String claimantPhone;
    // The claimant's stated reason/justification for the claim (alias: "reason").
    private String claimReason;
    // Free-text identifying details the claimant provides to prove ownership.
    private String identifyingDetails;
    // URL of a proof photo uploaded by the claimant.
    private String proofPhotoUrl;
    // Claimant's student id, if applicable.
    private String studentId;
    // Free-text describing when the claimant is available to pick up the item.
    private String pickupAvailability;
    // Workflow status of the claim (e.g. "under_review", "approved", "rejected", "completed").
    private String status;
    // Computed fraud/risk score for the claim (higher = riskier). Nullable until scored.
    private Integer riskScore;
    // List of risk indicator flags raised during review. Defaults to empty list.
    private List<String> riskFlags = new ArrayList<>();
    // Internal notes written by staff/admins reviewing the claim.
    private String adminNotes;
    // Identity (email) of the staff member who reviewed the claim.
    private String reviewedBy;
    // Timestamp (ISO-8601 string) when the claim was reviewed.
    private String reviewedAt;
    // Timestamp (ISO-8601 string) when the claimant confirmed they received the item.
    private String receivedConfirmedAt;
    // Id of the ReturnPass generated for picking up the item once the claim is approved.
    private String returnPassId;
    // Checklist of evidence items required/collected for verification. Defaults to empty list.
    private List<String> evidenceChecklist = new ArrayList<>();
    // Map of private verification question -> claimant's answer (ordered via LinkedHashMap).
    private Map<String, String> privateEvidenceResponses = new LinkedHashMap<>();
    // Summary text of the automated/staff verification outcome.
    private String verificationSummary;
    // Numeric verification score for the claim. Nullable until computed.
    private Integer verificationScore;
    // List of verification-related flags raised. Defaults to empty list.
    private List<String> verificationFlags = new ArrayList<>();
    // Star rating (1-5) the claimant gave for the recovery experience. Nullable until submitted.
    private Integer claimantRating;
    // Free-text review the claimant left about the experience.
    private String claimantReview;
    // Moderation status of the claimant's review (e.g. submitted/approved).
    private String reviewStatus;
    // Timestamp (ISO-8601 string) when the claimant submitted their review.
    private String reviewSubmittedAt;
    // Timestamp (ISO-8601 string) when staff reviewed/moderated the claimant's review.
    private String reviewReviewedAt;
    // Timestamp (ISO-8601 string) when this claim was created.
    private String createdDate;
    // Timestamp (ISO-8601 string) when this claim was last updated.
    private String updatedDate;
    // Flag marking this as seeded demo data (true) vs. a real claim. Nullable.
    private Boolean isDemo;

    // --- standard getters/setters (with a few aliases/null-guards noted below) ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFoundItemId() { return foundItemId; }
    public void setFoundItemId(String foundItemId) { this.foundItemId = foundItemId; }
    public String getFoundItemTitle() { return foundItemTitle; }
    public void setFoundItemTitle(String foundItemTitle) { this.foundItemTitle = foundItemTitle; }
    public String getClaimantName() { return claimantName; }
    public void setClaimantName(String claimantName) { this.claimantName = claimantName; }
    public String getClaimantEmail() { return claimantEmail; }
    public void setClaimantEmail(String claimantEmail) { this.claimantEmail = claimantEmail; }
    public String getClaimantPhone() { return claimantPhone; }
    public void setClaimantPhone(String claimantPhone) { this.claimantPhone = claimantPhone; }
    public String getClaimReason() { return claimReason; }
    public void setClaimReason(String claimReason) { this.claimReason = claimReason; }
    // Alias accessors: "reason" reads/writes the same underlying claimReason field (JSON compatibility).
    public String getReason() { return claimReason; }
    public void setReason(String reason) { this.claimReason = reason; }
    public String getIdentifyingDetails() { return identifyingDetails; }
    public void setIdentifyingDetails(String identifyingDetails) { this.identifyingDetails = identifyingDetails; }
    public String getProofPhotoUrl() { return proofPhotoUrl; }
    public void setProofPhotoUrl(String proofPhotoUrl) { this.proofPhotoUrl = proofPhotoUrl; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getPickupAvailability() { return pickupAvailability; }
    public void setPickupAvailability(String pickupAvailability) { this.pickupAvailability = pickupAvailability; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }
    public List<String> getRiskFlags() { return riskFlags; }
    // Null-guarded setter: substitutes an empty list when given null (collection never null).
    public void setRiskFlags(List<String> riskFlags) { this.riskFlags = riskFlags == null ? new ArrayList<>() : riskFlags; }
    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(String reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReceivedConfirmedAt() { return receivedConfirmedAt; }
    public void setReceivedConfirmedAt(String receivedConfirmedAt) { this.receivedConfirmedAt = receivedConfirmedAt; }
    public String getReturnPassId() { return returnPassId; }
    public void setReturnPassId(String returnPassId) { this.returnPassId = returnPassId; }
    public List<String> getEvidenceChecklist() { return evidenceChecklist; }
    // Null-guarded setter: substitutes an empty list when given null.
    public void setEvidenceChecklist(List<String> evidenceChecklist) { this.evidenceChecklist = evidenceChecklist == null ? new ArrayList<>() : evidenceChecklist; }
    public Map<String, String> getPrivateEvidenceResponses() { return privateEvidenceResponses; }
    // Null-guarded setter: substitutes an empty (ordered) map when given null.
    public void setPrivateEvidenceResponses(Map<String, String> privateEvidenceResponses) { this.privateEvidenceResponses = privateEvidenceResponses == null ? new LinkedHashMap<>() : privateEvidenceResponses; }
    public String getVerificationSummary() { return verificationSummary; }
    public void setVerificationSummary(String verificationSummary) { this.verificationSummary = verificationSummary; }
    public Integer getVerificationScore() { return verificationScore; }
    public void setVerificationScore(Integer verificationScore) { this.verificationScore = verificationScore; }
    public List<String> getVerificationFlags() { return verificationFlags; }
    // Null-guarded setter: substitutes an empty list when given null.
    public void setVerificationFlags(List<String> verificationFlags) { this.verificationFlags = verificationFlags == null ? new ArrayList<>() : verificationFlags; }
    public Integer getClaimantRating() { return claimantRating; }
    public void setClaimantRating(Integer claimantRating) { this.claimantRating = claimantRating; }
    public String getClaimantReview() { return claimantReview; }
    public void setClaimantReview(String claimantReview) { this.claimantReview = claimantReview; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public String getReviewSubmittedAt() { return reviewSubmittedAt; }
    public void setReviewSubmittedAt(String reviewSubmittedAt) { this.reviewSubmittedAt = reviewSubmittedAt; }
    public String getReviewReviewedAt() { return reviewReviewedAt; }
    public void setReviewReviewedAt(String reviewReviewedAt) { this.reviewReviewedAt = reviewReviewedAt; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
    public Boolean getIsDemo() { return isDemo; }
    public void setIsDemo(Boolean isDemo) { this.isDemo = isDemo; }
}
