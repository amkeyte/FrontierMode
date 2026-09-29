# FrontierMode & Satchel

<!-- TODO: one-line pitch for the GitHub repo description and the top of this page. -->
Two Minecraft Forge mods, developed together:

- **FrontierMode** -- gameplay and world-expansion systems: the world is bounded by a border that
  grows as you defeat bosses, and the game teaches you to find them.
- **Satchel** -- the core data and utility framework FrontierMode is built on (scoped state,
  networking, persistence, and a lifecycle-signal layer over Forge's events).

> **Status:** early development. Worlds are not compatible between versions; expect to start a new
> one after updating.

<!-- TODO: screenshots / a short GIF of a boss encounter would go here. -->

## Requirements

- Minecraft **1.20.1**
- Minecraft Forge **47.4.10** or newer 47.x
- Both mods installed together: FrontierMode depends on Satchel.

## Installation

<!-- TODO: point at GitHub Releases once jars are published there. -->
1. Install Forge for Minecraft 1.20.1.
2. Put `satchel-<version>.jar` and `frontiermode-<version>.jar` in your `mods` folder.
3. Start a **new world**.

## Playing

<!-- TODO: a short "how it works" for players (border, bosses, discovery tools). -->
Commands are operator-only (permission level 2): `/border`, `/boss`, `/feral`, `/admin`.

## Balance values

Game-balance numbers are kept in `config/satchel-tunables.csv`. The jar ships a default copy and
writes it out on first run; edit the file, stop the server, and start it again to apply changes.
This is a developer/tuning file, not a supported player setting.

## Building

Each mod is its own Gradle project (they are deliberately not combined into one build).

```
cd Satchel && ./gradlew build
cd ../FrontierMode && ./gradlew build
```

Build Satchel first; FrontierMode compiles against it.

## Repository layout

| Path | What it is |
|---|---|
| `FrontierMode/` | The gameplay mod |
| `Satchel/` | The framework mod |

## License

FrontierMode and Satchel are both released under the [MIT License](LICENSE).

Third-party assets (the sound files) keep their own licenses, listed per file in
[CREDITS.md](CREDITS.md).

## Credits

Sounds, art, and the tools and frameworks this project stands on are credited in
**[CREDITS.md](CREDITS.md)**.

*Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.*
