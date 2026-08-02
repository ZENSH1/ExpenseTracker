# Firestore Schema

The authoritative description of the cloud copy: document layout, field contract, access policy
and indexes. The rules in [`firestore.rules`](../firestore.rules) enforce the access policy
described here, and [`firestore-tests/`](../firestore-tests) tests it.

Room is the source of truth on the device. Firestore is a backup and sync channel, so every
document here is a projection of a local row.

---

## Layout

```
trackers/{trackerId}
└── sources/{sourceId}
    └── receipts/{receiptId}
```

A receipt belongs to its source and is addressed through it. This is the original layout and the
one to keep: an install that is never upgraded has to go on reading and writing the same
documents as one that is, and no migration rewrites documents in place.

Ids are device-generated UUIDs, assigned when the record is created, online or off. A record
keeps one identity for its whole life, so nothing is remapped at sync time and the same id is
valid in Room and in Firestore.

### The flat variant

0.1.5-alpha wrote receipts to `trackers/{trackerId}/receipts/{receiptId}` instead, with
`sourceId` demoted to a field. That is reverted — the app writes under the source again — but
the documents that build created were never rewritten, so a handful still sit there. They stay
readable and writable:

- the collection-group read below spans every collection named `receipts`, so it finds them
  without a separate rescue pass;
- writes are still permitted there so a tombstone can reach them rather than stranding them;
- when the same receipt exists in both places, the sync keeps the copy with the newer
  `updatedAt` and the stale one is swept by the tombstone purge or by a tracker delete.

Do not write new receipts there.

### Reading receipts without a query per source

Nesting receipts does not force one query per source. A collection-group query on `receipts`,
filtered by the denormalised `trackerId` field, fetches a whole tracker's receipts in one round
trip:

```kotlin
firestore.collectionGroup("receipts").whereEqualTo("trackerId", trackerId)
```

This is the same query the 0.1.0 receipt list used. The sync engine adds
`whereGreaterThanOrEqualTo("updatedAt", since)` for incremental pulls.

---

## Documents

### `trackers/{trackerId}`

| Field        | Type           | Notes |
|--------------|----------------|-------|
| `name`       | string         | Required. A document without it is skipped by the mapper. |
| `ownerId`    | string         | Creator's `uid`. Immutable after create — the rules pin it. |
| `sharedWith` | array\<string> | Member `uid`s, including the owner. Membership list for all access. |
| `grandTotal` | number         | Net balance (income − expenses). Denormalised. |
| `createdAt`  | number         | Epoch millis. |
| `updatedAt`  | number         | Epoch millis. Added in 0.1.5-alpha. |
| `deleted`    | boolean        | Tombstone. Added in 0.1.5-alpha. |

### `trackers/{trackerId}/sources/{sourceId}`

| Field         | Type    | Notes |
|---------------|---------|-------|
| `name`        | string  | Required. |
| `trackerId`   | string  | Denormalised parent id. |
| `type`        | string  | `INCOME` or `EXPENSE`. |
| `totalAmount` | number  | Sum of the source's receipts. Denormalised. |
| `createdAt`   | number  | Epoch millis. |
| `updatedAt`   | number  | Epoch millis. Added in 0.1.5-alpha. |
| `deleted`     | boolean | Tombstone. Added in 0.1.5-alpha. |

### `trackers/{trackerId}/sources/{sourceId}/receipts/{receiptId}`

| Field         | Type    | Notes |
|---------------|---------|-------|
| `name`        | string  | Required. |
| `trackerId`   | string  | Denormalised grandparent id. **Load-bearing** — the collection-group query and its rule both key off it. |
| `sourceId`    | string  | Owning source. Duplicates the path segment; falls back to the path when absent. |
| `type`        | string  | `INCOME` or `EXPENSE`. |
| `description` | string  | May be empty. |
| `amount`      | number  | Always positive; `type` carries the sign. |
| `date`        | number  | Epoch millis. |
| `createdAt`   | number  | Epoch millis. |
| `updatedAt`   | number  | Epoch millis. Added in 0.1.5-alpha. |
| `deleted`     | boolean | Tombstone. Added in 0.1.5-alpha. |

### `updatedAt` and `deleted`

These two are the only fields the offline work added, and they are additive — they carry the
sync engine's state and nothing else reads them. Firestore's `toObject` ignores properties a
model class does not declare, so clients that predate them are unaffected. Removing them would
mean removing offline sync: without a change clock there is no incremental pull and no
last-writer-wins reconcile, and without tombstones a delete made on one device never reaches
another.

### Compatibility rules for readers and for security rules

- **Every field is optional in practice.** Documents written before 0.1.5-alpha have no
  `updatedAt` and no `deleted`, and the earliest trackers may have no `sharedWith`. Readers
  default them; the security rules read every field through `get(field, default)` for the same
  reason. A bare `resource.data.sharedWith` on a document missing the field is an evaluation
  error, and Firestore reports that to the client as permission-denied.
