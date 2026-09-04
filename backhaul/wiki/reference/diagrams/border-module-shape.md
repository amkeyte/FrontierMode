---
id: reference/diagrams/border-module-shape
category: reference/diagrams
slug: border-module-shape
title: Border Module Shape
summary: 'High-level module map of Border: BordersBundle''s sibling fixtures, BordersFixture''s
  four facets, the BorderAPI facade, and the three consumer surfaces (commands, client
  rendering, server rules/triggers).'
keywords: null
status: verified
updated: '2026-09-03'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · reference / diagrams
<!-- bh-header:end -->

# Border Module Shape

High-level module map of Border: BordersBundle's sibling fixtures, BordersFixture's four facets, the BorderAPI facade, and the three consumer surfaces (commands, client rendering, server rules/triggers).

Large-font standalone render: [html/border-module-shape.html](html/border-module-shape.html).

Moved here from the old `frontiermode/diagrams` category as part of consolidating every diagram
under [Reference](_diagrams.md) -- see that page for the convention this now follows.

Scoped to the world-scoped (`LevelScope`) half of Border only -- the parallel `PlayerScope`
structure (`BorderPlayerBundle`/`BorderPlayerStatusFixture`) is left for its own diagram. Assumes
the reader already knows Satchel's bundle/fixture/jig model; nothing outside `border/` is shown,
including the one live call this module makes out to Boss on level bootstrap
(`BorderModule.onBordersScopeLoaded` -> `BossAPI.createBoss`).

Derived from: `border/BorderModule.java`, `border/BorderAPI.java`,
`border/common/bundle/BordersBundle.java`, `border/common/fixture/BordersFixture.java`,
`border/server/commands/{BorderCommands,BorderCommandHandler,BorderSelector}.java`,
`border/client/render/level/{RenderContext,WorldBordersRenderer,GrowthTriggerRenderer}.java`,
`border/server/rules/{BordersTriggers,DefaultBorderRules,BorderRules}.java`. Checked against
[Border](../../frontiermode/architecture/border.md) and
[Border Vocabulary](../../frontiermode/architecture/border-vocabulary.md) -- no drift found
between either page and current source.

```mermaid
flowchart TD
    BM["BorderModule<br/>.init()"]

    subgraph BUNDLE["BordersBundle (LevelScope)"]
        BB["BordersBundle"]
        BF["BordersFixture"]
        NF["NavigatorFixture"]
        CVF["BorderCurveFixture"]
        PGF["BorderPregenFixture"]
        PATH["PATH<br/>BordersPathFacet"]
        CRUD["CRUD<br/>BordersCrudFacet"]
        RULESF["RULES<br/>BordersRulesFacet"]
        INFO["INFO<br/>BordersInfoFacet"]

        BB -->|hosts| BF
        BB -->|hosts| NF
        BB -->|hosts| CVF
        BB -->|hosts| PGF
        BF -->|exposes| PATH
        BF -->|exposes| CRUD
        BF -->|exposes| RULESF
        BF -->|exposes| INFO
    end

    subgraph COMMANDS["Server commands"]
        CMD["BorderCommands /<br/>BorderCommandHandler /<br/>BorderSelector"]
    end

    subgraph RENDERING["Client rendering"]
        RC["RenderContext<br/>(per-level cache,<br/>refreshed every 20 ticks)"]
        WBR["WorldBordersRenderer"]
        GTR["GrowthTriggerRenderer"]
        RC --> WBR
        RC --> GTR
    end

    subgraph RULES_TRIG["Server rules & growth triggers"]
        DBR["DefaultBorderRules<br/>(BorderRules.ACTIVE)"]
        BT["BordersTriggers"]
        BT -->|growPathCriteria /<br/>updateFinderItems| DBR
    end

    API["BorderAPI<br/>(side-agnostic facade)"]

    API -->|resolves| PATH
    API -->|resolves| CRUD
    API -->|resolves| RULESF
    API -->|resolves| INFO
    API -->|resolves via bundle| CVF
    API -->|resolves via bundle| PGF

    CMD -->|calls| API
    RC -->|calls| API
    BT -->|calls grow| API

    BM -->|registers bundle schema +<br/>Tick/Loaded/Unloaded handlers| BB
    BM -->|raw Forge BlockEvent<br/>listener| BT
    BM -->|onBordersScopeLoaded:<br/>grow + startPregeneration| API
```
