package com.example.demo.hostel.repository;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.WorkOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {

        List<WorkOrder> findByComplaintOrderByCreatedAtDesc(Complaint complaint);
}
