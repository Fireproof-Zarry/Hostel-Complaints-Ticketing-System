package com.example.demo.hostel.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ComplaintJsonTest {

    @Test
    void serializesCategoryAndLocationAsFlatStrings() {
        Category category = new Category();
        category.setName("Plumbing");

        Location location = new Location();
        location.setFloor("Second");
        location.setRoomNumber("301");

        Complaint complaint = new Complaint();
        complaint.setCategory(category);
        complaint.setLocation(location);

        JsonNode json = JsonMapper.builder().build().valueToTree(complaint);

        assertThat(json.get("category").asString()).isEqualTo("Plumbing");
        assertThat(json.get("floor").asString()).isEqualTo("Second");
        assertThat(json.get("room").asString()).isEqualTo("301");
        assertThat(json.has("location")).isFalse();
        assertThat(json.has("categoryName")).isFalse();
    }

    @Test
    void neverSerializesStudentDeviceToken() {
        User student = new User();
        student.setEmail("student@smail.iitm.ac.in");
        student.setFcmToken("secret-device-token");

        Complaint complaint = new Complaint();
        complaint.setStudent(student);

        JsonNode json = JsonMapper.builder().build().valueToTree(complaint);

        assertThat(json.get("student").get("email").asString()).isEqualTo("student@smail.iitm.ac.in");
        assertThat(json.get("student").has("fcmToken")).isFalse();
        assertThat(json.toString()).doesNotContain("secret-device-token");
    }

    @Test
    void reopeningClearsArchivedFlagButFinalStatusesKeepIt() {
        Complaint complaint = new Complaint();
        complaint.setStatus(Status.RESOLVED);
        complaint.setArchived(true);

        complaint.setStatus(Status.REJECTED);
        assertThat(complaint.isArchived()).isTrue();

        complaint.setStatus(Status.PENDING);
        assertThat(complaint.isArchived()).isFalse();
    }

    @Test
    void serializesArchivedFlag() {
        Complaint complaint = new Complaint();
        complaint.setArchived(true);

        JsonNode json = JsonMapper.builder().build().valueToTree(complaint);

        assertThat(json.get("archived").asBoolean()).isTrue();
    }
}
