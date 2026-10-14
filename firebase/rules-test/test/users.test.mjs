import { readFileSync } from 'node:fs';
import { afterAll, beforeAll, beforeEach, describe, test } from 'vitest';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, where, writeBatch } from 'firebase/firestore';

let env;
beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-ironlog',
    firestore: {
      rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8'),
      host: '127.0.0.1',
      port: 8080,
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
