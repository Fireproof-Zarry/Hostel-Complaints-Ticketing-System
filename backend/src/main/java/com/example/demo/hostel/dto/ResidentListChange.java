package com.example.demo.hostel.dto;

import java.util.List;

/**
 * What replacing the resident list does (preview) or did.
 *
 * @param found          distinct roll numbers in the uploaded text
 * @param added          on the new list but not the current one
 * @param removed        on the current list but not the new one: they lose access
 * @param unchanged      on both
 * @param duplicates     roll numbers repeated in the text (counted once)
 * @param skippedCount   non-empty lines with no roll number
 * @param addedSample    the first few added roll numbers
 * @param removedSample  the first few removed roll numbers
 * @param skippedLines   the first few skipped lines
 * @param applied        false for a preview
 */
public record ResidentListChange(
        int found,
        int added,
        int removed,
        int unchanged,
        int duplicates,
        int skippedCount,
        List<String> addedSample,
        List<String> removedSample,
        List<String> skippedLines,
        boolean applied
) {
}
