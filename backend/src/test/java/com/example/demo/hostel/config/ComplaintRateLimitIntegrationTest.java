package com.example.demo.hostel.config;

import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.CategoryRepository;
import com.example.demo.hostel.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The rate limit through the real security filter chain (MockMvc + Spring Security). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:complaint-rate-limit-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class ComplaintRateLimitIntegrationTest {

    // A community complaint needs no profile room, so these all reach the service
    private static final String VALID_COMPLAINT = """
            {"title":"Dirty washroom","description":"Not cleaned","category":"Cleaning",
             "isCommunityArea":true,"floor":"First","wing":"East wing washroom"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        Category cleaning = new Category();
        cleaning.setName("Cleaning");
        cleaning.setDefaultSlaHours(24);
        categoryRepository.save(cleaning);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM complaints");
        jdbcTemplate.update("DELETE FROM locations");
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void sixthComplaintWithinAMinuteIsRejectedWithRetryAfter() throws Exception {
        String email = student("burst@smail.iitm.ac.in");

        // Invalid bodies count too: the limit is on requests, not on successes
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(fileComplaint(email, "{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().exists("X-Rate-Limit-Remaining"));
        }

        mockMvc.perform(fileComplaint(email, VALID_COMPLAINT))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("rate_limited"))
                .andExpect(jsonPath("$.message", containsString("Try again in")));
    }

    @Test
    void otherUsersAndOtherEndpointsAreNotAffected() throws Exception {
        String busy = student("busy@smail.iitm.ac.in");
        String other = student("other@smail.iitm.ac.in");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(fileComplaint(busy, "{}"));
        }
        mockMvc.perform(fileComplaint(busy, "{}")).andExpect(status().isTooManyRequests());

        mockMvc.perform(fileComplaint(other, VALID_COMPLAINT)).andExpect(status().isCreated());
        mockMvc.perform(get("/api/complaints/mine").with(jwt().jwt(t -> t.claim("email", busy))))
                .andExpect(status().isOk());
    }

    @Test
    void openComplaintLimitIsStillA429WithoutRetryAfter() throws Exception {
        // Five open complaints, filed by one account and handed to another, so the
        // second account is at the open-complaint limit with an unused rate-limit bucket
        String filer = student("filer@smail.iitm.ac.in");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(fileComplaint(filer, VALID_COMPLAINT)).andExpect(status().isCreated());
        }
        String second = student("five-open@smail.iitm.ac.in");
        jdbcTemplate.update("UPDATE complaints SET student_id = (SELECT id FROM users WHERE email = ?)", second);

        String body = mockMvc.perform(fileComplaint(second, VALID_COMPLAINT))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().doesNotExist("Retry-After"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("rate_limited");
    }

    private MockHttpServletRequestBuilder fileComplaint(String email, String body) {
        return post("/api/complaints")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(jwt().jwt(token -> token.claim("email", email)));
    }

    private String student(String email) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(Role.STUDENT);
        userRepository.save(user);
        return email;
    }
}
