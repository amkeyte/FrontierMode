---
id: FRO_059
uid: FRO
number: 59
client: FrontierMode
status: done
title: 'Border proposal: center bounds + id/displayName guard'
context: '[Susan_02] FRO_054''s data-security QA pass found BordersCrudFacet.failureReason()
  validates radius (BorderConstants bounds) and layerIndex (non-negative) but never
  checks center at all. Also found BorderProposal''s public id()/displayName() setters
  are unvalidated -- a caller building a raw proposal directly (bypassing BorderAPI.addBorder/transformBorder,
  neither of which touch either field) could set id() to an existing border''s UUID
  and silently replace it via BordersFixture.accept()''s remove-then-add, or set an
  unbounded displayName. No current call site does this -- latent, not live -- but
  BorderAPI''s own doc states any caller can build and apply its own proposal directly
  for cases the named operations don''t cover, so the surface is real. Architect to
  decide the intended id()/displayName() contract (reject a colliding id vs. document
  it as an intentional replace path) before Lead Dev adds the guard.'
priority: low
opened: '2026-08-29'
closed: '2026-08-29'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Border proposal: center bounds + id/displayName guard

## Log

- 2026-08-29: Ticket opened.
- 2026-08-29: Spec review done. Read `BorderProposal`, `BordersCrudFacet.failureReason()`,
  `BordersFixture.accept()`, and `BorderAPI.addBorder`/`transformBorder` against the ticket's own
  question, then ruled and wrote it directly onto
  [border.md](../wiki/frontiermode/architecture/border.md)'s new "Proposal identity and
  validation" subsection (under "Mutation surface") rather than just answering here. Summary:
  - **`id()` collision: not rejected -- confirmed `transformBorder()` itself depends on exactly
    this shape.** `proposal.insert(existing)` seeds id/displayName/center/radius/layerIndex from
    the border being updated, then `applyProposal()` replaces that record via `accept()`'s
    remove-then-add -- a blanket id-collision reject would break every transform, not just a
    careless raw caller, since the two are structurally identical once a proposal reaches
    `applyProposal()`. Ruled: `insert(Border)` is the one sanctioned way to build a colliding-id
    proposal (it's the only path that carries every identity/geometry field forward coherently);
    a raw `.id(existingId)` call stays possible (the type is deliberately general-purpose, per
    "Mutation surface") but is now a documented contract, not an implicit one. No code change
    needed for this half -- it's a documentation fix.
  - **`displayName()` gets a real bound.** No sanctioned pattern needs an unbounded one -- every
    named operation already sources it from either the curated default pool or `insert()`'s
    carry-forward. Ruled: reject blank/whitespace-only, cap at 32 characters (longest existing
    default name plus headroom), same shape as the radius/layerIndex checks in `failureReason()`.
  - **`center` gets a real bounds check**, ruled to mirror vanilla's own world limits rather than
    invent a FrontierMode-specific number -- it's a corrupt-input guard, not a gameplay-balance
    number the way radius/layerIndex are. Horizontal (X/Z) against vanilla's world-border hard
    limit (~+/-29,999,984); vertical (Y) against the *level's own* build-height accessor (varies
    by dimension) rather than a hardcoded range -- `fixture.resolveLevel()` is already available
    for this. Exact vanilla API surface left for Lead Dev to confirm at implementation time
    (obfuscation mapping names shift across MC versions); the bound itself is the ruling.
  - Along the way, corrected a second, unrelated staleness in the same paragraph: the wiki said
    `insert(Border)` seeds "all three" (center/radius/layerIndex) -- it actually seeds five
    fields (id/displayName too, added since that text was written). Fixed in the same edit.
  Closing this ticket -- its own scope was the spec review, now done and on the wiki page.
  Implementation against the corrected spec (the three `failureReason()` checks) is Lead Dev's
  next step, same shape as FRO_056 -> FRO_057 and FRO_058: expect a follow-up build ticket when
  that work is picked up, not opened here.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
