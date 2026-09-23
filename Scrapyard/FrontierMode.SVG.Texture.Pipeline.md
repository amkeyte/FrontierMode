# FrontierMode SVG Texture Pipeline

Sep 22, 2026 · @Someone

## Executive Summary

FrontierMode will source new textures as SVG files authored in Inkscape rather than as hand-exported PNGs from a raster editor. A Python script rasterizes finished SVGs to PNG ahead of time -- run manually when art changes, not wired into the Gradle build and not run at game runtime. This keeps source-file diffs readable in git, keeps Inkscape's own renderer as the source of truth for what ships, and avoids the cost and risk of generating textures inside the running game.

## Background

The original idea was a Java utility that builds a PNG in memory (`BufferedImage`/`Graphics2D`) and, for Forge, registers it directly with Minecraft's `TextureManager` as a `DynamicTexture` built from a `NativeImage` -- bypassing disk entirely. Extending that with SVG input, via a rasterizing library such as JSVG, would let one vector source drive textures at multiple resolutions at runtime.

On review, true runtime generation isn't the right default for ordinary block and item textures:

- It bypasses Minecraft's texture atlas, so those textures don't batch with everything else and need manual mipmap handling to avoid shimmering at a distance.
- Textures registered as synthetic `DynamicTexture` locations at runtime can't be overridden by user resource packs the way normal `assets/.../textures/...` PNGs can.
- Rasterizing at runtime (or on every world load) adds latency the vanilla PNG-decode path already avoids.
- Regenerating textures repeatedly means manually releasing old `DynamicTexture` instances or leaking VRAM.

Runtime generation still earns its keep for content that's genuinely unknowable until the game is running -- a boss's aura shifting color with its live HP percentage, or a border/claim overlay whose shape depends on a player-drawn boundary. Those are narrow, deliberate uses, not the default for shipping ordinary textures.

## Chosen Approach

Textures are authored as SVG in Inkscape -- easier to work in than a raster editor for this kind of art, and small, diffable text files in git rather than opaque binary blobs. Finished SVGs are rasterized to PNG ahead of time using Inkscape's own CLI exporter, so what ships is exactly what was seen while authoring. Generation happens once, by a hand-run script, not on every build and not while the game is running.

## Pipeline Architecture

Source SVGs live in their own tree, `art-src/`, mirroring the target texture path (`art-src/item/glowing_orb.svg` -> `assets/frontiermode/textures/item/glowing_orb.png`) and kept outside `src/main/resources` so Gradle and the mod jar never see the SVG sources at all.

**Static textures.** One SVG, one Inkscape CLI export, one PNG written into `src/main/resources/assets/frontiermode/textures/...`.

**Animated textures.** A single master SVG is built at the full strip's dimensions (for example, 16 px wide by 16 px times frame count tall). It contains one copy of the shape per frame, each placed at its frame's vertical slot and carrying a stable element id so a script can target it, with the value that changes per frame (a circle's `cy` for a wave, or a gradient stop's opacity for a glow) already set for that frame. One Inkscape CLI export of the whole canvas produces the finished strip PNG in a single pass -- no per-frame export, no separate stitching step. A companion `.mcmeta` file (frame count and frame time) is generated alongside it, since Inkscape has no concept of Minecraft's animation format.

## Tooling Choices and Rationale

**Rasterizer: Inkscape CLI.** Chosen over resvg, librsvg, and Apache Batik because it's the same renderer used while authoring -- what's seen in the editor is guaranteed to be what's exported, with no risk of a second renderer interpreting a filter or gradient differently. The tradeoff is that any machine running the export needs Inkscape installed; acceptable here because the export is a manual, occasional script, not something CI or a fresh build machine ever needs to run.

**SVG manipulation: lxml, or stdlib `xml.etree.ElementTree` for a zero-dependency option.** The animated pipeline needs to open an existing hand-authored SVG, find a specific element by id, clone it, and set an attribute per frame -- both libraries handle SVG's XML namespaces correctly and are suited to editing existing content, which is what this workflow needs.

