# FrontierMode & Satchel

**@ Your Own Risk.**

Two Minecraft Forge mods, developed together:

- **FrontierMode** -- the game: a safe, known area that only grows when you defeat the Amethyst Keeper
  hiding in it, and a deadly Wild beyond.
- **Satchel** -- the core data and utility framework FrontierMode is built on (scoped state,
  networking, persistence, and a lifecycle-signal layer over Forge's events).

<!-- TODO: screenshots / a short GIF of a Keeper fight would go here (needs captures). -->

## The Wild

There is no surviving beyond the Frontier. In the Wild, there is only death and disease.

## The Amethyst Keepers

Powerful enemies that must be defeated to expand the "safe" known areas and push back the Wild -- if
you can find them.

## The Unknown

Discover new materials and tools to aid your mission and navigate dangerous territory. Explore the
ever-growing safe areas to find the next Amethyst Keeper. Learn to build or find structures to guide
your journey.

## Expansive

The Wild is as big as the Overworld itself -- and the Keeper is always ready, waiting to do battle!

## Progress

Structures are currently partially implemented, and game balancing is in progress. At release this is
a hard, fast start, and there are no difficulty levels yet. Expect to die working out how to stay
alive. Multiplayer is supported, but the system is currently geared to single player. Worlds are not
guaranteed to be portable between versions; expect to start a new one after updating.

## Requirements

- Minecraft **1.20.1**
- Minecraft Forge **47.4.10** or newer 47.x
- Both mods installed together: FrontierMode depends on Satchel.

## Installation

1. Install Forge for Minecraft 1.20.1.
2. Download `satchel-<version>.jar` and `frontiermode-<version>.jar` from
   [Releases](https://github.com/amkeyte/FrontierMode/releases) -- take both, at the same version.
3. Put them in your `mods` folder.
4. Start a **new world**.


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

All visual assets for Frontier Mode are created by Arryn77. Audio assets are created and edited by
Arryn77, or by an attributed creator. System design and code architecture by Arryn77. The source has
been vibecoded by Arryn77 using Claude Code.

Sounds, art, and the tools and frameworks this project stands on are credited in
**[CREDITS.md](CREDITS.md)**.

*Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.*
