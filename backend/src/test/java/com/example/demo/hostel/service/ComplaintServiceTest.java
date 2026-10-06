package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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
}