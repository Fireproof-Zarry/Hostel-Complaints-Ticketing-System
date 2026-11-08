package com.example.demo.hostel.config;

import com.example.demo.hostel.service.CommunityComplaintService;
import com.example.demo.hostel.service.DuplicateDetectionService;
import com.example.demo.hostel.service.FcmNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The all-in-one Docker image's database setup and demo data, on an empty PostgreSQL like
 * the container's own. Skipped when Docker is unavailable; runs in CI.
 */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("docker")
@SpringBootTest
class DockerDatabaseSetupPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean private FcmNotificationService fcmNotificationService;

    @Autowired private DockerDatabaseSetup dockerDatabaseSetup;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CommunityComplaintService communityComplaintService;
    @Autowired private DuplicateDetectionService duplicateDetectionService;

    @Test
    void loadsTheCategoriesAndDemoComplaints() {
        assertThat(jdbcTemplate.queryForList("SELECT name FROM categories ORDER BY name", String.class))
                .containsExactly("Carpentry", "Cleaning", "Electrical", "IT", "Plumbing");
        assertThat(count("complaints")).isEqualTo(10);
        List<Map<String, Object>> byStatus = jdbcTemplate.queryForList(
                "SELECT status, count(*) AS n FROM complaints GROUP BY status ORDER BY status");
        assertThat(byStatus).extracting(row -> row.get("status"))
                .containsExactly("ASSIGNED", "IN_PROGRESS", "PENDING", "REJECTED", "RESOLVED");
    }

    @Test
    void everyActiveComplaintHasOneActiveWorkOrder() {
        Long withoutWorkOrder = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM complaints c
                WHERE c.status IN ('ASSIGNED', 'IN_PROGRESS')
                  AND NOT EXISTS (SELECT 1 FROM work_orders w
                                  WHERE w.complaint_id = c.id AND w.status IN ('OPEN', 'IN_PROGRESS'))
                """, Long.class);
        assertThat(withoutWorkOrder).isZero();
    }

    @Test
    void runningAgainChangesNothing() {
        dockerDatabaseSetup.setUp();

        assertThat(count("complaints")).isEqualTo(10);
        assertThat(count("categories")).isEqualTo(5);
    }

    @Test
    void newComplaintsGetTheNextId() {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO complaints (version, title, description, category_id, location_id, status,
                                        key_with_security, is_archived, student_id, created_at, updated_at)
                SELECT 0, 'Test', 'Test', category_id, location_id, status, false, false, student_id, now(), now()
                FROM complaints WHERE id = 1
                RETURNING id
                """, Long.class);
        assertThat(id).isEqualTo(11);
        jdbcTemplate.update("DELETE FROM complaints WHERE id = ?", id);
    }

    @Test
    void thePostgresOnlyFeaturesWorkOnTheDemoData() {
        assertThat(communityComplaintService.getCommunityComplaints(
                "demo.ananya@smail.iitm.ac.in", null, null, 0, 20).items()).isNotEmpty();
        assertThat(duplicateDetectionService.findPotentialDuplicates(
                "demo.divya@smail.iitm.ac.in", "Corridor lights flickering", true, "Second",
                "Corridor near rooms 310-320", null)).isNotEmpty();
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
