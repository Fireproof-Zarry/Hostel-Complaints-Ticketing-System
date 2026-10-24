package com.example.demo.hostel.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "complaints")
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    private String assignedTo; 

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    // When the student is available for a worker visit, e.g. "Mon: 2 PM - 5 PM"
    @Column(name = "available_time_slot", length = 500)
    private String availableTimeSlot;

    // Student is away during working hours and leaves the room key with security
    @Column(name = "key_with_security")
    private Boolean keyWithSecurity = false;

    // Soft-archived by ComplaintArchivalService once resolved/rejected for 30 days;
    // archived complaints are hidden from the admin dashboard
    @Column(name = "is_archived", nullable = false)
    private boolean isArchived = false;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    @JsonIgnore
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    @JsonIgnore
    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getAvailableTimeSlot() { return availableTimeSlot; }
    public void setAvailableTimeSlot(String availableTimeSlot) { this.availableTimeSlot = availableTimeSlot; }
    public boolean isKeyWithSecurity() { return Boolean.TRUE.equals(keyWithSecurity); }
    public void setKeyWithSecurity(boolean keyWithSecurity) { this.keyWithSecurity = keyWithSecurity; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) {
        this.status = status;
        // Reopening a complaint brings it back onto the dashboard
        if (status != Status.RESOLVED && status != Status.REJECTED) {
            this.isArchived = false;
        }
    }
    public boolean isArchived() { return isArchived; }
    public void setArchived(boolean archived) { this.isArchived = archived; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }

    // Flattened read-only views that keep the existing JSON response shape
    @JsonProperty("category")
    public String getCategoryName() { return category == null ? null : category.getName(); }
    @JsonProperty("floor")
    public String getFloor() { return location == null ? null : location.getFloor(); }
    @JsonProperty("room")
    public String getRoom() { return location == null ? null : location.getRoomNumber(); }
    @JsonProperty("wing")
    public String getWing() { return location == null ? null : location.getWing(); }
    @JsonProperty("communityArea")
    public boolean isCommunityArea() { return location != null && location.isCommunityArea(); }
}
