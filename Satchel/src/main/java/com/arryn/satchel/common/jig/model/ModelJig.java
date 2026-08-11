package com.arryn.satchel.common.jig.model;

import com.arryn.satchel.common.jig.guts.ASatchelJig;
import com.arryn.satchel.common.jig.guts.ScopeCoupler;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class ModelJig
        extends ASatchelJig<ModelScope> {

    @Override
    public UUID determineUUID(Object source) {
        if (!(source instanceof ModelSource ms)) {
            throw new IllegalArgumentException(
                    "ModelJig requires ModelSource, got: "
                            + (source == null ? "<null>" : source.getClass().getName())
            );
        }
        return getDeterminantUUID(ms);
    }

    @Override
    public ModelScope resolveScope(Object source) {
        if (!(source instanceof ModelSource ms)) {
            return null; // non-matching source types are ignored
        }

        return new ModelScope(ms);
    }

    @Override
    public Class<? extends ScopeCoupler> couplerClass() {
        return ModelCoupler.class;
    }

    @Override
    public Class<ModelScope> scopeType() {
        return ModelScope.class;
    }

    public static UUID getDeterminantUUID(ModelSource source) {
        Objects.requireNonNull(source, "source");

        String seed = source.name() + ":" + source.index();
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
