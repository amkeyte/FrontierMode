<!-- bh-header:start -->
**mcRepos** — [Dashboard](../BACKHAUL.md)
<!-- bh-header:end -->

# Work Board

## open

| Ticket | Client | Pri | Title | Context | Edit |
|---|---|---|---|---|---|
| [SAT_028](tickets/SAT_028_consolidate-bundlefactories-into-schema.md) | Satchel | normal | Consolidate BundleFactories into JigBundles.Schema | JigBundles.BundleDecl already carries a real bundle Factory and FixtureDecl list -- functionally identical to what BundleFactories.registerFactory/.registerFixture store separately. ScopeEngine.create()/get() read BundleFactories.entryFor(key), never the already-populated bundleDecls map from registerBundleSchema(). Confirmed mid-refactor (user): the schema config was meant to do more than it currently does. Deferred -- not urgent, but worth doing before more modules are built on the current duality, since it has already caused two independent bugs (SAT_022, and the identical pattern in Border). | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/SAT_028_consolidate-bundlefactories-into-schema.md) |

## in-progress

| Ticket | Client | Pri | Title | Context | Edit |
|---|---|---|---|---|---|
| [FRO_016](tickets/FRO_016_null-level-on-exit-crash.md) | FrontierMode | normal | RenderContext.getInstance() crashes on level exit | Threw IllegalStateException when Minecraft.level is null, which happens for one or more client ticks after disconnect before the jig's tick participation unsubscribes. Author's own comment already flagged this as unexplained; root cause is a normal teardown-window race, not a real error. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_016_null-level-on-exit-crash.md) |

## blocked

_No tickets in this state._
