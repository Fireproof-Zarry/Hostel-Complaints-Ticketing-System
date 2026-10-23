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
    private final RoomLocationValidator roomLocationValidator;

    public ComplaintService(
            ComplaintRepository complaintRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            LocationRepository locationRepository,
            SupabaseStorageService storageService,
            RoomLocationValidator roomLocationValidator) {
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.storageService = storageService;
        this.roomLocationValidator = roomLocationValidator;
    }

    /**
     * Creates a complaint from an already validated request. Personal issues
     * always use the room from the student's profile; any room sent by the
     * client is ignored. Community-area issues use the requested floor and wing.
     */
    public Complaint createComplaint(String email, CreateComplaintRequest request) {
        User student = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        long openComplaints = complaintRepository.countByStudentAndStatusIn(
                student, 
                List.of(Status.PENDING, Status.IN_PROGRESS)
        );

        if (openComplaints >= 5) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "You cannot have more than 5 open complaints at a time.");
        }

        Category complaintCategory = categoryRepository.findByNameIgnoreCase(request.category())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category: " + request.category()));

        Location location = request.communityArea()
                ? communityAreaLocation(request.floor(), request.wing())
                : studentRoomLocation(student);

        Complaint complaint = new Complaint();
        complaint.setTitle(request.title());
        complaint.setDescription(request.description());
        complaint.setCategory(complaintCategory);
        complaint.setLocation(location);
        complaint.setStudent(student);
        complaint.setAvailableTimeSlot(request.availableTimeSlot());
        // A room key only applies to the student's own room
        complaint.setKeyWithSecurity(!request.communityArea() && request.keyLeftWithSecurity());
        
        return complaintRepository.save(complaint);
    }

    private Location studentRoomLocation(User student) {
        String room = student.getRoomNumber();
        String floor = roomLocationValidator.floorForRoom(room);
        if (floor == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Add a valid room number to your profile before filing a personal complaint.");
        }
        return locationRepository.findByFloorAndRoomNumberAndWingIsNull(floor, room)
                .orElseGet(() -> {
                    Location newLocation = new Location();
                    newLocation.setFloor(floor);
                    newLocation.setRoomNumber(room);
                    return locationRepository.save(newLocation);
                });
    }

    private Location communityAreaLocation(String floor, String wing) {
        return locationRepository.findByFloorAndWingIgnoreCaseAndRoomNumberIsNull(floor, wing)
                .orElseGet(() -> {
                    Location newLocation = new Location();
                    newLocation.setFloor(floor);
                    newLocation.setWing(wing);
                    newLocation.setCommunityArea(true);
                    return locationRepository.save(newLocation);
                });
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
