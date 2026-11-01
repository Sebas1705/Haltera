import { readFileSync } from 'node:fs';
import { afterAll, beforeAll, beforeEach, describe, expect, test } from 'vitest';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, orderBy, query, setDoc, where, writeBatch } from 'firebase/firestore';

// emulators:exec exports where it listens (firebase.json: 8080); read it so another port works locally.
const [emulatorHost, emulatorPort] = (process.env.FIRESTORE_EMULATOR_HOST ?? '127.0.0.1:8080').split(':');
let env;
beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-haltera',
    firestore: {
      rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8'),
      host: emulatorHost,
      port: Number(emulatorPort),
    },
  });
});
afterAll(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'users/ana/routines/r1'), synced(1_000));
    await setDoc(doc(ctx.firestore(), 'users/luis/sessions/s1'), synced(1_000));
  });
});

/** A synced document as the app writes it (data:remote RemoteDocument). */
const synced = (updatedAt, deleted = false) => ({ payload: deleted ? '' : '{"id":"x"}', updatedAt, deleted, schema: 1 });
/** The same with the upload time the engine adds (data:remote RemoteDocument.pushedAt). */
const pushed = (updatedAt, pushedAt) => ({ ...synced(updatedAt), pushedAt });
const as = (uid) => env.authenticatedContext(uid).firestore();
const anonymous = (uid) => env.authenticatedContext(uid, { firebase: { sign_in_provider: 'anonymous' } }).firestore();
const nobody = () => env.unauthenticatedContext().firestore();

describe('own data', () => {
  test('a user reads and writes their own documents', async () => {
    await assertSucceeds(getDoc(doc(as('ana'), 'users/ana/routines/r1')));
    await assertSucceeds(setDoc(doc(as('ana'), 'users/ana/routines/r2'), synced(2_000)));
    await assertSucceeds(setDoc(doc(as('ana'), 'users/ana/routines/r1'), synced(3_000, true)));
    await assertSucceeds(deleteDoc(doc(as('ana'), 'users/ana/routines/r1')));
  });
  test('anonymous users have the same access to their own tree', async () => {
    await assertSucceeds(setDoc(doc(anonymous('anon1'), 'users/anon1/bodyWeight/b1'), synced(1)));
    await assertSucceeds(getDoc(doc(anonymous('anon1'), 'users/anon1/bodyWeight/b1')));
  });
  test('the changes query (updatedAt > cursor, ordered) is allowed on the own collection', async () => {
    const q = query(collection(as('ana'), 'users/ana/routines'), where('updatedAt', '>', 0));
    await assertSucceeds(getDocs(q));
  });
  test('batched writes across collections of the own tree succeed', async () => {
    const db = as('ana');
    const batch = writeBatch(db);
    batch.set(doc(db, 'users/ana/sessions/s9'), synced(5));
    batch.set(doc(db, 'users/ana/settings/user'), synced(5));
    await assertSucceeds(batch.commit());
  });
});

describe('other users and outsiders', () => {
  test('a user cannot read or write another user\'s data', async () => {
    await assertFails(getDoc(doc(as('ana'), 'users/luis/sessions/s1')));
    await assertFails(getDocs(collection(as('ana'), 'users/luis/sessions')));
    await assertFails(setDoc(doc(as('ana'), 'users/luis/sessions/s2'), synced(1)));
    await assertFails(deleteDoc(doc(as('ana'), 'users/luis/sessions/s1')));
  });
  test('a batch that touches another user fails as a whole', async () => {
    const db = as('ana');
    const batch = writeBatch(db);
    batch.set(doc(db, 'users/ana/routines/r5'), synced(1));
    batch.set(doc(db, 'users/luis/routines/r5'), synced(1));
    await assertFails(batch.commit());
  });
  test('signed-out clients get nothing', async () => {
    await assertFails(getDoc(doc(nobody(), 'users/ana/routines/r1')));
    await assertFails(setDoc(doc(nobody(), 'users/ana/routines/r9'), synced(1)));
  });
  test('the users collection itself cannot be listed', async () => {
    await assertFails(getDocs(collection(as('ana'), 'users')));
  });
  test('anything outside users/ is denied', async () => {
    await assertFails(setDoc(doc(as('ana'), 'catalog/e1'), { name: 'Bench' }));
    await assertFails(getDoc(doc(as('ana'), 'catalog/e1')));
  });
});

// The queries FirestoreRemoteStore.changes runs, and the write semantics the sync relies on (docs/adr/0009).
describe('sync queries and writes', () => {
  const ids = (snapshot) => snapshot.docs.map((d) => d.id).sort();
  const colRef = () => collection(as('ana'), 'users/ana/exercises');
  const DAY = 24 * 3_600_000;
  const cursor = 19.5 * DAY; // what a phone that synced `recent` has seen

  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, 'users/ana/exercises/legacy'), synced(1_000)); // written before pushedAt existed
      await setDoc(doc(db, 'users/ana/exercises/old'), pushed(1_000, 20 * DAY)); // changed on day 0 by a phone that was offline, uploaded on day 20
      await setDoc(doc(db, 'users/ana/exercises/recent'), pushed(19 * DAY, 19.2 * DAY));
    });
  });

  test('the cursor query on pushedAt finds a document uploaded late although it was changed long ago', async () => {
    const byPushedAt = await assertSucceeds(
      getDocs(query(colRef(), where('pushedAt', '>', cursor), orderBy('pushedAt', 'asc'))),
    );
    expect(ids(byPushedAt)).toEqual(['old']);
    // The query it replaces does not: this is the bug.
    const byUpdatedAt = await assertSucceeds(
      getDocs(query(colRef(), where('updatedAt', '>', cursor - 7 * DAY), orderBy('updatedAt', 'asc'))),
    );
    expect(ids(byUpdatedAt)).not.toContain('old');
  });

  test('a filter on pushedAt skips documents without the field, the unfiltered query from 0 returns them', async () => {
    expect(ids(await getDocs(query(colRef(), where('pushedAt', '>', 0), orderBy('pushedAt', 'asc'))))).toEqual([
      'old',
      'recent',
    ]);
    const all = await assertSucceeds(getDocs(query(colRef(), orderBy('updatedAt', 'asc'))));
    expect(ids(all)).toEqual(['legacy', 'old', 'recent']);
  });

  test('a push replaces the whole document: nothing of the previous version survives', async () => {
    const ref = doc(as('ana'), 'users/ana/exercises/old');
    await setDoc(ref, { payload: '{"v":2}', updatedAt: 2_000, deleted: false, schema: 1, pushedAt: 9_500 });
    expect((await getDoc(ref)).data()).toEqual({
      payload: '{"v":2}',
      updatedAt: 2_000,
      deleted: false,
      schema: 1,
      pushedAt: 9_500,
    });
    // A version without the new field (an older app) drops it instead of keeping the stale value.
    await setDoc(ref, synced(3_000, true));
    expect((await getDoc(ref)).data()).toEqual({ payload: '', updatedAt: 3_000, deleted: true, schema: 1 });
  });
});
