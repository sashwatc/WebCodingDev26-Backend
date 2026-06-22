package com.FBLA.WebCodingDev26Backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "return_passes")
public class ReturnPass {
    @Id
    private String id;
    @Indexed
    private String claimId;
    @Indexed
    private String foundItemId;
    private String claimantEmail;
    private String pickupWindow;
    private String pickupLocation;
    private String status;
    private String oneTimeCode;
    private String token;
    private String expiresAt;
    private String redeemedAt;
    private String redeemedBy;
    private String createdDate;
    private String updatedDate;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getClaimId() { return claimId; }
    public void setClaimId(String claimId) { this.claimId = claimId; }
    public String getFoundItemId() { return foundItemId; }
    public void setFoundItemId(String foundItemId) { this.foundItemId = foundItemId; }
    public String getClaimantEmail() { return claimantEmail; }
    public void setClaimantEmail(String claimantEmail) { this.claimantEmail = claimantEmail; }
    public String getPickupWindow() { return pickupWindow; }
    public void setPickupWindow(String pickupWindow) { this.pickupWindow = pickupWindow; }
    public String getPickupLocation() { return pickupLocation; }
    public void setPickupLocation(String pickupLocation) { this.pickupLocation = pickupLocation; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOneTimeCode() { return oneTimeCode; }
    public void setOneTimeCode(String oneTimeCode) { this.oneTimeCode = oneTimeCode; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
    public String getRedeemedAt() { return redeemedAt; }
    public void setRedeemedAt(String redeemedAt) { this.redeemedAt = redeemedAt; }
    public String getRedeemedBy() { return redeemedBy; }
    public void setRedeemedBy(String redeemedBy) { this.redeemedBy = redeemedBy; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
}
