/**
 * Security rules tests for firestore.rules.
 *
 * Run with `npm test` in this directory; the script boots the Firestore emulator around the
 * run. Nothing here talks to a real project -- `demo-` project ids never leave the emulator.
 *
 * Two client generations have to keep working against one rule set, so the suite is written
 * from their point of view rather than rule-by-rule:
 *
 *   0.1.0-alpha  reads receipts with a collection-group query, keeps receipts nested under
 *                sources, and edits them inside transactions that also touch the parents.
 *   current      keeps receipts nested under sources too, and syncs with batched writes and
 *                incremental collection-group reads. 0.1.5-alpha briefly wrote them flat under
 *                the tracker, so those documents exist and must stay reachable.
 */

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { after, before, beforeEach, describe, it } from 'node:test';

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment
} from '@firebase/rules-unit-testing';
import {
  collection,
  collectionGroup,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  orderBy,
  query,
  runTransaction,
  setDoc,
  updateDoc,
  where,
  writeBatch
} from 'firebase/firestore';

const RULES = fileURLToPath(new URL('../firestore.rules', import.meta.url));

const ALICE = 'alice-uid';
const BOB = 'bob-uid';
const MALLORY = 'mallory-uid';

let testEnv;

/** A tracker document as the app writes it. */
const tracker = (ownerId, sharedWith, extra = {}) => ({
  name: 'Groceries',
  ownerId,
  sharedWith,
  grandTotal: 0,
  createdAt: 1_700_000_000_000,
  updatedAt: 1_700_000_000_000,
  deleted: false,
  ...extra
});

const source = (trackerId, extra = {}) => ({
  name: 'Card',
  trackerId,
  type: 'EXPENSE',
  totalAmount: 0,
  createdAt: 1_700_000_000_000,
  updatedAt: 1_700_000_000_000,
  deleted: false,
  ...extra
});

const receipt = (trackerId, sourceId, extra = {}) => ({
  name: 'Milk',
  trackerId,
  sourceId,
  type: 'EXPENSE',
  description: '',
  amount: 3.5,
  date: 1_700_000_000_000,
  createdAt: 1_700_000_000_000,
  updatedAt: 1_700_000_000_000,
  deleted: false,
  ...extra
});

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-expensetracker',
    firestore: { rules: readFileSync(RULES, 'utf8') }
  });
});

after(async () => {
  await testEnv?.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();

    // Alice's own tracker, both receipt locations populated at once -- the real state of any
    // account that passed through 0.1.5-alpha.
    await setDoc(doc(db, 'trackers/t-alice'), tracker(ALICE, [ALICE]));
    await setDoc(doc(db, 'trackers/t-alice/sources/s1'), source('t-alice'));
    await setDoc(doc(db, 'trackers/t-alice/receipts/r-flat'), receipt('t-alice', 's1'));
    await setDoc(
      doc(db, 'trackers/t-alice/sources/s1/receipts/r-nested'),
      receipt('t-alice', 's1', { name: 'Bread', updatedAt: 1_800_000_000_000 })
    );

    // Shared with Bob, plus a tombstone for the purge query.
    await setDoc(doc(db, 'trackers/t-shared'), tracker(ALICE, [ALICE, BOB]));
    await setDoc(doc(db, 'trackers/t-shared/sources/s1'), source('t-shared'));
    await setDoc(doc(db, 'trackers/t-shared/receipts/r1'), receipt('t-shared', 's1'));
    await setDoc(
      doc(db, 'trackers/t-shared/sources/s1/receipts/r-dead'),
      receipt('t-shared', 's1', { deleted: true, updatedAt: 1_500_000_000_000 })
    );

    // Written before `sharedWith` existed, and one where a collaborator dropped the owner from
    // the array. Neither should be able to lock the owner out.
    await setDoc(doc(db, 'trackers/t-nofield'), {
      name: 'Ancient',
      ownerId: ALICE,
      grandTotal: 0,
      createdAt: 1_600_000_000_000
    });
    await setDoc(doc(db, 'trackers/t-nofield/sources/s1'), { name: 'Cash', type: 'EXPENSE' });
    await setDoc(doc(db, 'trackers/t-orphaned'), tracker(ALICE, [BOB]));

    // For the tracker-tree delete.
    await setDoc(doc(db, 'trackers/t-del'), tracker(ALICE, [ALICE]));
    await setDoc(doc(db, 'trackers/t-del/sources/s1'), source('t-del'));
    await setDoc(doc(db, 'trackers/t-del/receipts/r1'), receipt('t-del', 's1'));
    await setDoc(doc(db, 'trackers/t-del/sources/s1/receipts/r-nested'), receipt('t-del', 's1'));
  });
});

