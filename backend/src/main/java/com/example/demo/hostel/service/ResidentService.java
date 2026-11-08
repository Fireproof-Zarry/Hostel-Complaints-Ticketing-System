package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.ResidentListChange;
import com.example.demo.hostel.dto.ResidentSummary;
import com.example.demo.hostel.model.Resident;
import com.example.demo.hostel.repository.ResidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The hostel's resident list. Each year the hostel office pastes or uploads its list of
 * roll numbers; while the list has anyone on it, only those students can use the student
 * portal. An empty list lets every verified smail student in, so nobody is locked out
 * before the first upload. Staff and admins are never affected.
 *
 * The list is checked on every student request, so it is kept in memory: reloaded after
 * each change here, and every 5 minutes in case it was edited in the database directly.
 */
@Service
public class ResidentService implements ResidentDirectory {

    private static final Logger log = LoggerFactory.getLogger(ResidentService.class);
    private static final int SAMPLE_SIZE = 10;
    // Far more than any hostel's list; stops a wrong file being parsed at all
    private static final int MAX_TEXT_LENGTH = 1_000_000;

    private final ResidentRepository residentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    // null until first needed; replaced as a whole, never modified
    private volatile Set<String> residents;

    public ResidentService(
            ResidentRepository residentRepository,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate) {
        this.residentRepository = residentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public boolean isResident(String email) {
        Set<String> current = residents();
        if (current.isEmpty()) {
            return true;
        }
        String rollNumber = RollNumbers.fromEmail(email);
        return rollNumber != null && current.contains(rollNumber);
    }

    /** How many are on the list, and the roll numbers matching a search. */
    public ResidentSummary summary(String search) {
        String text = search == null ? "" : search.trim().toLowerCase(java.util.Locale.ROOT);
        List<String> matches = text.isEmpty() ? List.of()
                : residentRepository.findTop50ByRollNumberContainingOrderByRollNumberAsc(text).stream()
                        .map(Resident::getRollNumber)
                        .toList();
        return new ResidentSummary(residents().size(), matches);
    }

    /** What replacing the list with this text would do. Changes nothing. */
    public ResidentListChange preview(String text) {
        return compare(parse(text), residents(), false);
    }

    /**
     * Replaces the whole list with the roll numbers in this text, in one transaction.
     * Text without any roll number is refused, so a wrong file cannot empty the list.
     */
    public ResidentListChange replace(String text, String adminEmail) {
        RollNumbers.Parsed parsed = parse(text);
        ResidentListChange change = transactionTemplate.execute(status -> {
            Set<String> current = new HashSet<>(residentRepository.findAllRollNumbers());
            List<String> removed = current.stream().filter(roll -> !parsed.rollNumbers().contains(roll)).toList();
            List<String> added = parsed.rollNumbers().stream().filter(roll -> !current.contains(roll)).toList();

            jdbcTemplate.batchUpdate("DELETE FROM residents WHERE roll_number = ?", removed, 500,
                    (statement, roll) -> statement.setString(1, roll));
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            jdbcTemplate.batchUpdate("INSERT INTO residents (roll_number, added_at, added_by) VALUES (?, ?, ?)",
                    added, 500, (statement, roll) -> {
                        statement.setString(1, roll);
                        statement.setTimestamp(2, now);
                        statement.setString(3, adminEmail);
                    });
            return compare(parsed, current, true);
        });
        reload();
        log.info("Resident list replaced by {}: {} on the list, {} added, {} removed",
                adminEmail, change.found(), change.added(), change.removed());
        return change;
    }

    /** Adds one roll number (or smail address), e.g. a student who moved in mid-year. */
    public String add(String value, String adminEmail) {
        String rollNumber = requireRollNumber(value);
        if (residentRepository.existsById(rollNumber)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, rollNumber.toUpperCase(java.util.Locale.ROOT)
                    + " is already on the list");
        }
        residentRepository.save(new Resident(rollNumber, adminEmail));
        reload();
        return rollNumber;
    }

    /** Removes one roll number. If it was the last one, every smail student can sign in again. */
    public void remove(String value) {
        String rollNumber = requireRollNumber(value);
        if (!residentRepository.existsById(rollNumber)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, rollNumber.toUpperCase(java.util.Locale.ROOT)
                    + " is not on the list");
        }
        residentRepository.deleteById(rollNumber);
        reload();
    }

    /** Empties the list, which lets every verified smail student sign in. Returns how many were removed. */
    public long clear(String adminEmail) {
        long count = transactionTemplate.execute(status -> {
            long existing = residentRepository.count();
            residentRepository.deleteAllInBatch();
            return existing;
        });
        reload();
        log.info("Resident list cleared by {} ({} removed)", adminEmail, count);
        return count;
    }

    /** Picks up changes made in the database directly. */
    @Scheduled(fixedDelay = 5, initialDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void reload() {
        residents = Set.copyOf(residentRepository.findAllRollNumbers());
    }

    private Set<String> residents() {
        Set<String> current = residents;
        if (current == null) {
            reload();
            current = residents;
        }
        return current;
    }

    private static RollNumbers.Parsed parse(String text) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paste the roll numbers or choose a file first");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This file is too large. Upload only the list of residents.");
        }
        RollNumbers.Parsed parsed = RollNumbers.parse(text);
        if (parsed.rollNumbers().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No roll numbers found. They look like CS23B051.");
        }
        return parsed;
    }

    private static ResidentListChange compare(RollNumbers.Parsed parsed, Set<String> current, boolean applied) {
        List<String> added = parsed.rollNumbers().stream().filter(roll -> !current.contains(roll)).toList();
        List<String> removed = current.stream().filter(roll -> !parsed.rollNumbers().contains(roll)).sorted().toList();
        return new ResidentListChange(
                parsed.rollNumbers().size(),
                added.size(),
                removed.size(),
                parsed.rollNumbers().size() - added.size(),
                parsed.duplicates(),
                parsed.skippedCount(),
                added.stream().limit(SAMPLE_SIZE).toList(),
                removed.stream().limit(SAMPLE_SIZE).toList(),
                parsed.skippedLines(),
                applied);
    }

    private static String requireRollNumber(String value) {
        String rollNumber = RollNumbers.normalize(value);
        if (rollNumber == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a roll number like CS23B051");
        }
        return rollNumber;
    }
}
