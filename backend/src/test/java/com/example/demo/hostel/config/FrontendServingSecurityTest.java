package com.example.demo.hostel.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Docker image's app.serve-frontend opens the React pages, never the API. */
class FrontendServingSecurityTest {

    private static final String H2 = "spring.datasource.url=jdbc:h2:mem:frontend-serving-";

    @Nested
    @SpringBootTest(properties = {
            H2 + "on", "spring.datasource.username=sa", "spring.datasource.password=",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
            "app.serve-frontend=true"
    })
    @AutoConfigureMockMvc
    class WhenServingTheFrontend {

        @Autowired private MockMvc mockMvc;

        @Test
        void pagesNeedNoSignIn() throws Exception {
            int status = mockMvc.perform(get("/student")).andReturn().getResponse().getStatus();
            assertThat(status).isNotEqualTo(401);
        }

        @Test
        void theApiStillDoes() throws Exception {
            mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/admin/complaints")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @SpringBootTest(properties = {
            H2 + "off", "spring.datasource.username=sa", "spring.datasource.password=",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
    })
    @AutoConfigureMockMvc
    class OnRender {

        @Autowired private MockMvc mockMvc;

        @Test
        void nothingOutsideTheApiIsOpened() throws Exception {
            mockMvc.perform(get("/student")).andExpect(status().isUnauthorized());
        }
    }
}
