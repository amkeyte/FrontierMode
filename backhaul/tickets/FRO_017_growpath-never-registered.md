---
id: FRO_017
uid: FRO
number: 17
client: FrontierMode
status: done
title: Gold block path growth never wired to Forge event bus
context: BorderModule.onBlockPlaced was never registered on MinecraftForge.EVENT_BUS
  -- a plain static method, not an @SubscribeEvent instance method on a registered
  listener. Gold-block-triggered path growth was fully unreachable, not just untested;
  this is why placing a gold block near spawn had no effect.
priority: high
opened: '2026-08-14'
closed: '2026-08-14'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Not a crash this time — a silent no-op, reported by the user after placing a gold block near
spawn in a fresh world (the scenario [FRO_015](FRO_015_empty-path-tip-crash.md)'s
`growPathCriteria` fix needed a real test of) produced no visible effect at all.

**Root cause:** `BorderModule.onBlockPlaced(BlockEvent.EntityPlaceEvent)` — the method that calls
`BordersTriggers.growPath(event)` — is a plain `public static` method, **not** annotated
`@SubscribeEvent`. `FrontierMode`'s constructor does `MinecraftForge.EVENT_BUS.register(this)`,
which makes Forge scan the `FrontierMode` *instance* for `@SubscribeEvent`-annotated methods — it
found exactly one (`onRegisterCommands`). Nothing anywhere registers `BorderModule` itself, or a
method reference to `BorderModule::onBlockPlaced`, with any event bus. Grepped the whole
`FrontierMode` source tree for `addListener`/`register(` — confirmed zero other registration paths
for `BlockEvent.EntityPlaceEvent`. So `BordersTriggers.growPath` → `growPathCriteria` →
`BorderAPI.grow()` was never reachable from a real gold block placement, full stop — not a subtler
bug in the criteria logic itself, which is why [FRO_015](FRO_015_empty-path-tip-crash.md)'s
`growPathCriteria` fix could never have been exercised by actual gameplay even after that fix
landed.

**Fix applied:** added `MinecraftForge.EVENT_BUS.addListener(BorderModule::onBlockPlaced);` to the
end of `BorderModule.init()`, matching the method-reference listener pattern already used
elsewhere in this codebase (`modBus.addListener(this::commonSetup)` in `FrontierMode`'s
constructor).

**Related finding, also fixed:** the admin `/border grow` command sends a chat message on success
(`BorderCommandHandler.pathGrow`: `"[Border] Advanced border progression"`), but the natural
gold-block-triggered growth path had no player-facing feedback at all — silent by omission, not by
design. Asked the user directly rather than guessing at the intended UX; answer was to match the
command's feedback. Added the identical message (`sendSystemMessage` to the placing
`ServerPlayer`) to `BordersTriggers.growPath` right after a successful `BorderAPI.grow(level)`.

**Left `in-progress`, not `done`:** need a real re-run to confirm — placing a gold block near
spawn in a fresh world should now actually grow the path AND print "[Border] Advanced border
progression" to the placing player.

## Log

- 2026-08-14: Confirmed — gold-block growth and the "[Border] Advanced border progression" chat
  message have both fired repeatedly across real play sessions since (`SAT_029`/`SAT_030`'s logs
  show it happening twice; final confirmation is the user's "rings and borders is working as
  intended"). Closing.
- 2026-08-14: Added matching chat feedback to `BordersTriggers.growPath` per user's steer (match
  the `/border grow` command's message rather than stay silent). Still `in-progress` pending a
  real re-run.
- 2026-08-14: Root cause traced (see above), fix applied to `BorderModule.java`. Left
  `in-progress` pending a real re-run to confirm. Flagged the missing-chat-feedback question
  separately rather than guessing at the intended UX.
- 2026-08-14: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
