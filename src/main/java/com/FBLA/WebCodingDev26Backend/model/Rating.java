package com.FBLA.WebCodingDev26Backend.model;

import jakarta.persistence.Embeddable;

@Embeddable
public class Rating {
    private String claimId;
    private Integer rating;
    private String review;
    private String claimantName;
    private String reviewerEmail;
    private String reviewStatus;
    private String reviewSubmittedAt;
    private String reviewReviewedAt;

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
