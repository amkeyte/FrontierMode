---
id: plans/strip-down
category: plans
slug: strip-down
title: Bare-Necessity Strip-Down Plan
summary: Plan to strip both mod repos to the minimum needed to remain valid Forge
  mods, and replace git history clean.
keywords: null
status: published
updated: '2026-08-11'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../BACKHAUL.md) · [Wiki Index](../../WIKI_INDEX.md) · plans
<!-- bh-header:end -->

# Bare-Necessity Strip-Down Plan (FrontierMode & Satchel)

Strip both mod repos down to what's actually required to remain a buildable Forge mod, and
remove all traces of the existing git packages. Both repos live in a backed-up folder, so
deletion is safe. A clean git init happens separately, after sanitization — not part of this
plan.

## Confirmed decisions

- **Git**: remove `.git/` entirely from both repos (no squash, no history preservation). Also
  drop `.gitattributes` / `.gitignore` as part of the same removal — they're meaningless without
  a repo and will be re-authored (or regenerated) when the clean git init happens later.
- **Gradle scope**: full strip, not just directories/files. This pass also prunes
  `build.gradle` content itself:
  - Remove the `maven-publish` plugin and both mods' `publishing { ... }` blocks.
  - Remove all API-documentation build tooling: FrontierMode's `apiDumpFrontier` task block,
    Satchel's `gradle/apiDump.gradle` module (and its `apply from` line).
  - Remove Satchel's other custom build-logic modules that go beyond the stock Forge MDK
    template: `gradle/distribution.gradle` and `gradle/versioning.gradle` (and their `apply
    from` lines). Note: `versioning.gradle` is currently what sets Satchel's version — removing
    it means `build.gradle` needs a direct `version = mod_version` line (matching how
    FrontierMode already does it) so the build doesn't break. Flagging for whoever executes this.
  - FrontierMode's dependency on Satchel (`compileOnly files(...)` block) stays — FrontierMode
    is functionally coupled to Satchel (mods.toml declares it mandatory, `AFTER`). What's
    flagged as debt is *how* it's wired: a hardcoded absolute path
    (`C:/_local/mcRepos/Satchel/build/libs/`) and a `flatDir` repo pointing at the same. Not
    resolving the mechanism in this ticket — just documenting it as a known fragility for
    Lead Dev to fix properly (e.g. a real project/module reference) once both repos are
    sanitized.
- **Extra top-level docs & root clutter**: strip all of it. "Keep forge-related items, anything
  extra goes" — `CREDITS.txt`, `LICENSE.txt`, `changelog.txt`, `WIKI.md`, `depmods/`
  (FrontierMode), `build-number.txt` (Satchel), plus the stray `forge-1.20.1-47.4.10-mdk.zip`
  and `.vs/` sitting at the mcRepos root (neither belongs to either mod). None of these are
  required for the mod to build or run.
- **DocletProject**: gone entirely — delete the whole project, not just its `.git/`. Its only
  purpose was feeding the API-dump tooling being removed above, so it has no remaining reason to
  exist here. Per your instruction, any other breakage this causes (e.g. dangling references in
  build scripts before they're cleaned up) is expected and not a blocker — sanitize first, let
  the dependents break, fix forward.

## What "bare necessity" keeps (per mod)

- `src/` (main, generated, test)
- `build.gradle`, `settings.gradle`, `gradle.properties`
- `gradle/wrapper/` (`gradle-wrapper.jar`, `gradle-wrapper.properties`), `gradlew`, `gradlew.bat`
- `README.txt` — still stock Forge MDK boilerplate; tracked separately by
  [FRO_001](../../tickets/FRO_001_readme-boilerplate.md) /
  [SAT_001](../../tickets/SAT_001_readme-boilerplate.md), not part of this strip.
- Satchel's `design/` diagrams (`satchelArchitectureOverview.*`, `satchelDataAvailabilty.*`,
  `Satchel external event flow.png`) — real architecture documentation, not build cruft. Keep.

## In-source `.md` files — migrate to wiki, don't just leave in place

Revised after review: these aren't scratch dev notes, they're real architecture documentation
(bundle/facet/fixture/net/persistence model) substantial enough to belong in the wiki, not
scattered across `src/`. Found and read all five:

- `common/bundle/notes.md`
- `common/fixture/notes.md`
- `common/fixture/SatchelFacet.md`
- `common/net/notes.md`
- `server/persistence/BundlePersistence.md`

Checked two of their concrete claims against the actual code before trusting them:

- `fixture/notes.md` ends with `<!-- include: SatchelFacet.md -->` — the two are meant to be
  read as one document; migration should merge them rather than keep them separate.
