package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.ComplaintRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StudentService {
    
    private final ComplaintRepository complaintRepository;

    public StudentService(ComplaintRepository complaintRepository) {
        this.complaintRepository = complaintRepository;
    }

    public Complaint submitComplaint(User student, String title, String description) {
        Complaint complaint = new Complaint();
        complaint.setTitle(title);
        complaint.setDescription(description);
        complaint.setStatus(Status.PENDING); 
        complaint.setStudent(student);
        complaint.setCreatedAt(LocalDateTime.now()); 
        
        return complaintRepository.save(complaint);
    }

    public List<Complaint> getStudentComplaints(User student) {
        return complaintRepository.findByStudent(student);
    }
}