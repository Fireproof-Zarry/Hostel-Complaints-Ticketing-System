export const HOSTEL_FLOORS = [
  { name: 'Ground', roomHundreds: 1 },
  { name: 'First', roomHundreds: 2 },
  { name: 'Second', roomHundreds: 3 },
  { name: 'Third', roomHundreds: 4 },
  { name: 'Fourth', roomHundreds: 5 },
  { name: 'Fifth', roomHundreds: 6 },
  { name: 'Sixth', roomHundreds: 7 },
];

const roomHundredsByFloor = new Map(
  HOSTEL_FLOORS.flatMap(({ name, roomHundreds }) => {
    const ordinal = {
      First: '1st',
      Second: '2nd',
      Third: '3rd',
      Fourth: '4th',
      Fifth: '5th',
      Sixth: '6th',
    }[name];
    return [
      [name.toLowerCase(), roomHundreds],
      ...(ordinal ? [[ordinal, roomHundreds]] : []),
    ];
  })
);

export function getRoomHundredsForFloor(floor) {
  const normalizedFloor = floor?.trim().toLowerCase().replace(/\s+floor$/, '');
  return roomHundredsByFloor.get(normalizedFloor) ?? null;
}

export function isRoomOnFloor(floor, room) {
  const roomHundreds = getRoomHundredsForFloor(floor);
  const normalizedRoom = String(room ?? '');
  return roomHundreds !== null
    && /^\d{3}$/.test(normalizedRoom)
    && Number(normalizedRoom[0]) === roomHundreds;
}
