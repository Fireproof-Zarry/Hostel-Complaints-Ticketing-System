package com.example.demo.hostel.controller;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.service.StudentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The original complaint API under /api/student/complaints. The app itself uses
 * ComplaintController, which validates location and category.
 */
@RestController
@RequestMapping("/api/student/complaints")
public class StudentController {

    private final StudentService studentService;
    private final UserRepository userRepository;

    public StudentController(StudentService studentService, UserRepository userRepository) {
        this.studentService = studentService;
        this.userRepository = userRepository;
    }

    /** Files a complaint with only a title and description. */
    @PostMapping
    public Complaint createComplaint(
            @AuthenticationPrincipal Jwt jwt, 
            @RequestParam String title, 
            @RequestParam String description) {
        
        String email = jwt.getClaimAsString("email");
        
        User student = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Student not found"));
                
        return studentService.submitComplaint(student, title, description);
    }

    /** The caller's complaints, newest first. */
    @GetMapping
    public List<Complaint> getMyComplaints(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        
        User student = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Student not found"));
                
        return studentService.getStudentComplaints(student);
    }
}