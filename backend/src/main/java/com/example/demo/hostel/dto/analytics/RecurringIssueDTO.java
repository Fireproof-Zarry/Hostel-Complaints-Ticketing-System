package com.example.demo.hostel.dto.analytics;

/**
 * The same category reported at least twice in one place. type is ROOM, or COMMUNITY_AREA
 * for a community area.
 */
public class RecurringIssueDTO {

    // ROOM or COMMUNITY_AREA
    private String type;

    private String floor;
    private String roomNumber;
    private String wing;
    private String category;
    private long count;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFloor() {
        return floor;
    }

    public void setFloor(String floor) {
        this.floor = floor;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public String getWing() {
        return wing;
    }

    public void setWing(String wing) {
        this.wing = wing;
    }

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