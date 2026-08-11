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
