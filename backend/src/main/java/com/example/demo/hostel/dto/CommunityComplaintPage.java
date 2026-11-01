package com.example.demo.hostel.dto;

import java.util.List;

/** One page of the community dashboard (a stable JSON shape, unlike Spring's Page). */
public record CommunityComplaintPage(
        List<CommunityComplaintView> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
