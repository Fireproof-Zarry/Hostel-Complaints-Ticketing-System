import assert from 'node:assert/strict';
import test from 'node:test';
import { DRAFT_TTL_MS, draftKey, isDraftEmpty, mergeDraftFields, parseStoredDraft } from './complaintDraft.js';

const defaults = {
  title: '',
  description: '',
  category: 'Electrical',
  isCommunityArea: false,
  floor: 'Second',
  wing: '',
  availableTimeSlot: '',
  keyWithSecurity: false,
};
const now = 1_800_000_000_000;

test('an untouched form is not worth keeping as a draft', () => {
  assert.equal(isDraftEmpty(defaults), true);
  assert.equal(isDraftEmpty({ ...defaults, category: 'Plumbing', floor: 'First' }), true);
  assert.equal(isDraftEmpty({ ...defaults, title: '   ' }), true);
  assert.equal(isDraftEmpty(null), true);
});

test('any typed text or the key checkbox makes it a draft', () => {
  assert.equal(isDraftEmpty({ ...defaults, title: 'Fan' }), false);
  assert.equal(isDraftEmpty({ ...defaults, availableTimeSlot: 'Mon 2-5 PM' }), false);
  assert.equal(isDraftEmpty({ ...defaults, keyWithSecurity: true }), false);
});

test('drafts are stored per account, case-insensitively', () => {
  assert.equal(draftKey('Student@smail.iitm.ac.in'), draftKey('student@smail.iitm.ac.in'));
  assert.notEqual(draftKey('a@smail.iitm.ac.in'), draftKey('b@smail.iitm.ac.in'));
});

test('restores a stored draft over the defaults', () => {
  const form = { ...defaults, title: 'Fan not working', isCommunityArea: true, floor: 'First', wing: 'East' };
  const raw = JSON.stringify({ form, savedAt: now - 60_000 });
  assert.deepEqual(parseStoredDraft(raw, defaults, now), form);
});

test('drafts expire 7 days after they were last saved', () => {
  const form = { ...defaults, title: 'Fan' };
  assert.notEqual(parseStoredDraft(JSON.stringify({ form, savedAt: now - DRAFT_TTL_MS + 1000 }), defaults, now), null);
  assert.equal(parseStoredDraft(JSON.stringify({ form, savedAt: now - DRAFT_TTL_MS - 1000 }), defaults, now), null);
});

test('ignores unknown fields, wrong types and corrupt data', () => {
  const merged = mergeDraftFields({ title: 'Fan', isCommunityArea: 'yes', injected: '<script>' }, defaults);
  assert.equal(merged.title, 'Fan');
  assert.equal(merged.isCommunityArea, false);
  assert.equal('injected' in merged, false);
  assert.deepEqual(mergeDraftFields(null, defaults), defaults);
  assert.equal(parseStoredDraft('{not json', defaults, now), null);
  assert.equal(parseStoredDraft('null', defaults, now), null);
  // A draft without a save time (e.g. hand-edited) is treated as expired
  assert.equal(parseStoredDraft(JSON.stringify({ form: { title: 'Fan' } }), defaults, now), null);
});
