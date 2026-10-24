package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.CreateComplaintRequest;
import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.CategoryRepository;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.LocationRepository;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.validation.RoomLocationValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ComplaintServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private SupabaseStorageService storageService;

    @Spy
    private RoomLocationValidator roomLocationValidator = new RoomLocationValidator();

    @InjectMocks
    private ComplaintService complaintService;

    private User testStudent;

    @BeforeEach
    void setUp() {
        testStudent = new User();
        testStudent.setId("1");
        testStudent.setEmail("student@smail.iitm.ac.in");
    }

    private static CreateComplaintRequest personal(String category, boolean keyWithSecurity) {
        return new CreateComplaintRequest("Broken Fan", "Making a loud noise", category,
                false, null, null, null, "Mon: 2 PM - 5 PM", keyWithSecurity);
    }

    private void stubStudentUnderCap() {
        when(userRepository.findByEmail("student@smail.iitm.ac.in")).thenReturn(Optional.of(testStudent));
        when(complaintRepository.countByStudentAndStatusIn(testStudent, List.of(Status.PENDING, Status.IN_PROGRESS)))
                .thenReturn(3L);
    }

    private void stubCategory(String name) {
        Category category = new Category();
        category.setName(name);
        when(categoryRepository.findByNameIgnoreCase(name)).thenReturn(Optional.of(category));
    }

    @Test
    void personalIssue_usesRoomFromStudentProfile() {
        testStudent.setRoomNumber("312");
        stubStudentUnderCap();
        stubCategory("Electrical");
        Location room = new Location();
        when(locationRepository.findByFloorAndRoomNumberAndWingIsNull("Second", "312"))
                .thenReturn(Optional.of(room));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Complaint result = complaintService.createComplaint("student@smail.iitm.ac.in", personal("Electrical", true));

        assertSame(room, result.getLocation());
        assertEquals("Mon: 2 PM - 5 PM", result.getAvailableTimeSlot());
        assertTrue(result.isKeyWithSecurity());
        verify(locationRepository, never()).findByFloorAndWingIgnoreCaseAndRoomNumberIsNull(any(), any());
    }

    @Test
    void personalIssue_createsLocationForProfileRoomOnDerivedFloor() {
        testStudent.setRoomNumber("512");
        stubStudentUnderCap();
        stubCategory("Electrical");
        when(locationRepository.findByFloorAndRoomNumberAndWingIsNull("Fourth", "512")).thenReturn(Optional.empty());
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Complaint result = complaintService.createComplaint("student@smail.iitm.ac.in", personal("Electrical", false));

        assertEquals("Fourth", result.getFloor());
        assertEquals("512", result.getRoom());
        assertFalse(result.isCommunityArea());
    }

    @Test
    void personalIssue_withoutProfileRoom_isRejected() {
        stubStudentUnderCap();
        stubCategory("Electrical");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            complaintService.createComplaint("student@smail.iitm.ac.in", personal("Electrical", false))
        );

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, exception.getStatusCode());
        verify(complaintRepository, never()).save(any(Complaint.class));
    }

    @Test
    void communityArea_usesFloorAndWingAndIgnoresKey() {
        testStudent.setRoomNumber("312");
        stubStudentUnderCap();
        stubCategory("Cleaning");
        when(locationRepository.findByFloorAndWingIgnoreCaseAndRoomNumberIsNull("First", "East wing washroom"))
                .thenReturn(Optional.empty());
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateComplaintRequest request = new CreateComplaintRequest("Dirty floor", "Not cleaned today", "Cleaning",
                true, "First", null, "East wing washroom", null, true);
        Complaint result = complaintService.createComplaint("student@smail.iitm.ac.in", request);

        assertTrue(result.isCommunityArea());
        assertEquals("First", result.getFloor());
        assertEquals("East wing washroom", result.getWing());
        assertNull(result.getRoom());
        assertFalse(result.isKeyWithSecurity());
        verify(locationRepository, never()).findByFloorAndRoomNumberAndWingIsNull(any(), any());
    }

    @Test
    void whenAtCap_thenThrowsTooManyRequests() {
        when(userRepository.findByEmail("student@smail.iitm.ac.in")).thenReturn(Optional.of(testStudent));
        when(complaintRepository.countByStudentAndStatusIn(testStudent, List.of(Status.PENDING, Status.IN_PROGRESS)))
                .thenReturn(5L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            complaintService.createComplaint("student@smail.iitm.ac.in", personal("Plumbing", false))
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
        assertTrue(exception.getReason().contains("5 open complaints"));
        verify(complaintRepository, never()).save(any(Complaint.class));
    }

    @Test
    void whenCategoryUnknown_thenThrowsBadRequest() {
        stubStudentUnderCap();
        when(categoryRepository.findByNameIgnoreCase("Gardening")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            complaintService.createComplaint("student@smail.iitm.ac.in", personal("Gardening", false))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(complaintRepository, never()).save(any(Complaint.class));
    }

    @Test
    void attachImage_uploadsAndSavesUrlOnOwnComplaint() {
        Complaint complaint = new Complaint();
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});
        when(userRepository.findByEmail("student@smail.iitm.ac.in")).thenReturn(Optional.of(testStudent));
        when(complaintRepository.findByIdAndStudent(7L, testStudent)).thenReturn(Optional.of(complaint));
        when(storageService.uploadImage(file)).thenReturn("https://cdn/a.jpg");
        when(complaintRepository.save(complaint)).thenReturn(complaint);

        Complaint result = complaintService.attachImage(7L, "student@smail.iitm.ac.in", file);

        assertEquals("https://cdn/a.jpg", result.getImageUrl());
        verify(complaintRepository).save(complaint);
    }

    @Test
    void attachImage_onOtherStudentsComplaint_isNotFoundAndNothingUploaded() {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});
        when(userRepository.findByEmail("student@smail.iitm.ac.in")).thenReturn(Optional.of(testStudent));
        when(complaintRepository.findByIdAndStudent(7L, testStudent)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            complaintService.attachImage(7L, "student@smail.iitm.ac.in", file)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(storageService);
        verify(complaintRepository, never()).save(any(Complaint.class));
    }
}
