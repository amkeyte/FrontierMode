# Credits

Everything in FrontierMode and Satchel that someone else made, and the things this project is built on.
The [README](README.md) links here.

FrontierMode's and Satchel's own code and original art are under the [MIT License](LICENSE). The
third-party files below, including art adapted from Minecraft's own textures, are **not** covered by
that license; each keeps its own terms, as listed.

> Sound files from Freesound carry per-file licenses (CC0, CC BY, CC BY-NC, ...). A non-commercial
> (NC) license is not compatible with this project's MIT release, so an NC file must not ship.

## Sounds

Sources are on [Freesound](https://freesound.org). Files were converted to OGG Vorbis and edited for
this project; the originals are not distributed in this repository.

| Shipped file | Original | Author | Source | License | Changes |
|---|---|---|---|---|---|
| `frontiermode/sounds/entry_cue.ogg` | "Couch Drop" | MegaBlasterRecordings | [Freesound #368641](https://freesound.org/s/368641/) | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) | Re-timed and tuned in Audacity (about 2.9 s); converted to OGG. |
| `frontiermode/sounds/boss_tell_1.ogg` to `boss_tell_5.ogg` | "Little dark house soundscape" | szegvari | [Freesound #557191](https://freesound.org/s/557191/) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | Cut into five variants and processed in Audacity; converted to OGG. |
| `frontiermode/sounds/cairn_ping1.ogg` to `cairn_ping4.ogg` | "chimes_bells.wav" | Alex_Vatr | [Freesound #578832](https://freesound.org/s/578832/) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | Cut into four variants and edited in Audacity; converted to mono OGG. |

## Textures and art

Created for this project by Arryn:

- Entity overlay textures: the boss eye layers (`textures/entity/boss_eyes/*.png`)
- Particle texture: the frontier ring (`textures/particle/frontier_ring.png`)
- Item textures: `textures/item/diorite_shard.png` and `textures/item/edge_stone.png`

Adapted by Arryn from Minecraft's own assets:

- Item textures: the Amethyst Compass animation frames (`textures/item/amethyst_compass_*.png`),
  repurposed from the vanilla compass texture by Mojang Studios. Mojang's artwork is not covered by this
  project's MIT license; it remains subject to the [Minecraft EULA](https://www.minecraft.net/eula) and
  Mojang's usage guidelines.
- Item textures: `textures/item/diorite_dust.png` (recolored from the vanilla redstone dust texture) and
  `textures/item/diorite_striker.png` (recolored from the vanilla flint and steel texture), by Mojang
  Studios. Same terms as the compass frames above.

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
