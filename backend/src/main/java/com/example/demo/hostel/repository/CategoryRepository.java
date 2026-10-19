package com.example.demo.hostel.repository;

import com.example.demo.hostel.model.Category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

        Optional<Category> findByNameIgnoreCase(String name);
}
