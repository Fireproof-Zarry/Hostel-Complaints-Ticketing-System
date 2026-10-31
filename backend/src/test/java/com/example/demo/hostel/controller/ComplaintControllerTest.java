package com.example.demo.hostel.controller;

import com.example.demo.hostel.dto.CreateComplaintRequest;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.service.ComplaintService;
import com.example.demo.hostel.service.DuplicateDetectionService;
import com.example.demo.hostel.service.UpvoteService;
import com.example.demo.hostel.validation.RoomLocationValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ComplaintControllerTest {

    private static final String EMAIL = "student@smail.iitm.ac.in";
    private static final Jwt JWT = Jwt.withTokenValue("token")
        .header("alg", "none")
        .claim("email", EMAIL)
        .build();

    private ComplaintService complaintService;
    private ComplaintController controller;

    @BeforeEach
    void setUp() {
        complaintService = mock(ComplaintService.class);
        when(complaintService.createComplaint(eq(EMAIL), any())).thenReturn(new Complaint());
        controller = new ComplaintController(complaintService, new RoomLocationValidator(), mock(UpvoteService.class), mock(DuplicateDetectionService.class));
    }

    @Test
    void personalIssue_ignoresFloorAndRoomFromPayload() {
        CreateComplaintRequest payload = new CreateComplaintRequest(
            " Broken fan ", " Not spinning ", "Electrical", false, "Ground", "101", "Some wing",
            " Mon: 2 PM - 5 PM ", true);

        assertEquals(HttpStatus.CREATED, controller.createComplaint(JWT, payload).getStatusCode());

        CreateComplaintRequest sent = captureRequest();
        assertFalse(sent.communityArea());
        assertNull(sent.floor());
        assertNull(sent.room());
        assertNull(sent.wing());
        assertEquals("Broken fan", sent.title());
        assertEquals("Mon: 2 PM - 5 PM", sent.availableTimeSlot());
        assertTrue(sent.keyLeftWithSecurity());
    }

    @Test
    void communityArea_normalizesFloorAndKeepsWing() {
        CreateComplaintRequest payload = new CreateComplaintRequest(
            "Dirty floor", "Not cleaned", "Cleaning", true, "2nd floor", "999", " East wing ", null, null);

        assertEquals(HttpStatus.CREATED, controller.createComplaint(JWT, payload).getStatusCode());

        CreateComplaintRequest sent = captureRequest();
        assertTrue(sent.communityArea());
        assertEquals("Second", sent.floor());
        assertEquals("East wing", sent.wing());
        assertNull(sent.room());
    }

    @Test
    void communityArea_requiresKnownFloorAndWing() {
        assertBadRequest(new CreateComplaintRequest("T", "D", "Cleaning", true, "Second", null, "  ", null, null));
        assertBadRequest(new CreateComplaintRequest("T", "D", "Cleaning", true, "Basement", null, "East", null, null));
        assertBadRequest(new CreateComplaintRequest("T", "D", "Cleaning", true, null, null, "East", null, null));
    }

    @Test
    void rejectsMissingOrWhitespaceOnlyFields() {
        assertBadRequest(new CreateComplaintRequest("   ", "D", "Electrical", false, null, null, null, null, null));
        assertBadRequest(new CreateComplaintRequest("T", "   ", "Electrical", false, null, null, null, null, null));
        assertBadRequest(new CreateComplaintRequest("T", "D", null, false, null, null, null, null, null));
    }

    @Test
    void rejectsOverlongAvailableTime() {
        assertBadRequest(new CreateComplaintRequest("T", "D", "Electrical", false, null, null, null, "x".repeat(501), null));
    }

    @Test
    void acceptsSnakeCaseFieldNames() {
        CreateComplaintRequest parsed = JsonMapper.builder().build().readValue(
            "{\"title\":\"T\",\"is_community_area\":true,\"available_time_slot\":\"Tue: 12 PM - 5 PM\",\"key_with_security\":true}",
            CreateComplaintRequest.class);

        assertTrue(parsed.communityArea());
        assertEquals("Tue: 12 PM - 5 PM", parsed.availableTimeSlot());
        assertTrue(parsed.keyLeftWithSecurity());
    }

    private void assertBadRequest(CreateComplaintRequest payload) {
        ComplaintService untouched = mock(ComplaintService.class);
        ComplaintController guarded = new ComplaintController(untouched, new RoomLocationValidator(), mock(UpvoteService.class), mock(DuplicateDetectionService.class));

        assertEquals(HttpStatus.BAD_REQUEST, guarded.createComplaint(JWT, payload).getStatusCode());
        verifyNoInteractions(untouched);
    }

    private CreateComplaintRequest captureRequest() {
        ArgumentCaptor<CreateComplaintRequest> captor = ArgumentCaptor.forClass(CreateComplaintRequest.class);
        verify(complaintService).createComplaint(eq(EMAIL), captor.capture());
        return captor.getValue();
    }
}