const asAlice = () => testEnv.authenticatedContext(ALICE).firestore();
const asBob = () => testEnv.authenticatedContext(BOB).firestore();
const asMallory = () => testEnv.authenticatedContext(MALLORY).firestore();
const asAnon = () => testEnv.unauthenticatedContext().firestore();

describe('trackers', () => {
  it('lets the owner read their own tracker', async () => {
    await assertSucceeds(getDoc(doc(asAlice(), 'trackers/t-alice')));
  });

  it('lets a shared member read the tracker', async () => {
    await assertSucceeds(getDoc(doc(asBob(), 'trackers/t-shared')));
  });

  it('denies a stranger', async () => {
    await assertFails(getDoc(doc(asMallory(), 'trackers/t-alice')));
  });

  it('denies anonymous readers', async () => {
    await assertFails(getDoc(doc(asAnon(), 'trackers/t-alice')));
  });

  it('serves the 0.1.5 list query (array-contains)', async () => {
    const q = query(
      collection(asAlice(), 'trackers'),
      where('sharedWith', 'array-contains', ALICE)
    );
    const snap = await assertSucceeds(getDocs(q));
    // t-alice, t-shared, t-del -- not t-nofield (no field) or t-orphaned (owner removed).
    if (snap.size !== 3) throw new Error(`expected 3 trackers, got ${snap.size}`);
  });

  it('serves the 0.1.0 list query (array-contains-any + orderBy createdAt)', async () => {
    const q = query(
      collection(asAlice(), 'trackers'),
      where('sharedWith', 'array-contains-any', [ALICE]),
      orderBy('createdAt', 'desc')
    );
    await assertSucceeds(getDocs(q));
  });

  it('still admits the owner when sharedWith is absent', async () => {
    await assertSucceeds(getDoc(doc(asAlice(), 'trackers/t-nofield')));
  });

  it('still admits the owner when a collaborator removed them from sharedWith', async () => {
    await assertSucceeds(getDoc(doc(asAlice(), 'trackers/t-orphaned')));
  });

  it('allows a create that names the author as owner and member', async () => {
    await assertSucceeds(
      setDoc(doc(asAlice(), 'trackers/t-fresh'), tracker(ALICE, [ALICE]))
    );
  });

  it('rejects a create that hands ownership to someone else', async () => {
    await assertFails(setDoc(doc(asAlice(), 'trackers/t-fresh'), tracker(BOB, [BOB, ALICE])));
  });

  it('rejects a create that leaves the author out of sharedWith', async () => {
    await assertFails(setDoc(doc(asAlice(), 'trackers/t-fresh'), tracker(ALICE, [])));
  });

  it('lets a member rename a shared tracker', async () => {
    await assertSucceeds(updateDoc(doc(asBob(), 'trackers/t-shared'), { name: 'Renamed' }));
  });

  it('lets a member add another member (0.1.0 shareTracker)', async () => {
    await assertSucceeds(
      updateDoc(doc(asBob(), 'trackers/t-shared'), { sharedWith: [ALICE, BOB, MALLORY] })
    );
  });

  it('refuses to let a collaborator seize ownership', async () => {
    await assertFails(updateDoc(doc(asBob(), 'trackers/t-shared'), { ownerId: BOB }));
  });

  it('refuses a stranger writing to a tracker', async () => {
    await assertFails(updateDoc(doc(asMallory(), 'trackers/t-alice'), { name: 'Mine now' }));
  });

  it('lets the owner delete', async () => {
    await assertSucceeds(deleteDoc(doc(asAlice(), 'trackers/t-shared')));
  });

  it('refuses a non-owner member deleting', async () => {
    await assertFails(deleteDoc(doc(asBob(), 'trackers/t-shared')));
  });
});

