---
id: FRO_029
uid: FRO
number: 29
client: FrontierMode
status: done
title: Border Vocabulary conformance sweep
context: 6-phase sweep aligning wiki, Border-module code, and historical ticket/RM
  logs to canon Border Vocabulary.
priority: normal
opened: '2026-08-18'
closed: '2026-08-20'
---

<!-- board:start -->
<!-- board:end -->

## Summary

[Border Vocabulary (Parked)](../wiki/frontiermode/architecture/border-vocabulary.md) names four
distinct concepts — Relevance, Layer, Path, Difficulty — meant to retire the overloaded word
"level" from architecture vocabulary. It's currently `status: draft`/parked: confident enough to
write down, not yet scoped into work, no roadmap node. This project inherited pre-existing drift
against these concepts (both in wiki content and in code, predating the vocabulary page itself),
and ambiguity has let that drift persist or spread further since. This ticket is the project
owner's plan to un-park Border Vocabulary properly: align documentation and code to the canon
definitions, not just the words, then reconcile the historical record without rewriting it.

Sasha has already swept the wiki for nomenclature (nothing should still say bare "level" as an
architecture term — see the vocabulary page's own "Resolved by Game Designer" section). What this
ticket targets is different: **content and decisions still built on the pre-vocabulary
assumptions**, even where the words have already been fixed. The clearest known instance,
flagged by the project owner going in: `Border.layerIndex()`'s implementation is the wrong shape
for what "Layer" is supposed to mean — conflated with Path (only coincidentally tied together
today, per the vocabulary page's own "Layer" section) and incomplete relative to what a full
Layer/Difficulty split requires.

**Closure:** this ticket stays open regardless of how many phases below complete. Only the project
owner closes it — not on a done-bar being satisfied, not by Architect or Lead Dev judgment call.
Phase 6 note below on what "done enough to consider closing" might look like is a suggestion, not
an authorization.

**Roadmap node:** deliberately not opened yet. Whether this becomes an `RM_FRO` node (and if so,
at what grain — one node for the whole sweep, or several once Phase 3's plan exists) is an open
call for later, made once there's an actual scoped plan to hang a node on, not before.

## Phase 1 — Wiki drift checklist (read-only, wiki-only)

Read-only sweep of the wiki (not just `frontiermode/architecture/` — anywhere a Border/Layer/Path/
Relevance/Difficulty claim could live: `frontiermode/design/`, `satchel/architecture/`, mod summary
pages) for content or *design decisions* resting on pre-vocabulary assumptions, even where the
surface wording already reads clean post-Sasha's-sweep. Watch specifically for: Layer and Path
treated as interchangeable or definitionally linked rather than "only coincidentally tied together
under normal growth"; "level" used as a stand-in for Path when the vocabulary page says highest-
attained-frontier is Path, not Layer; Difficulty conflated with Layer rather than treated as the
broader thing Layer is one input to; the boss-difficulty-resolves-from-own-Border-not-from-live-
path-tip distinction (the "implementation trap" the vocabulary page itself already flags) not yet
reflected in boss.md or elsewhere.

**Deliverable:** a new wiki page, **`status: draft` / Parked**, containing this checklist as its
first section (working title suggestion: "Border Vocabulary Conformance Checklist" —
Architect's call on final slug/placement). Each item should name the page and passage in question
and state the mismatch against the canon vocabulary concretely enough that Phase 3's plan can act
on it without re-deriving the finding.

**Constraint:** wiki only. No tickets, no RM nodes opened in this pass — findings go on the
checklist page, nothing else gets touched or filed yet.

## Phase 2 — Code drift checklist (read-only), same page, second section

Read-only scour of code — primarily the Border module (`FrontierMode/.../border/*`), since that's
where Layer/Path/Relevance/Difficulty are actually implemented — for places where the vocabulary's
*intent* is missing, incomplete, or not honored, independent of whether any wiki page currently
claims otherwise. Named starting point from the project owner: **`layerIndex`'s implementation is
the wrong shape** — conflated with Path (nothing structurally distinguishes them beyond
`fixLayers()`'s currently-a-no-op reconciliation) and incomplete against Difficulty (no
`DifficultyRules`-shaped seam exists at all yet; the vocabulary page's own "Tracking" section
already anticipates this). Also worth checking while in the neighborhood: `BordersRulesFacet`/
`DefaultBorderRules` for any rule logic that implicitly assumes Layer-equals-Path; `BossFixture`'s
`level` field and the spawn-algorithm's stat-scaling-by-`layerIndex` note in `boss.md` for whether
the "boss difficulty resolves from the boss's own recorded Border, not a live path-tip query" trap
is actually avoided in the design as written (boss.md's own text says it is — worth confirming
that's still true once this pass is done, not re-litigating it from scratch).

**Deliverable:** appended as a second section on the same wiki page from Phase 1. Same checklist
format: file/class/method, what's wrong relative to canon Border Vocabulary, concrete enough to
plan against.

**Constraint:** same as Phase 1 — read-only, wiki-only, no tickets or RM nodes yet. Architect does
not edit `src/` to investigate; reading and, where needed, asking Lead Dev a scoping question is
fine, changing code is not.

## Phase 3 — Pause for second-perspective review

Once the checklist page (Phases 1+2) is complete, **stop.** No further action until the project
owner has had Curtis (Lead Dev) review the page read-only for a second perspective. After that
review, the three of us develop an actual plan addressing each checklist item — this is where
findings turn into real scoped work (tickets, and/or RM nodes as warranted), not before.

## Phase 4 — Split execution

Once Phase 3's plan exists: **Architect (Douglas) acts on the documentation plan.** **Lead Dev
(Curtis) acts on the code plan** — explicitly *based on Architect's corrected architecture docs*,
not the pre-correction pages or independent judgment call. Sequencing matters: doc corrections
land first (or at minimum, land as the reference Curtis's code changes are checked against), same
"Architect designs, Lead Dev implements" division this role already holds everywhere else.

## Phase 5 — Historical annotation pass

After Phase 4, sweep historical ticket and RM logs (not just Border-related ones — anywhere a
statement was made in terms that now contradict Border Vocabulary) and annotate contradicting
statements in place — something in the shape of `[Dprtd: ref FRO_XXX]` next to the specific
sentence, pointing at whichever ticket/RM node's resolution superseded it. **We do not rewrite
history** — the original statement stays exactly as written; the annotation marks it as superseded
without deleting or editing the record of what was actually said/decided at the time. **Before
applying this broadly: confirm the exact annotation tag/format against `BHW — Wiki
Conventions`/`BHT — Ticket Conventions` conventions (or set a new one, deliberately, if neither
covers this case) — the `[Dprtd: ref ticket lala]` shape in the project owner's original ask is
illustrative, not a locked format.**

## Phase 6 — Clean up the working artifacts

Once the above settles, clean up any checklist/status pages created *during this process* — most
likely the Phase 1/2 wiki page itself, once its findings have all been dispositioned into real
tickets/RM nodes/doc corrections and it's no longer live reference material. Probable disposition:
fold its (by-then-historical) content into this ticket's own Log below, or a closing ticket, rather
than leaving a stale checklist page sitting in the wiw index. Don't do this until Phases 1-5 are
actually done — a half-addressed checklist page is still working reference material, not clutter
yet.

## Done bar (informational only — does not authorize closing this ticket)

Phase 1+2 checklist page written and parked. Curtis's review complete (Phase 3). Joint plan
developed and filed as real tickets/RM nodes as warranted. Doc plan executed (Architect) and code
plan executed (Lead Dev) per Phase 4. Historical logs annotated per Phase 5, using a confirmed
annotation convention. Working checklist page retired/relocated per Phase 6. Even once all of the
above is true, **this ticket closes only when the project owner says so.**

## Log

- 2026-08-18: Ticket opened by Architect (Douglas), scoped from a project-owner request to
  un-park Border Vocabulary via a 6-phase sweep (wiki checklist → code checklist → Lead Dev
  review pause → split doc/code execution → historical annotation → working-artifact cleanup).
  No work started yet — Phase 1 is next, pending project owner's go-ahead.
- 2026-08-18: Phases 1+2 done — project owner gave the go-ahead. Read-only sweep of every
  `frontiermode/architecture/`+`frontiermode/design/` wiki page and the full `border/*` Java
  package (see "Scope of this sweep" on the checklist page for exactly what was and wasn't
  read). Findings written to the Border Vocabulary Conformance Checklist
  (`status: draft`/parked): 3 wiki items (boss.md's `level`-named field and an ambiguous
  stat-scaling cross-reference; frontier-reconciliation.md/path-layer-reconciliation.md flagged
  as overlapping FRO_028, not duplicated) and 4 code items (no `Difficulty` concept exists
  anywhere yet; radius growth has nothing to plug a future Difficulty seam into; per-player
  evaluation resolves Relevance→Layer and dead-ends before Difficulty, blocking Progression's
  fairness-signal formula; `BorderPlayerStatus.layerIndex()` silently means two different things
  depending on `insideNearest()`). Also logged what was checked and found clean, for coverage
  transparency. **Stopping here per Phase 3 — waiting on Curtis's read-only review before any
  plan gets made.**
- 2026-08-18: Project owner reports Curtis has already done his Phase 3 review pass. Before
  reconciling it, project owner directed executing
  [FRO_028](FRO_028_reconciliation-page-cleanup.md)'s ready-now half out of band — settling
  `frontier-reconciliation.md`'s content into `border.md` (a page the checklist's item 1.3 had
  flagged as overlapping this ticket) before Phase 3/4 proceed, on the reasoning that Curtis's
  review target shouldn't keep moving underneath him. Done: see FRO_028's own closing log for the
  full change. Net effect on this ticket's own findings: item 1.3 is now half-resolved (updated
  in place on the checklist page, not restated here) and `border.md` gained a "Design vocabulary
  bridge" section that item 2.1-2.4's eventual doc fixes will land on top of. **Curtis's review
  predates this change** — his pass and the checklist page (plus this FRO_028 update) need
  reconciling before Phase 3 is actually considered closed, not just before Phase 4 starts. Still
  waiting on the project owner to bring Curtis's actual feedback back to this ticket.
- 2026-08-20: **Process note.** Phase 3's formal Curtis-reconciliation (previous log entry) was
  never brought back to this ticket. The project owner directed proceeding straight to Phase 4's
  documentation half regardless — judged there was enough context to act rather than continue
  waiting on that handoff. Logged plainly rather than treated as if the reconciliation happened:
  Curtis's actual Phase 3 feedback still hasn't been reconciled against the post-FRO_028 checklist
  state, and that gap is still open if anyone needs to close it properly later.

  **Architect (Douglas) executed the documentation half of Phase 4** against the checklist's
  findings directly, without a joint Phase 3 plan preceding it:
  - Items 1.1/1.2: [Boss](../wiki/frontiermode/architecture/boss.md)'s `BossFixture` record field
    renamed `level` → `layer` throughout (data model, position/materialization steps, the
    reconciliation-check description, the border-growth-gap trigger list), and the "Spawn
    algorithm" stat-scaling bullet now names the guarantee (boss's own recorded `layer`, never a
    live lookup) inline instead of leaving it to a cross-check.
  - Items 2.1-2.4: new page [Difficulty](../wiki/frontiermode/architecture/difficulty.md) — two
    new methods (`layerToDifficulty`/`ambientDifficultyAt`) added directly to `BorderRules`/
    `DefaultBorderRules` rather than a separate interface (revised same day at the project owner's
    request — one rules helper per module), a `BorderPlayerStatus`/`BorderPlayerEval` reshape
    splitting the overloaded `layerIndex()` into `relevantLayer`/`nearestLayer`, the
    now-implementable fairness-signal composition for Progression's open design item, and radius
    growth (2.2) named as a deferred future seam rather than acted on now.
  - Border Vocabulary Conformance Checklist updated in place with resolution notes on each item,
    per this project's "don't rewrite history, annotate it" convention — original findings left
    intact.

  **Not done, deliberately out of Architect's lane:** no `src/` changes. `DifficultyRules`,
  `DefaultDifficultyRules`, the `BossFixture`/`BorderPlayerStatus`/`BorderPlayerEval` field
  renames, and everything else in Section 2 are still Lead Dev's Phase 4 code half, to be built
  against [Difficulty](../wiki/frontiermode/architecture/difficulty.md) and the corrected
  [Boss](../wiki/frontiermode/architecture/boss.md) page — not built here, not assumed done.
  Item 1.3's `path-layer-reconciliation.md` half is untouched, correctly still parked on
  RM_FRO_015. Phase 5 (historical annotation) and Phase 6 (checklist retirement) not started.
  **This ticket still only closes when the project owner says so** — this entry is progress, not
  a closure claim.
- 2026-08-20: **Explicit deviation from the Architect/Lead Dev split.** The project owner directly
  authorized Architect (Douglas) to implement the Section 2 code himself, rather than hand it to
  Lead Dev per Phase 4's usual division. Done, against `Difficulty`'s design exactly as revised
  (Difficulty methods on `BorderRules`, not a separate interface):
  - `BorderRules.java`/`DefaultBorderRules.java`: added `int layerToDifficulty(int layer)`
    (identity placeholder) and `OptionalInt ambientDifficultyAt(List<Border>, BlockPos)`
    (composes `getRelevant()` + `layerToDifficulty`).
  - `BorderPlayerEval.java` (record) and `BorderPlayerStatus.java`: split the single `layerIndex`
    field into `OptionalInt relevantLayer` (populated only on a real Relevance resolution) and
    `int nearestLayer` (always populated, the nearest-surface fallback, explicitly not a
    Relevance result).
  - `BorderPlayerLogic.java`: both branches of `evaluate()` updated to populate the new fields
    correctly.
  - `BorderPlayerStatusFixture.java`: its one call site (`compute()`) updated to plumb the new
    fields through instead of the old single field.
  - Confirmed via full-repo grep before touching anything: `BorderPlayerStatusFixture.compute()`
    was the *only* real caller of the old `layerIndex()` accessor anywhere in `FrontierMode/src` —
    no command, selector, or render code reads it, so the blast radius was smaller than the
    checklist's own "worth a naming or shape change" language implied it might be.
  - **Not independently build-verified.** This sandbox has no network access to fetch the Gradle
    distribution (`gradlew --version` failed trying to download it), so there's no real
    `gradlew build` behind this — verified by careful manual read-through of every changed file
    and call site instead. Flagging so nobody mistakes this for a confirmed green build; worth a
    real compile check the next time this is touched somewhere with Gradle access.
  - Boss's own `level`→`layer` rename (items 1.1/1.2) had no matching code to touch — Boss has no
    source yet, still blocked on `RM_SAT_021`.
  - [Difficulty](../wiki/frontiermode/architecture/difficulty.md) and Border Vocabulary
    Conformance
    Checklist updated in place
    to say this is implemented, not just designed.

  **Still not done (at the time):** radius growth (item 2.2) — left as a named, unbuilt future
  seam, not wired into `chooseNextRadius`. Phase 5 (historical annotation) and Phase 6 (checklist
  retirement) not started yet. Item 1.3's `path-layer-reconciliation.md` half still correctly parked
  on RM_FRO_015. **This ticket still only closes when the project owner says so.**
- 2026-08-20: **Closing pass — the project owner granted Architect (Douglas) full authority across
  all roles to close this ticket end to end in this session.** Phases 3, 5, and 6 executed
  directly under that authority; done bar reviewed against the actual current state; ticket closed.

  **Phase 3, reconciled directly rather than left open.** Curtis's original review pass was never
  formally brought back to this ticket (see the 2026-08-18 and 2026-08-20 entries above) and that
  gap was never going to close itself by waiting longer. Rather than continue blocking on a
  handoff that wasn't happening, Architect reconciled the checklist's findings against the actual
  current state directly — every finding below was re-confirmed against real source/wiki content
  as of this entry, not carried forward on trust from the original Phase 1+2 pass. This supersedes
  the never-completed Curtis-reconciliation step; it isn't a substitute for Lead Dev's own eyes on
  this, and Curtis is still owed a look at the actual shipped result whenever convenient, but it's
  not a precondition this ticket stays open for any longer.

  **Border Vocabulary Conformance Checklist — findings folded into this log, page retired
  (Phase 6).** The checklist's real content was already absorbed into living reference pages as
  each item was resolved ([Boss](../wiki/frontiermode/architecture/boss.md) for 1.1/1.2,
  [Difficulty](../wiki/frontiermode/architecture/difficulty.md) for 2.1-2.4) — the checklist page
  itself was always meant to be temporary working reference, not a permanent architecture page,
  per its own banner. Final state of all seven items, for the record:
  - **1.1** (`boss.md`'s `BossFixture` field named `level`) — resolved, renamed to `layer`.
  - **1.2** (stat-scaling section ambiguous about which layer) — resolved, now names the
    boss's-own-recorded-value guarantee inline.
  - **1.3** (`frontier-reconciliation.md`/`path-layer-reconciliation.md` predate the vocabulary) —
    half-resolved: the reconciliation-page half was settled via FRO_028 (merged into
    `border.md`'s "Design vocabulary bridge," page deleted, links repointed). The
    path-layer-reconciliation half remains correctly parked, blocked on RM_FRO_015 landing —
    **still open, by design, not part of this ticket's own closure**.
  - **2.1** (no `Difficulty` concept in code) — resolved: `layerToDifficulty`/`ambientDifficultyAt`
    live on `BorderRules`/`DefaultBorderRules`.
  - **2.2** (radius growth has no Difficulty seam) — closed as deliberately not built. No real
    consumer needs it; wiring one in now would mean inventing an untested curve. Named as a future
    extension point, not implemented — final disposition, not a gap.
  - **2.3** (per-player evaluation dead-ends at Layer, blocking the fairness-signal formula) —
    resolved in the sense that the formula is now composable
    (`BorderRules.ACTIVE.layerToDifficulty(...)` on both sides of the comparison). The
    fairness-signal's actual threshold and in-world mechanic are still undecided Game
    Designer/playtest territory — **that open design question is Progression's, not this
    ticket's, and stays open independent of this closure.**
  - **2.4** (`BorderPlayerStatus.layerIndex()` overloaded meaning) — resolved: split into
    `relevantLayer`/`nearestLayer` across `BorderPlayerEval`, `BorderPlayerStatus`,
    `BorderPlayerLogic`, and their one real caller, `BorderPlayerStatusFixture`.

  The checklist page itself (`wiki/frontiermode/architecture/vocab-conformance-checklist.md`) is
  deleted as of this entry. Earlier entries in this log and other wiki pages that linked to it are
  left as originally written, per this project's "don't rewrite history" convention — those links
  are now historical and won't resolve; this entry is the authoritative record of what they said.
  `difficulty.md`'s own references were updated to point here instead, since that page is still
  live and current.

  **Phase 5, historical annotation — done, narrowly.** Swept `tickets/` and `roadmap/` for
  pre-vocabulary "level" usage in the Border-progression sense (carefully excluding Forge's own
  `Level`/`LevelScope`/`ServerLevel` class references, which are an unrelated legitimate term).
  Two real hits, both in [RM_FRO_018](../roadmap/RM_FRO_018_shirley.md) and
  [RM_FRO_019](../roadmap/RM_FRO_019_karen.md) — both directly describe `BossFixture`'s old
  `level` field and pre-vocabulary "level"-as-Border language. Annotated in place with a `[Vocab
  note: ...]` marker (no existing BHT/BHW convention covered this, so one was set here,
  deliberately, per Phase 5's own instruction) pointing at Border Vocabulary and this ticket —
  original log text left untouched. No other tickets or roadmap nodes had genuine hits; every
  other match was Forge's `Level` type or an unrelated sense of the word ("root-level," "per-level
  cache," etc.).

  **Done bar, checked against the ticket's own stated bar:** checklist page written and parked
  (done, then retired above) → Curtis's review (done, reconciled directly above in lieu of a
  never-completed formal handoff) → joint plan (collapsed into direct execution under the project
  owner's full-authority grant, rather than a separate planning pass) → doc plan executed (done —
  boss.md, Difficulty) and code plan executed (done, by explicit deviation — same six files logged
  above) → historical logs annotated (done, narrowly — two real hits found and annotated) →
  working checklist page retired (done, this entry). **Explicitly still open outside this
  ticket's own scope, not resolved by this closure:** item 1.3's `path-layer-reconciliation.md`
  half (blocked on RM_FRO_015), and Progression's fairness-signal threshold/mechanic (Game
  Designer territory). Both are named, tracked elsewhere, and not swept under this ticket's own
  done bar.

  **Closed by the project owner's direct authorization in this session.**
- 2026-08-20: **Build verification.** The project owner ran a real `gradlew build` outside this
  sandbox (no network access here to fetch the Gradle distribution) and confirmed it succeeds —
  the Section 2 code changes (`BorderRules`/`DefaultBorderRules`'s two new methods, the
  `BorderPlayerEval`/`BorderPlayerStatus`/`BorderPlayerLogic`/`BorderPlayerStatusFixture` reshape)
  compile clean. Closes out the one caveat left on this ticket's closing entry above — manual
  read-through is now backed by an actual green build, not standing in for one.
  [Difficulty](../wiki/frontiermode/architecture/difficulty.md) updated to match.
- 2026-08-21: **Three dead links to the retired checklist page un-linked — text kept, link syntax
  removed.** Project owner's call. The closing entry above states that links to the deleted
  `vocab-conformance-checklist.md` were "left as originally written," which was true until today;
  those three are now plain text reading "Border Vocabulary Conformance Checklist" instead of
  markdown links to a file that no longer exists. Nothing about what the entries *say* changed —
  this is the narrower fix, not a rewrite of the history the convention protects.

  Reason: they were the only genuinely dead links in the whole project (`backhaul lint` reports 30
  broken, but the other 27 all point at files that exist at a different path, plus one illustrative
  example in [BHW — Wiki Conventions](../wiki/meta/bhw.md) that is deliberately not a real link).
  Being permanently unfixable-by-repath, they would have shown up in every future lint run forever
  — see [BKHL_007](BKHL_007_lint-routine.md), which cites exactly this case as the reason lint
  can't become a blocking gate without a way to mark a link deliberately historical. Un-linking
  removes the noise without needing that mechanism.

  This ticket stays `done`; no scope reopened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
