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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final SupabaseStorageService storageService;

    public ComplaintService(
            ComplaintRepository complaintRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            LocationRepository locationRepository,
            SupabaseStorageService storageService) {
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.storageService = storageService;
    }

    public Complaint createComplaint(String email, String title, String description, String category, String floor, String room) {
        User student = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        long openComplaints = complaintRepository.countByStudentAndStatusIn(
                student, 
                List.of(Status.PENDING, Status.IN_PROGRESS)
        );

        if (openComplaints >= 5) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "You cannot have more than 5 open complaints at a time.");
        }

        Category complaintCategory = categoryRepository.findByNameIgnoreCase(category)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category: " + category));

        Location location = locationRepository.findByFloorAndRoomNumberAndWingIsNull(floor, room)
                .orElseGet(() -> {
                    Location newLocation = new Location();
                    newLocation.setFloor(floor);
                    newLocation.setRoomNumber(room);
                    return locationRepository.save(newLocation);
                });

        Complaint complaint = new Complaint();
        complaint.setTitle(title);
        complaint.setDescription(description);
        complaint.setCategory(complaintCategory);
        complaint.setLocation(location);
        complaint.setStudent(student);
        
        return complaintRepository.save(complaint);
    }

    public List<Complaint> getMyComplaints(String email) {
        User student = userRepository.findByEmail(email).orElseThrow();
        return complaintRepository.findByStudentOrderByCreatedAtDesc(student);
    }

    public Complaint getComplaintById(Long id, String email) {
        User student = userRepository.findByEmail(email).orElseThrow();
        return complaintRepository.findByIdAndStudent(id, student)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found or you do not have permission to view it."));
    }

    public Complaint attachImage(Long id, String email, MultipartFile file) {
        // Ownership check first, so nothing is uploaded for someone else's complaint
        Complaint complaint = getComplaintById(id, email);
        complaint.setImageUrl(storageService.uploadImage(file));
        return complaintRepository.save(complaint);
    }
}