describe('receipts (flat variant left behind by 0.1.5-alpha)', () => {
  it('lets a member read the collection', async () => {
    await assertSucceeds(getDocs(collection(asAlice(), 'trackers/t-alice/receipts')));
  });

  it('lets a member write one', async () => {
    await assertSucceeds(
      setDoc(doc(asBob(), 'trackers/t-shared/receipts/r-new'), receipt('t-shared', 's1'))
    );
  });

  it('denies a stranger', async () => {
    await assertFails(getDocs(collection(asMallory(), 'trackers/t-alice/receipts')));
  });

  it('follows the parent when a share is revoked', async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), 'trackers/t-shared'), { sharedWith: [ALICE] });
    });
    await assertFails(getDocs(collection(asBob(), 'trackers/t-shared/receipts')));
  });
});

describe('receipts (nested under the source -- where they live)', () => {
  it('lets a member read them', async () => {
    await assertSucceeds(
      getDocs(collection(asAlice(), 'trackers/t-alice/sources/s1/receipts'))
    );
  });

  // The regression that made 0.1.0 installs read-only against their own data, and that the
  // current client would now hit too, since this is the path it writes.
  it('lets a member create one', async () => {
    await assertSucceeds(
      setDoc(
        doc(asAlice(), 'trackers/t-alice/sources/s1/receipts/r-new'),
        receipt('t-alice', 's1')
      )
    );
  });

  it('lets a member edit one', async () => {
    await assertSucceeds(
      updateDoc(doc(asAlice(), 'trackers/t-alice/sources/s1/receipts/r-nested'), { amount: 9 })
    );
  });

  it('lets a member delete one', async () => {
    await assertSucceeds(
      deleteDoc(doc(asAlice(), 'trackers/t-alice/sources/s1/receipts/r-nested'))
    );
  });

  it('denies a stranger', async () => {
    await assertFails(
      getDocs(collection(asMallory(), 'trackers/t-alice/sources/s1/receipts'))
    );
  });

  // 0.1.0's addReceipt: write the receipt and roll up both parent totals atomically.
  it('supports the transactional add that rolls up parent totals', async () => {
    const db = asAlice();
    await assertSucceeds(
      runTransaction(db, async (tx) => {
        const sourceRef = doc(db, 'trackers/t-alice/sources/s1');
        const trackerRef = doc(db, 'trackers/t-alice');
        const sourceSnap = await tx.get(sourceRef);
        const trackerSnap = await tx.get(trackerRef);

        tx.set(
          doc(db, 'trackers/t-alice/sources/s1/receipts/r-tx'),
          receipt('t-alice', 's1', { amount: 12 })
        );
        tx.update(sourceRef, { totalAmount: (sourceSnap.data().totalAmount ?? 0) + 12 });
        tx.update(trackerRef, { grandTotal: (trackerSnap.data().grandTotal ?? 0) - 12 });
      })
    );
  });
});

