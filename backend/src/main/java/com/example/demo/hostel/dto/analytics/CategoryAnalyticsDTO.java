package com.example.demo.hostel.dto.analytics;

/** Complaints in one category; categories with none are included with a count of 0. */
public class CategoryAnalyticsDTO {

    private String category;
    private long count;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}