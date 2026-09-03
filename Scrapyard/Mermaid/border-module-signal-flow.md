# BorderModule — signal flow (scratch)

Not promoted to wiki yet. Every distinct entry point that actually runs code inside
`BorderModule` as of current source, stacked as separate sections in one diagram (they trigger
independently, not sequentially in real time -- the vertical order below is just reading order,
not a real timeline).

Derived from: `border/BorderModule.java`, `FrontierMode.java`, `border/server/rules/BordersTriggers.java`,
`border/BorderAPI.java`.

One thing worth flagging: `init()` also wires `Tick -> BordersTriggers.updateFinderItems`,
`Tick -> Rendering.onClientTick`, and `Unloaded -> Rendering.onClientUnload` directly to
Satchel's event bus via the `EventHandlers` builder. Those are real signals BorderModule sets up,
but the dispatch calls the target method straight -- BorderModule itself is off the stack by the
time they actually fire. Shown as a note, not a flow, for that reason.

```mermaid
%%{init: {'themeVariables': {'fontSize': '44px'}, 'sequence': {'actorFontSize': 30, 'actorFontWeight': 'bold', 'messageFontSize': 26, 'noteFontSize': 26}}}%%
sequenceDiagram
    participant FM as FrontierMode
    participant FEB as Forge EVENT_BUS
    participant SEB as Satchel event bus
    participant BM as BorderModule
    participant BT as BordersTriggers
    participant DBR as DefaultBorderRules
    participant BC as BorderCommands
    participant API as BorderAPI
    participant BF as BordersFixture
    participant BPB as BorderPlayerBundle
    participant BPSF as BorderPlayerStatusFixture
    participant Boss as BossAPI

    Note over BM: === Boot: FrontierMode() constructor, once ===
    FM->>BM: init()
    BM->>SEB: registerJigConfig(config) -- LevelJigConfig, BOTH-applicability
    BM->>SEB: registerJigConfig(playerConfig) -- PlayerJigConfig
    BM->>FEB: addListener(BorderModule::onBlockPlaced)
    Note over BM,SEB: init() also binds Tick->BordersTriggers.updateFinderItems,<br/>Tick->Rendering.onClientTick, Unloaded->Rendering.onClientUnload --<br/>those fire straight to their targets, not back through BorderModule.

    Note over BM: === Command registration, once per world join ===
    FEB->>FM: RegisterCommandsEvent
    FM->>BM: onRegisterCommands(event)
    BM->>BC: register(dispatcher)

    Note over BM: === Block-placed growth trigger, raw Forge listener ===
    FEB->>BM: onBlockPlaced(BlockEvent.EntityPlaceEvent)
    BM->>BT: growPath(event)
    BT->>DBR: growPathCriteria(level, pos, placedBlock) [BorderRules.ACTIVE]
    DBR-->>BT: boolean
    alt criteria met
        BT->>API: grow(level)
        API->>BF: PATH.grow()
        BF-->>API: Result
        Note over BT: sendSystemMessage - '[Border] Advanced border progression'
    end

    Note over BM: === Player-scope tick, PlayerJig, filtered to BORDER_PLAYER_JIG ===
    SEB->>BM: ScopeEvent.Tick (PlayerJig)
    BM->>BM: filter info.jigInfo().key == BORDER_PLAYER_JIG
    BM->>BPB: jig.getOrCreate(scope, BORDER_PLAYER_BUNDLE)
    BPB-->>BM: bundle
    BM->>BPB: getOrCreateFixture(BORDER_PLAYER_STATUS, ...)
    BPB-->>BM: fixture
    BM->>API: CRUD(player.serverLevel())
    API->>BF: CRUD facet
    BF-->>BM: List of Border
    BM->>BPSF: accept(proposal, borders, player.blockPosition())

    Note over BM: === Level-scope loaded, LevelJig, filtered to BORDERS_JIG, server + overworld only ===
    SEB->>BM: ScopeEvent.Loaded (LevelJig)
    BM->>BM: filter key==BORDERS_JIG, side==SERVER, dimension==OVERWORLD
    BM->>API: INFO(level)
    API-->>BM: seeded()?
    alt not seeded
        BM->>API: grow(level)
        API->>BF: PATH.grow()
        BF-->>API: Result
        API-->>BM: Result (border)
        BM->>Boss: createBoss(level, border)
        BM->>API: startPregeneration(level, border.id())
    end
```
