package com.FBLA.WebCodingDev26Backend.model;

/**
 * A user-submitted rating/review left for a completed claim (e.g. rating the lost-and-found
 * service experience after picking up an item).
 *
 * <p>This is a plain POJO value object: it carries no {@code @Document}/{@code @Id}
 * persistence annotations, so it is not a standalone MongoDB collection. It is used as a
 * DTO / embedded value tied to a claim via {@code claimId}.</p>
 */
public class Rating {
    /** Id of the claim this rating is associated with. */
    private String claimId;
    /** Numeric star rating value (e.g. 1-5). */
    private Integer rating;
    /** Free-text review/comment left by the reviewer. */
    private String review;
    /** Display name of the claimant who submitted the rating. */
    private String claimantName;
    /** Email of the user who submitted the review. */
    private String reviewerEmail;
    /** Moderation/lifecycle status of the review, e.g. "pending", "approved", "rejected". */
    private String reviewStatus;
    /** Timestamp (ISO string) when the review was submitted by the user. */
    private String reviewSubmittedAt;
    /** Timestamp (ISO string) when the review was moderated/reviewed by staff. */
    private String reviewReviewedAt;

    // --- Trivial getters/setters: plain field accessors with no extra logic. ---
    public String getClaimId() {
        return claimId;
    }

    public void setClaimId(String claimId) {
        this.claimId = claimId;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getReview() {
        return review;
    }

    public void setReview(String review) {
        this.review = review;
    }

    public String getClaimantName() {
        return claimantName;
    }

    public void setClaimantName(String claimantName) {
        this.claimantName = claimantName;
    }

    public String getReviewerEmail() {
        return reviewerEmail;
    }

    public void setReviewerEmail(String reviewerEmail) {
        this.reviewerEmail = reviewerEmail;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewSubmittedAt() {
        return reviewSubmittedAt;
    }

    public void setReviewSubmittedAt(String reviewSubmittedAt) {
        this.reviewSubmittedAt = reviewSubmittedAt;
    }

    public String getReviewReviewedAt() {
        return reviewReviewedAt;
    }

    public void setReviewReviewedAt(String reviewReviewedAt) {
        this.reviewReviewedAt = reviewReviewedAt;
    }
}
