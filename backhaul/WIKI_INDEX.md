<!-- bh-header:start -->
**mcRepos** — [Dashboard](../BACKHAUL.md)
<!-- bh-header:end -->

# Wiki Index

## frontiermode

| Title | Status | Summary | Edit |
|---|---|---|---|
| [FrontierMode](wiki/frontiermode/frontiermode.md) | verified | Gameplay and world-tuning modifications for Minecraft; depends on Satchel for core data/utility support. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/frontiermode/frontiermode.md) |

## frontiermode/architecture

| Title | Status | Summary | Edit |
|---|---|---|---|
| [Border Curve](wiki/frontiermode/architecture/border-curve.md) | draft | Technical shape for BorderCurveFixture -- a sibling fixture in BordersBundle giving borders zero-to-many named intensity curves (placement, difficulty, ...), evaluated through BorderMath. First concrete consumer is Guardian Mobs -- proposal stage. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/frontiermode/architecture/border-curve.md) |
| [Border Pregeneration](wiki/frontiermode/architecture/border-pregeneration.md) | verified | Proactive, throttled terrain generation for a border's entire disk, owned by Border itself -- closes the "chunks are still loading" discovery exploit and gives Boss (and future consumers, like a placed Environmental Tell) real validated terrain to build on instead of a blind, unchecked coordinate. Partially supersedes boss.md's Spawn Algorithm. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/frontiermode/architecture/border-pregeneration.md) |
| [Border Vocabulary](wiki/frontiermode/architecture/border-vocabulary.md) | verified | Canon terminology for FrontierMode's Border system: Relevance, Layer, Path, and Difficulty, replacing overloaded use of 'level' across the wiki and code. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/frontiermode/architecture/border-vocabulary.md) |
| [Border](wiki/frontiermode/architecture/border.md) | verified | FrontierMode's world-border system -- the mod's one substantial feature, built on Satchel's fixture/facet and jig/scope model. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/frontiermode/architecture/border.md) |
| [Boss Command Surface](wiki/frontiermode/architecture/boss-commands.md) | draft | Command-tree design space for an in-game Boss admin/dev surface (RM_FRO_022 "Joyce"). Six items shipped and playtest-verified on FRO_057 (info/add/delete/mob spawn/transform defeat/debug goto, plus debug distance added mid-playtest); the rest of the info/add/delete/transform/mob/debug tree, the borderId-based selector chain, and the border-move reconciliation direction remain documented proposal, not yet built. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/frontiermode/architecture/boss-commands.md) |
| [Boss](wiki/frontiermode/architecture/boss.md) | verified | Boss entity/spawn system for Tier 1 -- data model, mutation validation boundary, spawn algorithm, and the defeat-detection caller into BorderAPI. RM_FRO_019 builds against this. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/frontiermode/architecture/boss.md) |
| [Difficulty](wiki/frontiermode/architecture/difficulty.md) | draft | Design pass for the Difficulty seam on BorderRules (FRO_029 Phase 4): the Layer-to-Difficulty formula, the ambient-vs-boss selector split, and the BorderPlayerStatus reshape it depends on. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/frontiermode/architecture/difficulty.md) |
| [Boss Discovery Systems](wiki/frontiermode/architecture/discovery-systems.md) | verified | Technical shape for Tier 2's discovery-gradient tools (guardian mobs, tells, beacons, tracker, compass, warps) and the navigation/attunement mechanism, hosted in Border, that most of them build on -- proposal stage, drafted ahead of minting RM_FRO_023's real intermediate nodes. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/frontiermode/architecture/discovery-systems.md) |
| [Border Path & Layer Reconciliation](wiki/frontiermode/architecture/path-layer-reconciliation.md) | draft | How fixLayers() reconciles Border.layer() to borderPath order after a manual reorder, and why the two are allowed to diverge in the first place. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/frontiermode/architecture/path-layer-reconciliation.md) |

## frontiermode/design

