package com.example.demo.hostel.dto.analytics;

import java.util.List;

/** Everything the analytics dashboard shows, from one request. */
public class AnalyticsResponseDTO {

    private OverviewDTO overview;
    private List<CategoryAnalyticsDTO> categoryBreakdown;
    private List<FloorAnalyticsDTO> floorBreakdown;
    private List<RecurringIssueDTO> recurringIssues;
    private List<MonthlyTrendDTO> monthlyTrends;

    public OverviewDTO getOverview() {
        return overview;
    }

    public void setOverview(OverviewDTO overview) {
        this.overview = overview;
    }

    public List<CategoryAnalyticsDTO> getCategoryBreakdown() {
        return categoryBreakdown;
    }

    public void setCategoryBreakdown(List<CategoryAnalyticsDTO> categoryBreakdown) {
        this.categoryBreakdown = categoryBreakdown;
    }

    public List<FloorAnalyticsDTO> getFloorBreakdown() {
        return floorBreakdown;
    }

    public void setFloorBreakdown(List<FloorAnalyticsDTO> floorBreakdown) {
        this.floorBreakdown = floorBreakdown;
    }

    public List<RecurringIssueDTO> getRecurringIssues() {
        return recurringIssues;
    }

    public void setRecurringIssues(List<RecurringIssueDTO> recurringIssues) {
        this.recurringIssues = recurringIssues;
    }

    public List<MonthlyTrendDTO> getMonthlyTrends() {
        return monthlyTrends;
    }

    public void setMonthlyTrends(List<MonthlyTrendDTO> monthlyTrends) {
        this.monthlyTrends = monthlyTrends;
    }
}