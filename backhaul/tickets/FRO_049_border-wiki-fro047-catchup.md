---
id: FRO_049
uid: FRO
number: 49
client: FrontierMode
status: done
title: Border wiki needs FRO_047 catch-up pass
context: Readiness + Mutation surface sections overstate Result usage vs. FRO_047's
  final built behavior.
priority: normal
opened: '2026-08-28'
closed: '2026-08-28'
---

<!-- board:start -->
<!-- board:end -->

## Summary

[FRO_047](FRO_047_border-interface-refactor.md) closed against the wiki as already-canon, per
its own item 5 ("this ticket is source catching up to already-canon wiki") -- and for almost
everything it built, that held. Two specific passages in
[Border](../wiki/frontiermode/architecture/border.md) state something more definite than the
final, settled implementation actually does, and per [Lead Dev](../roles/lead-dev.md)'s wiki
discipline that's flagged here rather than edited directly -- wiki changes are the Architect's
call, not something to rewrite unilaterally mid-build.

## What's wrong, specifically

**"Readiness" section** currently reads:

> a not-ready call surfaces through the same `Result` the mutating operations use ... not a
> thrown exception.

This isn't what got built, after three rounds of design discussion during FRO_047 (see that
ticket's log, 2026-08-28 entries). The four facet resolvers (`PATH`/`CRUD`/`RULES`/`INFO`) return
`Optional`, not `Result` -- `Result` only ever carries a single `Border`, and forcing a
zero-to-many or not-ready-vs-empty query into that shape doesn't fit. And `bordersContaining()`,
the one query call site where "not ready" actually mattered in practice, ended up throwing
`SatchelException.ScopeNotReady` -- neither `Result` nor `Optional`. The real rule that emerged
(and should replace this paragraph): not-ready degrades differently depending on who's calling --
routine and silent (`Optional.empty()`) for render/tick call sites reachable before Satchel's
world-identity token round-trip, a thrown exception for server-side command-dispatch call sites
where not-ready would mean something else is already broken. `Result` is reserved for a rejected
*mutation* a caller must act on, never for query absence.

**"Mutation surface" section** currently reads:

> `applyProposal()` -- and every other `BorderAPI` operation that can fail -- returns a `Result`
> rather than throwing or returning `Optional.empty()`

This is too broad by one method: `bordersContaining(Level, BlockPos)` is a `BorderAPI` operation
that can fail (not-ready), and it throws, not `Result`. The sentence should scope itself to
mutating operations specifically (`grow`/`addBorder`/`transformBorder`/`removeBorder`/
`applyProposal`), with `bordersContaining()` called out separately as the query exception to that
rule, for the same reason given above.

## Not filed as bug/regression

Nothing here is a defect in FRO_047's actual code -- the source matches the *intended* design
that came out of the project-owner discussion, confirmed by two real playtest sessions. The gap
is purely that the wiki's prior wording (written when FRO_046 closed, before FRO_047's
implementation surfaced the Result-doesn't-fit-a-query problem) never got updated to match where
the design actually landed. Filed as its own ticket rather than reopening FRO_047, since FRO_047
itself is done and this is a documentation-only follow-up.

## Log

- 2026-08-28: Ticket opened, off FRO_047's build+playtest cycle. Not yet edited -- per wiki
  discipline, flagging the drift here is as far as this role goes; the actual rewrite is the
  Architect's call.

- 2026-08-28: **Rewritten (Architect).** Both passages fixed on
  [Border](../wiki/frontiermode/architecture/border.md): "Mutation surface" now scopes `Result` to
  the mutating operations (`grow`/`addBorder`/`transformBorder`/`removeBorder`/`growCenteredOn`/
  `applyProposal`) and calls out `bordersContaining()` as the thrown-exception exception; "Readiness"
  now states the real per-caller rule -- silent `Optional.empty()` for the four facet resolvers
  (render/tick, pre-token-round-trip), a thrown `SatchelException.ScopeNotReady` for
  `bordersContaining()`'s server-command callers, `Result` reserved for mutation rejection only.
  A third drift not flagged by this ticket but caught in the same pass: the "Data model" section
  claimed `BordersFixture` itself is package-private, which FRO_047's own log (its first logged
  deviation) already corrected -- the class stays a public Java type (Satchel's `FixtureKey<T>`
  needs it accessible from `FrontierKeys`/`BordersBundle`/`BorderModule.init()`), encapsulation
  enforced instead by `BorderAPI.borders(Level)` being gone and the four facet resolvers being the
  only path in. Fixed alongside the other two rather than filing a fourth ticket for it.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
