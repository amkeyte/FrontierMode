package com.arryn.satchel.common.jig.model;

import com.arryn.satchel.common.jig.guts.ASatchelScope;
import net.minecraft.world.level.Level;

import java.util.UUID;

public final class ModelScope extends ASatchelScope {

    private final ModelSource source;

    public ModelScope(ModelSource source) {
        super(ModelJig.getDeterminantUUID(source));
        this.source = source;
    }

    public ModelSource source() {
        return source;
    }

    @Deprecated
    @Override
    protected UUID determineUUID(Object source) {
        if (!(source instanceof ModelSource sourceModel)) {
            throw new IllegalStateException(
                    "ModelJig source must be a ModelSource, got: "
                            + source.getClass().getName()
            );
        }
        return ModelJig.getDeterminantUUID(sourceModel);
    }

    @Override
    public String debugName() {
        return "ModelScope[" + source.name() + ":" + source.index() + "]";
    }
}