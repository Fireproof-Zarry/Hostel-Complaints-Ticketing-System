package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.CategoryRepository;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.LocationRepository;
import com.example.demo.hostel.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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

    @InjectMocks
    private ComplaintService complaintService;

    private User testStudent;

    @BeforeEach
    void setUp() {
        testStudent = new User();
        testStudent.setId("1");
        testStudent.setEmail("student@smail.iitm.ac.in");
    }

    @Test
    void whenUnderCap_thenComplaintIsCreated() {
        when(userRepository.findByEmail("student@smail.iitm.ac.in")).thenReturn(Optional.of(testStudent));
        when(complaintRepository.countByStudentAndStatusIn(testStudent, List.of(Status.PENDING, Status.IN_PROGRESS)))
                .thenReturn(3L); 

        Category electrical = new Category();
        electrical.setName("Electrical");
        when(categoryRepository.findByNameIgnoreCase("Electrical")).thenReturn(Optional.of(electrical));
        when(locationRepository.findByFloorAndRoomNumberAndWingIsNull("2nd Floor", "312"))
                .thenReturn(Optional.of(new Location()));

        Complaint savedComplaint = new Complaint();
        savedComplaint.setTitle("Broken Fan");
        savedComplaint.setStatus(Status.PENDING);
        
        when(complaintRepository.save(any(Complaint.class))).thenReturn(savedComplaint);

        Complaint result = complaintService.createComplaint(
            "student@smail.iitm.ac.in", 
            "Broken Fan", 
            "Making a loud noise", 
            "Electrical", 
            "2nd Floor", 
            "312"
        );

        assertNotNull(result);
        assertEquals("Broken Fan", result.getTitle());
        verify(complaintRepository, times(1)).save(any(Complaint.class));
    }

    @Test
    void whenAtCap_thenThrowsTooManyRequests() {
        when(userRepository.findByEmail("student@smail.iitm.ac.in")).thenReturn(Optional.of(testStudent));
        when(complaintRepository.countByStudentAndStatusIn(testStudent, List.of(Status.PENDING, Status.IN_PROGRESS)))
                .thenReturn(5L); 

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            complaintService.createComplaint(
                "student@smail.iitm.ac.in", 
                "Leaky tap", 
                "Dripping constantly", 
                "Plumbing", 
                "2nd Floor", 
                "312"
            );
        });

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
        assertTrue(exception.getReason().contains("5 open complaints"));
        
        verify(complaintRepository, never()).save(any(Complaint.class));
    }

    @Test
    void whenCategoryUnknown_thenThrowsBadRequest() {
        when(userRepository.findByEmail("student@smail.iitm.ac.in")).thenReturn(Optional.of(testStudent));
        when(complaintRepository.countByStudentAndStatusIn(testStudent, List.of(Status.PENDING, Status.IN_PROGRESS)))
                .thenReturn(0L);
        when(categoryRepository.findByNameIgnoreCase("Gardening")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            complaintService.createComplaint(
                "student@smail.iitm.ac.in",
                "Overgrown hedge",
                "Blocking the path",
                "Gardening",
                "2nd Floor",
                "312"
            )
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
