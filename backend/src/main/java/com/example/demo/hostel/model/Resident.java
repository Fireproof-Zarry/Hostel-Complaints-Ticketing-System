package com.example.demo.hostel.model;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * A roll number on the hostel's resident list. While the list has anyone on it, only
 * these students can use the student portal (their email is rollno@smail.iitm.ac.in).
 */
@Entity
@Table(name = "residents")
public class Resident implements Persistable<String> {

    /** Lower case, e.g. cs23b051. */
    @Id
    @Column(name = "roll_number", length = 8, nullable = false)
    private String rollNumber;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    /** Email of the admin who added it. */
    @Column(name = "added_by")
    private String addedBy;

    // The id is assigned, so Spring Data cannot tell a new row from a stored one;
    // without this, save() would SELECT before every INSERT
    @Transient
    private boolean isNew = true;

    protected Resident() {
    }

    public Resident(String rollNumber, String addedBy) {
        this.rollNumber = rollNumber;
        this.addedBy = addedBy;
        this.addedAt = LocalDateTime.now();
    }

    @Override
    public String getId() {
        return rollNumber;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    public String getRollNumber() { return rollNumber; }
    public LocalDateTime getAddedAt() { return addedAt; }
    public String getAddedBy() { return addedBy; }
}
