---
id: SAT_017
uid: SAT
number: 17
client: Satchel
status: done
title: 'persistence.md false claim: dead file'
context: Page says ServerPersistenceContext.java was deleted; it still exists on disk,
  dead.
priority: normal
opened: '2026-08-13'
closed: '2026-08-13'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Originally scoped as "wait for SAT_004 to execute, then drop persistence.md's pending-rename
caveat." That premise is stale: **SAT_004 is already closed** (2026-08-13) and `persistence.md`
already reflects the rename correctly (`common.persistence`, not `common.newstuff` — no
pending-caveat language left in the page).

Re-scoped after re-checking `persistence.md` against real source during the documentation-coverage
survey: the page (lines 27-33, `status: verified`) states the old `server/persistence` package
"is gone entirely... its one file, `ServerPersistenceContext.java`... has since been deleted along
with the rest of the directory." **That's false.** The file and directory are both still on disk:
`Satchel/src/main/java/com/arryn/satchel/server/persistence/ServerPersistenceContext.java`,
fully commented-out dead code (confirmed by direct read, 2026-08-13). SAT_004's own closing log
made the same incorrect claim — this doc inherited it rather than independently verifying.

**Fix, two parts:**
1. Delete the actual dead file (`server/persistence/ServerPersistenceContext.java` and, if
   nothing else is in it, the now-empty `server/persistence/` directory).
2. Correct `persistence.md`'s claim to match — either update it after the file is deleted (so the
   claim becomes true), or rephrase it as "not yet deleted, tracked in SAT_017" if deletion isn't
   done in the same pass.

See [SAT_004](SAT_004_newstuff-rename.md) for the original (incorrect) claim this traces back to.

## Log

- 2026-08-13: Both parts done. Deleted `server/persistence/ServerPersistenceContext.java` (dead,
  fully commented out) and the now-empty `server/persistence/` directory — confirmed gone from
  disk after deletion. Corrected `persistence.md`'s claim: it previously (incorrectly) attributed
  the deletion to the 2026-08-13 compile-fix pass; now correctly attributes it to this ticket and
  notes the file survived that earlier pass as leftover cruft despite SAT_004's log claiming
  otherwise.
- 2026-08-13: Re-scoped from "persistence.md rename follow-up" (moot — already done) to
  "persistence.md false claim about a dead file" (real, found during documentation-coverage
  survey — see [documentation-coverage plan](../wiki/plans/doc-coverage.md)).
- 2026-08-13: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
