---
id: RM_FRO_015
uid: RM_FRO
number: 15
kind: work
status: resolved
title: Border command-surface completion
owner: Arryn
depends_on:
- RM_FRO_008
created: '2026-08-16'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Border command-surface completion

- 2026-08-20: **Resolved, project owner's call.** Item 1 (`fixLayers()` reorder design) is
  implemented and real-build confirmed (2026-08-20 entries below). Item 2 ("whatever else turns up
  in the `/border` command tree") produced four real bugs this session, all found via genuine
  playtesting and all fixed: the `pathGrow` uncaught-exception crash, phantom path entries not
  self-healing, the layer-uniqueness design reversal (Layer and Path now correctly divorced), and
  `pathGrow`'s backwards empty-path bootstrap guard. The `/border info` `LEV`/`LAY` display change
  is also shipped and real-build confirmed.

  **Two small items knowingly left open, not blocking this close:** `pathGrow`'s success message
  still doesn't announce what it created (the actual cause of the "layer 1 after delete" report
  below — see that entry), and the `/border delete @all` cosmetic "unexpected error" alongside a
  successful delete (root cause undetermined, project owner already called it deprioritized). Both
  stay documented here rather than vanishing. Per this node's own precedent (it exists because
  RM_FRO_011 flagged rough edges without reopening itself), any further `/border` command-surface
  work found later gets a **new** sibling node rather than reopening this one — this node's own
  scope item 2 was always "the standing home for *this pass's* findings," not a promise to stay open
  indefinitely.

  **Still not independently build-verified as a single end-to-end pass** — every individual fix in
  this node's log was manually read-through verified and, where a rebuild did happen this session
  (the LEV/LAY and delete-then-grow entries), confirmed live in-game; no network access in this
  sandbox to run a full `gradlew build` myself at any point. Owed, standing caveat carried at the
  parent convergence level now, same as every other resolved sibling under
  [RM_FRO_010](RM_FRO_010_susan.md) ("Susan") already carries it.
