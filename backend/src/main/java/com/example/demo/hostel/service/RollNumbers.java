package com.example.demo.hostel.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IIT Madras roll numbers: 2 letters (department), 2 digits (year of joining), 1 letter
 * (degree: b BTech, m MTech, d PhD, ...) and 3 digits, e.g. CS23B051. A student's smail
 * address is always rollno@smail.iitm.ac.in.
 */
public final class RollNumbers {

    private static final Pattern ROLL_NUMBER = Pattern.compile("[a-z]{2}\\d{2}[a-z]\\d{3}");
    // A roll number anywhere in a line: in a CSV cell, a pasted Excel column or an email
    // address, but not inside a longer word or number
    private static final Pattern IN_TEXT = Pattern.compile(
            "(?<![a-z0-9])[a-z]{2}\\d{2}[a-z]\\d{3}(?![a-z0-9])", Pattern.CASE_INSENSITIVE);
    private static final int SKIPPED_LINES_SHOWN = 10;
    private static final int SKIPPED_LINE_LENGTH = 80;

    private RollNumbers() {
    }

    /** The roll number in lower case, or null if it is not one. Accepts an smail address too. */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim().toLowerCase(Locale.ROOT);
        int at = candidate.indexOf('@');
        if (at >= 0) {
            candidate = candidate.substring(0, at);
        }
        return ROLL_NUMBER.matcher(candidate).matches() ? candidate : null;
    }

    /** The roll number of an smail address, or null for any other address. */
    public static String fromEmail(String email) {
        if (email == null || !email.toLowerCase(Locale.ROOT).endsWith("@smail.iitm.ac.in")) {
            return null;
        }
        return normalize(email);
    }

    /**
     * Every roll number in pasted text or a CSV file, in the order they appear. Names,
     * room numbers and headings are ignored; lines without any roll number are reported.
     */
    public static Parsed parse(String text) {
        Set<String> found = new LinkedHashSet<>();
        List<String> skipped = new ArrayList<>();
        int skippedCount = 0;
        int duplicates = 0;
        for (String line : (text == null ? "" : text).split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            Matcher matcher = IN_TEXT.matcher(line);
            boolean any = false;
            while (matcher.find()) {
                any = true;
                if (!found.add(matcher.group().toLowerCase(Locale.ROOT))) {
                    duplicates++;
                }
            }
            if (!any) {
                skippedCount++;
                if (skipped.size() < SKIPPED_LINES_SHOWN) {
                    String shown = line.strip();
                    skipped.add(shown.length() > SKIPPED_LINE_LENGTH
                            ? shown.substring(0, SKIPPED_LINE_LENGTH) + "…" : shown);
                }
            }
        }
        return new Parsed(found, duplicates, skippedCount, skipped);
    }

    /**
     * @param rollNumbers  distinct roll numbers, lower case, in order of appearance
     * @param duplicates   roll numbers that appeared again after the first time
     * @param skippedCount non-empty lines with no roll number
     * @param skippedLines the first few of those lines, to show the admin
     */
    public record Parsed(Set<String> rollNumbers, int duplicates, int skippedCount, List<String> skippedLines) {
    }
}
