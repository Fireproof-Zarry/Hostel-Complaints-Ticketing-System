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
}
