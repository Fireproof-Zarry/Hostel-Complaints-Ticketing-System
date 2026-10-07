package com.example.demo.hostel.validation;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class RoomLocationValidator {

    private static final Map<String, Integer> ROOM_HUNDREDS_BY_FLOOR = Map.ofEntries(
            Map.entry("ground", 1),
            Map.entry("first", 2),
            Map.entry("1st", 2),
            Map.entry("second", 3),
            Map.entry("2nd", 3),
            Map.entry("third", 4),
            Map.entry("3rd", 4),
            Map.entry("fourth", 5),
            Map.entry("4th", 5),
            Map.entry("fifth", 6),
            Map.entry("5th", 6),
            Map.entry("sixth", 7),
            Map.entry("6th", 7)
    );

    public boolean isRoomOnFloor(String floor, String room) {
        if (floor == null || room == null) {
            return false;
        }

        String normalizedFloor = floor.trim().toLowerCase(Locale.ROOT).replaceFirst("\\s+floor$", "");
        Integer expectedHundreds = ROOM_HUNDREDS_BY_FLOOR.get(normalizedFloor);
        return expectedHundreds != null
                && room.matches("[1-7][0-9]{2}")
                && room.charAt(0) - '0' == expectedHundreds;
    }
}
