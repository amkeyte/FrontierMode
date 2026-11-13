package com.arryn.frontiermode.border.common.fixture;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable per-border pregeneration job record -- {@link BorderPregenFixture}'s sole persisted
 * state, per wiki/frontiermode/architecture/border-pregeneration.md's "Persisted state stays
 * small" section. Chunk generation itself is idempotent (Minecraft already knows what's
 * generated), so this only needs to remember where a throttled pass left off, not re-derive
 * anything about the terrain itself.
 *
 * <p>No record for a given {@code borderId} at all means "pregeneration was never triggered for
 * this border" -- a third state, distinct from "triggered, still in progress"
 * ({@code complete=false}) and "complete" ({@code complete=true}). See that page's "Starting a
 * border's pregeneration is an explicit call" section for why a border can legitimately have no
 * record here at all.
 */
public final class BorderPregenRecord {

    private final UUID borderId;
    private final boolean complete;
    private final int cursor;

    public BorderPregenRecord(UUID borderId, boolean complete, int cursor) {
        this.borderId = Objects.requireNonNull(borderId, "borderId");
        this.complete = complete;
        this.cursor = cursor;
    }

    public UUID borderId() {
        return borderId;
    }

    public boolean complete() {
        return complete;
    }

    public int cursor() {
        return cursor;
    }

    /**
     * Returns a copy advanced to {@code newCursor}, flipping {@code complete} true once
     * {@code newCursor >= totalChunks}. Once {@code complete} is true, {@code cursor} stops
     * mattering and is never revisited, per this class's own doc.
     */
    public BorderPregenRecord advancedTo(int newCursor, int totalChunks) {
        return new BorderPregenRecord(borderId, newCursor >= totalChunks, newCursor);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BorderPregenRecord other)) return false;
        return borderId.equals(other.borderId);
    }

    @Override
    public int hashCode() {
        return borderId.hashCode();
    }
}
