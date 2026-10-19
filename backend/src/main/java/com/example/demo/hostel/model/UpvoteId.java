package com.example.demo.hostel.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UpvoteId implements Serializable {

    @Column(name = "complaint_id")
    private Long complaintId;

    @Column(name = "user_id")
    private String userId;

    public UpvoteId() {
    }

    public UpvoteId(Long complaintId, String userId) {
        this.complaintId = complaintId;
        this.userId = userId;
    }

    // Getters and Setters
    public Long getComplaintId() { return complaintId; }
    public void setComplaintId(Long complaintId) { this.complaintId = complaintId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UpvoteId other)) return false;
        return Objects.equals(complaintId, other.complaintId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(complaintId, userId);
    }
}
