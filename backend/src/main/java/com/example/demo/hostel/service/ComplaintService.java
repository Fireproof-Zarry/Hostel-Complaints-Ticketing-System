package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.AssigneeContact;
import com.example.demo.hostel.dto.ComplaintHistoryEntry;
import com.example.demo.hostel.dto.CreateComplaintRequest;
import com.example.demo.hostel.dto.MyComplaint;
import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.ComplaintHistory;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.model.WorkOrder;
import com.example.demo.hostel.repository.CategoryRepository;
import com.example.demo.hostel.repository.ComplaintHistoryRepository;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.LocationRepository;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.repository.WorkOrderRepository;
import com.example.demo.hostel.validation.RoomLocationValidator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complaints from the reporter's side: filing, reading their own, deleting and photos.
 * Status changes belong to ComplaintTransitionService.
 */
@Service
public class ComplaintService {

    private static final int MAX_OPEN_COMPLAINTS = 5;
    // Staff report on behalf of the hostel; this only stops something going badly wrong
    private static final int MAX_OPEN_COMPLAINTS_STAFF = 50;
    /** Matches the length of the description column. */
    private static final int MAX_DESCRIPTION_LENGTH = 1000;

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final SupabaseStorageService storageService;
    private final RoomLocationValidator roomLocationValidator;
    private final ComplaintHistoryRepository complaintHistoryRepository;
    private final WorkOrderRepository workOrderRepository;

    public ComplaintService(
            ComplaintRepository complaintRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            LocationRepository locationRepository,
            SupabaseStorageService storageService,
            RoomLocationValidator roomLocationValidator,
            ComplaintHistoryRepository complaintHistoryRepository,
            WorkOrderRepository workOrderRepository) {
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.storageService = storageService;
        this.roomLocationValidator = roomLocationValidator;
        this.complaintHistoryRepository = complaintHistoryRepository;
        this.workOrderRepository = workOrderRepository;
    }

