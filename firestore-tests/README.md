# Firestore rules tests

Security rules tests for [`../firestore.rules`](../firestore.rules), run against the Firestore
emulator.

```bash
npm install
npm test
```

`npm test` boots the emulator, runs the suite, and shuts the emulator down. Nothing reaches a
real project — the `demo-expensetracker` project id keeps the SDK pinned to the emulator.

Requires Node 18+ (the suite uses the built-in `node:test` runner), a JDK for the emulator, and
`firebase-tools` on the `PATH`.

## What it covers

The rules serve two client generations at once, so the suite is organised by client rather than
rule-by-rule:

- **0.1.0-alpha** — reads receipts through a `collectionGroup("receipts")` query, keeps receipts
  nested under sources, and edits them inside transactions that also roll up the parent totals.
- **current** — keeps receipts nested under sources too, and syncs through batched writes and
  incremental collection-group reads. 0.1.5-alpha briefly wrote receipts flat under the tracker,
  so those documents exist and have to stay reachable.

Alongside the access-control cases it pins the behaviours that are easy to regress:

- collection-group reads (only a recursive-wildcard rule ever matches them), in all three forms
  the clients issue: whole-tracker, incremental, and tombstone-purge
- reads and writes on both receipt locations
- a tracker and its children created in one atomic batch
- a whole tracker tree deleted in one atomic batch
- documents missing `sharedWith`, `updatedAt` or `deleted` — an undefined field dereference in a
  rule reaches the client as permission-denied
- an owner whom a collaborator removed from `sharedWith`

## Troubleshooting

If a run dies partway, the emulator's JVM can outlive it and hold port 8080, and the next run
fails with `Could not start Firestore Emulator, port taken`. Kill the listener:

```bash
npx kill-port 8080
```
