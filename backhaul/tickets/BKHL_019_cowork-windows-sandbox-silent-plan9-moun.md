---
id: BKHL_019
uid: BKHL
number: 19
client: Backhaul
status: open
title: 'Cowork Windows sandbox: silent Plan9 mount failure'
context: KB5124008 breaks Hyper-V Plan9 share-attach; VM boots clean but shell has
  no mounted shares. See ticket body.
priority: normal
opened: '2026-09-09'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

**Symptom:** `device_bash` (any shell tool routed through the Windows Cowork sandbox VM) fails
on every command, including trivial ones, with:

```
sandbox-helper: no Plan9 drive shares mounted under /mnt/.virtiofs-root/shared
```

File-based tools (list/stage/commit) keep working the whole time; only the shell is affected.

The VM itself boots cleanly — `cowork_vm_node.log` shows `[VM:steps] add_plan9_shares completed`
with no error, network `CONNECTED`, SDK installed, API `REACHABLE` — but the guest never
actually receives the mounted share. The host-side call reports success while doing nothing,
confirmed directly from the log rather than inferred.

**Cause:** a Windows cumulative update breaking Hyper-V's Plan9 share-attach path. Confirmed
instance: KB5124008 (Windows 11 24H2/25H2, x64). A matching case was community-reported against
KB5124012 on ARM64. App-level remedies — Claude Desktop relaunch, a full OS reboot alone,
re-enabling Windows Hypervisor Platform, resetting `vm_bundles` — do **not** fix it on their own,
since the VM boot already succeeds end to end; the break is in the OS's Plan9-attach servicing,
not app or VM state.

**Fix:** uninstall the offending KB (`wusa /uninstall /kb:5124008`, or Settings → Windows Update
→ Update History; fall back to DISM `Remove-Package` if `wusa` can't find it) and reboot. Pause
Windows Update afterward or it silently reinstalls the same KB on the next cycle.

**Diagnosis path for next time:** the Electron app's `main.log` only shows that a command was
dispatched, not why it failed. The actual failure detail lives in `cowork_vm_node.log` (same
`logs` folder), specifically the `[VM:steps] add_plan9_shares` and `[vm-stderr oneshot-]` lines
around a failed command.

Worth a wiki runbook entry once confirmed stable across a few sessions — written as a
symptom/cause/fix reference, not an incident narrative.

## Log

- 2026-09-09: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/backhaul)
<!-- bh-header:end -->
