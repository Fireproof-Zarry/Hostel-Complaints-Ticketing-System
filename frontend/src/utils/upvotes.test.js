import assert from 'node:assert/strict';
import test from 'node:test';
import { replaceComplaint, toggleUpvoteOptimistically } from './upvotes.js';

test('upvoting flips the flag and adds one', () => {
  assert.deepEqual(
    toggleUpvoteOptimistically({ id: 1, upvotedByYou: false, upvoteCount: 2 }),
    { id: 1, upvotedByYou: true, upvoteCount: 3 },
  );
});

test('removing an upvote flips the flag and subtracts one, never below zero', () => {
  assert.deepEqual(
    toggleUpvoteOptimistically({ id: 1, upvotedByYou: true, upvoteCount: 3 }),
    { id: 1, upvotedByYou: false, upvoteCount: 2 },
  );
  assert.equal(toggleUpvoteOptimistically({ id: 1, upvotedByYou: true, upvoteCount: 0 }).upvoteCount, 0);
});

test('toggling does not mutate the original (needed to revert on failure)', () => {
  const original = { id: 1, upvotedByYou: false, upvoteCount: 2 };
  toggleUpvoteOptimistically(original);
  assert.deepEqual(original, { id: 1, upvotedByYou: false, upvoteCount: 2 });
});

test('replaceComplaint only changes the matching complaint', () => {
  const list = [{ id: 1, upvoteCount: 0 }, { id: 2, upvoteCount: 5 }];
  const updated = replaceComplaint(list, 2, (c) => ({ ...c, upvoteCount: 6 }));
  assert.deepEqual(updated, [{ id: 1, upvoteCount: 0 }, { id: 2, upvoteCount: 6 }]);
  assert.equal(updated[0], list[0]);
});
