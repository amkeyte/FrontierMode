# Sick Wildlife -- ELI5 (scratch)

Not promoted to wiki yet. High-level block diagram for RM_FRO_037 ("Brenda," Frontier Sickness
epoch 1), specifically the **Sick Wildlife** half of [FRO_099](../../backhaul/tickets/FRO_099_frontier-sickness-core-build.md)
(design source: [FRO_098](../../backhaul/tickets/FRO_098_design-note-for-sick-wildlife-passive-mo.md)
and `wiki/frontiermode/design/exterior.md#sensory-design`'s "Sick wildlife" bullet) -- the new
function giving a sound and visual effect to passive animal mobs (rabbits etc.) once a player or
those animals are outside the Frontier.

Two independent halves, both keyed off the same underlying value -- how far past a border's edge
a position is (`BorderAPI.MATH.distanceOutside`, minimum across every border on the level):

1. **`ExteriorTellFixture`** (player-scoped, manually ticked from `BorderModule.onPlayerScopeTick`
   every player tick -- not off Satchel's normal `onJigTick()` dispatch, which SAT_049 found is
   dead in practice). Every ~1 second of game time, if the *player* is outside the Frontier, rolls
   two independent chances per nearby passive animal: a poison-colored particle (targeted, only
   that player sees it) and a hurt-sounding noise (area broadcast). Purely cosmetic -- no real
   damage, no death; a mob "clears" the moment it (or the player) is back inside.
2. **`BorderModule.onMobSpawnFinalize`** (hooked into the mod's shared `MobSpawnEvent.FinalizeSpawn`
   handler, alongside Boss's own delegate). Every time vanilla finalizes a passive-animal spawn, if
   the *spawn position* is outside the Frontier, rolls once against a distance-scaled chance
   (`FrontierSicknessLogic.wildlifeDensityMultiplier` -- rises fast right at the edge, flattens out
   further in, per project-owner direction) to spawn one extra copy of that same animal nearby.

Not shown here (separate, player-only, not an animal-mob effect): the one-time Entry Cue bass-drop
tone that plays for the player the moment they first cross into the Exterior, also driven from the
same `onPlayerScopeTick` handler.

Derived from: `border/common/player/ExteriorTellFixture.java` (full), `border/common/
FrontierSicknessLogic.java` (full, `wildlifeDensityMultiplier`), `border/BorderModule.java`
(`onPlayerScopeTick`, `onFrontierSicknessTick`, `onMobSpawnFinalize`, `spawnDensityCompanion`),
`wiki/frontiermode/design/exterior.md#sensory-design`, `FRO_098`/`FRO_099` ticket bodies (including
FRO_099's build log, which is the source for the "manually ticked, not via onJigTick()" detail and
the SAT_049 cross-reference).

```mermaid
%%{init: {'themeVariables': {'fontSize': '20px'}, 'flowchart': {'useMaxWidth': false, 'htmlLabels': true, 'nodeSpacing': 40, 'rankSpacing': 90}}}%%
flowchart TD
    DIST["How far past the Frontier's edge is a spot?<br/>(0 or less = still inside, nothing below applies)"]

    subgraph TELL["Animals already nearby a player<br/>(ExteriorTellFixture -- rechecked a few times a second, per player)"]
        PDIST{"Is the PLAYER<br/>outside the Frontier?"}
        FIND["Look at every passive animal<br/>near the player -- rabbits, etc."]
        ROLL1["Roll odds per animal,<br/>separately for particle and sound"]
        PARTICLE["Poison-colored particle<br/>(only that player sees it)"]
        SOUND["Hurt-sounding noise<br/>(anyone nearby can hear it)"]
        SKIP1["Nothing happens"]
    end

    subgraph SPAWN["New animals spawning<br/>(BorderModule -- every time vanilla spawns a passive animal)"]
        SDIST{"Is the SPAWN SPOT<br/>outside the Frontier?"}
        MULT["Farther out = more likely --<br/>rises fast right at the edge,<br/>then levels off"]
        ROLL2["Roll the odds once"]
        EXTRA["Spawn one extra copy of<br/>that same animal nearby"]
        SKIP2["Animal spawns normally,<br/>no extra copy"]
    end

    DIST --> PDIST
    DIST --> SDIST

    PDIST -->|"yes"| FIND --> ROLL1
    ROLL1 -->|"wins"| PARTICLE
    ROLL1 -->|"wins"| SOUND
    PDIST -->|"no"| SKIP1

    SDIST -->|"yes"| MULT --> ROLL2
    ROLL2 -->|"wins"| EXTRA
    ROLL2 -->|"loses"| SKIP2
    SDIST -->|"no"| SKIP2

    NOTE["Both halves are purely cosmetic --<br/>no real damage, no real death.<br/>An animal (or player) stops being<br/>flagged the moment it is back inside.<br/><br/>Not shown: a separate one-time<br/>entry-cue sound that plays for<br/>the PLAYER the first time they<br/>cross into the Exterior."]

    TELL ~~~ NOTE
    SPAWN ~~~ NOTE

    classDef note fill:#161b22,stroke:#30363d,color:#8b949e,text-align:left;
    class NOTE note
```

Large-font standalone render: `sick-wildlife-eli5.html`
