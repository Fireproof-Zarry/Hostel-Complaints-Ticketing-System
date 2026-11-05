import assert from 'node:assert/strict';
import test from 'node:test';
import { formatServerDateTime, parseServerDate } from './serverDate.js';

test('reads a timestamp without an offset as UTC', () => {
  assert.equal(parseServerDate('2026-11-05T10:00:00').toISOString(), '2026-11-05T10:00:00.000Z');
  assert.equal(parseServerDate('2026-11-05T10:00:00.123456').toISOString(), '2026-11-05T10:00:00.123Z');
});

test('keeps an offset the server did send', () => {
  assert.equal(parseServerDate('2026-11-05T10:00:00Z').toISOString(), '2026-11-05T10:00:00.000Z');
  assert.equal(parseServerDate('2026-11-05T15:30:00+05:30').toISOString(), '2026-11-05T10:00:00.000Z');
});

test('missing or invalid timestamps are not dates', () => {
  assert.equal(parseServerDate(null), null);
  assert.equal(parseServerDate('not a date'), null);
  assert.equal(formatServerDateTime(null), '—');
  assert.equal(formatServerDateTime('not a date'), '—');
});
