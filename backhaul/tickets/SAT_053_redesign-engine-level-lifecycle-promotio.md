---
id: SAT_053
uid: SAT
number: 53
client: Satchel
status: open
title: Redesign engine-level lifecycle promotion / hydration coupling (SAT_049 root
  cause) + fallout
context: "## Background\n\nSAT_049 (satchel run tick never actually fires for SatchelHealth's\
  \ tracker bundles) was\nroot-caused to: ScopeEngine_Server.hydrateBundle() only\
  \ calls bundle.hydrateFrom(...) --\nthe ONLY code path that ever transitions a SatchelBundle\
  \ out of CREATED (CREATED ->\nHYDRATED, then onLoaded() takes it HYDRATED -> LOADED\
  \ -> ACTIVE) -- when the jig's\nJigPolicies.Capabilities declares requiresPersistence(true).\
  \ Any jig that doesn't need\npersistence (SatchelHealth's MOB_JIG/LEVEL_JIG/PLAYER_JIG\
  \ among them) never gets\nhydrateFrom() called at all, so it sits at CREATED permanently.\
  \ ScopeEngine_Server.onJigTick()\nskips any bundle that isn't ACTIVE, so ticking\
  \ is silently dead for every such bundle,\nforever, with no exception and no warning\
  \ (the RM_SAT_013 stuck-bundle diagnostic is\nitself gated behind a different flag,\
  \ participatesInExecutionPulse, that also isn't set --\ntwo independent silent failures\
  \ stacked on each other). Full trace is in SAT_049's log\nentries.\n\nA targeted\
  \ patch (always hydrateFrom() an explicitly-empty source when persistence isn't\n\
  required, so lifecycle promotion no longer depends on persistence being needed)\
  \ was\ndrafted and verified against current code, but was explicitly stopped before\
  \ shipping.\n\n## The actual problem this ticket is for\n\nLifecycle promotion to\
  \ ACTIVE is currently coupled to persistence hydration -- a bundle\ncan only ever\
  \ leave CREATED via the same method (hydrateFrom) that's meant for loading\nreal\
  \ saved data. That coupling is itself the defect: a jig with no persistence\nrequirement\
  \ shouldn't need to go through a \"hydrate from nothing\" fiction just to become\n\
  tickable. This has already recurred once before under a different gap (SAT_027 --\
  \ see\nthe historical note in SatchelBundle.hydrateFrom()'s own javadoc), which\
  \ suggests the\ncoupling itself is the wrong shape, not that we keep missing edge\
  \ cases in an otherwise\ncorrect design.\n\nThere's also a live internal contradiction\
  \ in ScopeEngine_Server's own comments:\nresolveServerLevel()'s null-return is documented\
  \ as \"nothing to hydrate, not an error\"\n(implying skip is safe) while SatchelBundle.hydrateFrom()'s\
  \ javadoc says \"the bundle must\nleave CREATED to ever reach ACTIVE\" (implying\
  \ skip is fatal). Both are true as currently\nwritten -- that's the bug.\n\n## Scope\n\
  \nThis needs an actual architecture pass on ScopeEngine_Server (and whatever of\n\
  ASatchelJig / AScopeCoupler / JigPolicies needs to change alongside it), not a patch:\n\
  - What should actually gate a bundle's promotion to ACTIVE, decoupled from whether\
  \ it\n  requires persistence, networking, or clock? (Likely candidates: a dedicated\n\
  \  \"activate\"/\"ready\" step separate from hydration, or splitting \"hydration\"\
  \ itself into\n  a real optional capability vs. an unconditional lifecycle step.)\n\
  - Does participatesInTick vs. participatesInExecutionPulse (two independently-gated\n\
  \  flags) still make sense once promotion no longer depends on capabilities()? Right\
  \ now\n  a jig can tick without ever running an execution pulse, which is also what\
  \ silenced\n  the RM_SAT_013 stuck-bundle warning for this exact case.\n- Fallout:\
  \ ScopeEngine_Server is a single per-side singleton shared by every jig (see its\n\
  \  own SAT_023 comment), so whatever shape this lands on affects every jig on it\
  \ -- not\n  just SatchelHealth's. FrontierMode's BorderPregenFixture rides the same\
  \ engine and\n  needs to be checked against whatever the new contract is once designed.\n\
  - SAT_049 stays open/parked pointing at this ticket; its root cause is fully understood\n\
  \  and reproducible, only the fix is deferred here.\n\n## Not in scope here\n\n\
  Actually implementing the fix -- this ticket is the design/redesign work. Implementation\n\
  follows once the shape is agreed, on its own branch (satchel/SAT_053-lifecycle-redesign),\n\
  kept separate from the working tree other playtesting is currently using (which\
  \ still\ncarries the SAT_049 workarounds, e.g. ExteriorTellFixture's manual-call\
  \ bypass)."
priority: high
opened: '2026-09-09'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Redesign engine-level lifecycle promotion / hydration coupling (SAT_049 root cause) + fallout

## Log

- 2026-09-09: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
