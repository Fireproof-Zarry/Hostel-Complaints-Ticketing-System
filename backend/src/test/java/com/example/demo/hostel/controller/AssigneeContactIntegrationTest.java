package com.example.demo.hostel.controller;

import com.example.demo.hostel.dto.UpdateWorkOrderRequest;
import com.example.demo.hostel.dto.WorkOrderView;
import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.model.WorkOrderPriority;
import com.example.demo.hostel.model.WorkOrderStatus;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A student sees who is handling their own complaint, and nobody else's. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:assignee-contact-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class AssigneeContactIntegrationTest {

    private static final String WARDEN = "warden@gmail.com";
    private static final String WARDEN_PHONE = "9876543210";
    private static final String OWNER = "owner@smail.iitm.ac.in";
    private static final String NEIGHBOUR = "neighbour@smail.iitm.ac.in";

    @Autowired private MockMvc mockMvc;
    @Autowired private WorkOrderService workOrderService;
    @Autowired private UserRepository userRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User warden;
    private Long complaintId;

    @BeforeEach
    void setUp() {
        warden = user(WARDEN, Role.STAFF);
        warden.setFullName("Ravi Kumar");
        warden.setPhoneNumber(WARDEN_PHONE);
        warden = userRepository.save(warden);
        User owner = userRepository.save(user(OWNER, Role.STUDENT));
        userRepository.save(user(NEIGHBOUR, Role.STUDENT));

        Category category = new Category();
        category.setName("Electrical");
        category.setDefaultSlaHours(48);
        Location location = new Location();
        location.setFloor("First");
        location.setRoomNumber("204");

        Complaint complaint = new Complaint();
        complaint.setTitle("Fan not working");
        complaint.setDescription("Contact test");
        complaint.setCategory(categoryRepository.save(category));
        complaint.setLocation(locationRepository.save(location));
        complaint.setStudent(owner);
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
    void noContactBeforeTheComplaintIsAssigned() throws Exception {
        mockMvc.perform(get("/api/complaints/mine").with(student(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(complaintId))
                .andExpect(jsonPath("$[0].assignee").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void theOwnerSeesTheNameAndPhoneButNotTheAccountDetails() throws Exception {
        assign();

        mockMvc.perform(get("/api/complaints/mine").with(student(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Fan not working"))
                .andExpect(jsonPath("$[0].status").value("ASSIGNED"))
                .andExpect(jsonPath("$[0].assignee.name").value("Ravi Kumar"))
                .andExpect(jsonPath("$[0].assignee.phoneNumber").value(WARDEN_PHONE))
                .andExpect(jsonPath("$[0].assignee.email").doesNotExist())
                .andExpect(jsonPath("$[0].assignee.id").doesNotExist());
    }

    @Test
    void assigneesWithoutAPhoneNumberStillShowTheirName() throws Exception {
        warden.setPhoneNumber(null);
        userRepository.save(warden);
        assign();

        mockMvc.perform(get("/api/complaints/mine").with(student(OWNER)))
                .andExpect(jsonPath("$[0].assignee.name").value("Ravi Kumar"))
                .andExpect(jsonPath("$[0].assignee.phoneNumber").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void theContactGoesAwayWhenTheWorkOrderFinishes() throws Exception {
        WorkOrderView workOrder = assign();
        workOrderService.updateWorkOrder(workOrder.id(),
                new UpdateWorkOrderRequest(null, null, null, WorkOrderStatus.DONE), WARDEN);

        mockMvc.perform(get("/api/complaints/mine").with(student(OWNER)))
                .andExpect(jsonPath("$[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$[0].assignee").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void otherStudentsNeverReceiveThePhoneNumber() throws Exception {
        assign();

        assertNoPhoneNumber("/api/complaints/mine");
        assertNoPhoneNumber("/api/complaints/community");
        mockMvc.perform(get("/api/complaints/" + complaintId).with(student(NEIGHBOUR)))
                .andExpect(status().is4xxClientError());
    }

    private void assertNoPhoneNumber(String url) throws Exception {
        String body = mockMvc.perform(get(url).with(student(NEIGHBOUR)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(WARDEN_PHONE);
    }

    private WorkOrderView assign() {
        return workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.HIGH, null, WARDEN);
    }

    private static JwtRequestPostProcessor student(String email) {
        return jwt().jwt(token -> token.claim("email", email)).authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    private static User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        return user;
    }
}
