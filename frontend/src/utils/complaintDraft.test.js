import assert from 'node:assert/strict';
import test from 'node:test';
import { isDraftEmpty, mergeDraft } from './complaintDraft.js';

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

test('restores a stored draft over the defaults', () => {
  const draft = { ...defaults, title: 'Fan not working', isCommunityArea: true, floor: 'First', wing: 'East' };
  assert.deepEqual(mergeDraft(JSON.stringify(draft), defaults), draft);
});

test('ignores unknown fields, wrong types and corrupt data', () => {
  const merged = mergeDraft(JSON.stringify({ title: 'Fan', isCommunityArea: 'yes', injected: '<script>' }), defaults);
  assert.equal(merged.title, 'Fan');
  assert.equal(merged.isCommunityArea, false);
  assert.equal('injected' in merged, false);
  assert.deepEqual(mergeDraft('{not json', defaults), defaults);
  assert.deepEqual(mergeDraft('null', defaults), defaults);
});
