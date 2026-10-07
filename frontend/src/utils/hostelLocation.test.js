import assert from 'node:assert/strict';
import test from 'node:test';
import { HOSTEL_FLOORS, isRoomOnFloor } from './hostelLocation.js';

test('frontend floor-to-room ranges match the seven-floor convention', () => {
  const expectedRanges = [
    ['100', '199'],
    ['200', '299'],
    ['300', '399'],
    ['400', '499'],
    ['500', '599'],
    ['600', '699'],
    ['700', '799'],
  ];

  assert.deepEqual(
    HOSTEL_FLOORS.map(({ roomHundreds }) => [
      `${roomHundreds}00`,
      `${roomHundreds}99`,
    ]),
    expectedRanges
  );
});

test('accepts first and last room numbers and rejects adjacent numbers for every floor', () => {
  HOSTEL_FLOORS.forEach(({ name, roomHundreds }) => {
    assert.equal(isRoomOnFloor(name, `${roomHundreds}00`), true, `${name} first room`);
    assert.equal(isRoomOnFloor(name, `${roomHundreds}99`), true, `${name} last room`);
    assert.equal(isRoomOnFloor(name, `${roomHundreds - 1}99`), false, `${name} below range`);
    assert.equal(isRoomOnFloor(name, `${roomHundreds + 1}00`), false, `${name} above range`);
  });
});

test('rejects room values that are not exactly three digits', () => {
  assert.equal(isRoomOnFloor('Ground', ' 100'), false);
  assert.equal(isRoomOnFloor('Ground', '100 '), false);
  assert.equal(isRoomOnFloor('Ground', '1000'), false);
});