- `bundle/notes.md` has a leftover editorial artifact at lines 14–17 (an assistant-style
  response — "Perfect — here's a clean, collapsed rewrite that gives you both pieces without
  re-introducing manifesto bloat..." — clearly pasted in and never cleaned up). Needs stripping
  before this is wiki-clean.
- `BundlePersistence.md` claims `com.arryn.satchel.server.persistence` is where per-bundle
  persistence lives, centered on a `BundleSavedData` class, with the old
  `PlayerBundleSavedData`/`ServerWorldBundleSavedData` variants deprecated and gone. Checked
  against source: the deprecation claim holds (grepped `src/` — neither old class exists
  anymore). But the location claim doesn't: `BundleSavedData.java` actually lives in
  `common/newstuff/`, not `server/persistence/`. The only file actually inside
  `server/persistence/` — `ServerPersistenceContext.java` — is entirely commented out, dead
  code. So the doc's architecture description is broadly accurate but its stated package
  location is stale, and `common/newstuff` reads like a placeholder package name that never got
  renamed. This needs Architect eyes before it's trusted as current in the wiki, not something
  I should silently correct or silently republish as-is.

Proposed handling: migrate all five into the wiki under `satchel/architecture/*` (see doc
structure rule below), merging the two `fixture` files per the include directive, stripping the
leftover artifact from `bundle/notes.md`, and flagging the `BundlePersistence.md`
location/package drift for Architect review rather than treating it as gospel. Once migrated,
the `.md` files get removed from `src/` — they don't count as "bare necessity" once they have a
proper home.

## New rule: per-mod doc sections, cross-reference only in the dependency direction

Each mod's real documentation (not the short `mods/*` identity/summary pages already in the
wiki — those stay as the index-level pointer) gets its own category, kept separate:

- `satchel/architecture/*` — Satchel's docs (the five migrated `.md` files above, going
  forward: bundle, fixture, net, persistence, and whatever else accumulates).
- `frontiermode/architecture/*` — FrontierMode's docs (none exist yet — noted in
  [FrontierMode](../frontiermode/frontiermode.md) already; this is where they'll land once written).

Cross-references are only allowed in the direction of the actual code dependency:
FrontierMode depends on Satchel (`mods.toml`, mandatory, `AFTER`), so FrontierMode docs may link
into Satchel's architecture pages. The reverse is not allowed — Satchel docs must not reference
FrontierMode, since Satchel has no dependency on it and shouldn't read as if it does. This keeps
the doc graph honest about which mod actually knows about the other.

**Open question — where do overall/cross-cutting project items live?** Not everything is
mod-specific — e.g. this strip-down plan itself, or future ecosystem-wide decisions. You asked
for suggestions rather than a unilateral call, so here are three options, not a decision:

1. **Keep using FRO for cross-cutting items** (status quo) — matches the existing "general work
   goes under FRO for now" convention already in place. Cheapest, but slightly odd once
   FrontierMode is meant to be the ecosystem name rather than just one mod in it — general items
   end up filed under a project-specific-sounding UID.
2. **Mint a new ecosystem-level UID** (e.g. `ECO`, or the eventual ecosystem name once it's
   picked) alongside `FRO`/`SAT` in the same Backhaul instance — a real home for cross-cutting
   tickets/roadmap/wiki without borrowing FrontierMode's identity. Requires picking a UID now
   even before the ecosystem has a settled name.
3. **Stand up a genuinely separate Backhaul project** for ecosystem-level coordination, keeping
   `FRO`/`SAT` purely mod-scoped. Cleanest long-term separation, but more moving pieces
   (a second config, a second dashboard) for what's currently a two-mod workspace.

My lean would be #1 for now (lowest friction while things are still this small) with a note to
revisit #2 once the ecosystem has a real name — but this is genuinely your call.

## What gets removed (per mod)

- `.git/`, `.gitattributes`, `.gitignore` — FrontierMode's `.git/` is 420MB despite `.gitignore`
  excluding build/run artifacts, meaning something heavy got committed anyway at some point.
  Satchel's is 2.6MB.
- `.gradle/`, `.idea/`, `build/`, `run/` — all already `.gitignore`d, i.e. already understood to
  be regenerable, not source. Satchel also has a top-level `logs/` (currently empty). `.idea/`
  specifically is IntelliJ IDE metadata (module files, run configs, inspection profiles) — it
  plays no part in a Gradle/Forge build; `gradlew build` never touches it. ForgeGradle can
  regenerate working IDE run configs from `build.gradle` via `genIntellijRuns` if the project's
  reopened in IntelliJ later, so nothing is lost by removing it.
- `FrontierMode/src/main/java/com/arryn/frontiermode/api_dump.txt` — generated javadoc-doclet
  output that landed inside `src/` instead of `build/`. Treated as generated cruft, not source,
  and doubly so once the tooling that generates it is removed.
