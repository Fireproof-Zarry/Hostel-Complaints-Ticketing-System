package com.example.demo.hostel.controller;

import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Student Login and Staff Login rules over HTTP, one test per case. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:login-api-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "app.auth.admin-emails=bhadra@smail.iitm.ac.in,owner@gmail.com"
})
@AutoConfigureMockMvc
class LoginApiIntegrationTest {

    private static final String SMAIL = "smail.iitm.ac.in";
    private static final String STUDENT = "student@smail.iitm.ac.in";
    private static final String STAFF = "ravi@gmail.com";
    private static final String ADMIN = "bhadra@smail.iitm.ac.in";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @MockitoBean private JwtDecoder jwtDecoder;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    // ---- Student Login ----

    @Test
    void aNewSmailStudentIsCreated() throws Exception {
        google("token", STUDENT, "New Student", SMAIL, true);

        login("token", "STUDENT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.email").value(STUDENT));

        assertThat(userRepository.findByEmail(STUDENT)).isPresent();
    }

    @Test
    void anExistingSmailStudentIsLoggedIn() throws Exception {
        userRepository.save(user(STUDENT, Role.STUDENT));
        google("token", STUDENT, "Student", SMAIL, true);

        login("token", "STUDENT").andExpect(status().isOk());

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void aPersonalGmailIsRefusedAndNotCreated() throws Exception {
        google("token", "someone@gmail.com", "Someone", null, true);

        login("token", "STUDENT")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Students must sign in with their @smail.iitm.ac.in account."));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void anSmailAddressWithoutGooglesDomainClaimIsRefused() throws Exception {
        google("token", STUDENT, "Spoofed", null, true);

        login("token", "STUDENT").andExpect(status().isUnauthorized());

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void staffOnTheStudentTabAreToldToUseStaffLogin() throws Exception {
        userRepository.save(user(STAFF, Role.STAFF));
        google("token", STAFF, "Ravi", null, true);

        login("token", "STUDENT")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Please use Staff Login."));
    }

    @Test
    void adminsWithSmailOnTheStudentTabAreToldToUseStaffLogin() throws Exception {
        google("token", ADMIN, "Bhadra Office", SMAIL, true);

        login("token", "STUDENT")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Please use Staff Login."));

        assertThat(userRepository.count()).isZero();
    }

    // ---- Staff Login ----

    @Test
    void addedStaffCanSignInForTheFirstTimeAndKeepTheirRole() throws Exception {
        userRepository.save(user(STAFF, Role.STAFF));
        google("token", STAFF, "Ravi Kumar", null, true);

        login("token", "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STAFF"));

        User staff = userRepository.findByEmail(STAFF).orElseThrow();
        assertThat(staff.getRole()).isEqualTo(Role.STAFF);
        assertThat(staff.getName()).isEqualTo("Ravi Kumar");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void aGmailNobodyAddedIsRefusedAndNotCreated() throws Exception {
        google("token", "someone@gmail.com", "Someone", null, true);

        login("token", "ADMIN")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Unauthorized staff email"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void removedStaffAreRefused() throws Exception {
        userRepository.save(user(STAFF, Role.STUDENT));
        google("token", STAFF, "Ravi", null, true);

        login("token", "ADMIN").andExpect(status().isForbidden());
    }

    @Test
    void studentsAreRefusedAndANewOneIsNotCreated() throws Exception {
        google("token", STUDENT, "Student", SMAIL, true);

        login("token", "ADMIN")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Unauthorized staff email"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void listedAdminsAreCreatedOnTheirFirstStaffLogin() throws Exception {
        google("token", ADMIN, "Bhadra Office", SMAIL, true);

        login("token", "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        assertThat(userRepository.findByEmail(ADMIN).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void listedAdminsCanUseAPersonalEmail() throws Exception {
        google("token", "owner@gmail.com", "Owner", null, true);

        login("token", "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    // ---- bad requests ----

    @Test
    void anEmailGoogleHasNotVerifiedIsRefused() throws Exception {
        google("student-token", STUDENT, "Student", SMAIL, false);
        google("admin-token", ADMIN, "Admin", SMAIL, false);

        login("student-token", "STUDENT").andExpect(status().isUnauthorized());
        login("admin-token", "ADMIN").andExpect(status().isUnauthorized());
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void aTokenThatFailsVerificationIsRefused() throws Exception {
        when(jwtDecoder.decode("forged")).thenThrow(new BadJwtException("bad signature"));

        login("forged", "STUDENT")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Your Google sign-in could not be verified. Please try again."));
    }

    @Test
    void theCredentialAndLoginTypeAreRequired() throws Exception {
        post("{\"loginType\":\"STUDENT\"}").andExpect(status().isBadRequest());
        post("{\"credential\":\"token\"}").andExpect(status().isBadRequest());
        post("{\"credential\":\"token\",\"loginType\":\"SUPERUSER\"}").andExpect(status().isBadRequest());
    }

    private ResultActions login(String token, String loginType) throws Exception {
        return post("{\"credential\":\"" + token + "\",\"loginType\":\"" + loginType + "\"}");
    }

    private ResultActions post(String body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void google(String token, String email, String name, String hostedDomain, boolean verified) {
        Jwt.Builder jwt = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .claim("email", email)
                .claim("name", name)
                .claim("email_verified", verified);
        if (hostedDomain != null) {
            jwt.claim("hd", hostedDomain);
        }
        when(jwtDecoder.decode(token)).thenReturn(jwt.build());
    }

    private static User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        return user;
    }
}
