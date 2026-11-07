package com.example.demo.hostel.dto.analytics;

/** Complaints filed and resolved in one month, written as yyyy-MM. */
public class MonthlyTrendDTO {

    // Format: yyyy-MM
    private String month;

    private long created;
    private long resolved;

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public long getResolved() {
        return resolved;
    }

    public void setResolved(long resolved) {
        this.resolved = resolved;
    }
}