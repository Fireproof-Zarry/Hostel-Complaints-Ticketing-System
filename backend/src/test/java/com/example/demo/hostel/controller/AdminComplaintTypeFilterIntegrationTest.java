package com.example.demo.hostel.controller;

import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.CategoryRepository;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.LocationRepository;
import com.example.demo.hostel.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The admin complaint list filtered to room issues or community issues. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:complaint-type-filter-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class AdminComplaintTypeFilterIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LocationRepository locationRepository;

    private User student;
    private Category category;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("student@smail.iitm.ac.in");
        user.setName("Student");
        user.setRole(Role.STUDENT);
        student = userRepository.save(user);

        Category electrical = new Category();
        electrical.setName("Electrical");
        electrical.setDefaultSlaHours(48);
        category = categoryRepository.save(electrical);

        complaint("Room on first", "First", false);
        complaint("Corridor on first", "First", true);
        complaint("Corridor on second", "Second", true);
    }

    @AfterEach
    void tearDown() {
        complaintRepository.deleteAll();
        categoryRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void showsBothTypesByDefault() throws Exception {
        mockMvc.perform(get("/api/admin/complaints").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void filtersToRoomIssues() throws Exception {
        mockMvc.perform(get("/api/admin/complaints").param("communityArea", "false").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", containsInAnyOrder("Room on first")));
    }

    @Test
    void filtersToCommunityIssues() throws Exception {
        mockMvc.perform(get("/api/admin/complaints").param("communityArea", "true").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", containsInAnyOrder("Corridor on first", "Corridor on second")));
    }

    @Test
    void combinesWithTheOtherFilters() throws Exception {
        mockMvc.perform(get("/api/admin/complaints")
                        .param("communityArea", "true")
                        .param("floor", "First")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", containsInAnyOrder("Corridor on first")));
    }

    @Test
    void rejectsValuesThatAreNotTrueOrFalse() throws Exception {
        mockMvc.perform(get("/api/admin/complaints").param("communityArea", "sometimes").with(admin()))
                .andExpect(status().isBadRequest());
    }

    private void complaint(String title, String floor, boolean communityArea) {
        Location location = new Location();
        location.setFloor(floor);
        location.setCommunityArea(communityArea);
        if (communityArea) {
            location.setWing("East");
        } else {
            location.setRoomNumber("204");
        }

        Complaint complaint = new Complaint();
        complaint.setTitle(title);
        complaint.setDescription("Type filter test");
        complaint.setCategory(category);
        complaint.setLocation(locationRepository.save(location));
        complaint.setStudent(student);
        complaintRepository.save(complaint);
    }

    private static JwtRequestPostProcessor admin() {
        return jwt().jwt(token -> token.claim("email", "admin@smail.iitm.ac.in"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
