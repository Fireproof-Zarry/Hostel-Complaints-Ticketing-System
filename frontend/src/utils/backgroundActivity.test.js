import assert from 'node:assert/strict';
import test from 'node:test';
import { getBackgroundActivity, trackBackground } from './backgroundActivity.js';

// Lets the promise callbacks inside trackBackground run
const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

test('a batch is busy until its last request settles, then reports success', async () => {
  const before = getBackgroundActivity().batch;
  let finishFirst;
  trackBackground(new Promise((resolve) => { finishFirst = resolve; }));
  trackBackground(Promise.resolve(true));
  await flush();

  assert.equal(getBackgroundActivity().busy, true);

  finishFirst({ ok: true });
  await flush();

  assert.deepEqual(getBackgroundActivity(), { busy: false, batch: before + 1, lastBatchOk: true });
});

test('a rejected request, false, or an unsuccessful Response fails the batch', async () => {
  for (const failing of [Promise.reject(new Error('down')), Promise.resolve(false), Promise.resolve(new Response('', { status: 500 }))]) {
    trackBackground(Promise.resolve(true));
    trackBackground(failing);
    await flush();
    assert.equal(getBackgroundActivity().lastBatchOk, false);
  }
});

test('a failure does not carry over to the next batch', async () => {
  trackBackground(Promise.resolve(false));
  await flush();
  trackBackground(Promise.resolve(true));
  await flush();

  assert.equal(getBackgroundActivity().lastBatchOk, true);
});

test('returns the promise it was given', () => {
  const promise = Promise.resolve(1);
  assert.equal(trackBackground(promise), promise);
});
