package com.FBLA.WebCodingDev26Backend.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Claim {
    @Id
    private String id;
    private String foundItemId;
    private String claimantName;
    private String claimantEmail;
    private String claimantPhone;
    @Lob
    private String claimReason;
    @Lob
    private String identifyingDetails;
    @Column(length = 4000)
    private String proofPhotoUrl;
    private String status;
    private Integer riskScore;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "claim_risk_flags", joinColumns = @JoinColumn(name = "claim_id"))
    @Column(name = "risk_flag")
    private List<String> riskFlags = new ArrayList<>();
    @Lob
    private String adminNotes;
    private String reviewedBy;
    private String reviewedAt;
    private String createdDate;
    private String updatedDate;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFoundItemId() { return foundItemId; }
    public void setFoundItemId(String foundItemId) { this.foundItemId = foundItemId; }
    public String getClaimantName() { return claimantName; }
    public void setClaimantName(String claimantName) { this.claimantName = claimantName; }
    public String getClaimantEmail() { return claimantEmail; }
    public void setClaimantEmail(String claimantEmail) { this.claimantEmail = claimantEmail; }
    public String getClaimantPhone() { return claimantPhone; }
    public void setClaimantPhone(String claimantPhone) { this.claimantPhone = claimantPhone; }
    public String getClaimReason() { return claimReason; }
    public void setClaimReason(String claimReason) { this.claimReason = claimReason; }
    public String getIdentifyingDetails() { return identifyingDetails; }
    public void setIdentifyingDetails(String identifyingDetails) { this.identifyingDetails = identifyingDetails; }
    public String getProofPhotoUrl() { return proofPhotoUrl; }
    public void setProofPhotoUrl(String proofPhotoUrl) { this.proofPhotoUrl = proofPhotoUrl; }
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
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
}