describe('collection-group receipt reads', () => {
  // The break users reported: every receipt screen on 0.1.0 goes through this query, and a
  // path-scoped rule never matches a collection-group read. The current client depends on the
  // same rule to pull a tracker's receipts without one query per source.
  it('lets a member read a whole tracker in one query', async () => {
    const q = query(
      collectionGroup(asAlice(), 'receipts'),
      where('trackerId', '==', 't-alice')
    );
    const snap = await assertSucceeds(getDocs(q));
    // Reaches both locations, so a receipt stranded flat by 0.1.5-alpha is still found.
    if (snap.size !== 2) throw new Error(`expected 2 receipts, got ${snap.size}`);
  });

  it('supports the ordered form 0.1.0 issues', async () => {
    const q = query(
      collectionGroup(asAlice(), 'receipts'),
      where('trackerId', '==', 't-alice'),
      orderBy('date', 'desc')
    );
    await assertSucceeds(getDocs(q));
  });

  it('supports the incremental form the sync engine issues', async () => {
    const q = query(
      collectionGroup(asAlice(), 'receipts'),
      where('trackerId', '==', 't-alice'),
      where('updatedAt', '>=', 1_750_000_000_000)
    );
    const snap = await assertSucceeds(getDocs(q));
    if (snap.size !== 1) throw new Error(`expected 1 changed receipt, got ${snap.size}`);
  });

  it('supports the tombstone-purge form', async () => {
    const q = query(
      collectionGroup(asAlice(), 'receipts'),
      where('trackerId', '==', 't-shared'),
      where('deleted', '==', true),
      where('updatedAt', '<', 1_600_000_000_000)
    );
    const snap = await assertSucceeds(getDocs(q));
    if (snap.size !== 1) throw new Error(`expected 1 tombstone, got ${snap.size}`);
  });

  it('denies a stranger', async () => {
    const q = query(
      collectionGroup(asMallory(), 'receipts'),
      where('trackerId', '==', 't-alice')
    );
    await assertFails(getDocs(q));
  });

  it('denies an unfiltered sweep of every tracker', async () => {
    await assertFails(getDocs(query(collectionGroup(asAlice(), 'receipts'))));
  });
});

describe('sources', () => {
  it('lets a member read them', async () => {
    await assertSucceeds(getDocs(collection(asAlice(), 'trackers/t-alice/sources')));
  });

  it('lets a member write one', async () => {
    await assertSucceeds(
      setDoc(doc(asBob(), 'trackers/t-shared/sources/s-new'), source('t-shared'))
    );
  });

  it('denies a stranger', async () => {
    await assertFails(
      setDoc(doc(asMallory(), 'trackers/t-alice/sources/s-new'), source('t-alice'))
    );
  });

  it('still admits the owner of a tracker with no sharedWith field', async () => {
    await assertSucceeds(getDocs(collection(asAlice(), 'trackers/t-nofield/sources')));
  });
});

describe('batched sync writes (0.1.5 SyncEngine)', () => {
  // A tracker created offline arrives in the same batch as its children. Batches are atomic,
  // so a `get()` on the parent sees nothing yet no matter which order the writes were queued
  // in -- only the post-write state can approve this.
  it('accepts a tracker and its children created together', async () => {
    const db = asAlice();
    const batch = writeBatch(db);
    batch.set(doc(db, 'trackers/t-new'), tracker(ALICE, [ALICE]));
    batch.set(doc(db, 'trackers/t-new/sources/s1'), source('t-new'));
    batch.set(doc(db, 'trackers/t-new/sources/s1/receipts/r1'), receipt('t-new', 's1'));
    await assertSucceeds(batch.commit());
  });

  it('still rejects children smuggled in beside someone else\'s tracker', async () => {
    const db = asMallory();
    const batch = writeBatch(db);
    batch.set(doc(db, 'trackers/t-alice/sources/s-evil'), source('t-alice'));
    await assertFails(batch.commit());
  });

  it('rejects a child whose tracker is never created', async () => {
    const db = asAlice();
    const batch = writeBatch(db);
    batch.set(doc(db, 'trackers/t-ghost/sources/s1/receipts/r1'), receipt('t-ghost', 's1'));
    await assertFails(batch.commit());
  });

  // deleteTrackerTree removes the parent in the same batch as the children, so the children
  // have to be judged on the state before the batch, not after.
  it('accepts a whole tracker tree deleted in one batch', async () => {
    const db = asAlice();
    const batch = writeBatch(db);
    batch.delete(doc(db, 'trackers/t-del/receipts/r1'));
    batch.delete(doc(db, 'trackers/t-del/sources/s1/receipts/r-nested'));
    batch.delete(doc(db, 'trackers/t-del/sources/s1'));
    batch.delete(doc(db, 'trackers/t-del'));
    await assertSucceeds(batch.commit());
  });

  it('accepts a soft-delete tombstone from a member', async () => {
    await assertSucceeds(
      updateDoc(doc(asAlice(), 'trackers/t-alice/sources/s1/receipts/r-nested'), {
        deleted: true,
        updatedAt: 1_900_000_000_000
      })
    );
  });
});
