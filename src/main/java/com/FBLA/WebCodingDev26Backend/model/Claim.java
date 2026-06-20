package com.FBLA.WebCodingDev26Backend.model;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@JsonIgnoreProperties(ignoreUnknown = true)
@Document(collection = "claims")
public class Claim {
    @Id
    private String id;
    private String foundItemId;
    private String foundItemTitle;
    private String claimantName;
    private String claimantEmail;
    private String claimantPhone;
    private String claimReason;
    private String identifyingDetails;
    private String proofPhotoUrl;
    private String studentId;
    private String pickupAvailability;
    private String status;
    private Integer riskScore;
    private List<String> riskFlags = new ArrayList<>();
    private String adminNotes;
    private String reviewedBy;
    private String reviewedAt;
    private String receivedConfirmedAt;
    private Integer claimantRating;
    private String claimantReview;
    private String reviewStatus;
    private String reviewSubmittedAt;
    private String reviewReviewedAt;
    private String createdDate;
    private String updatedDate;

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
    public void setRiskFlags(List<String> riskFlags) { this.riskFlags = riskFlags == null ? new ArrayList<>() : riskFlags; }
    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(String reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReceivedConfirmedAt() { return receivedConfirmedAt; }
    public void setReceivedConfirmedAt(String receivedConfirmedAt) { this.receivedConfirmedAt = receivedConfirmedAt; }
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
}
