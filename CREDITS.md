# Credits

Everything in FrontierMode and Satchel that someone else made, and the things this project is built on.
The [README](README.md) links here.

> **Licenses marked _unverified_ have not been checked against the source page yet.** Do not treat an
> _unverified_ row as cleared for release. Sound files from Freesound carry per-file licenses (CC0,
> CC BY, CC BY-NC, ...); a non-commercial license would not be compatible with an MIT release.

## Sounds

Sources are on [Freesound](https://freesound.org). Files were converted to OGG Vorbis and edited for
this project; the originals are not distributed in this repository.

| Shipped file | Original | Author | Source | License | Changes |
|---|---|---|---|---|---|
| `frontiermode/sounds/entry_cue.ogg` | "Couch drop" | megablasterrecordings | [Freesound #368641](https://freesound.org/s/368641/) | _unverified_ | Re-timed and tuned in Audacity (about 2.9 s); converted to OGG. |
| `frontiermode/sounds/boss_tell_1.ogg` to `boss_tell_5.ogg` | "Little dark house soundscape" | szegvari | [Freesound #557191](https://freesound.org/s/557191/) | _unverified_ | Cut into five variants and processed in Audacity; converted to OGG. |
| `frontiermode/sounds/cairn_ping1.ogg` to `cairn_ping4.ogg` | "Ping" | bristolstories | [Freesound #51702](https://freesound.org/s/51702/) | _unverified_ | Four pitch variants; converted to mono OGG. |

<!-- TODO(owner): confirm whether the boss tell files also mix in these two sources, which sit in the same
     working folder: Freesound #469623 (onderwish, "Cloverfield monster howling") and #485009
     (inspectorj, "Fox vocal cry distant 01"). If they do, add them to the boss_tell row. -->

## Textures and art

Created for this project by Arryn:

- Item textures: the Amethyst Compass animation frames (`textures/item/amethyst_compass_*.png`)
- Entity overlay textures: the boss eye layers (`textures/entity/boss_eyes/*.png`)
- Particle texture: the frontier ring (`textures/particle/frontier_ring.png`)

<!-- TODO(owner): confirm these are all original work, and whether any were derived from vanilla
     Minecraft textures (the vanilla compass, or a vanilla mob's eye layer as a template). Derived work
     of Mojang's assets needs a different line than "original". -->

## Built on

| Project | Used for | License |
|---|---|---|
| [Minecraft](https://www.minecraft.net) (Mojang Studios) | The game these mods extend | [Minecraft EULA](https://www.minecraft.net/eula) |
| [Minecraft Forge](https://minecraftforge.net) | Mod loader and API (47.4.10) | LGPL 2.1 |
| Mojang official mappings | Class and method names used when compiling | Mojang's mappings license (text at the head of the mappings file) |
| [Gradle](https://gradle.org) and ForgeGradle | Build | see each project |

## Made with

Audacity (sound editing), Inkscape and GIMP (art), ffmpeg (audio conversion). Not required by any
license; credited for thanks.

## Adding an entry

When you add a sound, texture, model, or a bundled library, add a row above **in the same change**:

1. Shipped file (path under `assets/`).
2. Original title, author, and a link to the source page.
3. The license, copied from the source page, and whether it needs attribution or forbids commercial use.
4. What you changed.

If you can't fill in the license, leave it _unverified_ rather than guessing.

