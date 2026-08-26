---
id: FRO_046
uid: FRO
number: 46
client: FrontierMode
status: open
title: Border external interface ruling
context: 'BorderLogic removed, proposals stay public with a Result type, BorderAPI exposes
  facets.'
priority: high
opened: '2026-08-25'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Rules on Border's external interface and internal structure together, superseding this ticket's
original `growCenteredOn`/`applyProposal`-contract questions. `BordersFixture` itself is never
the outward surface — its four facets are, reached through `BorderAPI`, not by handing the
fixture out. `BorderProposal`, `getProposal()`, and `applyProposal()` stay public and
general-purpose on purpose — `BorderAPI`'s named operations (`grow`, `addBorder`,
`transformBorder`, `growCenteredOn`) are the safe/easy path, not the only path; nothing forces
every future way of creating a `Border` through them. Every operation that can fail — including
`applyProposal` itself — returns one `Result` type instead of today's mixed
throw/`Optional.empty()` behavior. `BorderLogic` is eliminated outright: most of it was dead code,
and what wasn't belongs directly in the Rules or Facet domains it was routing between. One other
piece of dead capability code (`BorderAuthority`) comes out too.

## 1. `BorderLogic` is eliminated

Checked every member against real call sites in the repo — most of this class was dead weight,
not a layer worth preserving:

- **Dead outright, delete:** `requireAuthority(BorderAuthority)` (zero call sites),
  `nearest(List<Border>, BlockPos)` (zero call sites anywhere, not even internally),
  `requireServerSide()` (zero call sites, including within `BorderLogic` itself).
- **`containing(List<Border>, BlockPos)` folds into `BordersRulesFacet.containing(BlockPos)`
  directly, not relocated as its own method.** Its only caller already always passes
  `setting.all()` — the fixture's own full list — so the "any list, ownership-checked first"
  generality was never exercised. `BordersRulesFacet.containing()` can do the `BorderMath.isInside`
  loop over `setting.all()` itself, no intermediate method, no ownership check.
- **`getInitial()`/`grow(Border)`'s mechanics move to `BordersPathFacet.grow()` directly.** It
  computes center/radius/layer from `BorderRules` and calls `CRUD.getProposal()`/`applyProposal()`
  itself — both stay public (see item 2), so there's no reason for `BorderLogic` to sit between
  `BordersPathFacet` and `CRUD` anymore.
- **`defaultRadius()`/`defaultCenter()` vaporize — they were one-line pass-throughs to
  `BorderRules`.** `BorderProposal.applyGeometryDefaults()` calls `BorderRules.ACTIVE` directly
  instead.
- **`getDefaultDisplayName()` moves to the fixture/facet domain, not the rules domain.** It needs
  `CRUD.all()` for name dedup, and `BorderRules` is explicitly documented as never touching
  fixtures — lands on `BordersFixture`/`BordersCrudFacet`.
- **`BorderProposal`'s constructor changes from taking a `BorderLogic` to taking the owning
  `BordersFixture`.** `BordersCrudFacet.getProposal()` already constructs it from inside the same
  package; a fixture reference supplies `CRUD.all()` (name dedup), `scope()` (for `Level`), and
  `BorderRules.ACTIVE` (geometry) directly.
- **Bonus cleanup this surfaces:** `BordersFixture` currently constructs its own private
  `new DefaultBorderRules()` inside `BorderLogic`'s constructor — a second instance nobody else
  uses that way. `BorderPlayerLogic` and `BordersTriggers` already go through the shared
  `BorderRules.ACTIVE` singleton; removing `BorderLogic` removes the redundant instantiation for
  free.
- `DefaultBorderRules.growPathCriteria()` has a comment referencing "`fixture.logic.getInitial()`"
  as the empty-tip fallback — no code depends on it, just needs updating so it doesn't describe a
  class that no longer exists.

Net effect: `border.server.rules` drops a whole class and shrinks to what's actually rules —
`BorderRules`, `DefaultBorderRules`, `BordersTriggers`, `PlayerRules`, `items/BorderPathCompass`.
No more mutation-orchestration layer wearing a rules-package address.

## 2. `getProposal()`/`applyProposal()` stay public; `applyProposal()` is where the `Result` type lives

Deliberate call, not an oversight: `BorderAPI`'s named operations are the safe/best-practice path,
not a closed set — the project owner doesn't want to foreclose future ways of creating a `Border`
this early. `BorderProposal`'s constructor stays package-private (only `CRUD.getProposal()`
constructs one), but the type itself and its fluent configuration methods stay public, same as
`getProposal()`/`applyProposal()` — a public method returning an inaccessible type is a dead end
for outside callers, so both have to move together.

Because `applyProposal()` is a real, general-purpose, directly-callable entry point (not just
something `BorderAPI` wraps), it's where the new `Result` type actually belongs, not just on
`BorderAPI`'s convenience methods. Every operation that can fail — `BorderAPI.grow()`,
`addBorder()`, `transformBorder()`, `removeBorder()`, `bordersContaining()`, and `applyProposal()`
itself — returns a `Result` carrying: an outcome enum, a failure-kind enum (populated only on
failure — distinguishes a transient not-ready state from a permanent validation rejection from a
not-found lookup), a message string, and the `Border` itself on success. Enums rather than
open-ended strings for both, so a new failure condition is one added case, and removing one is a
compile break at every `switch` site rather than a silent behavior change. `BorderAPI`'s named
methods become thin wrappers that call `applyProposal()` (or the relevant facet method) and
forward whatever `Result` comes back.

`BorderSelectorResult` (`border.server.commands`) is direct in-repo precedent for the shape —
static factories, final fields, tagged by an enum — reuse that idiom rather than inventing a new
one.

## 3. `BordersFixture` goes package-private; `BorderAPI` exposes facets, not the fixture

Realizes the intent already sitting in `BordersFixture`'s own class doc comment ("to become
package private. Use facet accessors instead."). `BorderAPI.borders(Level)` — public today,
returns `Optional<BordersFixture>` — is replaced by one resolver per facet: `BorderAPI.PATH(Level)`,
`BorderAPI.CRUD(Level)`, `BorderAPI.RULES(Level)`, `BorderAPI.INFO(Level)`. Each does the same
resolve-then-return every existing `BorderAPI` method already does, handing back the requested
facet instead of the fixture itself.

`BossModule.reconcilePathAgainstBossRecords()` is the one confirmed outside holder of a raw
`BordersFixture` reference today (`BorderAPI.borders(level)` then `borders.PATH.all()` /
`borders.CRUD.get(id)` directly) — it moves to `BorderAPI.PATH(level)`/`BorderAPI.CRUD(level)`
calls.

## 4. `BorderAuthority` is dead weight

Confirmed against source: every reference is confined to `border.common.fixture` (`Border`'s own
`authority` field, `BordersFixture implements BorderAuthority`) plus `BorderLogic.ensureOwned()`/
`requireAuthority()` in `server.rules` — both already gone under item 1. Nothing outside Border's
own package boundary ever calls `.authority()` (Boss imports `Border` directly but never touches
it). Remove the interface, `Border.authority()`, and `BordersFixture implements BorderAuthority`.

## Known blast radius

- `BordersCrudFacet.applyProposal`'s current `IllegalStateException`-on-rejection is what
  `BorderCommandHandler` catches and relays to players today — that call site moves to reading the
  new `Result`.
- `BorderAPI.borders(Level)`'s only outside caller is `BossModule` (item 3) — moves to the new
  per-facet resolvers.

## Log

- 2026-08-25: Ticket rewritten (Architect) to reflect the settled design above.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
