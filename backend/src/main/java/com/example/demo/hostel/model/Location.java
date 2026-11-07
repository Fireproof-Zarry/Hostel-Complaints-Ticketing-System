package com.example.demo.hostel.model;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Where a complaint is: a room (floor and room number) or a community area (floor and
 * wing or area name). Complaints in the same place share a row.
 */
@Entity
@Table(name = "locations")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "location_id")
    private UUID id;

    @Column(nullable = false)
    private String floor;

    @Column(name = "room_number")
    private String roomNumber;

    private String wing;

    @Column(name = "is_community_area", nullable = false)
    private boolean communityArea;

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getFloor() { return floor; }
    public void setFloor(String floor) { this.floor = floor; }
    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public String getWing() { return wing; }
    public void setWing(String wing) { this.wing = wing; }
    public boolean isCommunityArea() { return communityArea; }
    public void setCommunityArea(boolean communityArea) { this.communityArea = communityArea; }
}
