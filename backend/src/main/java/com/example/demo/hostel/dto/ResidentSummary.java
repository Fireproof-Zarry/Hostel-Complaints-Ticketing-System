package com.example.demo.hostel.dto;

import java.util.List;

/**
 * The resident list at a glance.
 *
 * @param count   roll numbers on the list; 0 means every smail student can sign in
 * @param matches up to 50 roll numbers containing the search text (empty without one)
 */
public record ResidentSummary(long count, List<String> matches) {
}
