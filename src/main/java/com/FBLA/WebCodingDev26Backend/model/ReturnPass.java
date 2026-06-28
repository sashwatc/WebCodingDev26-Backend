package com.FBLA.WebCodingDev26Backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A "return pass" — a redeemable pickup credential issued to a claimant once their claim is
 * approved, used to securely collect their item at a pickup location.
 *
 * <p>Persisted as a MongoDB document in the "return_passes" collection (mapped via
 * {@code @Document}). It carries the pickup window/location plus security material (a
 * one-time code, a hashed PIN and a token) and records redemption.</p>
 *
 * <p>Related models: links to a claim via the indexed {@code claimId} and to the found item
 * via the indexed {@code foundItemId}; addressed to the claimant by {@code claimantEmail}.</p>
 */
@Document(collection = "return_passes")
public class ReturnPass {
    /** MongoDB document primary key (auto-generated string id). */
    @Id
    private String id;
    /** Id of the approved claim this pass belongs to; indexed for lookup by claim. */
    @Indexed
    private String claimId;
    /** Id of the found item being returned; indexed for lookup by item. */
    @Indexed
    private String foundItemId;
    /** Email of the claimant entitled to redeem this pass. */
    private String claimantEmail;
    /** Scheduled pickup window (e.g. date/time range) for collecting the item. */
    private String pickupWindow;
    /** Location where the item is to be picked up. */
    private String pickupLocation;
    /** Lifecycle status of the pass, e.g. "active", "redeemed", "expired", "archived". */
    private String status;
    /** One-time code shown to/entered by the claimant to verify pickup. */
    private String oneTimeCode;
    /** Hashed PIN used to authenticate the claimant at pickup (never stored in clear). */
    private String pinHash;
    /** Opaque token (e.g. for a pickup link/QR) identifying this pass. */
    private String token;
    /** Timestamp (ISO string) after which the pass is no longer valid. */
    private String expiresAt;
    /** Timestamp (ISO string) when the pass was redeemed; null until pickup occurs. */
    private String redeemedAt;
    /** Identifier (e.g. staff email) of who processed the redemption; null until redeemed. */
    private String redeemedBy;
    /** Timestamp (ISO string) when the pass was created/issued. */
    private String createdDate;
    /** Timestamp (ISO string) when the pass was last updated. */
    private String updatedDate;
    /** Flag marking this as demo/seed data rather than a real production pass. */
    private Boolean isDemo;

    // --- Trivial getters/setters: plain field accessors with no extra logic. ---
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
    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }
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
    public Boolean getIsDemo() { return isDemo; }
    public void setIsDemo(Boolean isDemo) { this.isDemo = isDemo; }
}
