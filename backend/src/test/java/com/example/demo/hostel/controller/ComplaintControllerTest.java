package com.example.demo.hostel.controller;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.service.ComplaintService;
import com.example.demo.hostel.validation.RoomLocationValidator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ComplaintControllerTest {

    private static final Jwt JWT = Jwt.withTokenValue("token")
        .header("alg", "none")
        .claim("email", "student@smail.iitm.ac.in")
        .build();

    @Test
    void acceptsRoomsMatchingTheirFloor() {
        ComplaintService complaintService = mock(ComplaintService.class);
        when(complaintService.createComplaint(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(new Complaint());
        ComplaintController controller = new ComplaintController(complaintService, new RoomLocationValidator());

        Map<String, String> floorRooms = Map.of(
            "Ground", "101",
            "First", "201",
            "Second", "301",
            "Third", "401",
            "Fourth", "501",
            "Fifth", "601",
            "Sixth", "701"
        );

        floorRooms.forEach((floor, room) ->
            assertEquals(
                HttpStatus.CREATED,
                controller.createComplaint(JWT, payload(floor, room)).getStatusCode(),
                floor
            )
        );
    }

    @Test
    void rejectsRoomsOutsideTheirFloorRange() {
        ComplaintService complaintService = mock(ComplaintService.class);
        ComplaintController controller = new ComplaintController(complaintService, new RoomLocationValidator());

        Map<String, String> floorRooms = Map.of(
            "Ground", "201",
            "First", "101",
            "Second", "401",
            "Third", "301",
            "Fourth", "601",
            "Fifth", "501",
            "Sixth", "801"
        );

        floorRooms.forEach((floor, room) ->
            assertEquals(
                HttpStatus.BAD_REQUEST,
                controller.createComplaint(JWT, payload(floor, room)).getStatusCode(),
                floor
            )
        );
        verifyNoInteractions(complaintService);
    }

    @Test
    void rejectsRoomNumbersThatAreNotThreeDigits() {
        ComplaintService complaintService = mock(ComplaintService.class);
        ComplaintController controller = new ComplaintController(complaintService, new RoomLocationValidator());

        assertEquals(
            HttpStatus.BAD_REQUEST,
            controller.createComplaint(JWT, payload("Ground", "10")).getStatusCode()
        );
        verifyNoInteractions(complaintService);
    }

    private static Map<String, String> payload(String floor, String room) {
        return Map.of(
            "title", "Test complaint",
            "description", "Test description",
            "category", "Electrical",
            "floor", floor,
            "room", room
        );
    }
}