**Ruled out:**

- Apache Batik as a runtime dependency -- too heavy to ship inside the mod; not applicable here since generation isn't wired into the build at all.
- svgwrite -- builds new SVGs from scratch; this pipeline edits SVGs the artist already authored, so it's the wrong shape of tool.
- svgelements -- reserved for later, only if a shape's motion needs to compose correctly through nested `<g transform="...">` groups; not needed for a simple, unnested shape.
- A Gradle-integrated build task -- considered and dropped in favor of the plain script, avoiding incremental-build input/output wiring and continuous file-watching for a benefit (auto re-export on save) not worth the added complexity here.

## Technical Gotchas

**Alpha edge halos.** Soft edges that fade to fully transparent (a glow or aura) can come out of a rasterizer with a faint dark ring at the edge, from a mismatch between straight and premultiplied alpha. Worth a test render and an in-game check before producing a full set of glow textures.

**Antialiasing vs. the blocky look.** Ordinary block and item art should keep Minecraft's crisp, flat-color style; Inkscape's default smoothing can soften edges that should stay sharp. Antialiasing should stay off for this kind of texture.

**Atlas bleeding at a distance.** Minecraft's texture atlas generates mipmaps for distant viewing; art that runs edge-to-edge with no margin can pick up a faint color fringe from the texture next to it on the atlas sheet. A small margin in the SVG artboard avoids it.

**Frame alignment on animated strips.** Every frame on a master SVG's canvas must land exactly on a pixel-size boundary (a multiple of 16 px, for a 16x16 texture) or the finished strip shows visible seams between frames in-game.

**SVG file bloat.** The point of moving to SVG was small, diffable files. Tracing a bitmap or pasting a reference image into an SVG in Inkscape can silently embed that raster data inside the file, pushing it back up toward PNG-sized -- worth an occasional glance at file sizes.

## Preview and Iteration Workflow

**Checking motion and timing** doesn't need Minecraft running at all. Stitching the frame PNGs into an animated GIF with one ImageMagick command (`convert -delay N -loop 0 frame_*.png preview.gif`, delay matched to the mcmeta frametime) gives instant feedback in any image viewer or browser. A small local HTML/JS previewer that reads the real strip PNG and `.mcmeta` file directly is a further option if frame-exact timing matters.

**Checking the real in-game result** doesn't require a full relaunch. Forge's resource-pack reload, bound to F3+T by default, re-reads everything under `assets/` and re-stitches the atlas in a running client. Since this pipeline isn't hooked into Gradle, the loop is: edit the SVG, run the export script, alt-tab into the running client, press F3+T.

## Day-to-Day Workflow

1. Author or edit the SVG in Inkscape, under `art-src/`, giving stable ids to any elements the animated pipeline will need to target.
2. Run the Python export script.
3. The script walks `art-src/`, tells static sources from animated ones apart by naming convention, skips anything whose output is already newer than its source, and for each remaining file either exports directly (static) or builds a master SVG, exports it, and writes the `.mcmeta` (animated).
4. Finished PNGs and `.mcmeta` files land in `src/main/resources/assets/frontiermode/textures/...`, mirroring their source path.
5. Commit the changed SVGs and their generated PNGs together, exactly like any other asset change. No build machine or CI ever needs Inkscape or Python installed.

## Open Items and Next Steps

- Build the Python export script: walk `art-src/`, dispatch static vs. animated by naming convention, generate animated frames with lxml (stable element ids plus per-frame attribute overrides), invoke Inkscape's CLI, write the `.mcmeta`, and skip up-to-date files by comparing modification times.
- Settle on the animated-source naming convention: an .anim.svg suffix, or a separate subfolder for animated sources.
- Optionally build the frame-accurate HTML previewer for animation timing.
- Test a real glow or aura export end-to-end to confirm there's no alpha-halo artifact before building out a full set of these textures.
