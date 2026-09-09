---
id: SAT_052
uid: SAT
number: 52
client: Satchel
status: done
title: Shipped jar missing reobf -- NoSuchMethodError on vanilla methods (readUUID,
  Commands.literal) on every real install
context: 'Real player installs (Mac via official launcher, then reproduced on the
  Windows dev machine''s

  own official-launcher install) threw NoSuchMethodError on ordinary vanilla methods
  --

  FriendlyByteBuf.readUUID() on join (decoding Satchel''s own S2cBundleParcel/S2cWorldIdentityToken

  packets), then Commands.literal() during world/command-tree creation (MobTrackCommands.java:78,

  via RegisterCommandsEvent) -- while the exact same Satchel-0.0.3.jar ran perfectly
  in the

  ForgeGradle dev environment. Ruled out first: Satchel-side packet field order/count
  (both

  packet classes reviewed, correct), client/server jar version skew (byte-identical
  via unzip+diff),

  Satchel''s own compiled bytecode (constant-pool dump showed exactly correct official-name

  signatures for both readUUID and Commands.literal), Mixins/coremods/ASM transforms
  (none --

  grep confirmed), and a shaded/bundled copy of vanilla or brigadier classes inside
  Satchel''s jar

  (none). A clean, from-scratch Forge reinstall on the Mac didn''t fix it either.


  Root cause: Satchel/build.gradle had no `reobf { jar {} }` block. A comment already
  in the file

  even said "Jar (DEV ONLY -- NO REOBF HERE)", and `tasks.matching{it.name.startsWith("reobf")}`

  further down was configuring zero tasks (silently a no-op) because no `reobfJar`
  task existed to

  match. ForgeGradle''s dev run environment (runClient/runServer) deobfuscates the
  Minecraft/Forge

  classpath to official/MCP names for you, which is why mod code written and tested
  there against

  `Commands.literal`/`FriendlyByteBuf.readUUID` just works -- but the actual jar Forge
  distributes

  to real players keeps its own classes under SRG (searge) names internally (confirmed
  directly:

  dumped the loaded `Commands.class` from a real install''s `client-...-srg.jar` and
  its `literal`

  equivalent is defined as `m_82127_`, same descriptor, different name -- no method
  named `literal`

  exists in it at all). Without the reobf step, the shipped jar''s compiled bytecode
  still called

  vanilla methods by their official name, which the real runtime jar simply doesn''t
  have.


  Fix: added `reobf { jar {} }` to Satchel/build.gradle, wired via ForgeGradle''s
  RenameJarInPlace

  (already imported at the top of the file, previously configuring nothing). Left
  `copyToFrontierMode`

  pointed at the plain `jar` task''s output, NOT reobfJar''s -- FrontierMode is itself
  a ForgeGradle

  dev project (its own `runs{}` + dev mappings), so its run/mods and run-server/mods
  dev-testing

  directories need the *official*-named jar, same as Satchel''s own dev runs. Caught
  this the hard

  way: an earlier pass of mine wired copyToFrontierMode to reobfJar''s output instead,
  which fed the

  SRG-renamed jar into FrontierMode''s dev-testing folders and broke its dev server
  with the exact

  mirror-image error (NoSuchMethodError on Commands.m_82127_ -- SRG name not found
  against the dev

  environment''s official-named classpath). Reverted that part. Tech support then
  ran the corrected

  reobf step through the actual build chain (this session has no network/matching
  JDK to run gradle

  itself, so build.gradle was hand-edited and reasoned through here, not build-verified
  in-session).


  Confirmed fixed: both affected machines (Mac via official launcher, Windows dev
  machine via

  official launcher) now join and play normally. Windows crash reports from before
  the fix

  (crash-2026-09-08_22.18.14 and _22.19.04-client.txt, both NoSuchMethodError on Commands.literal

  from MobTrackCommands.java:78) corroborate the same root cause independently of
  the Mac.'
priority: high
opened: '2026-09-09'
closed: '2026-09-09'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Shipped jar missing reobf -- NoSuchMethodError on vanilla methods (readUUID, Commands.literal) on every real install

## Log

- 2026-09-09: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
