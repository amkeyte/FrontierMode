package com.arryn.frontiermode.boss.common;

/**
 * A kind's creation arguments (boss.md § Creation is one operation). {@code BossFixture.create(kind, args)}
 * passes them to the kind's {@link BossKind#onCreate} untouched, so the base reads nothing of what a kind
 * needs and imports nothing from it. Each kind declares its own record implementing this, and its
 * {@code onCreate} reads that one type.
 */
public interface BossCreation {
}
