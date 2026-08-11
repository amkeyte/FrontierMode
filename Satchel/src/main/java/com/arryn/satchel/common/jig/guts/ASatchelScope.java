package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.common.jig.level.LevelScope;

import java.util.UUID;

public abstract class ASatchelScope implements SatchelScope {


    protected final UUID scopeId;


    /**
     * This is a placeholder you will need as a reminder.
     * you MUST provide a determinant id for this scopeInfo object.
     * Most likely you will need to delegate to a (private) static method
     * to satisfy the abstract constructor.
     * @param source the object used to create the id.
     * @return the resulting scopeId
     */
    @Deprecated
    protected abstract UUID determineUUID(Object source);

    public ASatchelScope(UUID scopeId) {
        this.scopeId = scopeId;
    }

    @Override
    public UUID uuid() {
        return scopeId;
    }

    @Override
    public abstract String debugName();

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ASatchelScope other)) return false;
        return scopeId.equals(other.scopeId);
    }

    @Override
    public final int hashCode() {
        return scopeId.hashCode();
    }

}
