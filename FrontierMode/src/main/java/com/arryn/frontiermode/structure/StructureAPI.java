package com.arryn.frontiermode.structure;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.structure.common.CairnFixture;
import com.arryn.frontiermode.structure.common.StructureBundle;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.level.LevelScope;
import net.minecraft.world.level.Level;

import java.util.Optional;

/** The structure module's public surface, for the code in other packages that reads its records. */
public final class StructureAPI {

    private StructureAPI() {
    }

    /**
     * The level's Cairn records, or empty while Satchel or the level's structure scope isn't ready --
     * the same "standby, don't crash" answer as {@code BossAPI.bosses(Level)}.
     */
    public static Optional<CairnFixture> cairnFixture(Level level) {
        if (!Satchel.isReady()) {
            return Optional.empty();
        }
        LevelScope scope = new LevelScope(level);
        // Satchel.tryReadyScopeInfo() resolves the scope info and checks it is ready in one call.
        var infoOpt = Satchel.tryReadyScopeInfo(FrontierKeys.STRUCTURE_JIG, scope);
        if (infoOpt.isEmpty()) {
            return Optional.empty();
        }
        StructureBundle bundle = infoOpt.get().getOrCreate(FrontierKeys.STRUCTURE_BUNDLE);
        return Optional.of(bundle.getOrCreateFixture(FrontierKeys.CAIRN, CairnFixture::new));
    }
}