- `updatedAt` falls back to `createdAt` when absent.
- `sourceId` falls back to the owning source in the document's own path when absent.
- Documents are mapped by hand rather than with `toObject`, so an unknown `type` value or a
  missing timestamp degrades instead of throwing mid-sync.
- Deletes are tombstones (`deleted: true` plus a fresh `updatedAt`) so they propagate.
  `purgeTombstones` hard-deletes them after a retention window.

---

## Access policy

Everything is gated on membership of the parent tracker's `sharedWith`.

| Operation | Who |
|-----------|-----|
| Read tracker | Any member, or the owner |
| Create tracker | Any signed-in user, naming themselves as `ownerId` **and** listing themselves in `sharedWith` |
| Update tracker | Any member. `ownerId` may not change |
| Delete tracker | Owner only |
| Read / write sources and receipts | Any member of the parent tracker |

Notes on the shape of that policy:

- **Children are gated on a live lookup of the parent**, not on a copy of the member list. A
  revoked share takes effect on the next request and cannot be defeated by a stale field on a
  child document. The cost is one `get()` per rule evaluation.
- **The owner always retains access**, even if `sharedWith` is missing, malformed, or was
  emptied by a collaborator. Any member can edit `sharedWith` — that is what makes a tracker
  collaborative — so the owner fallback is what stops a collaborator from evicting the owner.
- **Writes are judged on the state after the write** (`getAfter`), reads and deletes on the
  state before it (`get`). Batched writes are atomic: a tracker created in the same batch as its
  sources does not yet exist while those children are evaluated, and no ordering of the
  operations changes that. Deletes need the opposite — deleting a whole tracker tree removes
  the parent in the same batch, and `getAfter` would find nothing to check against.
- **Collection-group reads need their own rule.** `collectionGroup("receipts")` is matched only
  by a recursive wildcard (`/{path=**}/receipts/{id}`); path-scoped rules never apply to it.
  Both client generations depend on it. That rule is read-only: it takes the tracker from the
  client-controlled `trackerId` field rather than from the document's location, so granting
  writes through it would let a member of one tracker plant documents under another.

### Document access call budget

`get()`/`getAfter()` count against Firestore's per-request limits — 10 for single-document and
query requests, 20 for transactions and batched writes. The rules spend exactly **one** call per
evaluation, and repeated lookups of the same tracker within a request are cached. A batch
spanning more than ~20 *distinct* trackers could still exceed the budget; sync batches are built
per user and a user has a handful of trackers, so this has headroom, but it is the reason the
rules do not spend a second call on a redundant `exists()` check.

---

## Indexes

Declared in [`firestore.indexes.json`](../firestore.indexes.json).

| Collection | Scope | Fields | Serves |
|------------|-------|--------|--------|
| `trackers` | collection | `sharedWith` contains, `createdAt` desc | 0.1.0 tracker list |
| `receipts` | **collection group** | `trackerId` asc, `date` desc | 0.1.0 receipt list |
| `receipts` | **collection group** | `trackerId` asc, `updatedAt` asc | incremental pull |
| `receipts` | **collection group** | `trackerId` asc, `deleted` asc, `updatedAt` asc | tombstone purge |
| `sources`  | collection | `deleted` asc, `updatedAt` asc | tombstone purge |

Plus one **single-field index exemption**, declared under `fieldOverrides`:

| Collection | Field | Added scope | Serves |
|------------|-------|-------------|--------|
| `receipts` | `trackerId` | `COLLECTION_GROUP` ascending | the full pull and `deleteTrackerTree` |

That exemption is not optional and is easy to miss. Firestore indexes every field
automatically, but **only at collection scope** — a collection-group query filtering on a single
field has no index unless one is declared, and fails with `FAILED_PRECONDITION`. The composite
indexes above do not cover it: they are keyed on `trackerId` *plus* another field, so they
answer `trackerId + updatedAt` but not `trackerId` alone.

The tempting workaround — always appending `updatedAt >= 0` so the composite index applies — is
wrong. Documents written before 0.1.5-alpha have no `updatedAt`, a range filter excludes
documents missing the field, and the full pull would silently drop every legacy receipt.

Collection-scoped single-field queries (`updatedAt >= since` on sources,
`sharedWith array-contains` on trackers) do use the automatic indexes.

---

## Testing and deploying the rules

```bash
cd firestore-tests && npm install && npm test
```

The suite boots the Firestore emulator around the run and exercises both client generations. It
never contacts a real project — the `demo-` project id prefix keeps everything local.

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

Deploy rules and indexes together. The collection-group indexes back queries that the rules now
permit; permitting a query without the index it needs just moves the failure.