- 2026-08-20: **Item 1 (`fixLayers()` reorder design) implemented — explicit deviation from the
  Architect/Lead Dev split, same one-time deviation pattern logged on
  [FRO_029](../tickets/FRO_029_border-vocab-conformance.md) earlier this session.** The project
  owner directed Architect to implement this node's design directly rather than hand it to Lead
  Dev. Design page: [Border Path & Layer
  Reconciliation](../wiki/frontiermode/architecture/path-layer-reconciliation.md) (now carrying its
  own "Implemented" note). Shipped:
  - `BordersFixture.reassignLayers(Map<UUID, Integer>)` — new package-private bulk reassignment:
    finds off-path borders colliding with the reserved path-index range, moves them clear
    (preserving relative layer order), applies the whole batch as one atomic replace against the
    internal `borders` list, returns the count of borders actually changed.
  - `BordersPathFacet.fixLayers()` — no longer a hardcoded `return false`; builds the path's own
    target-layer map and delegates to `reassignLayers`. Now returns `int` (count changed), not
    `boolean` — a deliberate deviation from the design page's own text, needed for the command
    message below; noted inline on the design page.
  - `BorderCommandHandler.pathFixLayers` — reports the two real outcomes distinctly ("Reconciled N
    border layer(s) with path order" vs. "Path and layer order already match -- no changes made"),
    replacing the old unconditional "not implemented yet" message.

  **Not independently build-verified** — this sandbox has no network access to fetch the Gradle
  distribution (confirmed again this session: `gradlew --version` failed the same way it did for
  [FRO_029](../tickets/FRO_029_border-vocab-conformance.md)'s own Difficulty implementation).
  Verified by manual read-through of every changed file and call site instead. **Still owed before
  this item's own done bar is met:** a real `gradlew build`, then the command sequence [Border Path
  & Layer Reconciliation](../wiki/frontiermode/architecture/path-layer-reconciliation.md)'s own
  "Done bar" names — reorder a path via `moveup`/`movedown`, confirm `getRelevant()`/`@relevant`
  reflects the new order, confirm an off-path border with a colliding layer gets bumped and not
  lost, confirm a no-op case reports honestly.

  Item 2 (whatever else turns up in the `/border` command tree) remains the standing, open-ended
  home it always was — nothing new there this pass. Node stays `open`.
- 2026-08-20: **Real build + real command sequence run, read from `run/logs/latest.log` (chat is
  logged client-side) and `run-server/logs/latest.log`.** Confirms the new code is live (the log
  messages below don't exist in the old code, so this is also incidental proof a real rebuild
  happened even though `build.log` itself wasn't touched by this run). Full sequence, 5 pre-existing
  borders (Ashring L1, Highfall L0, Blackgate L2, Shadowmere L3, Goldfall L4):
  - `/border path moveup` on Highfall → "Moved border up in path." (path reordered, layers
    untouched — confirmed via `/border info` showing the same layer values as before).
  - `/border path fixlayers` → **"Reconciled 2 border layer(s) with path order."** Follow-up
    `/border info` confirms Ashring/Highfall's layers swapped (1↔0) to match the new path
    order — exactly the 2-border reassignment expected.
  - `/border path fixlayers` again → **"Path and layer order already match -- no changes made."**
    The honest no-op case, confirmed.
  - `/border path remove` on Shadowmere (path-only removal, per the design's off-path setup) →
    "Removed border from path." Shadowmere still exists, still layer 3, now off-path with pathSize
    dropped to 4 — putting its layer inside the newly-reserved `[0, 4)` range.
  - `/border path fixlayers` → **"Reconciled 2 border layer(s) with path order."** Goldfall
    (path member) reassigned 4→3 to fill the gap; Shadowmere (off-path, colliding) bumped
    0→5 — matches `max(pathSize=4, currentMaxLayer=4 + 1) = 5` exactly. `/border debug` afterward
    confirms **Count = 5** — Shadowmere's layer changed, it wasn't lost. **This is the off-path
    bump-and-not-lost case from the done bar, confirmed precisely.**

  **All of item 1's done-bar items now have direct, positive confirmation** except an explicit
  `@relevant` re-check specifically after a fixLayers() reconciliation (not run this pass) — the
  underlying layer values `getRelevant()` sorts by are confirmed correct by the above, and
  `@relevant`'s own correctness was independently confirmed under
  [RM_FRO_006](RM_FRO_006_sandra.md)/[FRO_026](../tickets/FRO_026_sandra-implementation.md), so
  this is a minor, low-risk gap rather than an open question.

  **Real bug found, not caused by this pass — this world's `borderPath` contains a stale UUID
  with no matching `Border`.** Every `fixLayers()` call this session logged (server log, three
  times): `"[Border] fixLayers(): <uuid> is in the reassignment set but has no matching border --
  skipped."` This is exactly the defensive path `reassignLayers`'s own doc describes ("real data
  corruption, not a normal transient state... log loudly, same 'detect and log' stance Boss's own
  reconciliation check takes") doing its job — it didn't crash, didn't corrupt the real borders,
  and every real reassignment still completed correctly around it. But the phantom entry itself is
  a genuine, previously-invisible pre-existing issue: old `fixLayers()` never walked the path
  against real borders, so nothing before this pass could have surfaced it. **Worth its own
  tracking — flagging here rather than filing it myself,** consistent with this node's own history
  (RM_FRO_011 deliberately didn't self-file this node either). Likely worth a quick source check for
  how a path entry can outlive its border — `BordersFixture.remove()` does clean the path
  (`borderPath.removeIf(...)`) on every delete it knows about, so this may predate that guarantee or
  come from some other mutation path.
- 2026-08-20: **Two bugs, both fixed — the phantom-path-entry finding above and a real crash the
  project owner hit playtesting `/border path grow` right after.**

  **`/border path grow` threw instead of failing cleanly.** `BorderLogic.grow()` proposes
  `layer = previous.layer() + 1` through `BordersCrudFacet.applyProposal()`, which throws a raw
  `IllegalStateException` on a layer collision -- the same rejection `addExplicit`/`addHere`/
  `transform` all catch and turn into a clean `[Border] Rejected: <reason>` message
  (RM_FRO_011/FRO_023). `BorderCommandHandler.pathGrow` was never given that same try/catch, so a
  collision there propagated uncaught into Brigadier's generic "unexpected error" instead. This
  playtest hit it directly: the earlier test session left Shadowmere at layer 5, off-path; growing
  the path from the current tip eventually asks for layer 5 too and collides with it, since organic
  growth has no bump-and-retry the way `fixLayers()` now does. **Fixed** by wrapping
  `BorderAPI.grow(level)` in the same try/catch pattern as the other three commands.

  **Phantom path entries now self-heal instead of just being logged.** Revised
  `BordersFixture.reassignLayers(Map)`: a `borderPath` UUID with no matching `Border` is now
  actually removed from the path (not just detected-and-skipped) -- leaving it in place would have
  re-triggered the same warning on every future `fixLayers()` call forever, and there's nothing else
  a stale reference can meaningfully do once found. Still logs loudly when it happens, so the fact
  that it occurred isn't lost. `reassignLayers`'s own return value now also counts entries
  self-healed this way (not just real layer changes), so a cleanup-only call still reports a real
  "Reconciled N" outcome instead of a misleading "no changes made."

  **Not independently build-verified**, same standing limitation this whole node has had -- no
  network access in this sandbox to fetch the Gradle distribution. Verified by manual read-through.
  **Still owed:** a real rebuild, then re-running the exact sequence that hit both bugs (grow into a
  colliding off-path layer; re-run `fixLayers()` on a world with a genuine phantom path entry) to
  confirm both fixes actually behave as designed in-game, not just on paper.
- 2026-08-20: **Design reversal — project owner's direct call, same day, immediately after
  retesting `/border path grow`.** The fix above (wrapping `pathGrow` in a try/catch) treated
  growth's layer collision as an expected, occasionally-legitimate rejection to handle gracefully.
  Retesting surfaced the real question instead: growth kept hitting "layer 0 collides" against
  Ashring, a completely unrelated off-path border, and blocking ordinary progression for it. Project
  owner's ruling: **Layer and Path are definitionally unrelated — full stop — and
  `DefaultBorderRules.getRelevant()` already resolves any same-layer overlap by nearest center
  (already documented on [Border Vocabulary](../wiki/frontiermode/architecture/border-vocabulary.md#relevance)
  as "lowest Layer wins, tie-broken by nearest center," true before this ticket ever touched
  anything). The layer-uniqueness validation RM_FRO_011 added was solving a problem `getRelevant()`
  never actually had.**

  **Removed:** `BordersCrudFacet.validateProposal`'s layer-collision check (radius-bounds checking
  is unaffected, still enforced). **Simplified accordingly:**
  `BordersFixture.reassignLayers(Map)` no longer bumps off-path borders clear of the reserved path
  range — nothing to bump, duplicate layers aren't a collision anymore. Path members' layers are set
  directly to their path index, full stop. The phantom-path-entry self-heal from the previous entry
  is unaffected by this change. Docs updated to match:
  [Border Path & Layer Reconciliation](../wiki/frontiermode/architecture/path-layer-reconciliation.md)
  (major revision — the "off-path bump" design half is now historical, marked superseded in place),
  [Border](../wiki/frontiermode/architecture/border.md)'s Known Gaps section, and [Border
  Vocabulary](../wiki/frontiermode/architecture/border-vocabulary.md)'s own Layer section (new
  explicit "not required to be unique" statement — the direct, load-bearing consequence of Layer
  already being defined as "only coincidentally tied to Path," not a new or separate fact).
  [RM_FRO_011](RM_FRO_011_betty.md)'s own historical log entry annotated as superseded (layer half
  only; radius-bounds half stands), original text left as written.

  **Not independently build-verified**, same standing limitation. Verified by manual read-through.
  **Still owed:** a real rebuild, then re-running `/border path grow` (and the gold-block trigger,
  which routes through the same code) against the exact world state that hit this — confirm it now
  succeeds instead of rejecting, and confirm `@relevant`/`/border info` still resolve sensibly with
  two borders legitimately sharing a layer.
- 2026-08-20: **Third real bug from the same playtest thread: `/border path grow` unconditionally
  refused to grow a fresh level with zero borders — "No borders exist to grow." — instead of
  creating the level's first border.** `BorderCommandHandler.pathGrow` had its own guard clause
  (`if (borders.isEmpty()) throw ...`) sitting in front of `BorderAPI.grow(level)`, intercepting
  the exact case `BordersPathFacet.grow()` already exists to handle: an absent path tip branches
  internally to `BorderLogic.getInitial()`, which is precisely how a level's first border is meant
  to get created. The guard blocked that branch from ever running. Confirmed this was
  command-specific, not a deeper problem: `BordersTriggers.growPath` (the gold-block trigger) calls
  `BorderAPI.grow(level)` directly, has no equivalent guard, and was never affected — command and
  trigger now behave the same way. **Fixed** by removing the guard entirely; nothing else in
  `pathGrow` needed to change, the existing try/catch below it already covers real rejections
  (radius bounds).

  Real reproduction from this session's log: fresh level (`Borders loaded: 0`), `/border path grow`
  → `"No borders exist to grow."` (bug). Worked around live by `/border add here` +
  `/border path insert` to seed one border manually, then `/border path grow` succeeded normally
  from that tip — confirming the *organic-growth* half of `grow()` was never broken, only the
  bootstrap-from-empty half via the command specifically.

  **Not independently build-verified**, same standing limitation. Verified by manual read-through.
  **Still owed:** a real rebuild, then `/border path grow` on a genuinely fresh level (0 borders) to
  confirm it now creates the initial border instead of rejecting.
- 2026-08-20: **Fourth report from the same playtest thread investigated and closed as not a bug —
  a real gap found instead, and fixed.** Report as given: "when border in path deleted, then border
  grows (from empty) but adds at layer 1. suggests something isn't being removed from the path but
  doesn't show up in border info all."

  **Full sequence reconstructed from `run/logs/latest.log`'s chat lines, timestamps included:**
  `/border add here` creates Grimline (layer 0) → `/border path insert` puts it on the path →
  `/border path grow` (**grow #1**, tip=Grimline/L0) creates Stormreach at layer 1, appended to the
  path — but this command's own success message is just "Advanced border progression," silent on
  what got created, unlike `addExplicit`/`addHere`'s "Created border \<uuid\>." → `/border delete`
  removes Grimline → `/border path grow` (**grow #2**, tip is now Stormreach, not empty — Grimline's
  deletion only ever strips its own UUID from the path, per `BordersFixture.remove()`'s referential-
  integrity contract; grow #1's own append was never touched) creates Nightwatch at layer 2 →
  `/border info @all` shows exactly Stormreach L:1 and Nightwatch L:2, Grimline correctly gone.

  **`BordersFixture.remove()`'s path cleanup is correct** — verified directly against this sequence,
  not just by re-reading the source. The path was never actually empty at the point of grow #2; grow
  #1 (run *before* the delete) had already put a second, real border on it. Both resulting borders
  are right there in `/border info @all` — nothing phantom, nothing hidden. The report's premise
  ("doesn't show up in border info all") doesn't hold up against the log: everything present is
  accounted for.

  **Real gap, though, and the likely actual cause of losing track:** `pathGrow`'s success message
  reports nothing about what was created or where. Every other border-creating command
  (`addExplicit`/`addHere`) announces the new UUID; `pathGrow` doesn't, which is exactly the kind of
  gap that makes an earlier grow easy to forget about a few commands later, as it did here. Filed as
  a real, if smaller, finding rather than left as "user error" — the command surface should make its
  own state changes visible.

  **Separately, requested and implemented same session: `/border info`'s layer display changes from
  `L:\<n\>` to `LEV-\<pathIndex\>|LAY-\<layer\>`.** Per project owner's direct instruction — Layer
  and Path position are shown side by side now instead of just Layer, consistent with this whole
  thread's running theme that the two are related-but-distinct and conflating them (even just in a
  label) invites exactly this kind of confusion. `BorderDisplay.shortInfo`/`fullInfo` now take an
  explicit `pathIndex` parameter (`-1` renders as `LEV--` for an off-path border) instead of reading
  only `Border.layer()`; `BorderCommandHandler.info()` supplies it via
  `BordersFixture.PATH.indexOf(id)`. Only the single `info()` caller needed updating — no other code
  called either display method.

  **`pathGrow`'s missing creation-announcement is not fixed this pass** — flagged here as the real
  finding, left open under this node's own "whatever else turns up in the `/border` command tree"
  scope (item 2) rather than folded into this entry as a same-day fix, since it's a smaller,
  independent piece of work and this entry is already logging three other things.

  **Not independently build-verified**, same standing limitation. Verified by manual read-through.
  **Still owed:** a real rebuild, then `/border info` on both a path member and an off-path border to
  confirm `LEV`/`LAY` render as designed (including the `LEV--` off-path case).
- 2026-08-20: **LEV/LAY display confirmed working in the same real playtest that surfaced this
  entry's next item** — `/border info` showed `LEV-0|LAY-0` on-path and `LEV--|LAY-12` off-path
  (Highfall), matching design exactly. Done-bar item above satisfied.

  **New cosmetic finding, project owner's call: deprioritized, not fixed this pass.**
  `/border delete @all` against a single-border world removed the border correctly (`"[Border]
  Removed border <uuid>"`) but the client also displayed Brigadier's generic "An unexpected error
  occurred trying to execute that command" right after — despite the operation actually succeeding.
  Investigated at length: zero corroborating evidence anywhere in `run-server/logs/latest.log` or
  the full DEBUG-level `run-server/logs/debug.log` for the window in question — no `Command
  exception:` line (which vanilla's `Commands.performCommand` logs unconditionally at ERROR whenever
  a raw exception escapes a command's `.executes()`, stack trace or not), no stray dispatch of any
  kind, just the routine tick heartbeat running straight through. Only one border existed at the
  time, so `applySelector`'s per-border loop only ran once, and its own `catch (Exception e)` would
  produce a distinct `"[Border] Operation failed for border <id>"` message anyway, not vanilla's
  generic one — ruling out the obvious "second selector match failed" explanation. Root cause
  undetermined; the mismatch between the message text (implying a genuine escaped exception) and the
  total absence of the log line vanilla always produces for that case is itself unexplained.
  Everything downstream in the same session behaved normally — no bad state, no repeat. **Project
  owner's explicit call: cosmetic (the delete demonstrably works), not worth chasing further right
  now — flagging here for the record rather than opening a sibling node, may pick up later.**
- 2026-08-16: Node opened, sibling of [RM_FRO_009](RM_FRO_009_judith.md)/[RM_FRO_011](RM_FRO_011_betty.md)/
  [RM_FRO_012](RM_FRO_012_carolyn.md)/[RM_FRO_013](RM_FRO_013_judy.md)/[RM_FRO_006](RM_FRO_006_sandra.md)
  under [RM_FRO_010](RM_FRO_010_susan.md) ("Susan"), same convergence-gate shape (see
  [BKHL_002](../tickets/BKHL_002_convergence-gate.md)) — depends on RM_FRO_008 directly, folded
  into Susan's own `depends_on` rather than feeding Shirley in parallel.

  Not found fresh — RM_FRO_011's own log explicitly flagged this: `BordersPathFacet.fixLayers()`
  reorders nothing and always returns `false`, because reordering `layerIndex` to match a manual
  `/border path moveup`/`movedown` reorder "interacted unsafely with [RM_FRO_011] item 2's new
  collision check" and needs "a real two-pass (or bypass) design, not something safe to guess at."
  RM_FRO_011 deliberately didn't file this node itself ("a design call, not a mechanical one... the
  project owner said not to route around architectural decisions without a ticket") — this is that
  ticket.

  Scope widened, project owner's call: rather than a single-issue "fix fixLayers" node, this is the
  standing home for the `/border` admin/dev command surface's outstanding rough edges, since more
  commands are expected here over time and opening one node per edge case doesn't scale. Currently
  in scope:
  1. **`fixLayers()` reorder design** (the trigger for this node) — `Border.layerIndex()` is
     immutable (only a fresh `BorderProposal` sets it, and `BordersCrudFacet`'s RM_FRO_011
     collision check now rejects a colliding `layerIndex`), but `pathMoveUp`/`pathMoveDown` are
     live, op-exposed commands that reorder the *path list* only — so an op can already desync path
     order from the `layerIndex` order `DefaultBorderRules.getRelevant()` (oldest-ring-wins) sorts
     by. Needs a real design: a two-pass reassignment (clear to a non-colliding scratch range, then
     assign final values) or a temporary validation bypass scoped to a single reconciliation
     transaction — Architect to specify before Lead Dev touches this again.
     **[Rename note, 2026-08-20: `Border.layerIndex()` is now `Border.layer()` — the project owner
     renamed the accessor directly in code. This point's own text left as originally written; the
     actual design page, [Border Path & Layer
     Reconciliation](../wiki/frontiermode/architecture/path-layer-reconciliation.md), is already
     updated to the current name — build against that page, not this bullet's wording.]**
  2. Whatever else turns up in the `/border` command tree as new commands get added — this node is
     the standing home for that category of work, not a one-shot.

  **Explicitly not in scope, project owner's call (2026-08-16), not manufactured into work here:**
  `BorderCommandHandler.pathInsert`'s "probably crashes, but it's a TODO anyway" comment and
  `BordersFixture.requireServerSide()`'s "move this into super" dedup note. Both are real,
  pre-existing, self-documented, low-severity, and not evidenced to have caused an actual problem —
  left alone rather than folded in or ticketed. Noting them here only so a future pass over this
  node's neighborhood doesn't treat them as newly discovered.

**Design/spec first.** Per this session's steer: Architect produces the design (and a spec page if
the reorder logic ends up being a boundary contract other code depends on) before this gets handed
to Lead Dev, same as RM_FRO_011's own text asked for.

## Required By

<!-- required-by:start -->
- [**RM_FRO_010**](RM_FRO_010_susan.md) — Prototype hardening
<!-- required-by:end -->
