package com.example.demo.hostel.repository;

import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    List<User> findByRole(Role role);
}