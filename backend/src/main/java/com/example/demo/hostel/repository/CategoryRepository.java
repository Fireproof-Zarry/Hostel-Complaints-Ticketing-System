package com.example.demo.hostel.repository;

import com.example.demo.hostel.model.Category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Complaint categories. */
@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

        /** Used to resolve the category named in a new complaint. */
        Optional<Category> findByNameIgnoreCase(String name);
}
