# FrontierMode & Satchel

**@ Your Own Risk.**

Two Minecraft mods, developed together, for Forge 1.20.1 and NeoForge 1.21.1:

- **FrontierMode** -- the game: a safe, known area that only grows when you defeat the Amethyst Keeper
  hiding in it, and a deadly Wild beyond.
- **Satchel** -- the core data and utility framework FrontierMode is built on (scoped state,
  networking, persistence, and module events over the mod loader's events).

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

One of these two setups:

| Minecraft | Mod loader |
|---|---|
| **1.20.1** | Minecraft Forge **47.4.10** or newer 47.x |
| **1.21.1** | NeoForge **21.1.267** or newer 21.1.x |

Install both mods together: FrontierMode depends on Satchel.

## Installation

1. Install Forge for Minecraft 1.20.1, or NeoForge for Minecraft 1.21.1.
2. From [Releases](https://github.com/amkeyte/FrontierMode/releases), download both mods for your
   loader, at the same version. The file names end with the loader and Minecraft version:
   - `Satchel-<version>-<loader>-<minecraft>.jar`
   - `frontiermode-<version>-<loader>-<minecraft>.jar`

   For example, `frontiermode-0.3.0-neoforge-1.21.1.jar`.
3. Put them in your `mods` folder.
4. Start a **new world**.


## Building

Each mod has its own Gradle project for each loader (they are deliberately not combined into one
build). The Forge 1.20.1 projects use JDK 17, and the NeoForge 1.21.1 projects use JDK 21.

```
cd Satchel/forge-1.20.1 && ./gradlew build
cd ../../FrontierMode/forge-1.20.1 && ./gradlew build
```

For NeoForge, use the `neoforge-1.21.1` folders the same way. Build Satchel first, for the same
loader; FrontierMode compiles against its jar.

## Repository layout

| Path | What it is |
|---|---|
| `FrontierMode/` | The gameplay mod |
| `Satchel/` | The framework mod |
| `<mod>/shared/` | Code that both loaders compile |
| `<mod>/forge-1.20.1/` | The Forge project, with the code only it uses |
| `<mod>/neoforge-1.21.1/` | The NeoForge project, with the code only it uses |
| `CHANGELOG.md` | Release notes |

## License

FrontierMode and Satchel are both released under the [MIT License](LICENSE).

Third-party assets (the sound files) keep their own licenses, listed per file in
[CREDITS.md](CREDITS.md).

## Credits

All visual assets for Frontier Mode are created by Arryn77. Audio assets are created and edited by
Arryn77, or by an attributed creator. System design and code architecture by Arryn77. Source code by Arryn77 assisted by Claude Code.

Sounds, art, and the tools and frameworks this project stands on are credited in
**[CREDITS.md](CREDITS.md)**.

*Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.*
