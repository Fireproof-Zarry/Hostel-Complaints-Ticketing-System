package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.DuplicateCandidate;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.UpvoteRepository;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateDetectionServiceTest {

    private static final String EMAIL = "neighbour@smail.iitm.ac.in";

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private UpvoteRepository upvoteRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private RoomLocationValidator roomLocationValidator = new RoomLocationValidator();

    @InjectMocks
    private DuplicateDetectionService service;

    private User neighbour;
    private User reporter;

    @BeforeEach
    void setUp() {
        neighbour = user("u-neighbour");
        reporter = user("u-reporter");
    }

    @Test
    void returnsCandidatesInSimilarityOrderWithCountsAndFlags() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(neighbour));
        when(complaintRepository.findSimilarOpenCommunityComplaintIds("Washroom tap leaking", "First", "East wing"))
                .thenReturn(List.of(12L, 5L, 9L));
        // findAllById makes no ordering promise
        when(complaintRepository.findAllById(List.of(12L, 5L, 9L))).thenReturn(List.of(
                complaint(5L, "Tap leaking in washroom", reporter),
                complaint(9L, "Leaking tap near dispenser", neighbour),
                complaint(12L, "Washroom tap leaking badly", reporter)));
        when(upvoteRepository.countByComplaintIds(List.of(12L, 5L, 9L)))
                .thenReturn(List.of(new Object[]{12L, 4L}, new Object[]{5L, 1L}));
        when(upvoteRepository.findUpvotedComplaintIds("u-neighbour", List.of(12L, 5L, 9L))).thenReturn(List.of(5L));

        List<DuplicateCandidate> result = service.findPotentialDuplicates(EMAIL, "  Washroom tap leaking ", "1st floor", " East wing ");

        assertThat(result).extracting(DuplicateCandidate::id).containsExactly(12L, 5L, 9L);
        assertThat(result).extracting(DuplicateCandidate::upvoteCount).containsExactly(4L, 1L, 0L);
        assertThat(result).extracting(DuplicateCandidate::upvotedByYou).containsExactly(false, true, false);
        assertThat(result).extracting(DuplicateCandidate::reportedByYou).containsExactly(false, false, true);
        assertThat(result.getFirst().floor()).isEqualTo("First");
        assertThat(result.getFirst().wing()).isEqualTo("East wing washroom");
        assertThat(result.getFirst().status()).isEqualTo(Status.PENDING);
        // Thresholds are set in the same transaction, before the search
        verify(complaintRepository).setTrigramThresholds(DuplicateDetectionService.SIMILARITY_THRESHOLD);
    }

    @Test
    void missingWingIsSearchedAsEmptyString() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(neighbour));
        when(complaintRepository.findSimilarOpenCommunityComplaintIds("Dirty washroom", "Second", "")).thenReturn(List.of());

        assertThat(service.findPotentialDuplicates(EMAIL, "Dirty washroom", "Second", null)).isEmpty();
    }

    @Test
    void noMatchesSkipsTheFollowUpQueries() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(neighbour));
        when(complaintRepository.findSimilarOpenCommunityComplaintIds(anyString(), anyString(), anyString())).thenReturn(List.of());

        assertThat(service.findPotentialDuplicates(EMAIL, "Dirty washroom", "Second", "West")).isEmpty();
        verifyNoInteractions(upvoteRepository);
    }

    @Test
    void shortTitlesReturnNothingWithoutQuerying() {
        assertThat(service.findPotentialDuplicates(EMAIL, " fan ", "Second", null)).isEmpty();
        assertThat(service.findPotentialDuplicates(EMAIL, null, "Second", null)).isEmpty();
        verifyNoInteractions(complaintRepository, upvoteRepository, userRepository);
    }

    @Test
    void invalidFloorIsRejected() {
        assertThatThrownBy(() -> service.findPotentialDuplicates(EMAIL, "Dirty washroom", "Basement", null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(complaintRepository);
    }

    @Test
    void unknownUserIsNotFound() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findPotentialDuplicates(EMAIL, "Dirty washroom", "Second", null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(complaintRepository, org.mockito.Mockito.never()).findSimilarOpenCommunityComplaintIds(any(), any(), any());
    }

    private static Complaint complaint(Long id, String title, User student) {
        Location location = new Location();
        location.setFloor("First");
        location.setWing("East wing washroom");
        location.setCommunityArea(true);
        Complaint complaint = new Complaint();
        complaint.setId(id);
        complaint.setTitle(title);
        complaint.setLocation(location);
        complaint.setStudent(student);
        return complaint;
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        user.setRole(Role.STUDENT);
        return user;
    }
}