| Title | Status | Summary | Edit |
|---|---|---|---|
| [Boss Discovery](wiki/frontiermode/design/boss-discovery.md) | draft | The clue gradient players use to find bosses, from ambient guardian mobs to expensive tracking tools, plus loot design. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/frontiermode/design/boss-discovery.md) |
| [Guardian Mobs](wiki/frontiermode/design/guardian-mobs.md) | draft | Stronger, visually distinct hostile mob variants that cluster near bosses as a passive discovery aid and tension ramp. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/frontiermode/design/guardian-mobs.md) |
| [Multiplayer Sketch (Parked)](wiki/frontiermode/design/multiplayer-sketch.md) | draft | Early, unscoped ideas for how the frontier loop might work in multiplayer -- fixed/summoned bosses, gear-based scaling. Not in active design scope. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/frontiermode/design/multiplayer-sketch.md) |
| [Nethack Ideas (Parked)](wiki/frontiermode/design/nethack-ideas-parked.md) | draft | Nethack-derived feature ideas kept in mind for later so the core loop isn't band-aided to fit them after the fact. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/frontiermode/design/nethack-ideas-parked.md) |
| [Nether and End](wiki/frontiermode/design/nether-and-end.md) | draft | How the Nether and End tie into frontier progression via the furthest Path position the player has reached rather than their own spatial frontier. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/frontiermode/design/nether-and-end.md) |
| [Frontier Mode Overview](wiki/frontiermode/design/overview.md) | draft | Core identity, design pillars, and the gradient-not-walls philosophy for FrontierMode's game-mode concept. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/frontiermode/design/overview.md) |
| [Progression and Frontier Mechanics](wiki/frontiermode/design/progression.md) | draft | How the frontier expands: cylinder growth, re-centering, irregular shape, and the oldest-ring-wins overlap rule. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/frontiermode/design/progression.md) |

## meta

| Title | Status | Summary | Edit |
|---|---|---|---|
| [BHRM — Roadmap Conventions](wiki/meta/bhrm.md) | draft | Roadmap node ID scheme, why short slugs matter here, and CLI cheatsheet. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/meta/bhrm.md) |
| [BHRole — Agent Role Conventions](wiki/meta/bhrole.md) | verified | Role page structure, why bootstrap prompts must stay evergreen, and CLI cheatsheet. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/meta/bhrole.md) |
| [BHT — Ticket Conventions](wiki/meta/bht.md) | draft | Ticket ID scheme, slug convention, and CLI cheatsheet. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/meta/bht.md) |
| [BHW — Wiki Conventions](wiki/meta/bhw.md) | draft | Wiki page ID scheme, slug convention, and CLI cheatsheet. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/meta/bhw.md) |
| [Git Branching by Epoch](wiki/meta/git-branching.md) | draft | Proposed branch-per-epoch policy for parking side-quest work while the mainline moves into the next epoch. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/meta/git-branching.md) |

## plans

| Title | Status | Summary | Edit |
|---|---|---|---|
| [Full Documentation Coverage Plan](wiki/plans/doc-coverage.md) | published | Plan to bring FrontierMode and Satchel's wiki up to full design/spec coverage of existing code before content work starts. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/plans/doc-coverage.md) |
| [Donna Epoch Nodes](wiki/plans/donna-epoch-nodes.md) | draft | Candidate RM_FRO nodes for the Donna epoch's Tier 2 discovery-gradient cluster. Its shared-infrastructure trio (Navigator, Border Curve, Border Pregeneration) is minted -- RM_FRO_026/027/028; the six discovery tools remain staged here, pending their own scoping pass against that trio's real code. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/plans/donna-epoch-nodes.md) |
| [FrontierMode Operational Tiers](wiki/plans/operational-tiers.md) | published | The experience-tier framework FrontierMode's roadmap convergence nodes are organized around, instead of one convergence per module. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/plans/operational-tiers.md) |
| [Bare-Necessity Strip-Down Plan](wiki/plans/strip-down.md) | published | Plan to strip both mod repos to the minimum needed to remain valid Forge mods, and replace git history clean. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/plans/strip-down.md) |

## reference

| Title | Status | Summary | Edit |
|---|---|---|---|
| [Top Baby Names of 1945](wiki/reference/baby-names-1945.md) | verified | 1945 US top-100 baby names (SSA) -- source list for persona names, code slugs, and other naming needs across this project. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/reference/baby-names-1945.md) |

## satchel

