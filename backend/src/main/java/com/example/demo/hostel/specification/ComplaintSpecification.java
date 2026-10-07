package com.example.demo.hostel.specification;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Status;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class ComplaintSpecification {

    public static Specification<Complaint> withFilters(
            Status status,
            String category,
            String floor,
            String assignedTo,
            LocalDateTime from,
            LocalDateTime to) {

        return (root, query, criteriaBuilder) -> {

            var predicates = criteriaBuilder.conjunction();

            if (status != null) {
                predicates = criteriaBuilder.and(
                        predicates,
                        criteriaBuilder.equal(root.get("status"), status)
                );
            }

            if (category != null && !category.isBlank()) {
                predicates = criteriaBuilder.and(
                        predicates,
                        criteriaBuilder.equal(
                                criteriaBuilder.lower(criteriaBuilder.trim(root.<String>get("category"))),
                                category.trim().toLowerCase()
                        )
                );
            }

            if (floor != null && !floor.isBlank()) {
                predicates = criteriaBuilder.and(
                        predicates,
                        criteriaBuilder.equal(
                                criteriaBuilder.lower(criteriaBuilder.trim(root.<String>get("floor"))),
                                floor.trim().toLowerCase()
                        )
                );
            }

            if (assignedTo != null && !assignedTo.isBlank()) {
                predicates = criteriaBuilder.and(
                        predicates,
                        criteriaBuilder.equal(
                                criteriaBuilder.lower(criteriaBuilder.trim(root.<String>get("assignedTo"))),
                                assignedTo.trim().toLowerCase()
                        )
                );
            }

            if (from != null) {
                predicates = criteriaBuilder.and(
                        predicates,
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("createdAt"),
                                from
                        )
                );
            }

            if (to != null) {
                predicates = criteriaBuilder.and(
                        predicates,
                        criteriaBuilder.lessThanOrEqualTo(
                                root.get("createdAt"),
                                to
                        )
                );
            }

            return predicates;
        };
    }
}