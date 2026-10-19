package com.example.demo.hostel.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "upvotes")
public class Upvote {

    @EmbeddedId
    private UpvoteId id = new UpvoteId();

    // @MapsId fills the matching UpvoteId field from the associated entity's id
    @MapsId("complaintId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Complaint complaint;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Upvote() {
    }

    public Upvote(Complaint complaint, User user) {
        this.complaint = complaint;
        this.user = user;
    }

    // Getters and Setters
    public UpvoteId getId() { return id; }
    public void setId(UpvoteId id) { this.id = id; }
    public Complaint getComplaint() { return complaint; }
    public void setComplaint(Complaint complaint) { this.complaint = complaint; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