| Title | Status | Summary | Edit |
|---|---|---|---|
| [Satchel](wiki/satchel/satchel.md) | verified | Core data & utility mod for FrontierMode -- bundles/fixtures, networking, and per-bundle persistence. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/satchel/satchel.md) |

## satchel/architecture

| Title | Status | Summary | Edit |
|---|---|---|---|
| [Bundle](wiki/satchel/architecture/bundle.md) | verified | Satchel's primary unit of state aggregation -- identity, lifecycle, and the scope/bundle/facet/stitch mental model. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/satchel/architecture/bundle.md) |
| [Universal Sidedness Facade](wiki/satchel/architecture/facade-vision.md) | draft | Vision: Satchel as the exclusive path to Forge for every module -- every touch point, not just tick/lifecycle, guaranteed side-correct by construction rather than by thread discipline. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/satchel/architecture/facade-vision.md) |
| [Fixture](wiki/satchel/architecture/fixture.md) | verified | The fixture/facet package -- Satchel's modder-facing unit of persistent state: lifecycle, field registration, save/load contract, isolation rules. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/satchel/architecture/fixture.md) |
| [Forge Event Conduit (Parked)](wiki/satchel/architecture/forge-event-conduit.md) | draft | Open idea: route a jig's declared Forge gameplay events through Satchel-scoped dispatch instead of raw MinecraftForge.EVENT_BUS registration in module code. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/satchel/architecture/forge-event-conduit.md) |
| [Jig & Strap Registration — History](wiki/satchel/architecture/jig-registration-break.md) | verified | Historical record of the compile-blocking jig/strap registration regression found and fixed on 2026-08-13 -- investigation notes, root causes, and the fix chain. Current mechanism is documented in runtime.md. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/satchel/architecture/jig-registration-break.md) |
| [Jig & Strap Registration — Recovery Plan](wiki/satchel/architecture/jig-registration-recovery-plan.md) | verified | Proposed direction and first cleanup step to get FrontierMode compiling again against Satchel's newer declarative config system, deprecating the dead imperative Registrar/Strap pattern. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/satchel/architecture/jig-registration-recovery-plan.md) |
| [Networking](wiki/satchel/architecture/net.md) | verified | Satchel's transport-only networking layer -- packet model, guarantees, forbidden behavior. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/satchel/architecture/net.md) |
| [New Module Checklist](wiki/satchel/architecture/new-module-checklist.md) | draft | Footguns every new Satchel jig/module consumer has hit at least once -- register schema only, wire executionPulse if sync is needed, wire Forge listeners, respect LogicalSideContext thread discipline, keep bundles single-concern. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/satchel/architecture/new-module-checklist.md) |
| [Persistence](wiki/satchel/architecture/persistence.md) | verified | Server-side per-bundle persistence architecture (Satchel 2.0) -- BundleSavedData, identity rules, dirty propagation. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/satchel/architecture/persistence.md) |
| [Jig & Scope Runtime](wiki/satchel/architecture/runtime.md) | verified | The jig/scope/foundation tick-and-event delivery machinery underneath Satchel -- foundations, the dispatch chain, JigConfig registration, and the three jig kinds. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/satchel/architecture/runtime.md) |
| [SatchelHealth](wiki/satchel/architecture/satchel-health.md) | draft | Satchel's run-monitoring / self-verification home -- live regression checks for MobJig, LevelJig, and PlayerJig, checked every real client/server run rather than gated behind gradle test. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/satchel/architecture/satchel-health.md) |

## satchel/spec

| Title | Status | Summary | Edit |
|---|---|---|---|
| [Forge Integration & Sidedness Contract](wiki/satchel/spec/forge-integration.md) | verified | Which classes may touch Forge's event buses directly, which bus each legitimate touch-point uses, and the sidedness/thread-binding rules any code reaching into Satchel must follow. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/satchel/spec/forge-integration.md) |
| [MobScope.getFor() Contract](wiki/satchel/spec/mobscope-getfor.md) | verified | The static-factory boundary contract BossModule and Shirley's defeat handler depend on: fast-path semantics, the Optional.empty() removed-reference case, and what it guarantees about poll-cycle timing. | [Edit](editmd:///C:/_local/mcRepos/backhaul/wiki/satchel/spec/mobscope-getfor.md) |
