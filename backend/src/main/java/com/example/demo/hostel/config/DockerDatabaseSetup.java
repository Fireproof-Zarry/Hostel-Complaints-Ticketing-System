package com.example.demo.hostel.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The all-in-one Docker image only (profile "docker"): prepares the container's own empty
 * database. Hibernate creates the tables; this adds what the Supabase migrations would
 * (categories, pg_trgm, indexes) and, the first time, the demo data. Runs before the web
 * server starts, so the first page load already sees it.
 */
@Component
@Profile("docker")
@DependsOn("entityManagerFactory")
public class DockerDatabaseSetup {

    private static final Logger log = LoggerFactory.getLogger(DockerDatabaseSetup.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public DockerDatabaseSetup(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @PostConstruct
    void setUp() {
        jdbcTemplate.execute(script("docker/setup.sql"));
        Long complaints = jdbcTemplate.queryForObject("SELECT count(*) FROM complaints", Long.class);
        if (complaints != null && complaints == 0) {
            // All or nothing, so a failed load is retried on the next start
            transactionTemplate.executeWithoutResult(status -> jdbcTemplate.execute(script("docker/demo-data.sql")));
            log.info("Loaded the demo complaints");
        }
    }

    private static String script(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
    }
}
