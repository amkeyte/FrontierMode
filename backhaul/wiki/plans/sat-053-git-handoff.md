---
id: plans/sat-053-git-handoff
category: plans
slug: sat-053-git-handoff
title: SAT_053 Git Handoff
summary: What happened to git during the SAT_049 investigation, and how the SAT_053
  redesign work is set up for whoever picks it up.
keywords: null
status: draft
updated: '2026-09-09'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../BACKHAUL.md) · [Wiki Index](../../WIKI_INDEX.md) · plans
<!-- bh-header:end -->

# SAT_053 Git Handoff

What happened to git during the SAT_049 investigation, and how the SAT_053 redesign work is set
up for whoever picks it up. Project owner normally runs git directly -- this page exists because
that didn't happen here, and the resulting state shouldn't be a surprise to whoever looks at the
repo next.

## Why git got touched at all

While tracing [SAT_049](../../tickets/SAT_049_satchelfixture-onjigtick-is-never-actual.md)
(tracker fixtures never actually ticking) to its root cause, a patch to
`ScopeEngine_Server.hydrateBundle()` was drafted and verified against current code -- it would
have made every bundle hydrate from an explicitly-empty source when persistence isn't required,
instead of skipping hydration (and therefore lifecycle promotion to `ACTIVE`) entirely. Project
owner stopped this before it shipped: coupling lifecycle promotion to persistence-hydration
machinery is the actual defect, not something to patch around one jig at a time. See
[SAT_053](../../tickets/SAT_053_redesign-engine-level-lifecycle-promotio.md) for the real
redesign scope.

## What happened to git, in order

1. **Patch reverted.** `git checkout -- src/main/java/com/arryn/satchel/server/jig/guts/ScopeEngine_Server.java`
   in the `Satchel/` repo, undoing the drafted patch above. Verified clean afterward (`git diff`
   against that file produced no output).
2. **A stale `.git/index.lock` blocked that revert.** ~13 hours old, no live git process found
   (`ps aux` had nothing matching `git`) -- cleared it to unblock the checkout. Flagging this
   because a lock that age usually means some earlier git client (IDE, another terminal, a
   crashed process) didn't exit cleanly; it wasn't left by anything this session ran. Worth a
   glance if it happens again or if anyone was mid-operation around 2026-09-09 00:04 UTC.
3. **A worktree, not a branch switch, for SAT_053.** Rather than checking out a new branch in
   the shared `mcRepos/` working tree -- which would have changed what's on disk for anyone
   building from it, including whatever playtesting or tech-support work is using that checkout
   right now -- a `git worktree` was added instead, isolated from the main checkout by design.

No commits were made anywhere in this session. The only git-history change is the new branch
pointer itself (created empty, at the same commit `master` already sat at).

## Current state (as of 2026-09-09)

```
mcRepos/                                         934033a  [master]
mcRepos/_worktrees/SAT_053-lifecycle-redesign/   934033a  [satchel/SAT_053-lifecycle-redesign]
```

- **Main working tree (`mcRepos/`)** -- still on `master`, untouched by anything above. It still
  carries whatever was already uncommitted before this session started (the reobf/gradle build
  fix in both mods' `build.gradle`/`gradle.properties`, and an in-progress edit to
  `SatchelHealth.java` that this session didn't author or touch). None of that was committed,
  reverted, or otherwise disturbed here -- it's exactly as it was.
- **New worktree** at `mcRepos/_worktrees/SAT_053-lifecycle-redesign`, on branch
  `satchel/SAT_053-lifecycle-redesign`, checked out clean from `master`'s HEAD (`934033a`). No
  commits on it yet -- the redesign work itself hasn't started, this only stood up a place for it
  to happen without disturbing the main checkout.

## For whoever picks up git from here

This session used git directly (a revert and a worktree/branch creation) because the work was
mid-flight -- normal handling is for the project owner to run git. Nothing here is meant to be a
standing convention: fold the worktree into however git normally gets handled on this project --
convert it to a plain branch, delete it and start over with a regular checkout, whatever fits.
The only thing worth preserving is that `master`'s pre-existing uncommitted state (the reobf fix
and the in-progress `SatchelHealth.java` edit) is independent of and predates this plan, and
shouldn't be conflated with SAT_053 work.

## Related pages

- [SAT_049](../../tickets/SAT_049_satchelfixture-onjigtick-is-never-actual.md) -- parked pending
  the redesign; root cause is documented in its log.
- [SAT_053](../../tickets/SAT_053_redesign-engine-level-lifecycle-promotio.md) -- the redesign
  ticket this worktree exists for.
