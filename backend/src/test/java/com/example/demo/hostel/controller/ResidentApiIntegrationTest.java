package com.example.demo.hostel.controller;

import com.example.demo.hostel.repository.ResidentRepository;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.service.AuthService;
import com.example.demo.hostel.service.ResidentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The resident list over HTTP: who can manage it, and who it keeps out. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:resident-api-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "app.auth.admin-emails=office@smail.iitm.ac.in"
})
@AutoConfigureMockMvc
class ResidentApiIntegrationTest {

    private static final String SMAIL = "smail.iitm.ac.in";
    private static final String ADMIN = "office@smail.iitm.ac.in";
    private static final String RESIDENT = "cs23b051@smail.iitm.ac.in";
    private static final String OUTSIDER = "me22b100@smail.iitm.ac.in";

    @Autowired private MockMvc mockMvc;
    @Autowired private ResidentService residentService;
    @Autowired private ResidentRepository residentRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean private JwtDecoder jwtDecoder;

    @AfterEach
    void tearDown() {
        residentRepository.deleteAllInBatch();
        residentService.reload();
        userRepository.deleteAll();
    }

    @Test
    void onlyAdminsCanManageTheList() throws Exception {
        google("admin", ADMIN, null);
        google("student", RESIDENT, SMAIL);
        userRepository.save(staff("ravi@gmail.com"));
        google("staff", "ravi@gmail.com", null);

        mockMvc.perform(get("/api/admin/residents").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(get("/api/admin/residents").header("Authorization", "Bearer staff"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/residents").header("Authorization", "Bearer student"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminPreviewsThenReplacesTheList() throws Exception {
        google("admin", ADMIN, null);

        send(post("/api/admin/residents/preview"), "admin", "Roll No\\nCS23B051\\nEE21M012")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(2))
                .andExpect(jsonPath("$.applied").value(false));
        assertThat(residentRepository.count()).isZero();

        send(put("/api/admin/residents"), "admin", "CS23B051\\nEE21M012")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(2))
                .andExpect(jsonPath("$.applied").value(true));
        assertThat(residentRepository.count()).isEqualTo(2);
    }

    @Test
    void aFileWithoutRollNumbersIsRefusedWithAReason() throws Exception {
        google("admin", ADMIN, null);

        send(put("/api/admin/residents"), "admin", "Name,Room\\nRagini,214")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No roll numbers found. They look like CS23B051."));
    }

    @Test
    void aStudentNotOnTheListCannotLogIn() throws Exception {
        residentService.replace("cs23b051", ADMIN);
        google("outsider", OUTSIDER, SMAIL);

        login("outsider")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthService.NOT_RESIDENT_MESSAGE));
        assertThat(userRepository.findByEmail(OUTSIDER)).isEmpty();
    }

    @Test
    void aStudentOnTheListLogsIn() throws Exception {
        residentService.replace("cs23b051", ADMIN);
        google("resident", RESIDENT, SMAIL);

        login("resident").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void aStudentTakenOffTheListLosesAccessOnTheirNextRequest() throws Exception {
        google("resident", RESIDENT, SMAIL);
        login("resident").andExpect(status().isOk());

        residentService.replace("ee21m012", ADMIN);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer resident"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bhadra residents")));
    }

    @Test
    void theListDoesNotAffectAdmins() throws Exception {
        residentService.replace("cs23b051", ADMIN);
        google("admin", ADMIN, null);

        mockMvc.perform(get("/api/admin/residents").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    private ResultActions send(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String token, String text) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"" + text + "\"}"));
    }

    private ResultActions login(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\":\"" + token + "\",\"loginType\":\"STUDENT\"}"));
    }

    private void google(String token, String email, String hostedDomain) {
        Jwt.Builder jwt = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject(email)
                .claim("email", email)
                .claim("name", email)
                .claim("email_verified", true);
        if (hostedDomain != null) {
            jwt.claim("hd", hostedDomain);
        }
        when(jwtDecoder.decode(token)).thenReturn(jwt.build());
    }

    private static com.example.demo.hostel.model.User staff(String email) {
        com.example.demo.hostel.model.User user = new com.example.demo.hostel.model.User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(com.example.demo.hostel.model.Role.STAFF);
        return user;
    }
}
