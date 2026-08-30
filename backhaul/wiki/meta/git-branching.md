---
id: meta/git-branching
category: meta
slug: git-branching
title: Git Branching by Epoch
summary: Proposed branch-per-epoch policy for parking side-quest work while the mainline
  moves into the next epoch.
keywords: null
status: draft
updated: '2026-08-30'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../BACKHAUL.md) · [Wiki Index](../../WIKI_INDEX.md) · meta
<!-- bh-header:end -->

# Git Branching by Epoch

Proposed branch-per-epoch policy for parking side-quest work while the mainline moves into the
next epoch.

**Status: deferred, 2026-08-30, project owner's call.** Written up the same day per the project
owner's request (same conversation that un-folded [RM_FRO_021](../../roadmap/RM_FRO_021_susan-02.md)
("Susan_02") from [RM_FRO_017](../../roadmap/RM_FRO_017_donna.md) ("Donna")'s `depends_on` — see
that node's own log for the roadmap-side half of this), then set aside the same day on review:
neither shape below had a concrete-enough gain to justify the overhead at this team's current
scale — `master`-only has worked fine so far, and the roadmap's own container/status machinery
already gives "what's active vs. parked" a real home without touching git at all. **No branches
cut. Staying on `master`.** Left in place as a reference in case the calculus changes — see
"Revisit if" at the bottom — rather than deleted, since the reasoning for *not* doing this is worth
keeping as much as the reasoning for a convention that *was* adopted.

## Current state

Both repos are single-branch today — confirmed directly, 2026-08-30:

```
FrontierMode: * master  (only branch)
Satchel:      * master  (only branch)
```

Everything so far has been built and playtested straight on `master`. There is no prior branching
convention anywhere in this wiki to build on — this would be a first, not a refinement of
something existing.

## Why this is coming up now

[RM_FRO_021](../../roadmap/RM_FRO_021_susan-02.md) ("Susan_02," the Susan epoch's standing
review/fix container) has accumulated seven real items — display bugs, a future security audit,
closed-and-superseded validation findings, an already-fixed console-spam bug, an open design
question, and one real live bug ([FRO_064](../../tickets/FRO_064_boss-defeat-cascade-grows-border-level-r.md),
the boss-defeat-cascade/path-tip gap) whose actual fix is still being drafted by the project owner
directly. None of it currently blocks anything already built and playtest-verified, so it was
un-folded from Donna's `depends_on` rather than held onto structurally — see that node's own log.
But "doesn't block the graph" and "doesn't need real attention eventually" are different things,
and right now there's no git-level distinction between "the thing being actively worked" and "the
thing that's parked" — it's all just commits on `master`, in whatever order they happened to land.
That's the gap this page is trying to close.

## Two shapes, not pre-decided

### Option 1 — parking branch per epoch (lighter-weight)

`master` stays the single mainline, always reflecting current best-verified state, moving straight
through epochs same as it does today. When an epoch's leftover backlog is deliberately being set
aside (the way Susan_02's is right now), cut **one branch per epoch** at `master`'s current HEAD,
named for what it's parking (e.g. `park/susan`). That branch is a bookmark plus a workspace — not
a long-lived parallel mainline. Nobody's expected to keep it current with `master`; when someone
picks the backlog back up, they rebase it forward (or just start fresh off current `master` and
cherry-pick, if enough time has passed that a rebase would be painful) and merge back through the
normal flow once it's done.

**Overhead:** minimal. One branch gets created per epoch, only when there's actually something
worth parking (not automatic). Doesn't change how anyone works day to day on the active epoch.

### Option 2 — epoch-mainline branches (heavier, more traditional)

Each epoch gets its own branch cut from `master` when the epoch *begins* (e.g. `epoch/donna`), and
all real epoch work happens there. `master` stays frozen at "last fully-converged epoch" until the
epoch branch merges back on convergence. Side-quest/parked tickets from the previous epoch get
their own short branches off wherever `master` was, merged independently on their own schedule,
never blocking the active epoch branch.

**Overhead:** real. Every commit needs a target-branch decision, and merging an epoch branch back
is itself a real event that needs to happen cleanly. The payoff: `master` always represents a known
-good, fully-converged state — useful if there's ever a need to hand someone a build and be certain
what's actually in it, less useful for a two-person-plus-owner team that hasn't needed that
guarantee so far.

## Recommendation (not adopted)

If either shape were adopted, Option 1 was the pick — nothing about this project's current scale
or workflow needed `master` to represent anything more precise than "what's currently true," and
the roadmap's own node/container status already covers most of what Option 2's stronger guarantee
would buy. But "the cheaper of two options" isn't the same as "worth doing," and on review neither
cleared that bar — see "Status" at the top.

## Revisit if

- The team grows past project owner + Architect + Lead Dev, especially if more than one person is
  ever building against `master` at the same time.
- A real need shows up for `master` to represent a known-good, hand-off-able state at a specific
  point in time (a release, a build for someone outside this project) rather than just "whatever's
  currently true."
- The parked backlog on a container like Susan_02 grows large or old enough that its absence from
  git history (not just from the roadmap's `depends_on` graph) starts actually costing time to
  work around.

None of these are true as of 2026-08-30.

## Open questions, if this does get revisited

- **Naming.** `park/<epoch>` above is a placeholder — needs a real convention if this gets adopted
  (matching the roadmap's own `<epoch>-01`/`-02` container slugs would be one option:
  `park/susan-02`, tying the branch directly to the roadmap node that names its contents).
- **When exactly to cut the first one.** Susan_02's own backlog is the obvious first candidate, but
  nothing's been branched yet — this page is the proposal, not the trigger.
- **Whether Satchel needs its own parking branches independently of FrontierMode's,** or whether an
  epoch's parked work spanning both repos should be named/tracked as one unit somehow. Both repos
  are on the same "Susan epoch" framing today, but they're independent git histories.
- **Whether [RM_FRO_017](../../roadmap/RM_FRO_017_donna.md) ("Donna") is actually close to
  converging right now.** Worth flagging here since it's adjacent: all three of Donna's current
  `depends_on` entries ([RM_FRO_018](../../roadmap/RM_FRO_018_shirley.md) "Shirley,"
  [RM_FRO_019](../../roadmap/RM_FRO_019_karen.md) "Karen,"
  [RM_FRO_022](../../roadmap/RM_FRO_022_joyce.md) "Joyce") are `resolved` as of this page's writing
  — but Donna's own text still says not to treat her as close to reaching, since more Tier 1 scope
  was always expected to surface. Nobody has flipped her to `reached`; that's a real call for the
  project owner/Architect, not something to infer from the graph alone. If Donna does converge soon,
  that's the actual moment an epoch boundary — and the first real branch cut, under whichever option
  above — would matter.

## Related pages

- [BHRM — Roadmap Conventions § Epoch maintenance nodes](bhrm.md#epoch-maintenance-nodes-containers)
  — the roadmap-side half of "what gets parked and why"
- [RM_FRO_021](../../roadmap/RM_FRO_021_susan-02.md) ("Susan_02") — the real backlog that prompted
  this page
