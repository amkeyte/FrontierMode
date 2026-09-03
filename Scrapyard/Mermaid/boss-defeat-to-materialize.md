# forceDefeat() -> boss instanced as a mob (scratch)

Not promoted to wiki yet. Traces the full path from `BossAPI.forceDefeat(level, bossId)` being
called through to the next boss standing in the world as a real, Satchel-attached `Mob` entity --
assuming its home chunk is already loaded by the time materialization is attempted. Chunk-loaded
being assumed removes the tick-retry loop in `materializeUnresolvedBosses` (normally it just waits
for `Level.isLoaded` to go true), but it does **not** remove the separate pregeneration gate --
`finalizeUnpositionedBosses` still can't pick a real position until `BorderAPI.isPregenReady()`
passes for the new border's disk, which is genuine async wait time (can be real minutes), shown
here as a Note rather than a modeled retry loop.

Two early-exit guards inside `forceDefeat` itself aren't traced further (both return with no mob):
the target record must already be `positioned()` (a still-pregenerating boss can't be force-
defeated), and it must not already be defeated (FRO_058's double-defeat guard). Only the success
path is shown.

The diagram spans three genuinely separate execution contexts, which is what makes this one worth
having as its own drawing rather than folding into an existing one:

1. **The synchronous `forceDefeat` call itself** -- ends the instant it returns a `DefeatOutcome`,
   well before any mob exists.
2. **`BOSS_JIG`'s own tick** (`BossModule.onBossJigTick`, every server tick) -- this is where
   `finalizeUnpositionedBosses()` (once pregen is ready) and `materializeUnresolvedBosses()` (once
   the chunk is loaded) actually run, in that order, same tick.
3. **The next foundation pulse**, on an entirely different scope (`BOSS_MOB_JIG`, `MobScope`, not
   `BOSS_JIG`'s `LevelScope`) -- `BossModule.onBossMobScopeLoaded` is what actually attaches
   `BossMobFixture` to the freshly materialized entity. The mob is genuinely alive in the world one
   full step before this reverse-pointer exists.

Derived from: `boss/BossAPI.java`, `boss/BossModule.java` (`onBossJigTick`,
`finalizeUnpositionedBosses`, `materializeUnresolvedBosses`, `registerBossMobJig`,
`onBossMobScopeLoaded`), `boss/common/fixture/{BossFixture,BossRecord,BossMobFixture}.java`,
`boss/server/rules/{BossRules,DefaultBossRules}.java` (`materialize`'s actual `EntityType.create`
/ `addFreshEntity` spawn sequence).

```mermaid
%%{init: {'themeVariables': {'fontSize': '20px'}, 'sequence': {'actorFontSize': 18, 'actorFontWeight': 'bold', 'messageFontSize': 16, 'noteFontSize': 16, 'width': 170, 'height': 50, 'boxTextMargin': 8, 'useMaxWidth': false}}}%%
sequenceDiagram
    participant CALLER as Caller (e.g. /boss transform defeat)
    participant API as BossAPI
    participant BF as BossFixture
    participant BAPI as BorderAPI
    participant BPF as BorderPregenFixture
    participant BM as BossModule
    participant DBR as DefaultBossRules
    participant LVL as ServerLevel / Mob
    participant SAT as Satchel MobJig
    participant BMF as BossMobFixture

    Note over CALLER,API: === forceDefeat(level, bossId) -- synchronous call ===
    CALLER->>API: forceDefeat(level, bossId)
    API->>BF: bosses(level) -- get fixture
    BF-->>API: BossFixture
    API->>BF: get(bossId)
    BF-->>API: BossRecord
    Note over API: Two guard checks precede this point --<br/>record.positioned() and not already defeated.<br/>Both rejected paths return early with no mob.<br/>Tracing only the success path from here.
    API->>BF: markDefeated(bossId)
    BF-->>API: true
    API->>BAPI: grow(level, record.position())
    BAPI-->>API: Result (success, new Border)
    API->>API: createBoss(level, result.border())
    API->>BF: create(border.layer())
    BF-->>API: new BossRecord (unpositioned, unmaterialized)
    API->>BAPI: startPregeneration(level, result.border().id())
    BAPI->>BPF: kicks off async pregen disk
    API-->>CALLER: DefeatOutcome(result, nextBoss)

    Note over BPF: === Some real time later, async: BorderPregenFixture finishes the disk ===

    Note over BM: === BOSS_JIG tick, every server tick until ready ===
    BM->>BM: onBossJigTick -- finalizeUnpositionedBosses()
    BM->>BAPI: PATH(level), CRUD(level) -- resolveHomeBorder(layer)
    BAPI-->>BM: Border
    BM->>BAPI: isPregenReady(level, border.id())
    BAPI-->>BM: true -- disk ready, proceed (false = retried next tick, not shown)
    BM->>DBR: choosePosition(level, border)
    DBR-->>BM: BlockPos, flatness/hazard scored
    BM->>BF: finalizePosition(bossId, chosen)
    BF-->>BM: true -- record now positioned

    Note over BM: === Same tick, continues -- materializeUnresolvedBosses() ===
    BM->>LVL: isLoaded(position)?
    LVL-->>BM: true, assumed -- chunk already loaded
    BM->>DBR: materialize(level, position, layer)
    DBR->>LVL: EntityType.create(level)
    LVL-->>DBR: Mob
    DBR->>LVL: mob.moveTo(...), finalizeSpawn(...)
    DBR->>LVL: mob.setPersistenceRequired()
    DBR->>DBR: applyStatScaling(mob, layer), tagVisibly(mob, layer)
    DBR->>LVL: addFreshEntity(mob)
    LVL-->>DBR: true -- mob now live in the world
    DBR-->>BM: Optional, present -- the Mob
    BM->>BF: materialize(bossId, mob.getUUID())
    BF-->>BM: true -- record now materialized
    BM->>BM: addInterest(level, mob.getUUID())
    BM->>SAT: MobScope.getFor(mob) -- fast-path introduction
    SAT-->>BM: source introduced, attach deferred to next pulse

    Note over SAT,BMF: === Next foundation pulse -- BOSS_MOB_JIG's own scope converges ===
    SAT->>BM: ScopeEvent.Loaded (BOSS_MOB_JIG)
    BM->>API: bosses(mob.level())
    API-->>BM: BossFixture
    BM->>BF: all() -- find record by bossEntityId
    BF-->>BM: matching BossRecord
    BM->>SAT: jig.getOrCreate(scope, BOSS_MOB_BUNDLE)
    SAT-->>BM: BossMobBundle
    BM->>BMF: getOrCreateFixture(BOSS_MOB, ...)
    BMF-->>BM: BossMobFixture
    BM->>BMF: attachTo(record.bossId())
    Note over BMF: Boss is now a live entity in the world,<br/>Satchel-attached both directions --<br/>BossRecord.bossEntityId points to the entity,<br/>BossMobFixture.bossId points back to the record.
```
