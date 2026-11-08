package com.example.demo.hostel.repository;

import com.example.demo.hostel.model.Resident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** The hostel's resident list (roll numbers). */
public interface ResidentRepository extends JpaRepository<Resident, String> {

    /** Every roll number on the list. */
    @Query("SELECT r.rollNumber FROM Resident r")
    List<String> findAllRollNumbers();

    /** Up to 50 roll numbers containing the search text, in order. */
    List<Resident> findTop50ByRollNumberContainingOrderByRollNumberAsc(String text);
}
