package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.BossKinds;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.trinket.DynamicTrinket;
import com.arryn.satchel.common.trinket.Trinket;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The record-abilities Trinket on {@code BOSS_JIG} (wiki/frontiermode/architecture/boss.md § Abilities). Satchel's
 * {@link DynamicTrinket} is the host that holds the subscribers, declared before this Trinket on the jig; this one
 * is handed each {@link Boss} by the {@link BossRoster} the moment it builds it, and asks the boss's kind to
 * subscribe its record-side abilities to the host through {@code BossKind.subscribeRecordAbilities}. When the roster
 * drops a {@code Boss}, it takes the same subscribers back out. It runs on both sides; the kind subscribes each
 * ability on the side it applies to.
 *
 * <p>It does not tick: the roster calls it, before any {@code advance}, so a body that materializes on the roster's
 * first tick already has its {@code Persistent}. It holds only what it subscribed for each {@code Boss}, which lives
 * and dies with the scope.
 */
public final class RecordAbilities implements Trinket {

    private DynamicTrinket host;

    // bossId -> the subscribers the kind's hook added for that boss, so the roster dropping it can remove them.
    private final Map<UUID, List<Trinket>> subscribed = new HashMap<>();

    @Override
    public void onAttach(ScopeInfo info) {
        LevelScope scope = info.scopeAs();
        host = Satchel.trinket(FrontierKeys.BOSS_JIG, scope, DynamicTrinket.class)
                .orElseThrow(() -> new IllegalStateException(
                        "[Boss] RecordAbilities.onAttach(): the record-abilities host is not attached for level "
                                + scope.level().dimension().location()));
    }

    @Override
    public void onDetach(ScopeInfo info) {
        // The host detaches its subscribers with the other Trinkets; nothing here outlives the scope.
        subscribed.clear();
        host = null;
    }

    /**
     * A {@code Boss} has just been built: the kind subscribes its record-side abilities. The subscribers the
     * hook added are the host's attached subscribers after the call that were not there before it.
     */
    void bossBuilt(Level level, Boss boss) {
        List<Trinket> before = host.subscribers(Trinket.class);
        BossKinds.get(boss.kind()).orElseThrow().subscribeRecordAbilities(level, boss, host);
        List<Trinket> added = host.subscribers(Trinket.class);
        added.removeAll(before);
        subscribed.put(boss.bossId(), added);
    }

    /** The roster is dropping the {@code Boss} for {@code bossId}: its subscribers come out of the host. */
    void bossDropped(UUID bossId) {
        List<Trinket> abilities = subscribed.remove(bossId);
        if (abilities == null) {
            return;
        }
        for (Trinket ability : abilities) {
            host.unsubscribe(ability);
        }
    }
}