- Top-level docs and root clutter: `CREDITS.txt`, `LICENSE.txt`, `changelog.txt`, `WIKI.md`,
  `depmods/` (FrontierMode), `build-number.txt` (Satchel), `forge-1.20.1-47.4.10-mdk.zip` and
  `.vs/` (mcRepos root).
- API-documentation build tooling: FrontierMode's `apiDumpFrontier` task block in
  `build.gradle`; Satchel's `gradle/apiDump.gradle` (file + `apply from` line); the entire
  `DocletProject/` directory.
- `maven-publish` plugin + `publishing { ... }` block in both mods' `build.gradle`.
- Satchel's `gradle/distribution.gradle` and `gradle/versioning.gradle` (file + `apply from`
  lines) — custom build-logic beyond the stock Forge MDK template. Requires adding a direct
  `version = mod_version` line to Satchel's `build.gradle` in the same pass, or the build breaks
  once `versioning.gradle` is gone.

## Tickets (opened)

Per your instruction: general/shared work and anything FrontierMode-specific goes under FRO;
Satchel-specific work goes under SAT.

**FRO (FrontierMode + general):**
- [FRO_002](../../tickets/FRO_002_strip-bare.md) — Strip FrontierMode to bare necessity (`.git/`
  + git files, `.gradle/`, `.idea/`, `build/`, `run/`, top-level docs, in-source `api_dump.txt`,
  `apiDumpFrontier` task + `maven-publish`/`publishing` block).
- [FRO_003](../../tickets/FRO_003_root-cleanup.md) — Delete `DocletProject/` entirely, remove
  `forge-1.20.1-47.4.10-mdk.zip` / `.vs/` at the mcRepos root.
- [FRO_004](../../tickets/FRO_004_satchel-dep-debt.md) — Document the FrontierMode→Satchel
  hardcoded absolute-path dependency as known debt for Lead Dev (not fixed this pass).

**SAT (Satchel):**
- [SAT_002](../../tickets/SAT_002_strip-bare.md) — Strip Satchel to bare necessity (`.git/` +
  git files, `.gradle/`, `.idea/`, `build/`, `run/`, `logs/`, top-level docs,
  `maven-publish`/`publishing` block, `gradle/apiDump.gradle` + `gradle/distribution.gradle` +
  `gradle/versioning.gradle`, add direct `version = mod_version` line). Preserve `src/` and
  `design/`.
- [SAT_003](../../tickets/SAT_003_md-migration.md) — Migrate the five in-source architecture
  `.md` files into the wiki under `satchel/architecture/*` (merge the two `fixture` files, strip
  the leftover artifact, flag the `BundlePersistence.md` drift for Architect review), then
  remove them from `src/`.

## Roadmap: deliberately left alone for this pass

Explicit call: the strip-down tickets do **not** touch or close
[RM_FRO_001](../../roadmap/RM_FRO_001_scaffold.md) /
[RM_SAT_001](../../roadmap/RM_SAT_001_scaffold.md), and stripping work isn't tied to them. The
roadmap gets rebuilt deliberately afterward (see below), not backed into as a side effect of
cleanup tickets.

## Deferred — future work, not in scope now (don't lose these)

Flagged so they're on record, but explicitly not part of this stripping pass:

- **Mod release versioning.** Removing `versioning.gradle` kills the only version-bumping
  mechanism Satchel had (auto-incrementing build number appended to `mod_version`, see
  [versioning.gradle explanation above]). That mechanism itself was more dev-build churn than a
  real release scheme (58 dev jars piled up in `build/libs/`), so it's not being preserved
  as-is — but a real answer for how mod release versions get tracked and bumped going forward
  still needs to be designed. Needed eventually, not before/during the strip.
- **Roadmap backfill from current code.** After both repos are sanitized, go through the
  actual existing code and reconstruct a reasonable roadmap history — nodes representing what's
  already been built, written as if planned in hindsight — then continue real forward planning
  from that baseline instead of from a blank `_001 scaffold` node that no longer reflects
  reality. Deferred until after the strip is done and stable.

## Status: executed (2026-08-11)

All five tickets closed. Scoped as PM work (not Lead Dev) per a carve-out added to
[roles/pm.md](../../roles/pm.md), since this preceded the project proper. Cross-cutting/
ecosystem UID question resolved: stay under FRO for now, revisit once a real ecosystem-level
Backhaul makes sense.

Both repos verified clean afterward: no `.git` anywhere in mcRepos, `DocletProject`/
`forge-*.zip`/`.vs` gone from the root, both `build.gradle` files brace-balanced, no dangling
references to removed tooling (grepped `DocletProject|maven-publish|apiDump|bumpBuildNumber|
installToFrontierMode|build-number` across both repos — clean, after also catching and removing
a dangling "API dump tooling" block that had survived in both `gradle.properties` files).
FrontierMode: 361K (down from ~650MB+ including `.git`/`run`/`build`). Satchel: 1.8M (down from
~85MB+).

