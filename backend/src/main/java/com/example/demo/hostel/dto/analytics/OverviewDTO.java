package com.example.demo.hostel.dto.analytics;

public class OverviewDTO {

    private long totalComplaints;

    private long pending;
    private long assigned;
    private long inProgress;
    private long resolved;
    private long rejected;

    private long unresolved;

    private Double resolutionRate;
    private Double averageResolutionTimeHours;

    public long getTotalComplaints() {
        return totalComplaints;
    }

    public void setTotalComplaints(long totalComplaints) {
        this.totalComplaints = totalComplaints;
    }

    public long getPending() {
        return pending;
    }

    public void setPending(long pending) {
        this.pending = pending;
    }

    public long getAssigned() {
        return assigned;
    }

    public void setAssigned(long assigned) {
        this.assigned = assigned;
    }

    public long getInProgress() {
        return inProgress;
    }

    public void setInProgress(long inProgress) {
        this.inProgress = inProgress;
    }

    public long getResolved() {
        return resolved;
    }

    public void setResolved(long resolved) {
        this.resolved = resolved;
    }

    public long getRejected() {
        return rejected;
    }

    public void setRejected(long rejected) {
        this.rejected = rejected;
    }

    public long getUnresolved() {
        return unresolved;
    }

    public void setUnresolved(long unresolved) {
        this.unresolved = unresolved;
    }

    public Double getResolutionRate() {
        return resolutionRate;
    }

    public void setResolutionRate(Double resolutionRate) {
        this.resolutionRate = resolutionRate;
    }

    public Double getAverageResolutionTimeHours() {
        return averageResolutionTimeHours;
    }

    public void setAverageResolutionTimeHours(Double averageResolutionTimeHours) {
        this.averageResolutionTimeHours = averageResolutionTimeHours;
    }
}