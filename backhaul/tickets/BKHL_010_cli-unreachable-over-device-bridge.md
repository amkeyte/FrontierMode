---
id: BKHL_010
uid: BKHL
number: 10
client: Backhaul
status: done
title: bht/bhw/bhrm unreachable from a device-bridge session — this cycle ran on raw
  file edits with zero CLI validation
context: CLI installed in the cloud sandbox this session, not on the device where
  mcRepos actually lives. Every read/write for the whole Lead Dev cycle went through
  raw file access instead, bypassing bht/bhw/bhrm entirely.
priority: normal
opened: '2026-08-24'
closed: '2026-08-24'
---

<!-- board:start -->
<!-- board:end -->

## Summary

This cycle's Lead Dev session (the one that produced [FRO_043](FRO_043_boss-build.md) and
[BKHL_009](BKHL_009_done-bar-drift.md)) ran entirely over a device bridge — the agent's own
filesystem is a cloud sandbox; `C:\_local\mcRepos` lives on the project owner's machine, reached
through a separate bridge whose tools are `device_bash` (a shell on the device, no path-depth
limit) and `device_stage_files` (copies files sandbox-ward, hard-capped at 7 folders below the
connected root).

[Lead Dev](../roles/lead-dev.md)'s own session-bootstrap prompt says nothing about installing or
reaching `bht`/`bhw`/`bhrm` at all — it assumes the session already has working access. Whoever
launched this session layered a `pip install "git+...Backhaul...#subdirectory=src/Backhaul"
--break-system-packages` step on top, run in the cloud sandbox. That got the CLI installed
somewhere, but not somewhere that can reach `C:\_local\mcRepos` — the sandbox and the device are
separate filesystems; the bridge tools don't expose the device's filesystem to a process running
in the sandbox, only to specific tool calls the agent makes directly.

**Net effect: `bht`, `bhw`, and `bhrm` were never invoked once, for the entire cycle.** Every read
and every write — reading `BACKHAUL.md`, `boss.md`, the tickets, the roadmap node; opening and
closing [FRO_043](FRO_043_boss-build.md); appending roadmap log entries to
[RM_FRO_018](../roadmap/RM_FRO_018_shirley.md); rewriting `boss.md`/`border.md`; sixteen Java source
files — went through `device_bash` directly (`cat`, heredocs), because attempting the obvious
alternative failed first.

## Why the obvious workaround doesn't work for this project

The first approach tried was `device_stage_files` — stage the target file up into the sandbox,
edit or run the CLI against it there, `device_commit_files` it back down. That fails outright for
this project: `FrontierMode`/`Satchel`'s Java package layout
(`FrontierMode/src/main/java/com/arryn/frontiermode/border/common/fixture/BordersFixture.java` —
eight folders below the connected root) routinely exceeds `device_stage_files`' 7-folder cap. Every
attempt to stage a source file this deep failed with "too deeply nested to stage" before any CLI
command could even run against it. This isn't a one-off — it's structural to how deep a Forge mod's
own package convention goes, so the stage-up-run-commit-down pattern isn't a viable fallback for
this repo specifically, only for shallower ones.

## What this cost, concretely

- **Zero write-time validation.** [BKHL_006](BKHL_006_closed-status.md) already documents six
  tickets carrying a `status` value outside BHT's vocabulary, unrejected because nothing validates
  on write. This session hand-wrote `status: done` on FRO_043's frontmatter by pattern-matching two
  other closed tickets, not because anything checked it was the right value — same exposure
  BKHL_006 names, just via an agent's frontmatter edit instead of a person's.
- **Zero index/dashboard refresh from this session's own work.** `BACKHAUL.md`, `BOARD.md`,
  `WIKI_INDEX.md`, and `ROADMAP_INDEX.md` were not touched by anything this session did. They read
  correctly *now*, but only because something else — on the project owner's own machine, after this
  session's last edit — ran a real refresh; confirmed by file mtimes, all three index files stamped
  roughly an hour after this session's last write. Had that not happened, this is exactly
  [BKHL_007](BKHL_007_lint-routine.md)'s documented failure mode: the dashboard silently reporting
  stale counts while the content underneath is current.
- **Ticket numbers allocated by hand.** Both [FRO_043](FRO_043_boss-build.md) and
  [BKHL_009](BKHL_009_done-bar-drift.md) got their numbers from `ls backhaul/tickets/ | grep -oE
  ...` against the highest existing number per prefix, not from `bht open`. Harmless solo, but a
  real collision risk the moment two sessions (agent and human, or two agents) pick a number
  concurrently without the CLI's own allocation in between.

None of this produced an actual defect this cycle — the session cross-checked conventions by
reading precedent examples carefully, which is exactly what caught the
[BKHL_009](BKHL_009_done-bar-drift.md) drift in the first place. But "the agent read enough examples
to imitate the schema correctly" isn't a substitute for the CLI actually enforcing it, and won't
generalize to every future session doing the same thing under time pressure.

## Suggested direction, not a committed design

1. **Install the CLI on the device, not the sandbox, for device-bridge sessions.** `device_bash`
   itself has no path-depth restriction — only `device_stage_files` does — so `bht`/`bhw`/`bhrm`
   invoked *through* `device_bash` against the live `C:\_local\mcRepos` tree would sidestep this
   entirely, install location permitting (needs a working Python/pip on the device — unconfirmed
   whether that's already true).
2. **Say so in the bootstrap prompt.** [Lead Dev](../roles/lead-dev.md)'s own session-bootstrap
   block currently has no CLI-install step at all, for either session type — whoever launched this
   session added one from outside the documented role, aimed at the wrong filesystem for this
   session's actual setup. Worth the role prompt (or `BHRole` conventions generally) saying
   explicitly which install target applies for a device-bridge session versus a sandbox-only one,
   rather than leaving it to whoever pastes the prompt in to guess.
3. **If CLI-on-device isn't practical, this is another vote for [BKHL_007](BKHL_007_lint-routine.md)'s
   single-refresh-entry-point idea** — a device-bridge session that can't run the CLI at all still
   needs someone to run `backhaul refresh` afterward before the next session trusts the dashboard.
   That's the same fix BKHL_007 already proposes for a different root cause (people forgetting the
   five-command sequence); it would cover this failure mode too without needing a second mechanism.

## Log

- 2026-08-24: **Closed.** Bootstrap prompt fixed directly — `backhaul/roles/lead-dev.md` now has an
  explicit step 0 distinguishing sandbox-only from device-bridge sessions, with install/run guidance
  for each and an honest fallback (do the work directly, flag it, refresh afterward) when neither
  works. Suggestion 1 (confirm a working Python/pip on the device itself) is an operational fact
  about a specific machine, not something a doc or code change resolves — left as a live caveat for
  whoever runs the next device-bridge session to confirm directly. Suggestion 3 (single refresh
  entry point as fallback) is the same fix BKHL_007 already tracks upstream as BH_014 — not
  duplicated here.

- 2026-08-24: Ticket opened, off the same Lead Dev session that produced
  [BKHL_009](BKHL_009_done-bar-drift.md) — noted here as a separate finding because the root cause
  (device-bridge access) and the fix (CLI placement / bootstrap wording) are unrelated to
  [BKHL_009](BKHL_009_done-bar-drift.md)'s (done-bar text vs. architecture-page drift), even though
  both surfaced in the same cycle.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/backhaul)
<!-- bh-header:end -->
