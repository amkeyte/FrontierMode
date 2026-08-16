---
id: RM_SAT_015
uid: RM_SAT
number: 15
kind: work
status: resolved
title: Clean up dead registrar/ModelJig code
owner: Arryn
depends_on:
- RM_SAT_011
created: '2026-08-14'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Clean up dead registrar/ModelJig code

- 2026-08-15: **Resolved by Lead Dev (Curtis).** `ModelJig` package
  (`common/jig/model/ModelJig.java`, `ModelCoupler.java`, `ModelScope.java`, `ModelSource.java`)
  and `server/jig/guts/model/ServerModelIngress.java` deleted, plus the now-empty `model/`
  directories under both packages. Confirmed via grep across both repos first: nothing outside
  those five files referenced any of these symbols.
  - **Finding, not action taken:** the "dead registration scaffolding" item below
    (`SatchelJigRegistrar2.java`, `SatchelStrap.java`/`SatchelStrapRegistrar.java`, the commented
    `Satchel.activateRegistrations` block) does not exist anywhere in current `Satchel/src` — grepped
    case-insensitively for `Registrar`, `SatchelStrap`, and `activateRegistrations`, zero hits.
    `Satchel.java` itself is already clean (no commented block present). Most likely already removed
    during the 2026-08-13 compile-fix pass ([SAT_008](../tickets/SAT_008_fix-4-satchel-compile-blocking-errors.md)/[SAT_011](../tickets/SAT_011_wire-eventhandlers-install-into-boot.md))
    without this node or the recovery-plan wiki page being updated to match. Flagging for whoever
    owns wiki upkeep (PM/Architect) to correct
    [Jig & Strap Registration — Recovery Plan](../wiki/satchel/architecture/jig-registration-recovery-plan.md#rot-to-remove-safe-fully-superseded-nothing-depends-on-keeping-them)'s
    "Rot to remove" section, which still lists these as pending.
- 2026-08-14: Node opened during the hardening-convergence discussion. Two low-risk cleanup items,
  bundled because both are pure deletions with no design call attached.

**Dead registration scaffolding** — already identified as safe to remove in
[Jig & Strap Registration — Recovery Plan](../wiki/satchel/architecture/jig-registration-recovery-plan.md#rot-to-remove-safe-fully-superseded-nothing-depends-on-keeping-them)
but never actually deleted: `common/jig/guts/SatchelJigRegistrar2.java`,
`common/jig/strap/SatchelStrap.java` and `SatchelStrapRegistrar.java`, and the commented-out
`Satchel.activateRegistrations(LogicalSide)` block in `Satchel.java`. Fully superseded by
`JigConfig`/`JigConfigCompiler`; nothing depends on keeping them.

**`ModelJig`** (`common/jig/model/*` — `ModelJig`, `ModelCoupler`, `ModelScope`, `ModelSource`,
plus `ServerModelIngress`) — structurally complete, compiles, but has no `JigConfig` subclass and
no registered config anywhere in either repo (see
[Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md#the-three-jig-kinds)). Confirmed by
the project owner: it was built as a sample/reference model, not an in-progress feature — dead
weight, not scaffolding-in-waiting. Delete along with `ServerModelIngress` and its dedicated
ingress path.

**Explicitly not included here:** `PlayerJig` and its package (also fully commented out) stay
untouched — it's tangled up with [RM_FRO_006](RM_FRO_006_sandra.md)'s still-open per-player
scoping question, parked deliberately rather than resolved. Deleting it now would foreclose an
option that question might still need.

## Required By

*(computed — nothing depends on this yet)*
