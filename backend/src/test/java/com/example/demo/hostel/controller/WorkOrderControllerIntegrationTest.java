package com.example.demo.hostel.controller;

import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.model.WorkOrderPriority;
import com.example.demo.hostel.repository.CategoryRepository;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.LocationRepository;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.service.WorkOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The work-order API over HTTP: real security chain, JSON binding, service and database. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:work-order-api-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class WorkOrderControllerIntegrationTest {

    private static final String WARDEN = "warden@gmail.com";
    private static final String ADMIN = "admin@smail.iitm.ac.in";

    @Autowired private MockMvc mockMvc;
    @Autowired private WorkOrderService workOrderService;
    @Autowired private UserRepository userRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User admin;
    private Long complaintId;

    @BeforeEach
    void setUp() {
        userRepository.save(user(WARDEN, Role.STAFF));
        admin = userRepository.save(user(ADMIN, Role.ADMIN));
        User student = userRepository.save(user("student@smail.iitm.ac.in", Role.STUDENT));

        Category category = new Category();
        category.setName("Electrical");
        category.setDefaultSlaHours(48);
        Location location = new Location();
        location.setFloor("First");
        location.setRoomNumber("204");

        Complaint complaint = new Complaint();
        complaint.setTitle("Fan not working");
        complaint.setDescription("Controller test");
        complaint.setCategory(categoryRepository.save(category));
        complaint.setLocation(locationRepository.save(location));
        complaint.setStudent(student);
        complaintId = complaintRepository.save(complaint).getId();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM work_orders");
        jdbcTemplate.update("DELETE FROM complaint_history");
        jdbcTemplate.update("DELETE FROM complaints");
        categoryRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void everyEndpointNeedsASignIn() throws Exception {
        mockMvc.perform(get("/api/work-orders/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/work-orders/complaint/" + complaintId)).andExpect(status().isUnauthorized());
        mockMvc.perform(json(post("/api/work-orders/" + complaintId + "/assign"), "{\"priority\":\"HIGH\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(json(put("/api/work-orders/" + UUID.randomUUID()), "{\"status\":\"DONE\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentsAreRefused() throws Exception {
        mockMvc.perform(json(post("/api/work-orders/" + complaintId + "/assign"), "{\"priority\":\"HIGH\"}")
                        .with(as("student@smail.iitm.ac.in", "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffAssignThemselvesAndTheResponseIsTheView() throws Exception {
        mockMvc.perform(json(post("/api/work-orders/" + complaintId + "/assign"),
                        "{\"priority\":\"URGENT\",\"deadline\":\"2099-01-15T18:30:00\"}")
                        .with(as(WARDEN, "ROLE_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complaintId").value(complaintId))
                .andExpect(jsonPath("$.complaintStatus").value("ASSIGNED"))
                .andExpect(jsonPath("$.assigneeEmail").value(WARDEN))
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.deadline").value("2099-01-15T18:30:00"))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.hibernateLazyInitializer").doesNotExist());

        mockMvc.perform(get("/api/work-orders/me").with(as(WARDEN, "ROLE_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].complaintTitle").value("Fan not working"));
    }

    @Test
    void staffAssigningSomeoneElseGetsTheReason() throws Exception {
        mockMvc.perform(json(post("/api/work-orders/" + complaintId + "/assign"),
                        "{\"inChargeId\":\"" + admin.getId() + "\",\"priority\":\"HIGH\"}")
                        .with(as(WARDEN, "ROLE_STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Staff can only assign complaints to themselves"));
    }

    @Test
    void valuesOutsideTheFixedListsAreBadRequests() throws Exception {
        mockMvc.perform(json(post("/api/work-orders/" + complaintId + "/assign"), "{\"priority\":\"SOMEDAY\"}")
                        .with(as(WARDEN, "ROLE_STAFF")))
                .andExpect(status().isBadRequest());

        UUID workOrderId = workOrderService
                .assignInCharge(complaintId, null, WorkOrderPriority.LOW, null, WARDEN).id();
        mockMvc.perform(json(put("/api/work-orders/" + workOrderId), "{\"status\":\"FINISHED\"}")
                        .with(as(WARDEN, "ROLE_STAFF")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingPriorityAndUnknownRecordsAreReported() throws Exception {
        mockMvc.perform(json(post("/api/work-orders/" + complaintId + "/assign"), "{}")
                        .with(as(WARDEN, "ROLE_STAFF")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Priority is required"));
        mockMvc.perform(json(put("/api/work-orders/" + UUID.randomUUID()), "{\"status\":\"DONE\"}")
                        .with(as(ADMIN, "ROLE_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Work order not found"));
        mockMvc.perform(json(put("/api/work-orders/not-a-uuid"), "{\"status\":\"DONE\"}")
                        .with(as(ADMIN, "ROLE_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void markingDoneOverHttpResolvesTheComplaint() throws Exception {
        UUID workOrderId = workOrderService
                .assignInCharge(complaintId, null, WorkOrderPriority.LOW, null, WARDEN).id();

        mockMvc.perform(json(put("/api/work-orders/" + workOrderId),
                        "{\"status\":\"DONE\",\"completionNotes\":\"Replaced the capacitor\"}")
                        .with(as(WARDEN, "ROLE_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.complaintStatus").value("RESOLVED"))
                .andExpect(jsonPath("$.completionNotes").value("Replaced the capacitor"));

        assertThat(complaintRepository.findById(complaintId).orElseThrow().getStatus()).isEqualTo(Status.RESOLVED);
        mockMvc.perform(get("/api/work-orders/complaint/" + complaintId).with(as(ADMIN, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DONE"));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static JwtRequestPostProcessor as(String email, String role) {
        return jwt().jwt(token -> token.claim("email", email)).authorities(new SimpleGrantedAuthority(role));
    }

    private static User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        return user;
    }
}
