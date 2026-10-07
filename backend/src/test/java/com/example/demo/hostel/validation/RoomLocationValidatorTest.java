package com.example.demo.hostel.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomLocationValidatorTest {

    private final RoomLocationValidator validator = new RoomLocationValidator();

    @Test
    void acceptsFirstAndLastRoomNumbersAndRejectsAdjacentRangesForEveryFloor() {
        List<FloorRoomRange> ranges = List.of(
                new FloorRoomRange("Ground", "100", "199", "099", "200"),
                new FloorRoomRange("First", "200", "299", "199", "300"),
                new FloorRoomRange("Second", "300", "399", "299", "400"),
                new FloorRoomRange("Third", "400", "499", "399", "500"),
                new FloorRoomRange("Fourth", "500", "599", "499", "600"),
                new FloorRoomRange("Fifth", "600", "699", "599", "700"),
                new FloorRoomRange("Sixth", "700", "799", "699", "800")
        );

        for (FloorRoomRange range : ranges) {
            assertTrue(validator.isRoomOnFloor(range.floor(), range.firstValid()), range.floor());
            assertTrue(validator.isRoomOnFloor(range.floor(), range.lastValid()), range.floor());
            assertFalse(validator.isRoomOnFloor(range.floor(), range.beforeRange()), range.floor());
            assertFalse(validator.isRoomOnFloor(range.floor(), range.afterRange()), range.floor());
        }
    }

    @Test
    void acceptsFloorAliasesAndRejectsUnknownFloorOrNonThreeDigitRooms() {
        assertTrue(validator.isRoomOnFloor("1st floor", "201"));
        assertFalse(validator.isRoomOnFloor("Seventh", "801"));
        assertFalse(validator.isRoomOnFloor("Ground", "1000"));
    }

    private record FloorRoomRange(
            String floor,
            String firstValid,
            String lastValid,
            String beforeRange,
            String afterRange
    ) {
    }
}
