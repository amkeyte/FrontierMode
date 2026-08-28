package com.arryn.frontiermode.border.common.fixture;

import com.arryn.satchel.common.jig.level.LevelScope;
import net.minecraft.world.level.Level;

import java.util.UUID;


// per convention, always pull data direct off of setting class. not using
// INFO public helpers. this prevents problems with interactive dependency.
public final class BordersInfoFacet {
    private final BordersFixture setting;

    BordersInfoFacet(BordersFixture setting){
        this.setting = setting;
    }

    public LevelScope scope() {
        return (LevelScope) setting.scope();
    }

    public Level level(){
        return ((LevelScope) setting.scope()).level();
    }

    public UUID scopeID() {
        return setting.scope().uuid();
    }

    public long revision() {
        return setting.revision();
    }

    /**
     * RM_FRO_018: whether this level's path has ever had a border appended to it, ever. See
     * {@code BordersFixture}'s {@code KEY_SEEDED} field doc and Border's "Known gaps" wiki
     * section -- {@code BorderModule}'s own bootstrap hook reads this, not {@code PATH.isEmpty()}.
     */
    public boolean seeded() {
        return setting.seeded();
    }

    /**
     * FRO_047: exposes {@code SatchelFixture.isReady()} without leaking the fixture itself --
     * {@code RenderContext.standby()} needs this specifically (a fixture can be resolved via
     * {@code BorderAPI}'s facet resolvers -- scope known, world-identity token bound -- while
     * still mid-hydration, a narrower and later readiness state than what those resolvers already
     * gate on).
     */
    public boolean isReady() {
        return setting.isReady();
    }

    //package private accessors

    /**
     * package private. use only as a last resort for compatibility.
     * @return
     * the owning BordersFixture object
     */

    BordersFixture owner() {
        return  setting;
    }

}
