package com.arryn.frontiermode.border.common.fixture;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable named intensity-curve descriptor -- {@link BorderCurveFixture}'s sole persisted
 * record type, per wiki/frontiermode/architecture/border-curve.md. References its border by
 * {@code borderId} rather than embedding in it (see that page's "Why a sibling fixture, not a
 * field on Border" section) -- resolve the border it applies to via
 * {@code BorderAPI.borderOf(Level, BorderCurve)}.
 *
 * <p>{@code purpose} is a plain string, opaque to this class and to {@link BorderCurveFixture}
 * itself -- consumers agree on keys by convention ({@code "placement"}, {@code "difficulty"},
 * {@code "tell"}, ...), the same way a {@code TargetRef}'s tag means nothing to Navigator.
 *
 * <p>{@code steepness} is this class's concrete answer to the spec page's own open question of
 * "what {@code params} holds beyond the shape tag" -- a single tunable double, meaningful only to
 * {@code LOG} (see {@code BorderCurveMath}'s own doc); {@code LINEAR}/{@code SQUARE} ignore it.
 * Not settled by the page itself -- a starting shape, extend (or replace with a richer
 * {@code params} type) if a future curve consumer needs more than one number.
 */
public final class BorderCurve {

    private final UUID id;
    private final UUID borderId;
    private final String purpose;
    private final Shape shape;
    private final double steepness;

    public BorderCurve(
            UUID id,
            UUID borderId,
            String purpose,
            Shape shape,
            double steepness
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.borderId = Objects.requireNonNull(borderId, "borderId");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.shape = Objects.requireNonNull(shape, "shape");
        this.steepness = steepness;
    }

    public UUID id() {
        return id;
    }

    public UUID borderId() {
        return borderId;
    }

    public String purpose() {
        return purpose;
    }

    public Shape shape() {
        return shape;
    }

    public double steepness() {
        return steepness;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BorderCurve other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    static CompoundTag save(BorderCurve c) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", c.id);
        tag.putUUID("borderId", c.borderId);
        tag.putString("purpose", c.purpose);
        tag.putString("shape", c.shape.name());
        tag.putDouble("steepness", c.steepness);
        return tag;
    }

    static BorderCurve load(CompoundTag tag) {
        return new BorderCurve(
                tag.getUUID("id"),
                tag.getUUID("borderId"),
                tag.getString("purpose"),
                Shape.valueOf(tag.getString("shape")),
                tag.getDouble("steepness")
        );
    }
}
