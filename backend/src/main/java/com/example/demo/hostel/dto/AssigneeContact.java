package com.example.demo.hostel.dto;

/**
 * Who is handling a complaint, as shown to the student who filed it. Only the
 * name and phone number: never the assignee's email or other account details.
 */
public record AssigneeContact(String name, String phoneNumber) {
}
