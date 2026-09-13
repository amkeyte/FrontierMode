# Plan: Correct PersistStitch's javadoc to describe its actual (dead) state

**Purpose of this file:** this is a test task for a local LLM agent (Aider +
`ollama_chat/qwen2.5-coder:7b`, `edit_format: whole`), not a real production ticket. Scope
is deliberately tiny and mechanical: one file, one javadoc block, no logic changes. All
ground-truth facts below were confirmed directly against the repo just before this plan was
written (2026-09-13) -- the agent should not need to re-derive them, only apply them.

## Target file

`Satchel/src/main/java/com/arryn/satchel/common/stitch/PersistStitch.java`

This is the ONLY file to change. Nothing else in the repo should be touched.

## Confirmed facts (ground truth -- do not re-investigate, just use these)

- `PersistStitch` is an interface with one method, `loadBundleTag(BundleKey<?> key)`, plus a
  static factory `from(SatchelScope scope)`.
- It IS syntactically implemented by three scopes: `LevelScope`, `MobScope`, `PlayerScope`
  (all in `common/jig/{level,mob,player}/`). But every single implementation is a permanent
  stub: `return Optional.empty();` -- none of the three ever loads or returns real data.
- Nothing anywhere in either repo (Satchel or FrontierMode) ever calls
  `PersistStitch.from(...)` or `.loadBundleTag(...)`. Confirmed by a repo-wide grep for both
  call shapes -- zero matches outside the interface's own declaration.
- No wiki page in `backhaul/wiki/` mentions `PersistStitch` at all (confirmed by grep) --
  nothing needs to be pulled into context from the wiki for this task.
- Per ticket SAT_053 (Satchel, "Redesign engine-level lifecycle promotion..."), this
  interface was an earlier, superseded attempt at declaring per-scope persistence
  capability. It's been replaced by `SatchelBundle.required()` (a bundle-level declaration,
  checked as a bitflag against `JigPolicies.Capabilities`) -- that's the real, live
  mechanism today. `PersistStitch` was never wired into that replacement and never removed.

## What's wrong with the current javadoc

```java
/**
 * Capability stitch indicating that a scope supports persistence.
 *
 * <p>
 * This stitch is ingress-only. It provides access to persisted
 * bundle state for hydration. Egress (saving) is handled elsewhere.
 * </p>
 */
```

This describes the interface as if it's a live, functioning capability mechanism ("provides
access to persisted bundle state for hydration"). That's not true today -- every
implementation is a no-op stub and nothing calls it. A reader trusting this javadoc would
wrongly assume this is how persistence capability works in this codebase.

## Required change

Rewrite ONLY the class-level javadoc block above `public interface PersistStitch` so it
accurately states, in the agent's own words but covering all of these points:

1. What the interface was originally meant to do (declare that a scope supports
   persistence, giving access to persisted bundle state for hydration).
2. That it is currently dead: implemented by `LevelScope`, `MobScope`, and `PlayerScope`,
   but every implementation is a hardcoded `Optional.empty()` stub, and nothing in the
   codebase ever calls `from(...)` or `loadBundleTag(...)`.
3. That it has been superseded by `SatchelBundle.required()` / `JigPolicies.Capabilities`,
   which is the real, currently-active mechanism for declaring persistence capability.
4. That this is flagged for likely future removal (see SAT_053) but hasn't been deleted
   yet, so it should not be treated as live documentation of current behavior.

Do NOT change: the package declaration, imports, the interface signature, the method
signature, the static factory method's body, or any formatting/whitespace outside the
javadoc comment block itself. Do NOT touch `LevelScope.java`, `MobScope.java`, or
`PlayerScope.java` -- they are provided only as read-only context to confirm fact #2 above,
not as edit targets.

## Acceptance criteria

- Only `PersistStitch.java` is modified.
- The only change is to the top class-level javadoc comment (lines ~11-18 in the original).
- The new javadoc does not claim the interface is live or currently used.
- The new javadoc names the real replacement mechanism (`SatchelBundle.required()` /
  `JigPolicies.Capabilities`).
- No code, imports, method bodies, or other files change at all.
- File still compiles as valid Java (balanced braces/comment delimiters; this can't be
  verified by an actual build in this environment, so a careful manual diff review is the
  real check).
