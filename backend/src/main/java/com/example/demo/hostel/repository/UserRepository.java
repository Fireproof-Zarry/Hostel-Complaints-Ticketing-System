package com.example.demo.hostel.repository;

import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    List<User> findByRole(Role role);

    List<User> findByRoleInOrderByEmailAsc(Collection<Role> roles);

    List<User> findByFcmToken(String fcmToken);

    @Query("SELECT u.fcmToken FROM User u WHERE u.role = :role AND u.fcmToken IS NOT NULL")
    List<String> findFcmTokensByRole(@Param("role") Role role);
}