package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.StaffMemberView;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs schema_nov06.sql against real PostgreSQL, starting from the check an
 * older database has on users.role. H2 builds a fresh table, so it cannot catch
 * this. Skipped when Docker is unavailable; runs in CI.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.sql.init.mode=never"
})
class StaffRolePostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private StaffService staffService;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void addingStaffFailsOnAnOldDatabaseAndWorksAfterTheMigration() {
        jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
        jdbcTemplate.execute("ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('STUDENT', 'ADMIN'))");

        assertThatThrownBy(() -> staffService.addStaff("warden@gmail.com"))
                .isInstanceOf(DataIntegrityViolationException.class);

        migrate();
        migrate();

        StaffMemberView added = staffService.addStaff("warden@gmail.com");
        assertThat(added.role()).isEqualTo(Role.STAFF);
    }

    private void migrate() {
        new ResourceDatabasePopulator(new ClassPathResource("schema_nov06.sql")).execute(dataSource);
    }
}
