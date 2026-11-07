package com.example.demo.hostel.dto.analytics;

/** Complaints on one floor. */
public class FloorAnalyticsDTO {

    private String floor;
    private long count;

    public String getFloor() {
        return floor;
    }

    public void setFloor(String floor) {
        this.floor = floor;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}