The five in-source `.md` files are now [Bundle](../satchel/architecture/bundle.md),
[Fixture](../satchel/architecture/fixture.md), [Networking](../satchel/architecture/net.md), and
[Persistence](../satchel/architecture/persistence.md) under `satchel/architecture/`. `Fixture`
and `Persistence` were migrated as `draft`, each carrying a flagged discrepancy against current
code (facet/fixture terminology drift; `BundleSavedData`'s real package location). **Update:**
both have since been reviewed and resolved by the Architect role and are now `verified` — see
`RM_SAT_004` and `RM_SAT_007` in the roadmap for the resolutions.

`mods/satchel.md` and `mods/frontiermode.md` updated to reflect the sanitization and link the
new architecture pages. `.git` removal was total (no squash, no history) — clean init is a
separate step, not done here, per your instruction. Roadmap (`RM_FRO_001`/`RM_SAT_001`)
deliberately untouched, as agreed.

## Repo, build & IDE strategy (current, 2026-08-11)

Three separate layers — decided independently, don't conflate them when reasoning about "the
repo strategy":

**Version control — unified.** One git repo at the mcRepos root, covering FrontierMode +
Satchel + `backhaul/` together, replacing the original per-mod-repo layout. Can split back into
per-mod repos later if that turns out to be useful — not committed to either way long-term.
[FRO_005](../../tickets/FRO_005_unified-gitignore.md) — wrote a merged `mcRepos/.gitignore`
covering both mods' build/IDE/run artifacts (patterns match at any depth, so one file covers
both subdirectories without duplication). The actual `git init` + identity + first commit was
intentionally **not** done here — you're creating the repo yourself.

**Build — deliberately kept separate.** FrontierMode and Satchel remain two fully independent
Gradle/ForgeGradle projects — no shared `settings.gradle`, no composite build, no
`includeBuild`/`project(':Satchel')`-style dependency. Considered unifying (it would also
resolve the hardcoded-path debt below cleanly) but you recalled ForgeGradle-specific reasons not
to: each ForgeGradle subproject tends to want to own its own userdev/patched-Minecraft
environment, so combining multiple Forge mods into one multi-project build is a known source of
friction, not a style preference. Logged on
[FRO_004](../../tickets/FRO_004_satchel-dep-debt.md) and in `mods/frontiermode.md`. The
hardcoded absolute-path dependency (FrontierMode → Satchel jar, flagged as debt in the
"Confirmed decisions" section above) stays open/unresolved *by design* — it will not be "fixed"
via build unification.

**IDE — IntelliJ IDEA, not Visual Studio.** Confirmed you're using IntelliJ for this project.
(Full Visual Studio has no real Java/Gradle tooling — a `.sln` here would be an empty shell with
no code intelligence or Forge run-config support — so that idea was dropped once clarified.) No
repo changes are needed for IntelliJ to work: it reads `build.gradle`/`settings.gradle` directly
and generates its own `.idea/` (which stays gitignored, see "What gets removed" above). To view
both mods in one IntelliJ window without merging the Gradle builds, use *File → New → Module
from Existing Sources* (or *Link Gradle Project*) to attach Satchel as a second, independent
Gradle project alongside FrontierMode — purely an IDE-side setting, zero build-file changes.

**Net effect:** one shared git history, two separate Gradle builds, optionally one IntelliJ
window. None of the three implies or requires either of the others.

## Update (2026-08-13): mods/* landing pages consolidated into each mod's own category

The "New rule" section above (kept for history) had `mods/*` staying separate as an
index-level-only pointer, with each mod's real docs living under their own category
(`frontiermode/architecture/*`, `satchel/architecture/*`). In practice this scattered a mod's
content across unrelated alphabetical positions in `WIKI_INDEX.md` (`design/frontiermode` sorted
nowhere near `frontiermode/architecture` or `mods`), so there was no way to see everything under
one mod from the index, or to drill down from a landing page.

Fixed by moving the landing pages themselves into their mod's category instead of keeping them
separate:

- `wiki/mods/frontiermode.md` → `wiki/frontiermode/frontiermode.md`
- `wiki/mods/satchel.md` → `wiki/satchel/satchel.md`
- `wiki/design/frontiermode/*` → `wiki/frontiermode/design/*` (matches the existing
  `frontiermode/architecture/*` pattern; Satchel has no wiki-hosted design docs yet — its design
  material is the diagrams under `Satchel/design/` in the mod repo itself, linked from
  `satchel/satchel.md`, not moved).

Now every `frontiermode/*` and `satchel/*` category sorts together in `WIKI_INDEX.md`, and each
landing page's Architecture/Design sections are real drill-down links, not just index entries.
The `mods` category is retired. Updated all role bootstrap prompts and ticket/roadmap hyperlinks
that pointed at the old `wiki/mods/*.md` paths.