    /**
     * Creates a complaint from a validated request.
     * Personal issues use the student's profile room; staff and admins have no
     * room of their own, so they name the room in the request.
     * Community-area issues use the requested floor and wing.
     */
    public Complaint createComplaint(String email, CreateComplaintRequest request) {
        User student = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        boolean isStaff = student.getRole() == Role.ADMIN || student.getRole() == Role.STAFF;
        int openLimit = isStaff ? MAX_OPEN_COMPLAINTS_STAFF : MAX_OPEN_COMPLAINTS;
        long openComplaints = complaintRepository.countByStudentAndStatusIn(
                student, List.of(Status.PENDING, Status.ASSIGNED, Status.IN_PROGRESS));

        if (openComplaints >= openLimit) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "You cannot have more than " + openLimit + " open complaints at a time.");
        }

        Category complaintCategory = categoryRepository.findByNameIgnoreCase(request.category())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown category: " + request.category()));

        Location location = request.communityArea()
                ? communityAreaLocation(request.floor(), request.wing())
                : roomLocation(isStaff ? request.room() : student.getRoomNumber(), isStaff);

        Complaint complaint = new Complaint();
        complaint.setTitle(request.title());
        complaint.setDescription(request.description());
        complaint.setCategory(complaintCategory);
        complaint.setLocation(location);
        complaint.setStudent(student);
        complaint.setAvailableTimeSlot(request.availableTimeSlot());
        complaint.setKeyWithSecurity(
                !request.communityArea() && request.keyLeftWithSecurity());

        return complaintRepository.save(complaint);
    }

    private Location roomLocation(String room, boolean isStaff) {
        String floor = roomLocationValidator.floorForRoom(room);

        if (floor == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    isStaff
                            ? "Enter a hostel room from 101 to 799."
                            : "Add a valid room number to your profile before filing a personal complaint.");
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

    /** The complaints this user filed, newest first, including archived ones. */
    public List<Complaint> getMyComplaints(String email) {
        User student = userRepository.findByEmail(email).orElseThrow();
        return complaintRepository.findByStudentOrderByCreatedAtDesc(student);
    }

    /**
     * The caller's own complaints, each with the name and phone number of whoever
     * is handling it. Only the owner gets this, and only while the work order is
     * active: the contact goes away once it is done or cancelled.
     */
    public List<MyComplaint> getMyComplaintsWithContacts(String email) {
        List<Complaint> complaints = getMyComplaints(email);
        if (complaints.isEmpty()) {
            return List.of();
        }

        Map<Long, AssigneeContact> contacts = new HashMap<>();
        for (WorkOrder workOrder : workOrderRepository.findWithAssigneeByComplaints(complaints, WorkOrderService.ACTIVE)) {
            User assignee = workOrder.getAssignee();
            contacts.put(workOrder.getComplaint().getId(),
                    new AssigneeContact(WorkOrderService.displayName(assignee), assignee.getPhoneNumber()));
        }
        LocalDateTime now = LocalDateTime.now();
        return complaints.stream()
                .map(complaint -> new MyComplaint(
                        complaint,
                        contacts.get(complaint.getId()),
                        NudgeService.canNudge(complaint, now),
                        NudgeService.nextNudgeAt(complaint)))
                .toList();
    }

    /** One of this user's complaints; 404 for anyone else's, so others are not revealed. */
    public Complaint getComplaintById(Long id, String email) {
        User student = userRepository.findByEmail(email).orElseThrow();
        return complaintRepository.findByIdAndStudent(id, student)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Complaint not found or you do not have permission to view it."));
    }

    /**
     * Deletes the caller's own complaint while it is still PENDING (not yet
     * assigned). Soft delete: it disappears from every list at once and is
     * hard-deleted 15 days later by ComplaintPurgeService. Other students'
     * complaints return 404.
     */
    @Transactional
    public void deleteComplaint(Long id, String email) {
        Complaint complaint = getComplaintById(id, email);
        if (complaint.getStatus() != Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only pending complaints can be deleted; this one is already being handled.");
        }
        complaint.setDeletedAt(LocalDateTime.now());
        complaintRepository.save(complaint);
    }

    /**
     * Replaces the text of the caller's own complaint while it is still open:
     * 400 if blank or over 1000 characters, 409 once resolved or rejected, 404
     * for anyone else's complaint.
     */
    @Transactional
    public Complaint updateDescription(Long id, String email, String description) {
        String trimmed = description == null ? "" : description.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Description must be 1 to " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        Complaint complaint = getComplaintById(id, email);
        if (complaint.getStatus() == Status.RESOLVED || complaint.getStatus() == Status.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Closed complaints can no longer be edited.");
        }
        complaint.setDescription(trimmed);
        return complaintRepository.save(complaint);
    }

    /** Stores a photo for the user's own complaint; see SupabaseStorageService for the checks. */
    public Complaint attachImage(Long id, String email, MultipartFile file) {
        Complaint complaint = getComplaintById(id, email);
        complaint.setImageUrl(storageService.uploadImage(file));
        return complaintRepository.save(complaint);
    }

    /**
     * Status history of a complaint, newest first, as summaries that never
     * include the actor's account details. Students get their own complaints, with
     * staff shown as "Hostel office"; admins and staff get any complaint, with real
     * names. Read-only transaction so the lazily loaded actor can be read while mapping.
     */
    @Transactional(readOnly = true)
    public List<ComplaintHistoryEntry> getComplaintHistory(Long id, String email) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Complaint not found"));

        User student = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        boolean isStaff = student.getRole() == Role.ADMIN || student.getRole() == Role.STAFF;
        if (!isStaff && !complaint.getStudent().getId().equals(student.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Complaint not found");
        }

        return complaintHistoryRepository.findByComplaintOrderByChangedAtDesc(complaint).stream()
                .map(history -> toHistoryEntry(history, isStaff))
                .toList();
    }

    private static ComplaintHistoryEntry toHistoryEntry(ComplaintHistory history, boolean forStaff) {
        User actor = history.getActor();
        String changedBy;
        if (actor == null) {
            changedBy = "Hostel office";
        } else if (forStaff) {
            changedBy = WorkOrderService.displayName(actor);
        } else if (actor.getRole() == Role.ADMIN || actor.getRole() == Role.STAFF) {
            changedBy = "Hostel office";
        } else {
            changedBy = actor.getFullName() != null ? actor.getFullName() : actor.getName();
        }
        return new ComplaintHistoryEntry(
                history.getOldStatus(),
                history.getNewStatus(),
                history.getReason(),
                history.getChangedAt(),
                changedBy);
    }
}