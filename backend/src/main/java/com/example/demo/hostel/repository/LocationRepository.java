package com.example.demo.hostel.repository;

import com.example.demo.hostel.model.Location;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Finds an existing room or community area, so complaints there share one Location row. */
@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {

        // Rooms in the single-wing hostel have no wing value
        Optional<Location> findByFloorAndRoomNumberAndWingIsNull(String floor, String roomNumber);

        // Community areas (corridors, washrooms, common rooms) have a wing but no room
        Optional<Location> findByFloorAndWingIgnoreCaseAndRoomNumberIsNull(String floor, String wing);
}
